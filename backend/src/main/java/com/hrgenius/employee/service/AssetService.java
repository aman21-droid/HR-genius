package com.hrgenius.employee.service;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.util.SearchPredicates;
import com.hrgenius.employee.dto.ProfileDtos.*;
import com.hrgenius.employee.entity.Asset;
import com.hrgenius.employee.entity.Asset.AssetCategory;
import com.hrgenius.employee.entity.Asset.AssetStatus;
import com.hrgenius.employee.entity.AssetAssignment;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.AssetAssignmentRepository;
import com.hrgenius.employee.repository.AssetRepository;
import com.hrgenius.employee.repository.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asset register with a custody log. Invariant: an asset is ASSIGNED iff it has exactly one open
 * assignment, and {@code currentEmployeeId} mirrors that assignment.
 */
@Service
public class AssetService {

    private final AssetRepository repository;
    private final AssetAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final EmployeeAccessService access;

    public AssetService(AssetRepository repository, AssetAssignmentRepository assignments,
                        EmployeeRepository employees, EmployeeService employeeService,
                        EmployeeAccessService access) {
        this.repository = repository;
        this.assignments = assignments;
        this.employees = employees;
        this.employeeService = employeeService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetDto> list(String search, AssetCategory category, AssetStatus status, Pageable pageable) {
        Specification<Asset> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                p.add(cb.or(SearchPredicates.containsIgnoreCase(cb, root.get("assetTag"), search),
                        SearchPredicates.containsIgnoreCase(cb, root.get("name"), search),
                        SearchPredicates.containsIgnoreCase(cb, root.get("serialNumber"), search)));
            }
            if (category != null) p.add(cb.equal(root.get("category"), category));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = repository.findAll(spec, pageable);
        // Resolve holder names for the whole page in one query.
        Set<Long> holderIds = page.getContent().stream().map(Asset::getCurrentEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Employee> holders = employees.findAllById(holderIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return PageResponse.from(page.map(a -> toDto(a, holders.get(a.getCurrentEmployeeId()))));
    }

    @Transactional(readOnly = true)
    public AssetDto get(Long id) {
        Asset a = find(id);
        return toDto(a, holder(a));
    }

    @Transactional
    public AssetDto create(AssetRequest req) {
        String tag = req.assetTag().trim().toUpperCase();
        if (repository.existsByAssetTagIgnoreCase(tag)) {
            throw new BusinessException("Asset tag " + tag + " already exists");
        }
        if (req.status() == AssetStatus.ASSIGNED) {
            throw new BadRequestException("Create the asset first, then assign it to an employee");
        }
        Asset a = new Asset();
        a.setAssetTag(tag);
        apply(a, req);
        a.setStatus(req.status() == null ? AssetStatus.AVAILABLE : req.status());
        repository.save(a);
        return toDto(a, null);
    }

    @Transactional
    public AssetDto update(Long id, AssetRequest req) {
        Asset a = find(id);
        String tag = req.assetTag().trim().toUpperCase();
        if (repository.existsByAssetTagIgnoreCaseAndIdNot(tag, id)) {
            throw new BusinessException("Asset tag " + tag + " already exists");
        }
        a.setAssetTag(tag);
        apply(a, req);
        // Status moves to/from ASSIGNED only via assign/return, to keep the custody log consistent.
        if (req.status() != null && req.status() != a.getStatus()) {
            if (a.getStatus() == AssetStatus.ASSIGNED || req.status() == AssetStatus.ASSIGNED) {
                throw new BadRequestException("Use assign/return to change whether an asset is assigned");
            }
            a.setStatus(req.status());
        }
        return toDto(a, holder(a));
    }

    @Transactional
    public void delete(Long id) {
        Asset a = find(id);
        if (a.getStatus() == AssetStatus.ASSIGNED) {
            throw new BusinessException("Recover asset " + a.getAssetTag() + " before deleting it");
        }
        a.setDeleted(true);
    }

    @Transactional
    public AssetDto assign(Long assetId, AssignAssetRequest req) {
        Asset a = find(assetId);
        if (a.getStatus() != AssetStatus.AVAILABLE) {
            throw new BusinessException("Asset " + a.getAssetTag() + " is " + a.getStatus().name().toLowerCase()
                    .replace('_', ' ') + " and cannot be assigned");
        }
        Employee e = employeeService.find(req.employeeId());
        if (e.getStatus() == EmployeeStatus.EXITED) {
            throw new BusinessException(e.getFullName() + " has left the organization");
        }
        LocalDate on = req.assignedOn() == null ? LocalDate.now() : req.assignedOn();
        if (on.isAfter(LocalDate.now())) {
            throw new BadRequestException("Assignment date cannot be in the future");
        }
        AssetAssignment asg = new AssetAssignment();
        asg.setAsset(a);
        asg.setEmployeeId(e.getId());
        asg.setAssignedOn(on);
        asg.setAssignNotes(req.notes());
        assignments.save(asg);

        a.setStatus(AssetStatus.ASSIGNED);
        a.setCurrentEmployeeId(e.getId());
        return toDto(a, e);
    }

    @Transactional
    public AssetDto returnAsset(Long assetId, ReturnAssetRequest req) {
        Asset a = find(assetId);
        AssetAssignment open = assignments.findFirstByAsset_IdAndReturnedOnIsNull(assetId)
                .orElseThrow(() -> new BusinessException("Asset " + a.getAssetTag() + " is not currently assigned"));
        LocalDate on = req.returnedOn() == null ? LocalDate.now() : req.returnedOn();
        if (on.isBefore(open.getAssignedOn())) {
            throw new BadRequestException("Return date cannot be before the assignment date (" + open.getAssignedOn() + ")");
        }
        if (on.isAfter(LocalDate.now())) {
            throw new BadRequestException("Return date cannot be in the future");
        }
        open.setReturnedOn(on);
        open.setReturnCondition(req.condition());
        open.setReturnNotes(req.notes());

        a.setCurrentEmployeeId(null);
        // Damaged kit goes to repair rather than straight back into the pool.
        a.setStatus("DAMAGED".equalsIgnoreCase(req.condition()) ? AssetStatus.IN_REPAIR : AssetStatus.AVAILABLE);
        return toDto(a, null);
    }

    @Transactional(readOnly = true)
    public List<AssetAssignmentDto> history(Long assetId) {
        find(assetId);
        List<AssetAssignment> list = assignments.findByAsset_IdOrderByAssignedOnDescIdDesc(assetId);
        // Resolve holder names in one query rather than one per row.
        Map<Long, Employee> people = employees.findAllById(
                        list.stream().map(AssetAssignment::getEmployeeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return list.stream().map(x -> toAssignmentDto(x, people.get(x.getEmployeeId()))).toList();
    }

    /** An employee's current and past assets (profile "Assets" tab). */
    @Transactional(readOnly = true)
    public List<AssetAssignmentDto> forEmployee(Long employeeId) {
        employeeService.find(employeeId);
        access.requireFullProfile(employeeId);
        return assignments.findByEmployeeIdOrderByAssignedOnDescIdDesc(employeeId).stream()
                .map(x -> toAssignmentDto(x, null)).toList();   // the holder is the profile owner
    }

    private Asset find(Long id) {
        return repository.findById(id)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
    }

    private Employee holder(Asset a) {
        return a.getCurrentEmployeeId() == null ? null : employees.findById(a.getCurrentEmployeeId()).orElse(null);
    }

    private static void apply(Asset a, AssetRequest r) {
        a.setName(r.name().trim());
        a.setCategory(r.category());
        a.setSerialNumber(r.serialNumber());
        a.setPurchaseDate(r.purchaseDate());
        a.setPurchaseCost(r.purchaseCost());
        a.setNotes(r.notes());
    }

    private static AssetDto toDto(Asset a, Employee holder) {
        return new AssetDto(a.getId(), a.getAssetTag(), a.getName(), a.getCategory().name(), a.getSerialNumber(),
                a.getStatus().name(), a.getPurchaseDate(), a.getPurchaseCost(), a.getNotes(),
                a.getCurrentEmployeeId(), holder == null ? null : holder.getFullName(),
                holder == null ? null : holder.getEmployeeCode());
    }

    private static AssetAssignmentDto toAssignmentDto(AssetAssignment x, Employee holder) {
        Asset a = x.getAsset();
        return new AssetAssignmentDto(x.getId(), a.getId(), a.getAssetTag(), a.getName(), a.getCategory().name(),
                a.getSerialNumber(), x.getEmployeeId(), holder == null ? null : holder.getFullName(),
                holder == null ? null : holder.getEmployeeCode(), x.getAssignedOn(), x.getReturnedOn(), x.getAssignNotes(),
                x.getReturnCondition(), x.getReturnNotes());
    }
}
