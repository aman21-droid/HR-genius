package com.hrgenius.org.service;

import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.org.dto.OrgDtos.CompanyDto;
import com.hrgenius.org.dto.OrgDtos.CompanyRequest;
import com.hrgenius.org.entity.Company;
import com.hrgenius.org.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The single company profile row (created by seed data). */
@Service
public class CompanyService {

    public static final String DEFAULT_CODE_PREFIX = "EMP";

    private final CompanyRepository repository;

    public CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanyDto get() {
        return toDto(load());
    }

    @Transactional
    public CompanyDto update(CompanyRequest req) {
        Company c = load();
        c.setName(req.name().trim());
        c.setLegalName(req.legalName());
        c.setRegistrationNo(req.registrationNo());
        c.setWebsite(req.website());
        c.setCountry(req.country());
        c.setCurrency(req.currency());
        c.setFyStartMonth(req.fyStartMonth());
        c.setEmployeeCodePrefix(req.employeeCodePrefix());
        return toDto(c);
    }

    /** Prefix for new employee codes, falling back to "EMP". */
    @Transactional(readOnly = true)
    public String employeeCodePrefix() {
        return repository.findFirstByOrderByIdAsc()
                .map(Company::getEmployeeCodePrefix)
                .filter(p -> !p.isBlank())
                .orElse(DEFAULT_CODE_PREFIX);
    }

    private Company load() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Company profile has not been set up"));
    }

    private static CompanyDto toDto(Company c) {
        return new CompanyDto(c.getId(), c.getName(), c.getLegalName(), c.getRegistrationNo(), c.getWebsite(),
                c.getCountry(), c.getCurrency(), c.getFyStartMonth(), c.getEmployeeCodePrefix());
    }
}
