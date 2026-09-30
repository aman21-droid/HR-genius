-- ============================================================================
-- V8: Phase 3 seed - leave types, 2026 holiday calendar, opening balances
-- Deterministic and set-based so the smoke test can assert exact counts.
-- ============================================================================

-- ---- Leave types -----------------------------------------------------------
-- annual_entitlement is the yearly grant; accrual_rate is per accrual period
-- (per month for MONTHLY, per year for ANNUAL). carry_forward_cap caps year-end rollover.
INSERT INTO leave_types (id, code, name, description, color, paid, annual_entitlement, accrual_method, accrual_rate, carry_forward_cap, max_balance, allow_half_day, encashable, requires_approval, active, created_at, created_by, deleted)
    VALUES (leave_type_seq.NEXTVAL, 'CL',   'Casual Leave',   'Short-notice personal leave',            '#42a5f5', 1, 12, 'MONTHLY', 1.00,  0,  NULL, 1, 0, 1, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO leave_types (id, code, name, description, color, paid, annual_entitlement, accrual_method, accrual_rate, carry_forward_cap, max_balance, allow_half_day, encashable, requires_approval, active, created_at, created_by, deleted)
    VALUES (leave_type_seq.NEXTVAL, 'SL',   'Sick Leave',     'Illness and medical appointments',       '#ef5350', 1, 10, 'ANNUAL',  10.00, 0,  NULL, 1, 0, 1, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO leave_types (id, code, name, description, color, paid, annual_entitlement, accrual_method, accrual_rate, carry_forward_cap, max_balance, allow_half_day, encashable, requires_approval, active, created_at, created_by, deleted)
    VALUES (leave_type_seq.NEXTVAL, 'EL',   'Earned Leave',   'Privilege leave; accrues monthly',       '#66bb6a', 1, 15, 'MONTHLY', 1.25,  30, 45,   1, 1, 1, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO leave_types (id, code, name, description, color, paid, annual_entitlement, accrual_method, accrual_rate, carry_forward_cap, max_balance, allow_half_day, encashable, requires_approval, active, created_at, created_by, deleted)
    VALUES (leave_type_seq.NEXTVAL, 'COMP', 'Comp Off',       'Compensatory off for extra work',        '#ab47bc', 1, 0,  'NONE',    0.00,  0,  NULL, 1, 0, 1, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO leave_types (id, code, name, description, color, paid, annual_entitlement, accrual_method, accrual_rate, carry_forward_cap, max_balance, allow_half_day, encashable, requires_approval, active, created_at, created_by, deleted)
    VALUES (leave_type_seq.NEXTVAL, 'LWP',  'Loss of Pay',    'Unpaid leave beyond entitlement',        '#78909c', 0, 0,  'NONE',    0.00,  0,  NULL, 1, 0, 1, 1, SYSTIMESTAMP, 'system', 0);

-- ---- 2026 holiday calendar (company-wide; location_id NULL) -----------------
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-01-01', 'New Year''s Day',        0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-01-26', 'Republic Day',           0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-03-04', 'Holi',                   0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-05-01', 'Labour Day',             0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-08-15', 'Independence Day',       0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-10-02', 'Gandhi Jayanti',         0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-11-08', 'Diwali',                 0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-12-25', 'Christmas Day',          0, NULL, 2026, SYSTIMESTAMP, 'system', 0);
-- Optional/restricted holidays
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-04-14', 'Ambedkar Jayanti',       1, NULL, 2026, SYSTIMESTAMP, 'system', 0);
INSERT INTO holidays (id, holiday_date, name, optional_holiday, location_id, year_no, created_at, created_by, deleted) VALUES (holiday_seq.NEXTVAL, DATE '2026-09-14', 'Onam',                   1, NULL, 2026, SYSTIMESTAMP, 'system', 0);

-- ---- Opening balances for 2026 --------------------------------------------
-- Every current (non-exited) employee gets a balance row for CL, SL, EL.
-- Seeded as fully accrued for the year so the demo shows usable balances;
-- the accrual job (Part 2) posts increments idempotently from here on.
INSERT INTO leave_balances (id, employee_id, leave_type_id, year_no, opening, accrued, used, pending, adjustment, created_at, created_by, deleted)
    SELECT leave_balance_seq.NEXTVAL, e.id, t.id, 2026, 0, t.annual_entitlement, 0, 0, 0, SYSTIMESTAMP, 'system', 0
    FROM employees e
    CROSS JOIN leave_types t
    WHERE e.deleted = 0
      AND e.status <> 'EXITED'
      AND t.code IN ('CL', 'SL', 'EL');
