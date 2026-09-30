package com.hrgenius.recruitment.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.employee.dto.EmployeeDtos.CreateEmployeeResponse;
import com.hrgenius.employee.dto.EmployeeDtos.EmployeeRequest;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.employee.service.EmployeeService;
import com.hrgenius.onboarding.service.OnboardingService;
import com.hrgenius.recruitment.dto.RecruitmentDtos.HireRequest;
import com.hrgenius.recruitment.dto.RecruitmentDtos.HireResponse;
import com.hrgenius.recruitment.entity.Candidate;
import com.hrgenius.recruitment.entity.JobApplication;
import com.hrgenius.recruitment.entity.JobRequisition;
import com.hrgenius.recruitment.entity.Offer;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationEventType;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.OfferStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Converts an accepted offer into an employee: creates the employee (and optionally a login) via the
 * Core HR service, marks the application HIRED, counts the hire against the requisition, and starts
 * onboarding from a template. All in one transaction, so a failure leaves nothing half-done.
 */
@Service
public class HireService {

    private final OfferService offerService;
    private final ApplicationService applicationService;
    private final RequisitionService requisitionService;
    private final EmployeeService employeeService;
    private final EmployeeRepository employees;
    private final OnboardingService onboardingService;

    public HireService(OfferService offerService, ApplicationService applicationService,
                       RequisitionService requisitionService, EmployeeService employeeService,
                       EmployeeRepository employees, OnboardingService onboardingService) {
        this.offerService = offerService;
        this.applicationService = applicationService;
        this.requisitionService = requisitionService;
        this.employeeService = employeeService;
        this.employees = employees;
        this.onboardingService = onboardingService;
    }

    @Transactional
    public HireResponse hire(Long offerId, HireRequest req, Long actingEmpId) {
        Offer o = offerService.find(offerId);
        if (o.getStatus() != OfferStatus.ACCEPTED) {
            throw new BusinessException("Only an accepted offer can be converted into an employee");
        }
        JobApplication a = o.getApplication();
        if (a.getEmployeeId() != null || a.getStage() == ApplicationStage.HIRED) {
            throw new BusinessException("This candidate has already been converted into an employee");
        }
        Candidate c = a.getCandidate();
        JobRequisition r = a.getRequisition();
        LocalDate joining = req.dateOfJoining() != null ? req.dateOfJoining() : o.getJoiningDate();
        if (joining.isBefore(LocalDate.now().minusDays(30))) {
            throw new BadRequestException("Joining date is too far in the past");
        }
        Long managerId = req.managerId() != null ? req.managerId() : r.getHiringManager().getId();
        boolean createLogin = req.createLogin() == null || req.createLogin();

        EmployeeRequest er = new EmployeeRequest(
                c.getFirstName(), null, c.getLastName(), req.workEmail().trim(), c.getEmail(), c.getPhone(),
                null, null, null, null, null, null, null,
                o.getDepartment().getId(), o.getDesignation().getId(),
                o.getGrade() != null ? o.getGrade().getId() : null, o.getLocation().getId(), managerId,
                // probation end and notice period fall back to company defaults
                o.getEmploymentType(), EmployeeStatus.PROBATION, joining, null, null, o.getAnnualCtc(), createLogin);
        CreateEmployeeResponse created = employeeService.create(er);
        Long employeeId = created.employee().id();

        // CTC was approved on the offer; record it even if the acting user can't view compensation
        // (EmployeeService only writes CTC for EMPLOYEE_SENSITIVE_READ holders).
        employees.findById(employeeId).ifPresent(e -> e.setAnnualCtc(o.getAnnualCtc()));

        a.setEmployeeId(employeeId);
        a.setStage(ApplicationStage.HIRED);
        a.setStageChangedAt(Instant.now());
        requisitionService.recordHire(r);
        applicationService.log(a, ApplicationEventType.HIRED, ApplicationStage.OFFER, ApplicationStage.HIRED,
                "Converted to employee " + created.employee().employeeCode(), applicationService.actorName());

        Long planId = null;
        if (req.startOnboarding() == null || req.startOnboarding()) {
            planId = onboardingService.start(employeeId, req.onboardingTemplateId(), a.getId(), actingEmpId).id();
        }
        return new HireResponse(employeeId, created.employee().employeeCode(), created.employee().fullName(),
                created.loginEmail(), created.temporaryPassword(), planId);
    }
}
