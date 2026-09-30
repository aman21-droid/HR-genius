package com.hrgenius.employee.service;

import com.hrgenius.auth.entity.Role;
import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.RefreshTokenRepository;
import com.hrgenius.auth.repository.RoleRepository;
import com.hrgenius.auth.repository.UserRepository;
import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import com.hrgenius.employee.dto.EmployeeDtos.*;
import com.hrgenius.employee.dto.EmployeeFilter;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.entity.EmployeeEnums.TimelineEventType;
import com.hrgenius.employee.mapper.EmployeeMapper;
import com.hrgenius.employee.repository.AssetRepository;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.org.entity.*;
import com.hrgenius.org.repository.*;
import com.hrgenius.org.service.CompanyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;

/**
 * Employee lifecycle: directory queries, profile reads with field-level visibility,
 * create/update with automatic timeline + audit entries, and guarded soft delete.
 */
@Slf4j
@Service
public class EmployeeService {

    private static final int MIN_AGE_AT_JOINING = 16;
    private static final int DEFAULT_PROBATION_MONTHS = 6;
    private static final int DEFAULT_NOTICE_DAYS = 30;
    private static final String AUDIT_ENTITY = "Employee";

    private final EmployeeRepository repository;
    private final EmployeeMapper mapper;
    private final EmployeeAccessService access;
    private final TimelineService timeline;
    private final AuditService audit;
    private final CompanyService companyService;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final LocationRepository locations;
    private final AssetRepository assets;
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    public EmployeeService(EmployeeRepository repository, EmployeeMapper mapper, EmployeeAccessService access,
                           TimelineService timeline, AuditService audit, CompanyService companyService,
                           DepartmentRepository departments, DesignationRepository designations,
                           GradeRepository grades, LocationRepository locations, AssetRepository assets,
                           UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
                           PasswordEncoder passwordEncoder, EntityManager em) {
        this.repository = repository;
        this.mapper = mapper;
        this.access = access;
        this.timeline = timeline;
        this.audit = audit;
        this.companyService = companyService;
        this.departments = departments;
        this.designations = designations;
        this.grades = grades;
        this.locations = locations;
        this.assets = assets;
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
    }

    // =================================================================== queries

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryDto> list(EmployeeFilter f, Pageable pageable) {
        Optional<Specification<Employee>> spec = specFor(f);
        if (spec.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
        }
        return PageResponse.from(repository.findAll(spec.get(), pageable).map(mapper::toSummary));
    }

    /** Same filters and visibility rules as the directory, unpaged (capped) for Excel export. */
    @Transactional(readOnly = true)
    public List<Employee> findForExport(EmployeeFilter f, int maxRows) {
        return specFor(f)
                .map(spec -> repository.findAll(spec, PageRequest.of(0, maxRows, Sort.by("employeeCode"))).getContent())
                .orElse(List.of());
    }

    /** Sets the manager of a just-imported employee (used for managers defined in the same file). */
    @Transactional
    public void assignManager(Long employeeId, Long managerId) {
        Employee e = find(employeeId);
        e.setManager(resolveManager(e, managerId));
    }

    /**
     * Builds the directory query for a filter, applying visibility rules. Empty when the result
     * is known to be empty (e.g. "my team" for someone with no reports).
     */
    private Optional<Specification<Employee>> specFor(EmployeeFilter f) {
        boolean fullAccess = access.hasFullAccess();
        Set<Long> team = f.teamOnly() ? access.myReportingTree() : null;
        if (team != null && team.isEmpty()) {
            return Optional.empty();
        }

        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), like),
                        cb.like(cb.lower(root.get("workEmail")), like),
                        cb.like(cb.lower(root.get("employeeCode")), like)));
            }
            if (f.departmentId() != null) p.add(cb.equal(root.get("department").get("id"), f.departmentId()));
            if (f.designationId() != null) p.add(cb.equal(root.get("designation").get("id"), f.designationId()));
            if (f.locationId() != null) p.add(cb.equal(root.get("location").get("id"), f.locationId()));
            if (f.gradeId() != null) p.add(cb.equal(root.get("grade").get("id"), f.gradeId()));
            if (f.managerId() != null) p.add(cb.equal(root.get("manager").get("id"), f.managerId()));
            if (f.employmentType() != null) p.add(cb.equal(root.get("employmentType"), f.employmentType()));

            // Leavers are only visible to full-access HR, and only when asked for.
            if (f.status() == EmployeeStatus.EXITED) {
                p.add(fullAccess ? cb.equal(root.get("status"), EmployeeStatus.EXITED) : cb.disjunction());
            } else if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            } else if (!(fullAccess && f.includeExited())) {
                p.add(cb.notEqual(root.get("status"), EmployeeStatus.EXITED));
            }
            if (team != null) p.add(root.get("id").in(team));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return Optional.of(spec);
    }

    /** Picker search over current employees (name, code or email). */
    @Transactional(readOnly = true)
    public List<EmployeeLookupDto> lookup(String q, int limit) {
        EmployeeFilter f = new EmployeeFilter(q, null, null, null, null, null, null, null, false, false);
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), 50), Sort.by("firstName", "lastName"));
        return list(f, page).content().stream()
                .map(s -> new EmployeeLookupDto(s.id(), s.employeeCode(), s.fullName(), s.designationName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeDetailDto get(Long id) {
        Employee e = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
        // Non-HR users cannot open leavers' profiles.
        if (e.getStatus() == EmployeeStatus.EXITED && !access.hasFullAccess()) {
            throw new ResourceNotFoundException("Employee", id);
        }
        return toDetail(e);
    }

    @Transactional(readOnly = true)
    public List<OrgChartNodeDto> orgChart() {
        List<EmployeeRepository.OrgChartRow> rows = repository.findOrgChartRows(EmployeeStatus.EXITED);
        Map<Long, Integer> reports = new HashMap<>();
        for (var r : rows) {
            if (r.getManagerId() != null) {
                reports.merge(r.getManagerId(), 1, Integer::sum);
            }
        }
        return rows.stream()
                .map(r -> new OrgChartNodeDto(r.getId(), r.getEmployeeCode(), r.getFirstName() + " " + r.getLastName(),
                        r.getDesignation(), r.getDepartment(), r.getLocation(), r.getManagerId(),
                        reports.getOrDefault(r.getId(), 0)))
                .toList();
    }

    // =================================================================== commands

    @Transactional
    public CreateEmployeeResponse create(EmployeeRequest req) {
        if (repository.existsByWorkEmailIgnoreCase(req.workEmail())) {
            throw new BusinessException("An employee with work email " + req.workEmail() + " already exists");
        }
        if (req.createLogin() && users.existsByEmailIgnoreCase(req.workEmail())) {
            throw new BusinessException("A login with email " + req.workEmail() + " already exists");
        }
        if (req.status() == EmployeeStatus.EXITED) {
            throw new BadRequestException("A new employee cannot be created in EXITED status");
        }

        Employee e = new Employee();
        e.setEmployeeCode(nextEmployeeCode());
        applyPersonal(e, req);
        applyJob(e, req, null);
        e.setStatus(req.status() == null ? EmployeeStatus.PROBATION : req.status());
        e.setProbationEndDate(req.probationEndDate() != null ? req.probationEndDate()
                : req.dateOfJoining().plusMonths(DEFAULT_PROBATION_MONTHS));
        e.setNoticePeriodDays(req.noticePeriodDays() != null ? req.noticePeriodDays() : DEFAULT_NOTICE_DAYS);
        if (access.canViewSensitive(null)) {        // no self yet on create: authority check only
            e.setAnnualCtc(req.annualCtc());
        }
        validateDates(e);
        repository.save(e);

        timeline.add(e.getId(), TimelineEventType.JOINED, e.getDateOfJoining(), "Joined the company",
                "Joined as " + nameOf(e.getDesignation()) + ", " + nameOf(e.getDepartment()));
        audit.record(AUDIT_ENTITY, e.getId(), AuditAction.CREATE, e.getEmployeeCode() + " " + e.getFullName());

        String tempPassword = null;
        if (req.createLogin()) {
            tempPassword = provisionLogin(e);
        }
        return new CreateEmployeeResponse(toDetail(e), req.createLogin() ? e.getWorkEmail() : null, tempPassword);
    }

    @Transactional
    public EmployeeDetailDto update(Long id, EmployeeRequest req) {
        Employee e = find(id);
        if (repository.existsByWorkEmailIgnoreCaseAndIdNot(req.workEmail(), id)) {
            throw new BusinessException("Another employee already uses work email " + req.workEmail());
        }
        if (req.status() == EmployeeStatus.EXITED && e.getStatus() != EmployeeStatus.EXITED) {
            throw new BadRequestException("Use the offboarding flow to exit an employee");
        }

        Snapshot before = Snapshot.of(e);
        applyPersonal(e, req);
        applyJob(e, req, before);
        if (req.status() != null) {
            e.setStatus(req.status());
        }
        if (req.probationEndDate() != null) e.setProbationEndDate(req.probationEndDate());
        if (req.noticePeriodDays() != null) e.setNoticePeriodDays(req.noticePeriodDays());
        // Editors without sensitive access receive annualCtc = null in their view; treating that
        // null as "clear the salary" would silently wipe it. Only honour it from those who can see it.
        if (access.canViewSensitive(id)) {
            e.setAnnualCtc(req.annualCtc());
        }
        validateDates(e);

        recordChanges(e, before);
        return toDetail(e);
    }

    /** Soft delete. Blocked while the employee still has direct reports or holds assets. */
    @Transactional
    public void delete(Long id) {
        Employee e = find(id);
        long reports = repository.countByManager_IdAndStatusNot(id, EmployeeStatus.EXITED);
        if (reports > 0) {
            throw new BusinessException("Reassign " + e.getFullName() + "'s " + reports
                    + " direct report(s) before deleting");
        }
        long held = assets.countByCurrentEmployeeId(id);
        if (held > 0) {
            throw new BusinessException("Recover the " + held + " asset(s) assigned to " + e.getFullName()
                    + " before deleting");
        }
        em.createQuery("update Department d set d.head = null where d.head.id = :id")
                .setParameter("id", id).executeUpdate();
        users.findByEmployeeId(id).ifPresent(u -> {
            u.setStatus(User.UserStatus.DISABLED);
            refreshTokens.revokeAllForUser(u.getId());
        });
        e.setDeleted(true);
        audit.record(AUDIT_ENTITY, id, AuditAction.DELETE, e.getEmployeeCode() + " " + e.getFullName());
    }

    // =================================================================== helpers

    /**
     * Loads a live employee. Checks the deleted flag explicitly because a by-id load is not
     * guaranteed to apply the entity's @SQLRestriction.
     */
    Employee find(Long id) {
        return repository.findById(id)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private EmployeeDetailDto toDetail(Employee e) {
        Long id = e.getId();
        ViewPolicy policy = new ViewPolicy(access.canViewFullProfile(id), access.canViewSensitive(id),
                access.canViewSensitive(id), access.canEdit());
        long directReports = repository.countByManager_IdAndStatusNot(id, EmployeeStatus.EXITED);
        return mapper.toDetail(e, policy, directReports, users.existsByEmployeeId(id));
    }

    private String nextEmployeeCode() {
        return companyService.employeeCodePrefix() + String.format("%04d", repository.nextEmployeeCodeNumber());
    }

    private static void applyPersonal(Employee e, EmployeeRequest r) {
        e.setFirstName(r.firstName().trim());
        e.setMiddleName(blankToNull(r.middleName()));
        e.setLastName(r.lastName().trim());
        e.setWorkEmail(r.workEmail().trim().toLowerCase());
        e.setPersonalEmail(blankToNull(r.personalEmail()));
        e.setPhone(blankToNull(r.phone()));
        e.setGender(r.gender());
        e.setDateOfBirth(r.dateOfBirth());
        e.setMaritalStatus(r.maritalStatus());
        e.setBloodGroup(blankToNull(r.bloodGroup()));
        e.setNationality(blankToNull(r.nationality()));
        e.setCurrentAddress(blankToNull(r.currentAddress()));
        e.setPermanentAddress(blankToNull(r.permanentAddress()));
    }

    /**
     * Resolves and assigns job references. A master must be active to be newly assigned, but an
     * employee may keep one that was deactivated after they were placed ({@code before} != null).
     */
    private void applyJob(Employee e, EmployeeRequest r, Snapshot before) {
        Department dept = resolve(departments.findById(r.departmentId()), "Department",
                before != null && Objects.equals(before.departmentId, r.departmentId()));
        e.setDepartment(dept);
        // Business unit and cost center follow the department.
        e.setBusinessUnit(dept.getBusinessUnit());
        e.setCostCenter(dept.getCostCenter());
        e.setDesignation(resolve(designations.findById(r.designationId()), "Designation",
                before != null && Objects.equals(before.designationId, r.designationId())));
        e.setGrade(r.gradeId() == null ? null : resolve(grades.findById(r.gradeId()), "Grade",
                before != null && Objects.equals(before.gradeId, r.gradeId())));
        e.setLocation(resolve(locations.findById(r.locationId()), "Location",
                before != null && Objects.equals(before.locationId, r.locationId())));
        e.setEmploymentType(r.employmentType());
        e.setDateOfJoining(r.dateOfJoining());
        e.setManager(resolveManager(e, r.managerId()));
    }

    private static <M extends MasterEntity> M resolve(Optional<M> found, String label, boolean unchanged) {
        M m = found.filter(x -> !x.isDeleted())
                .orElseThrow(() -> new BadRequestException(label + " not found"));
        if (!m.isActive() && !unchanged) {
            throw new BadRequestException(label + " '" + m.getName() + "' is inactive");
        }
        return m;
    }

    /** Manager must be a current employee and must not create a reporting loop. */
    private Employee resolveManager(Employee e, Long managerId) {
        if (managerId == null) {
            return null;
        }
        if (managerId.equals(e.getId())) {
            throw new BadRequestException("An employee cannot report to themselves");
        }
        Employee manager = find(managerId);
        if (manager.getStatus() == EmployeeStatus.EXITED) {
            throw new BadRequestException("The selected manager has left the organization");
        }
        if (e.getId() != null && access.reportingTree(e.getId()).contains(managerId)) {
            throw new BadRequestException(manager.getFullName() + " reports (directly or indirectly) to "
                    + e.getFullName() + ", so cannot be their manager");
        }
        return manager;
    }

    private static void validateDates(Employee e) {
        LocalDate doj = e.getDateOfJoining();
        if (doj.isAfter(LocalDate.now().plusYears(1))) {
            throw new BadRequestException("Date of joining cannot be more than a year in the future");
        }
        if (e.getDateOfBirth() != null && e.getDateOfBirth().plusYears(MIN_AGE_AT_JOINING).isAfter(doj)) {
            throw new BadRequestException("Employee must be at least " + MIN_AGE_AT_JOINING
                    + " years old on the date of joining");
        }
        if (e.getProbationEndDate() != null && e.getProbationEndDate().isBefore(doj)) {
            throw new BadRequestException("Probation end date cannot be before the date of joining");
        }
    }

    /** Emits timeline events and audit entries for meaningful job/compensation changes. */
    private void recordChanges(Employee e, Snapshot b) {
        LocalDate today = LocalDate.now();
        Long id = e.getId();

        if (!Objects.equals(b.departmentId, idOf(e.getDepartment()))) {
            timeline.add(id, TimelineEventType.TRANSFERRED, today, "Moved to " + nameOf(e.getDepartment()),
                    b.departmentName + " -> " + nameOf(e.getDepartment()));
            audit.recordChange(AUDIT_ENTITY, id, "department", b.departmentName, nameOf(e.getDepartment()));
        }
        if (!Objects.equals(b.locationId, idOf(e.getLocation()))) {
            timeline.add(id, TimelineEventType.TRANSFERRED, today, "Relocated to " + nameOf(e.getLocation()),
                    b.locationName + " -> " + nameOf(e.getLocation()));
            audit.recordChange(AUDIT_ENTITY, id, "location", b.locationName, nameOf(e.getLocation()));
        }

        Integer newLevel = e.getGrade() == null ? null : e.getGrade().getLevelNo();
        boolean promoted = b.gradeLevel != null && newLevel != null && newLevel > b.gradeLevel;
        boolean designationChanged = !Objects.equals(b.designationId, idOf(e.getDesignation()));
        if (promoted) {
            timeline.add(id, TimelineEventType.PROMOTED, today, "Promoted to " + nameOf(e.getDesignation()),
                    b.designationName + " -> " + nameOf(e.getDesignation())
                            + " (" + b.gradeCode + " -> " + e.getGrade().getCode() + ")");
        } else if (designationChanged) {
            timeline.add(id, TimelineEventType.DESIGNATION_CHANGED, today,
                    "Designation changed to " + nameOf(e.getDesignation()),
                    b.designationName + " -> " + nameOf(e.getDesignation()));
        }
        audit.recordChange(AUDIT_ENTITY, id, "designation", b.designationName, nameOf(e.getDesignation()));
        audit.recordChange(AUDIT_ENTITY, id, "grade", b.gradeCode, e.getGrade() == null ? null : e.getGrade().getCode());

        if (!Objects.equals(b.managerId, idOf(e.getManager()))) {
            String newManager = e.getManager() == null ? "no manager" : e.getManager().getFullName();
            timeline.add(id, TimelineEventType.MANAGER_CHANGED, today, "Now reports to " + newManager,
                    (b.managerName == null ? "no manager" : b.managerName) + " -> " + newManager);
            audit.recordChange(AUDIT_ENTITY, id, "manager", b.managerName,
                    e.getManager() == null ? null : e.getManager().getFullName());
        }
        if (b.status != e.getStatus()) {
            if (b.status == EmployeeStatus.PROBATION && e.getStatus() == EmployeeStatus.ACTIVE) {
                if (e.getConfirmationDate() == null) {
                    e.setConfirmationDate(today);
                }
                timeline.add(id, TimelineEventType.CONFIRMED, today, "Probation completed",
                        "Confirmed as a permanent employee");
            } else {
                timeline.add(id, TimelineEventType.STATUS_CHANGED, today, "Status changed to " + e.getStatus(),
                        b.status + " -> " + e.getStatus());
            }
            audit.recordChange(AUDIT_ENTITY, id, "status", b.status, e.getStatus());
        }
        audit.recordChange(AUDIT_ENTITY, id, "employmentType", b.employmentType, e.getEmploymentType());
        audit.recordChange(AUDIT_ENTITY, id, "workEmail", b.workEmail, e.getWorkEmail());

        if (!sameAmount(b.annualCtc, e.getAnnualCtc())) {
            // Amounts go to the audit trail (HR-only); the timeline, visible to managers, stays vague.
            timeline.add(id, TimelineEventType.SALARY_REVISED, today, "Compensation revised", null);
            audit.recordChange(AUDIT_ENTITY, id, "annualCtc", b.annualCtc, e.getAnnualCtc());
        }
    }

    private String provisionLogin(Employee e) {
        Role employeeRole = roles.findByCode("EMPLOYEE")
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing from seed data"));
        String tempPassword = generateTemporaryPassword();
        User user = new User();
        user.setEmail(e.getWorkEmail());
        user.setFullName(e.getFullName());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setEmployeeId(e.getId());
        user.setRoles(new HashSet<>(Set.of(employeeRole)));
        users.save(user);
        audit.record("User", user.getId(), AuditAction.CREATE, "Login provisioned for " + e.getEmployeeCode());
        return tempPassword;
    }

    /** 14 chars with at least one upper, lower, digit and symbol; ambiguous glyphs excluded. */
    static String generateTemporaryPassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnpqrstuvwxyz";
        final String digits = "23456789";
        final String symbols = "@#$%&*!?";
        final String all = upper + lower + digits + symbols;
        SecureRandom rnd = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        for (String set : List.of(upper, lower, digits, symbols)) {
            chars.add(set.charAt(rnd.nextInt(set.length())));
        }
        while (chars.size() < 14) {
            chars.add(all.charAt(rnd.nextInt(all.length())));
        }
        Collections.shuffle(chars, rnd);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static Long idOf(MasterEntity m) {
        return m == null ? null : m.getId();
    }

    private static Long idOf(Employee e) {
        return e == null ? null : e.getId();
    }

    private static String nameOf(MasterEntity m) {
        return m == null ? "-" : m.getName();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Job/compensation state captured before an update, for diffing. */
    private record Snapshot(Long departmentId, String departmentName, Long designationId, String designationName,
                            Long gradeId, String gradeCode, Integer gradeLevel, Long locationId, String locationName,
                            Long managerId, String managerName, EmployeeStatus status,
                            Object employmentType, String workEmail, BigDecimal annualCtc) {
        static Snapshot of(Employee e) {
            return new Snapshot(
                    idOf(e.getDepartment()), nameOf(e.getDepartment()),
                    idOf(e.getDesignation()), nameOf(e.getDesignation()),
                    idOf(e.getGrade()), e.getGrade() == null ? null : e.getGrade().getCode(),
                    e.getGrade() == null ? null : e.getGrade().getLevelNo(),
                    idOf(e.getLocation()), nameOf(e.getLocation()),
                    idOf(e.getManager()), e.getManager() == null ? null : e.getManager().getFullName(),
                    e.getStatus(), e.getEmploymentType(), e.getWorkEmail(), e.getAnnualCtc());
        }
    }
}
