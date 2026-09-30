-- ============================================================================
-- V7: Approval engine + Leave management + Attendance (Phase 3)
-- Every table backing a BaseEntity carries: created_at, created_by, updated_at,
-- updated_by, deleted (soft delete). Oracle syntax; runs on H2 (Oracle mode) too.
-- ============================================================================

-- ---- New permissions -------------------------------------------------------
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'LEAVE_CONFIG',       'Manage leave types, holidays, and balances');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'ATTENDANCE_MANAGE',  'Edit attendance and manage others records');

-- SUPER_ADMIN and HR_ADMIN get the two new permissions.
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('LEAVE_CONFIG','ATTENDANCE_MANAGE')
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN');
-- HR_MANAGER can edit attendance too.
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code = 'ATTENDANCE_MANAGE'
    WHERE r.code = 'HR_MANAGER';

-- ============================================================================
-- Approval engine (generic, reused by Leave now and Payroll/Recruitment later)
-- ============================================================================
CREATE SEQUENCE approval_request_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE approval_step_seq    START WITH 1 INCREMENT BY 1 NOCACHE;

-- One approval flow instance. subject_type/subject_id point back at the domain row
-- (e.g. LEAVE -> leave_requests.id) so a handler can act when the flow resolves.
CREATE TABLE approval_requests (
    id                   NUMBER(19)    NOT NULL,
    subject_type         VARCHAR2(30)  NOT NULL,   -- LEAVE, REGULARIZATION, ...
    subject_id           NUMBER(19)    NOT NULL,
    requester_emp_id     NUMBER(19)    NOT NULL,
    title                VARCHAR2(200),
    status               VARCHAR2(20)  DEFAULT 'PENDING' NOT NULL, -- PENDING/APPROVED/REJECTED/CANCELLED
    current_step         NUMBER(10)    DEFAULT 1 NOT NULL,
    total_steps          NUMBER(10)    DEFAULT 1 NOT NULL,
    resolved_at          TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_approval_requests PRIMARY KEY (id),
    CONSTRAINT fk_appr_requester FOREIGN KEY (requester_emp_id) REFERENCES employees (id)
);
CREATE INDEX ix_appr_subject   ON approval_requests (subject_type, subject_id);
CREATE INDEX ix_appr_requester ON approval_requests (requester_emp_id);
CREATE INDEX ix_appr_status    ON approval_requests (status);

-- Ordered steps. approver_emp_id is the concrete approver resolved at creation time.
CREATE TABLE approval_steps (
    id                   NUMBER(19)    NOT NULL,
    request_id           NUMBER(19)    NOT NULL,
    step_no              NUMBER(10)    NOT NULL,
    approver_emp_id      NUMBER(19)    NOT NULL,
    role_hint            VARCHAR2(40),               -- e.g. MANAGER, HR (informational)
    status               VARCHAR2(20)  DEFAULT 'PENDING' NOT NULL, -- PENDING/APPROVED/REJECTED/SKIPPED
    decision_comment     VARCHAR2(500),
    decided_at           TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_approval_steps PRIMARY KEY (id),
    CONSTRAINT fk_step_request  FOREIGN KEY (request_id)      REFERENCES approval_requests (id),
    CONSTRAINT fk_step_approver FOREIGN KEY (approver_emp_id) REFERENCES employees (id),
    CONSTRAINT uq_step_no UNIQUE (request_id, step_no)
);
CREATE INDEX ix_step_approver ON approval_steps (approver_emp_id, status);

-- ============================================================================
-- Leave management
-- ============================================================================
CREATE SEQUENCE leave_type_seq    START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE leave_balance_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE leave_request_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE leave_accrual_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE leave_types (
    id                   NUMBER(19)    NOT NULL,
    code                 VARCHAR2(20)  NOT NULL,
    name                 VARCHAR2(80)  NOT NULL,
    description          VARCHAR2(300),
    color                VARCHAR2(20),
    paid                 NUMBER(1)     DEFAULT 1 NOT NULL,
    annual_entitlement   NUMBER(6,2)   DEFAULT 0 NOT NULL,
    accrual_method       VARCHAR2(20)  DEFAULT 'ANNUAL' NOT NULL, -- NONE/MONTHLY/ANNUAL
    accrual_rate         NUMBER(6,2)   DEFAULT 0 NOT NULL,        -- units per accrual period
    carry_forward_cap    NUMBER(6,2)   DEFAULT 0 NOT NULL,        -- max units carried into next year
    max_balance          NUMBER(6,2),                            -- optional ceiling; null = uncapped
    allow_half_day       NUMBER(1)     DEFAULT 1 NOT NULL,
    encashable           NUMBER(1)     DEFAULT 0 NOT NULL,
    requires_approval    NUMBER(1)     DEFAULT 1 NOT NULL,
    active               NUMBER(1)     DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_leave_types PRIMARY KEY (id),
    CONSTRAINT uq_leave_types_code UNIQUE (code)
);

-- Per employee, per type, per calendar year. balance = opening + accrued + adjustment - used - pending.
CREATE TABLE leave_balances (
    id                   NUMBER(19)    NOT NULL,
    employee_id          NUMBER(19)    NOT NULL,
    leave_type_id        NUMBER(19)    NOT NULL,
    year_no              NUMBER(10)    NOT NULL,
    opening              NUMBER(6,2)   DEFAULT 0 NOT NULL,   -- carried forward from prior year
    accrued              NUMBER(6,2)   DEFAULT 0 NOT NULL,
    used                 NUMBER(6,2)   DEFAULT 0 NOT NULL,
    pending              NUMBER(6,2)   DEFAULT 0 NOT NULL,   -- reserved by not-yet-approved requests
    adjustment           NUMBER(6,2)   DEFAULT 0 NOT NULL,   -- manual +/- by HR
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_leave_balances PRIMARY KEY (id),
    CONSTRAINT fk_bal_employee FOREIGN KEY (employee_id)   REFERENCES employees (id),
    CONSTRAINT fk_bal_type     FOREIGN KEY (leave_type_id) REFERENCES leave_types (id),
    CONSTRAINT uq_balance UNIQUE (employee_id, leave_type_id, year_no)
);
CREATE INDEX ix_balance_emp ON leave_balances (employee_id, year_no);

CREATE TABLE leave_requests (
    id                   NUMBER(19)    NOT NULL,
    employee_id          NUMBER(19)    NOT NULL,
    leave_type_id        NUMBER(19)    NOT NULL,
    start_date           DATE          NOT NULL,
    end_date             DATE          NOT NULL,
    half_day_start       NUMBER(1)     DEFAULT 0 NOT NULL,
    half_day_end         NUMBER(1)     DEFAULT 0 NOT NULL,
    days                 NUMBER(6,2)   NOT NULL,              -- working days requested (excludes holidays/weekends)
    reason               VARCHAR2(500),
    status               VARCHAR2(20)  DEFAULT 'PENDING' NOT NULL, -- PENDING/APPROVED/REJECTED/CANCELLED
    approval_request_id  NUMBER(19),
    decided_at           TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_leave_requests PRIMARY KEY (id),
    CONSTRAINT fk_lr_employee FOREIGN KEY (employee_id)         REFERENCES employees (id),
    CONSTRAINT fk_lr_type     FOREIGN KEY (leave_type_id)       REFERENCES leave_types (id),
    CONSTRAINT fk_lr_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id)
);
CREATE INDEX ix_lr_employee ON leave_requests (employee_id, status);
CREATE INDEX ix_lr_dates    ON leave_requests (start_date, end_date);

-- Idempotency ledger for the accrual job: one row per employee/type/period posted.
CREATE TABLE leave_accrual_log (
    id                   NUMBER(19)    NOT NULL,
    employee_id          NUMBER(19)    NOT NULL,
    leave_type_id        NUMBER(19)    NOT NULL,
    period               VARCHAR2(7)   NOT NULL,             -- YYYY-MM (MONTHLY) or YYYY (ANNUAL)
    amount               NUMBER(6,2)   NOT NULL,
    run_at               TIMESTAMP     NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_leave_accrual_log PRIMARY KEY (id),
    CONSTRAINT fk_acc_employee FOREIGN KEY (employee_id)   REFERENCES employees (id),
    CONSTRAINT fk_acc_type     FOREIGN KEY (leave_type_id) REFERENCES leave_types (id),
    CONSTRAINT uq_accrual UNIQUE (employee_id, leave_type_id, period)
);

-- ============================================================================
-- Attendance
-- ============================================================================
CREATE SEQUENCE holiday_seq        START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE attendance_day_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE regularization_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- Holiday calendar. location_id NULL = company-wide holiday.
CREATE TABLE holidays (
    id                   NUMBER(19)    NOT NULL,
    holiday_date         DATE          NOT NULL,
    name                 VARCHAR2(120) NOT NULL,
    optional_holiday     NUMBER(1)     DEFAULT 0 NOT NULL,
    location_id          NUMBER(19),
    year_no              NUMBER(10)    NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_holidays PRIMARY KEY (id),
    CONSTRAINT fk_holiday_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT uq_holiday UNIQUE (holiday_date, location_id)
);
CREATE INDEX ix_holiday_year ON holidays (year_no);

-- One row per employee per working day.
CREATE TABLE attendance_days (
    id                   NUMBER(19)    NOT NULL,
    employee_id          NUMBER(19)    NOT NULL,
    work_date            DATE          NOT NULL,
    check_in             TIMESTAMP,
    check_out            TIMESTAMP,
    worked_minutes       NUMBER(10)    DEFAULT 0 NOT NULL,
    status               VARCHAR2(20)  DEFAULT 'ABSENT' NOT NULL, -- PRESENT/ABSENT/ON_LEAVE/HOLIDAY/WEEKEND/HALF_DAY
    source               VARCHAR2(20)  DEFAULT 'SELF' NOT NULL,   -- SELF/ADMIN/REGULARIZED/SYSTEM
    remarks              VARCHAR2(300),
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_attendance_days PRIMARY KEY (id),
    CONSTRAINT fk_att_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT uq_attendance UNIQUE (employee_id, work_date)
);
CREATE INDEX ix_att_date ON attendance_days (work_date);

-- Manual correction of a day's punches; routed through the approval engine.
CREATE TABLE regularization_requests (
    id                   NUMBER(19)    NOT NULL,
    employee_id          NUMBER(19)    NOT NULL,
    work_date            DATE          NOT NULL,
    requested_check_in   TIMESTAMP,
    requested_check_out  TIMESTAMP,
    reason               VARCHAR2(500) NOT NULL,
    status               VARCHAR2(20)  DEFAULT 'PENDING' NOT NULL, -- PENDING/APPROVED/REJECTED/CANCELLED
    approval_request_id  NUMBER(19),
    decided_at           TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_regularization_requests PRIMARY KEY (id),
    CONSTRAINT fk_reg_employee FOREIGN KEY (employee_id)         REFERENCES employees (id),
    CONSTRAINT fk_reg_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id)
);
CREATE INDEX ix_reg_employee ON regularization_requests (employee_id, status);
