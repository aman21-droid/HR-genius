package com.hrgenius.employee.service;

import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.employee.dto.ProfileDtos.EmergencyContactDto;
import com.hrgenius.employee.dto.ProfileDtos.EmergencyContactRequest;
import com.hrgenius.employee.entity.EmergencyContact;
import com.hrgenius.employee.repository.EmergencyContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Emergency contacts. Employees manage their own; HR can manage anyone's. One primary max. */
@Service
public class EmergencyContactService {

    private static final int MAX_CONTACTS = 5;

    private final EmergencyContactRepository repository;
    private final EmployeeService employeeService;
    private final EmployeeAccessService access;

    public EmergencyContactService(EmergencyContactRepository repository, EmployeeService employeeService,
                                   EmployeeAccessService access) {
        this.repository = repository;
        this.employeeService = employeeService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactDto> list(Long employeeId) {
        employeeService.find(employeeId);
        access.requireFullProfile(employeeId);
        return repository.findByEmployeeIdOrderByPrimaryDescNameAsc(employeeId).stream()
                .map(EmergencyContactService::toDto).toList();
    }

    @Transactional
    public EmergencyContactDto create(Long employeeId, EmergencyContactRequest req) {
        employeeService.find(employeeId);
        access.requireSelfOrEditor(employeeId);
        List<EmergencyContact> existing = repository.findByEmployeeIdOrderByPrimaryDescNameAsc(employeeId);
        if (existing.size() >= MAX_CONTACTS) {
            throw new BusinessException("At most " + MAX_CONTACTS + " emergency contacts are allowed");
        }
        EmergencyContact c = new EmergencyContact();
        c.setEmployeeId(employeeId);
        apply(c, req);
        // The first contact is primary by default.
        c.setPrimary(req.primary() || existing.isEmpty());
        repository.save(c);
        if (c.isPrimary()) {
            repository.clearPrimaryExcept(employeeId, c.getId());
        }
        return toDto(c);
    }

    @Transactional
    public EmergencyContactDto update(Long employeeId, Long contactId, EmergencyContactRequest req) {
        access.requireSelfOrEditor(employeeId);
        EmergencyContact c = find(employeeId, contactId);
        apply(c, req);
        c.setPrimary(req.primary());
        if (c.isPrimary()) {
            repository.clearPrimaryExcept(employeeId, c.getId());
        }
        return toDto(c);
    }

    @Transactional
    public void delete(Long employeeId, Long contactId) {
        access.requireSelfOrEditor(employeeId);
        EmergencyContact c = find(employeeId, contactId);
        c.setDeleted(true);
    }

    private EmergencyContact find(Long employeeId, Long contactId) {
        return repository.findByIdAndEmployeeId(contactId, employeeId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact", contactId));
    }

    private static void apply(EmergencyContact c, EmergencyContactRequest r) {
        c.setName(r.name().trim());
        c.setRelationship(r.relationship().trim());
        c.setPhone(r.phone().trim());
        c.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim());
    }

    private static EmergencyContactDto toDto(EmergencyContact c) {
        return new EmergencyContactDto(c.getId(), c.getName(), c.getRelationship(), c.getPhone(),
                c.getEmail(), c.isPrimary());
    }
}
