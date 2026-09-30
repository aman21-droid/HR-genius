package com.hrgenius.payroll.service;

import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.service.ApprovalOutcomeHandler;
import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.approval.service.Approver;
import com.hrgenius.approval.service.ApproverResolver;
import com.hrgenius.attendance.repository.HolidayRepository;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.util.MaskingUtil;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.TaxRegime;
import com.hrgenius.employee.entity.EmployeeStatutory;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.employee.repository.EmployeeStatutoryRepository;
import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import com.hrgenius.leave.entity.LeaveRequest;
import com.hrgenius.leave.repository.LeaveRequestRepository;
import com.hrgenius.payroll.calc.PayrollCalculator;
import com.hrgenius.payroll.calc.PayrollCalculator.*;
import com.hrgenius.payroll.calc.TaxCalculator.Regime;
import com.hrgenius.payroll.dto.PayrollDtos.*;
import com.hrgenius.payroll.entity.*;
import com.hrgenius.payroll.entity.PayrollAdjustment.AdjustmentType;
import com.hrgenius.payroll.entity.PayrollRun.RunStatus;
import com.hrgenius.payroll.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Monthly payroll lifecycle. Calculation (re)generates every payslip of a run from the current
 * salary structure, each employee's CTC, their joining/exit dates and approved loss-of-pay leave.
 * Submitting sends the run through the approval engine (an account holding PAYROLL_APPROVE);
 * approval publishes the payslips to employees.
 */
@Service
public class PayrollService implements ApprovalOutcomeHandler {

    public static final String SUBJECT_TYPE = "PAYROLL_RUN";
    static final String APPROVE_PERMISSION = "PAYROLL_APPROVE";
    /** Leave type whose approved days are deducted as loss of pay. */
    static final String LOP_LEAVE_CODE = "LWP";
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayrollAdjustmentRepository adjustments;
    private final SalaryComponentRepository components;
    private final EmployeeRepository employees;
    private final EmployeeStatutoryRepository statutory;
    private final LeaveRequestRepository leaveRequests;
    private final HolidayRepository holidays;
    private final ApprovalService approvals;
    private final ApproverResolver approverResolver;

    public PayrollService(PayrollRunRepository runs, PayslipRepository payslips, PayrollAdjustmentRepository adjustments,
                          SalaryComponentRepository components, EmployeeRepository employees,
                          EmployeeStatutoryRepository statutory, LeaveRequestRepository leaveRequests,
                          HolidayRepository holidays, ApprovalService approvals, ApproverResolver approverResolver) {
        this.runs = runs;
        this.payslips = payslips;
        this.adjustments = adjustments;
        this.components = components;
        this.employees = employees;
        this.statutory = statutory;
        this.leaveRequests = leaveRequests;
        this.holidays = holidays;
        this.approvals = approvals;
        this.approverResolver = approverResolver;
    }

    // ================================================================ runs

    @Transactional(readOnly = true)
    public List<RunDto> list() {
        return runs.findAllByOrderByPeriodDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RunDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public RunDto create(CreateRunRequest req) {
        if (runs.existsByPeriod(req.period())) {
            throw new BusinessException("A payroll run for " + req.period() + " already exists");
        }
        YearMonth ym = YearMonth.parse(req.period());
        if (ym.isAfter(YearMonth.now().plusMonths(1))) {
            throw new BadRequestException("Payroll can be prepared at most one month ahead");
        }
        PayrollRun r = new PayrollRun();
        r.setPeriod(req.period());
        r.setPeriodStart(ym.atDay(1));
        r.setPeriodEnd(ym.atEndOfMonth());
        r.setStatus(RunStatus.DRAFT);
        r.setNotes(blankToNull(req.notes()));
        runs.save(r);
        return toDto(r);
    }

    /** DRAFT or CALCULATED -> CALCULATED: throws away existing payslips and computes them afresh. */
    @Transactional
    public RunDto calculate(Long id) {
        PayrollRun r = find(id);
        if (r.getStatus() != RunStatus.DRAFT && r.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessException("Only a draft or calculated run can be (re)calculated");
        }
        payslips.deleteLinesByRun(r.getId());
        payslips.deleteByRun(r.getId());
        payslips.flush();

        List<Component> structure = structure();
        Map<Long, List<PayrollAdjustment>> adjByEmployee = new HashMap<>();
        for (PayrollAdjustment a : adjustments.findByRun(r.getId())) {
            adjByEmployee.computeIfAbsent(a.getEmployee().getId(), k -> new ArrayList<>()).add(a);
        }
        Set<LocalDate> holidayDates = new HashSet<>();
        holidays.findByOptionalHolidayFalseAndHolidayDateBetween(r.getPeriodStart(), r.getPeriodEnd())
                .forEach(h -> holidayDates.add(h.getHolidayDate()));

        int count = 0;
        BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO, net = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        int daysInPeriod = r.getPeriodEnd().getDayOfMonth();
        for (Employee e : employees.findPayrollEligible(r.getPeriodStart(), r.getPeriodEnd())) {
            LocalDate from = max(r.getPeriodStart(), e.getDateOfJoining());
            LocalDate to = e.getExitDate() != null ? min(r.getPeriodEnd(), e.getExitDate()) : r.getPeriodEnd();
            BigDecimal activeDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1);
            BigDecimal lop = lossOfPayDays(e.getId(), from, to, holidayDates).min(activeDays);
            BigDecimal paidDays = activeDays.subtract(lop);

            EmployeeStatutory st = statutory.findById(e.getId()).orElse(null);
            Regime regime = st != null && st.getTaxRegime() == TaxRegime.OLD ? Regime.OLD : Regime.NEW;
            List<Adjustment> adj = adjByEmployee.getOrDefault(e.getId(), List.of()).stream()
                    .map(a -> new Adjustment(a.getAdjustmentType() == AdjustmentType.EARNING, a.getLabel(),
                            a.getAmount(), a.isTaxable()))
                    .toList();
            Result res = PayrollCalculator.calculate(new Input(e.getAnnualCtc(), structure, daysInPeriod, paidDays, regime, adj));

            Payslip p = new Payslip();
            p.setRun(r);
            p.setEmployee(e);
            p.setDaysInPeriod(daysInPeriod);
            p.setLopDays(lop);
            p.setPaidDays(paidDays);
            p.setAnnualCtc(e.getAnnualCtc());
            p.setGrossEarnings(res.gross());
            p.setTotalDeductions(res.deductions());
            p.setNetPay(res.net());
            p.setEmployerPf(res.employerPf());
            p.setEmployerEsi(res.employerEsi());
            p.setTaxRegime(regime.name());
            if (st != null) {
                p.setBankName(st.getBankName());
                p.setAccountMasked(st.getBankAccountNumber() == null ? null : MaskingUtil.mask(st.getBankAccountNumber(), 4));
                p.setBankIfsc(st.getBankIfsc());
            }
            int order = 0;
            for (Line l : res.lines()) {
                PayslipLine line = new PayslipLine();
                line.setCode(l.code());
                line.setName(l.name());
                line.setLineType(l.type());
                line.setAmount(l.amount());
                line.setSortOrder(order++);
                p.addLine(line);
            }
            payslips.save(p);

            count++;
            gross = gross.add(res.gross());
            deductions = deductions.add(res.deductions());
            net = net.add(res.net());
            cost = cost.add(res.employerCost());
        }
        r.setEmployeeCount(count);
        r.setTotalGross(gross);
        r.setTotalDeductions(deductions);
        r.setTotalNet(net);
        r.setTotalEmployerCost(cost);
        r.setCalculatedAt(Instant.now());
        r.setStatus(RunStatus.CALCULATED);
        return toDto(r);
    }

    /** CALCULATED -> PENDING_APPROVAL (or straight to APPROVED when nobody else can approve). */
    @Transactional
    public RunDto submit(Long id, Long requesterEmpId) {
        PayrollRun r = find(id);
        if (r.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessException("Calculate the run before submitting it for approval");
        }
        if (r.getEmployeeCount() == 0) {
            throw new BusinessException("This run has no payslips to approve");
        }
        r.setStatus(RunStatus.PENDING_APPROVAL);
        List<Approver> chain = approverResolver.chainOf(requesterEmpId, null, null, APPROVE_PERMISSION);
        String title = "Payroll · " + MONTH.format(r.getPeriodStart()) + " · " + r.getEmployeeCount()
                + " employees · net INR " + r.getTotalNet().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        ApprovalRequest approval = approvals.submit(SUBJECT_TYPE, r.getId(), requesterEmpId, title, chain);
        r.setApprovalRequestId(approval.getId());
        return toDto(r);
    }

    /** APPROVED -> PAID once salaries have been transferred. */
    @Transactional
    public RunDto markPaid(Long id, MarkPaidRequest req) {
        PayrollRun r = find(id);
        if (r.getStatus() != RunStatus.APPROVED) {
            throw new BusinessException("Only an approved run can be marked as paid");
        }
        r.setStatus(RunStatus.PAID);
        r.setPaidAt(Instant.now());
        r.setPaymentReference(req.paymentReference().trim());
        return toDto(r);
    }

    /** Deletes an unapproved run with its payslips and adjustments. */
    @Transactional
    public void delete(Long id) {
        PayrollRun r = find(id);
        if (r.getStatus() != RunStatus.DRAFT && r.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessException("Only a draft or calculated run can be deleted");
        }
        payslips.deleteLinesByRun(r.getId());
        payslips.deleteByRun(r.getId());
        adjustments.deleteAll(adjustments.findByRun(r.getId()));
        runs.delete(r);
    }

    // ================================================================ payslip table

    @Transactional(readOnly = true)
    public List<PayslipSummaryDto> payslips(Long runId) {
        find(runId);
        return payslips.findByRun(runId).stream().map(p -> {
            Employee e = p.getEmployee();
            return new PayslipSummaryDto(p.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null,
                    e.getDesignation() != null ? e.getDesignation().getName() : null,
                    p.getPaidDays(), p.getLopDays(), p.getGrossEarnings(), p.getTotalDeductions(), p.getNetPay(),
                    p.getAccountMasked() == null || p.getBankIfsc() == null);
        }).toList();
    }

    // ================================================================ adjustments

    @Transactional(readOnly = true)
    public List<AdjustmentDto> adjustments(Long runId) {
        find(runId);
        return adjustments.findByRun(runId).stream().map(PayrollService::toDto).toList();
    }

    /** Adding or removing an adjustment invalidates the calculation, so the run returns to DRAFT. */
    @Transactional
    public AdjustmentDto addAdjustment(Long runId, AdjustmentRequest req) {
        PayrollRun r = requireEditable(runId);
        Employee e = employees.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + req.employeeId() + " not found"));
        PayrollAdjustment a = new PayrollAdjustment();
        a.setRun(r);
        a.setEmployee(e);
        a.setAdjustmentType(req.type());
        a.setLabel(req.label().trim());
        a.setAmount(req.amount());
        a.setTaxable(req.taxable() == null || req.taxable());
        adjustments.save(a);
        r.setStatus(RunStatus.DRAFT);
        return toDto(a);
    }

    @Transactional
    public void removeAdjustment(Long runId, Long adjustmentId) {
        PayrollRun r = requireEditable(runId);
        PayrollAdjustment a = adjustments.findById(adjustmentId)
                .filter(x -> x.getRun().getId().equals(runId))
                .orElseThrow(() -> new ResourceNotFoundException("Adjustment " + adjustmentId + " not found"));
        adjustments.delete(a);
        r.setStatus(RunStatus.DRAFT);
    }

    // ================================================================ salary structure

    @Transactional(readOnly = true)
    public List<ComponentDto> components() {
        return components.findAllByOrderBySortOrderAscIdAsc().stream().map(PayrollService::toDto).toList();
    }

    @Transactional
    public ComponentDto createComponent(ComponentRequest req) {
        if (components.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessException("Component " + req.code() + " already exists");
        }
        SalaryComponent c = new SalaryComponent();
        c.setCode(req.code());
        applyComponent(c, req);
        components.save(c);
        validateStructure();
        return toDto(c);
    }

    @Transactional
    public ComponentDto updateComponent(Long id, ComponentRequest req) {
        SalaryComponent c = components.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component " + id + " not found"));
        if (!c.getCode().equals(req.code())) {
            throw new BadRequestException("A component's code cannot be changed");
        }
        applyComponent(c, req);
        validateStructure();
        return toDto(c);
    }

    @Transactional
    public void deleteComponent(Long id) {
        SalaryComponent c = components.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component " + id + " not found"));
        if ("BASIC".equals(c.getCode()) || c.getCalcType() == CalcType.BALANCING) {
            throw new BusinessException(c.getName() + " is required by the salary structure and cannot be deleted");
        }
        c.setDeleted(true);
    }

    /** The signed-in employee's full-month structure from their current CTC. */
    @Transactional(readOnly = true)
    public StructureDto myStructure(Long employeeId) {
        Employee e = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + employeeId + " not found"));
        if (e.getAnnualCtc() == null || e.getAnnualCtc().signum() <= 0) {
            throw new BusinessException("No CTC is recorded for you yet; please contact HR");
        }
        EmployeeStatutory st = statutory.findById(employeeId).orElse(null);
        Regime regime = st != null && st.getTaxRegime() == TaxRegime.OLD ? Regime.OLD : Regime.NEW;
        Result res = PayrollCalculator.calculate(new Input(e.getAnnualCtc(), structure(), 30, BigDecimal.valueOf(30),
                regime, List.of()));
        return new StructureDto(e.getAnnualCtc(), res.gross(), res.net(), regime.name(),
                lines(res.lines(), LineType.EARNING), lines(res.lines(), LineType.DEDUCTION),
                lines(res.lines(), LineType.EMPLOYER));
    }

    // ================================================================ approval callbacks

    @Override
    public String subjectType() {
        return SUBJECT_TYPE;
    }

    @Override
    @Transactional
    public void onApproved(ApprovalRequest request) {
        runs.findById(request.getSubjectId())
                .filter(r -> r.getStatus() == RunStatus.PENDING_APPROVAL)
                .ifPresent(r -> {
                    r.setStatus(RunStatus.APPROVED);
                    r.setApprovedAt(Instant.now());
                });
    }

    @Override
    @Transactional
    public void onRejected(ApprovalRequest request, String reason) {
        runs.findById(request.getSubjectId())
                .filter(r -> r.getStatus() == RunStatus.PENDING_APPROVAL)
                .ifPresent(r -> {
                    r.setStatus(RunStatus.CALCULATED);
                    if (reason != null && !reason.isBlank()) {
                        r.setNotes(("Returned by approver: " + reason.trim()));
                    }
                });
    }

    // ================================================================ helpers

    /** Approved LWP days falling on working days within [from, to], honouring half-day flags. */
    BigDecimal lossOfPayDays(Long employeeId, LocalDate from, LocalDate to, Set<LocalDate> holidayDates) {
        BigDecimal lop = BigDecimal.ZERO;
        List<LeaveRequest> approved = leaveRequests
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, LeaveStatus.APPROVED, to, from);
        for (LeaveRequest lr : approved) {
            if (!LOP_LEAVE_CODE.equals(lr.getLeaveType().getCode())) {
                continue;
            }
            for (LocalDate d = max(from, lr.getStartDate()); !d.isAfter(min(to, lr.getEndDate())); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY
                        || holidayDates.contains(d)) {
                    continue;
                }
                boolean half = (d.equals(lr.getStartDate()) && lr.isHalfDayStart())
                        || (d.equals(lr.getEndDate()) && !lr.getEndDate().equals(lr.getStartDate()) && lr.isHalfDayEnd());
                lop = lop.add(half ? HALF : BigDecimal.ONE);
            }
        }
        return lop;
    }

    List<Component> structure() {
        return components.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(c -> new Component(c.getCode(), c.getName(), c.getCalcType(), c.getCalcValue(), c.isTaxable()))
                .toList();
    }

    PayrollRun find(Long id) {
        return runs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payroll run " + id + " not found"));
    }

    private PayrollRun requireEditable(Long runId) {
        PayrollRun r = find(runId);
        if (r.getStatus() != RunStatus.DRAFT && r.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessException("Adjustments can only change before the run is submitted for approval");
        }
        return r;
    }

    private void applyComponent(SalaryComponent c, ComponentRequest req) {
        if (req.calcType() == CalcType.PERCENT_OF_CTC || req.calcType() == CalcType.PERCENT_OF_BASIC) {
            if (req.calcValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("A percentage cannot exceed 100");
            }
        }
        c.setName(req.name().trim());
        c.setCalcType(req.calcType());
        c.setCalcValue(req.calcType() == CalcType.BALANCING ? BigDecimal.ZERO : req.calcValue());
        c.setTaxable(req.taxable() == null || req.taxable());
        c.setSortOrder(req.sortOrder());
        c.setActive(req.active() == null || req.active());
    }

    /** BASIC must be an active % of CTC, and exactly one active BALANCING component must exist. */
    private void validateStructure() {
        components.flush();
        List<SalaryComponent> active = components.findByActiveTrueOrderBySortOrderAscIdAsc();
        boolean basicOk = active.stream().anyMatch(c -> "BASIC".equals(c.getCode()) && c.getCalcType() == CalcType.PERCENT_OF_CTC);
        long balancing = active.stream().filter(c -> c.getCalcType() == CalcType.BALANCING).count();
        if (!basicOk) {
            throw new BusinessException("BASIC must stay active and be a percentage of CTC");
        }
        if (balancing != 1) {
            throw new BusinessException("The structure needs exactly one active balancing component (found " + balancing + ")");
        }
    }

    private RunDto toDto(PayrollRun r) {
        return new RunDto(r.getId(), r.getPeriod(), r.getPeriodStart(), r.getPeriodEnd(), r.getStatus().name(),
                r.getEmployeeCount(), r.getTotalGross(), r.getTotalDeductions(), r.getTotalNet(), r.getTotalEmployerCost(),
                r.getApprovalRequestId(), r.getCalculatedAt(), r.getApprovedAt(), r.getPaidAt(), r.getPaymentReference(),
                r.getNotes(), adjustments.findByRun(r.getId()).size(), r.getCreatedAt());
    }

    private static AdjustmentDto toDto(PayrollAdjustment a) {
        Employee e = a.getEmployee();
        return new AdjustmentDto(a.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(), a.getAdjustmentType().name(),
                a.getLabel(), a.getAmount(), a.isTaxable());
    }

    private static ComponentDto toDto(SalaryComponent c) {
        return new ComponentDto(c.getId(), c.getCode(), c.getName(), c.getCalcType().name(), c.getCalcValue(),
                c.isTaxable(), c.getSortOrder(), c.isActive());
    }

    static List<PayslipLineDto> lines(List<Line> lines, LineType type) {
        return lines.stream().filter(l -> l.type() == type)
                .map(l -> new PayslipLineDto(l.code(), l.name(), l.type().name(), l.amount())).toList();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
