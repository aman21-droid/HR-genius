package com.hrgenius.leave.service;

import com.hrgenius.leave.entity.LeaveAccrualLog;
import com.hrgenius.leave.entity.LeaveBalance;
import com.hrgenius.leave.entity.LeaveEnums.AccrualMethod;
import com.hrgenius.leave.entity.LeaveType;
import com.hrgenius.leave.repository.LeaveAccrualLogRepository;
import com.hrgenius.leave.repository.LeaveBalanceRepository;
import com.hrgenius.leave.repository.LeaveTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * Posts periodic leave accrual. MONTHLY types accrue their rate once per calendar month; the
 * {@link LeaveAccrualLog} makes every run idempotent, so re-running a month never double-credits.
 * Accrual is capped at the type's annual entitlement and, where set, its max balance.
 */
@Slf4j
@Service
public class LeaveAccrualService {

    private final LeaveTypeRepository types;
    private final LeaveBalanceRepository balances;
    private final LeaveAccrualLogRepository accrualLog;

    public LeaveAccrualService(LeaveTypeRepository types, LeaveBalanceRepository balances,
                               LeaveAccrualLogRepository accrualLog) {
        this.types = types;
        this.balances = balances;
        this.accrualLog = accrualLog;
    }

    /** Monthly job: accrue for the current month. Cron configured via hrgenius.leave.accrual-cron. */
    @Scheduled(cron = "${hrgenius.leave.accrual-cron}")
    public void scheduledMonthlyAccrual() {
        int posted = runForPeriod(YearMonth.now());
        log.info("Monthly leave accrual for {} posted {} balance updates", YearMonth.now(), posted);
    }

    /** Accrue all MONTHLY types for the given month; returns the number of balances credited. */
    @Transactional
    public int runForPeriod(YearMonth period) {
        String periodKey = period.toString(); // YYYY-MM
        int year = period.getYear();
        int posted = 0;

        for (LeaveType type : types.findByActiveTrueOrderByNameAsc()) {
            if (type.getAccrualMethod() != AccrualMethod.MONTHLY || type.getAccrualRate().signum() <= 0) {
                continue;
            }
            for (LeaveBalance b : balances.findByLeaveTypeIdAndYear(type.getId(), year)) {
                Long employeeId = b.getEmployee().getId();
                if (accrualLog.existsByEmployeeIdAndLeaveTypeIdAndPeriod(employeeId, type.getId(), periodKey)) {
                    continue;
                }
                BigDecimal amount = cappedAmount(type, b);
                if (amount.signum() > 0) {
                    b.setAccrued(b.getAccrued().add(amount));
                }
                LeaveAccrualLog entry = new LeaveAccrualLog();
                entry.setEmployee(b.getEmployee());
                entry.setLeaveType(type);
                entry.setPeriod(periodKey);
                entry.setAmount(amount);
                entry.setRunAt(Instant.now());
                accrualLog.save(entry);
                posted++;
            }
        }
        return posted;
    }

    /** The accrual rate, reduced so it never pushes accrued past entitlement or balance past max. */
    private BigDecimal cappedAmount(LeaveType type, LeaveBalance b) {
        BigDecimal amount = type.getAccrualRate();
        if (type.getAnnualEntitlement().signum() > 0) {
            BigDecimal room = type.getAnnualEntitlement().subtract(b.getAccrued());
            amount = amount.min(room);
        }
        if (type.getMaxBalance() != null) {
            BigDecimal room = type.getMaxBalance().subtract(b.getAvailable());
            amount = amount.min(room);
        }
        return amount.max(BigDecimal.ZERO);
    }

    /** Convenience for the manual trigger endpoint. */
    @Transactional
    public int runForPeriods(List<YearMonth> periods) {
        int total = 0;
        for (YearMonth p : periods) {
            total += runForPeriod(p);
        }
        return total;
    }
}
