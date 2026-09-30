package com.hrgenius.leave.service;

import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.leave.dto.LeaveDtos.LeaveTypeDto;
import com.hrgenius.leave.dto.LeaveDtos.LeaveTypeRequest;
import com.hrgenius.leave.entity.LeaveEnums.AccrualMethod;
import com.hrgenius.leave.entity.LeaveType;
import com.hrgenius.leave.repository.LeaveBalanceRepository;
import com.hrgenius.leave.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** CRUD for configurable leave types. Guarded by LEAVE_CONFIG at the controller. */
@Service
public class LeaveTypeService {

    private final LeaveTypeRepository types;
    private final LeaveBalanceRepository balances;

    public LeaveTypeService(LeaveTypeRepository types, LeaveBalanceRepository balances) {
        this.types = types;
        this.balances = balances;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeDto> list(boolean activeOnly) {
        List<LeaveType> found = activeOnly ? types.findByActiveTrueOrderByNameAsc() : types.findAllByOrderByNameAsc();
        return found.stream().map(LeaveTypeService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LeaveTypeDto get(Long id) {
        return toDto(load(id));
    }

    @Transactional
    public LeaveTypeDto create(LeaveTypeRequest req) {
        if (types.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessException("A leave type with code '" + req.code() + "' already exists");
        }
        LeaveType t = new LeaveType();
        t.setCode(req.code().toUpperCase());
        apply(t, req);
        return toDto(types.save(t));
    }

    @Transactional
    public LeaveTypeDto update(Long id, LeaveTypeRequest req) {
        LeaveType t = load(id);
        // Code is immutable once set (balances and history reference it by meaning, not FK).
        apply(t, req);
        return toDto(t);
    }

    @Transactional
    public void delete(Long id) {
        LeaveType t = load(id);
        if (balances.existsByLeaveTypeIdAndUsedGreaterThan(id, BigDecimal.ZERO)) {
            throw new BusinessException("Cannot delete '" + t.getName() + "': employees have already used it");
        }
        t.setActive(false);
        t.setDeleted(true);
    }

    private void apply(LeaveType t, LeaveTypeRequest req) {
        t.setName(req.name().trim());
        t.setDescription(req.description());
        t.setColor(req.color());
        t.setPaid(req.paid() == null || req.paid());
        t.setAnnualEntitlement(req.annualEntitlement());
        t.setAccrualMethod(AccrualMethod.valueOf(req.accrualMethod()));
        t.setAccrualRate(req.accrualRate());
        t.setCarryForwardCap(req.carryForwardCap());
        t.setMaxBalance(req.maxBalance());
        t.setAllowHalfDay(req.allowHalfDay() == null || req.allowHalfDay());
        t.setEncashable(Boolean.TRUE.equals(req.encashable()));
        t.setRequiresApproval(req.requiresApproval() == null || req.requiresApproval());
        t.setActive(req.active() == null || req.active());
    }

    private LeaveType load(Long id) {
        return types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type " + id + " not found"));
    }

    static LeaveTypeDto toDto(LeaveType t) {
        return new LeaveTypeDto(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.getColor(),
                t.isPaid(), t.getAnnualEntitlement(), t.getAccrualMethod().name(), t.getAccrualRate(),
                t.getCarryForwardCap(), t.getMaxBalance(), t.isAllowHalfDay(), t.isEncashable(),
                t.isRequiresApproval(), t.isActive());
    }
}
