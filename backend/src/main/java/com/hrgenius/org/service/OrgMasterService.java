package com.hrgenius.org.service;

import com.hrgenius.common.config.CacheConfig;
import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.org.dto.OrgDtos.*;
import com.hrgenius.org.entity.*;
import com.hrgenius.org.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;

/**
 * CRUD for all six org master types behind one API. Type-specific behaviour (department
 * links, grade level, location address) is isolated in {@link #applyTypeSpecific}.
 */
@Service
public class OrgMasterService {

    private final Map<MasterType, MasterRepository<? extends MasterEntity>> repositories;
    private final DepartmentRepository departmentRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final CostCenterRepository costCenterRepository;
    private final EmployeeRepository employeeRepository;
    private final EntityManager em;

    public OrgMasterService(BusinessUnitRepository businessUnits, CostCenterRepository costCenters,
                            DepartmentRepository departments, DesignationRepository designations,
                            GradeRepository grades, LocationRepository locations,
                            EmployeeRepository employeeRepository, EntityManager em) {
        this.repositories = new EnumMap<>(MasterType.class);
        repositories.put(MasterType.BUSINESS_UNIT, businessUnits);
        repositories.put(MasterType.COST_CENTER, costCenters);
        repositories.put(MasterType.DEPARTMENT, departments);
        repositories.put(MasterType.DESIGNATION, designations);
        repositories.put(MasterType.GRADE, grades);
        repositories.put(MasterType.LOCATION, locations);
        this.departmentRepository = departments;
        this.businessUnitRepository = businessUnits;
        this.costCenterRepository = costCenters;
        this.employeeRepository = employeeRepository;
        this.em = em;
    }

    @SuppressWarnings("unchecked")
    private <E extends MasterEntity> MasterRepository<E> repo(MasterType type) {
        return (MasterRepository<E>) repositories.get(type);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<MasterDto> list(MasterType type, String search, Boolean active, Pageable pageable) {
        Specification<MasterEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<MasterEntity> page = this.<MasterEntity>repo(type).findAll(spec, pageable);
        Map<Long, Long> counts = employeeCounts(type);
        return PageResponse.from(page.map(e -> toDto(e, counts.getOrDefault(e.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public MasterDto get(MasterType type, Long id) {
        MasterEntity entity = find(type, id);
        return toDto(entity, employeeCounts(type).getOrDefault(id, 0L));
    }

    /** All active masters for dropdowns; cached and evicted on any master write. */
    @Cacheable(value = CacheConfig.LOOKUPS, key = "'org-lookups'")
    @Transactional(readOnly = true)
    public OrgLookups lookups() {
        Function<MasterType, List<LookupItem>> items = t -> this.<MasterEntity>repo(t)
                .findAllByActiveTrueOrderByNameAsc().stream()
                .map(e -> new LookupItem(e.getId(), e.getCode(), e.getName()))
                .toList();
        return new OrgLookups(items.apply(MasterType.BUSINESS_UNIT), items.apply(MasterType.COST_CENTER),
                items.apply(MasterType.DEPARTMENT), items.apply(MasterType.DESIGNATION),
                items.apply(MasterType.GRADE), items.apply(MasterType.LOCATION));
    }

    // ------------------------------------------------------------------ commands

    @CacheEvict(value = CacheConfig.LOOKUPS, allEntries = true)
    @Transactional
    public MasterDto create(MasterType type, MasterRequest req) {
        String code = req.code().trim().toUpperCase();
        if (repo(type).existsByCodeIgnoreCase(code)) {
            throw new BusinessException(type.label() + " code '" + code + "' already exists");
        }
        MasterEntity entity = newEntity(type);
        applyCommon(entity, req, code);
        applyTypeSpecific(type, entity, req);
        MasterEntity saved = this.<MasterEntity>repo(type).save(entity);
        return toDto(saved, 0);
    }

    @CacheEvict(value = CacheConfig.LOOKUPS, allEntries = true)
    @Transactional
    public MasterDto update(MasterType type, Long id, MasterRequest req) {
        MasterEntity entity = find(type, id);
        String code = req.code().trim().toUpperCase();
        if (repo(type).existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException(type.label() + " code '" + code + "' already exists");
        }
        applyCommon(entity, req, code);
        applyTypeSpecific(type, entity, req);
        return toDto(entity, employeeCounts(type).getOrDefault(id, 0L));
    }

    /** Soft delete; refused while current employees (or sub-departments) still reference it. */
    @CacheEvict(value = CacheConfig.LOOKUPS, allEntries = true)
    @Transactional
    public void delete(MasterType type, Long id) {
        MasterEntity entity = find(type, id);
        long inUse = employeeCounts(type).getOrDefault(id, 0L);
        if (inUse > 0) {
            throw new BusinessException("Cannot delete " + type.label().toLowerCase() + " '" + entity.getName()
                    + "': " + inUse + " current employee(s) are assigned to it. Reassign them or mark it inactive.");
        }
        if (type == MasterType.DEPARTMENT) {
            Long children = em.createQuery(
                            "select count(d) from Department d where d.parent.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult();
            if (children > 0) {
                throw new BusinessException("Cannot delete department '" + entity.getName()
                        + "': it has " + children + " sub-department(s)");
            }
        }
        entity.setDeleted(true);
        entity.setActive(false);
    }

    // ------------------------------------------------------------------ helpers

    private MasterEntity find(MasterType type, Long id) {
        return this.<MasterEntity>repo(type).findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(type.label(), id));
    }

    private static MasterEntity newEntity(MasterType type) {
        return switch (type) {
            case BUSINESS_UNIT -> new BusinessUnit();
            case COST_CENTER -> new CostCenter();
            case DEPARTMENT -> new Department();
            case DESIGNATION -> new Designation();
            case GRADE -> new Grade();
            case LOCATION -> new Location();
        };
    }

    private static void applyCommon(MasterEntity e, MasterRequest req, String code) {
        e.setCode(code);
        e.setName(req.name().trim());
        e.setDescription(req.description());
        e.setActive(req.active() == null || req.active());
    }

    private void applyTypeSpecific(MasterType type, MasterEntity entity, MasterRequest req) {
        switch (type) {
            case DEPARTMENT -> {
                Department d = (Department) entity;
                d.setBusinessUnit(req.businessUnitId() == null ? null : businessUnitRepository.findById(req.businessUnitId())
                        .orElseThrow(() -> new BadRequestException("Business unit not found")));
                d.setCostCenter(req.costCenterId() == null ? null : costCenterRepository.findById(req.costCenterId())
                        .orElseThrow(() -> new BadRequestException("Cost center not found")));
                d.setParent(resolveParent(d, req.parentId()));
                d.setHead(req.headEmployeeId() == null ? null : employeeRepository.findById(req.headEmployeeId())
                        .orElseThrow(() -> new BadRequestException("Department head employee not found")));
            }
            case GRADE -> ((Grade) entity).setLevelNo(req.levelNo());
            case LOCATION -> {
                Location l = (Location) entity;
                l.setAddressLine(req.addressLine());
                l.setCity(req.city());
                l.setState(req.state());
                l.setCountry(req.country());
                l.setPostalCode(req.postalCode());
                l.setTimezone(req.timezone());
            }
            default -> { /* no extra fields */ }
        }
    }

    /** Rejects self-parenting and cycles (A -> B -> A) in the department hierarchy. */
    private Department resolveParent(Department dept, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Department parent = departmentRepository.findById(parentId)
                .orElseThrow(() -> new BadRequestException("Parent department not found"));
        if (dept.getId() != null) {
            Set<Long> seen = new HashSet<>();
            for (Department p = parent; p != null; p = p.getParent()) {
                if (p.getId().equals(dept.getId()) || !seen.add(p.getId())) {
                    throw new BadRequestException("A department cannot be its own parent or ancestor");
                }
            }
        }
        return parent;
    }

    /** Current (non-exited) employee count per master id, in one grouped query. */
    private Map<Long, Long> employeeCounts(MasterType type) {
        String attr = type.employeeAttribute();     // enum constant, not user input
        List<Object[]> rows = em.createQuery(
                        "select x.id, count(e) from Employee e join e." + attr + " x "
                                + "where e.status <> :exited group by x.id", Object[].class)
                .setParameter("exited", EmployeeStatus.EXITED)
                .getResultList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : rows) {
            counts.put((Long) r[0], (Long) r[1]);
        }
        return counts;
    }

    private static MasterDto toDto(MasterEntity e, long employeeCount) {
        Long buId = null, ccId = null, parentId = null, headId = null;
        String buName = null, ccName = null, parentName = null, headName = null;
        Integer levelNo = null;
        String addressLine = null, city = null, state = null, country = null, postalCode = null, timezone = null;

        if (e instanceof Department d) {
            if (d.getBusinessUnit() != null) { buId = d.getBusinessUnit().getId(); buName = d.getBusinessUnit().getName(); }
            if (d.getCostCenter() != null) { ccId = d.getCostCenter().getId(); ccName = d.getCostCenter().getName(); }
            if (d.getParent() != null) { parentId = d.getParent().getId(); parentName = d.getParent().getName(); }
            Employee head = d.getHead();
            if (head != null) { headId = head.getId(); headName = head.getFullName(); }
        } else if (e instanceof Grade g) {
            levelNo = g.getLevelNo();
        } else if (e instanceof Location l) {
            addressLine = l.getAddressLine();
            city = l.getCity();
            state = l.getState();
            country = l.getCountry();
            postalCode = l.getPostalCode();
            timezone = l.getTimezone();
        }
        return new MasterDto(e.getId(), e.getCode(), e.getName(), e.getDescription(), e.isActive(),
                buId, buName, ccId, ccName, parentId, parentName, headId, headName,
                levelNo, addressLine, city, state, country, postalCode, timezone, employeeCount);
    }
}
