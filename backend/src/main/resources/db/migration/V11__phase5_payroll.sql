-- ============================================================================
-- V11: Payroll (Phase 5)
-- Monthly payroll runs computed from each employee's annual CTC and a configurable
-- earnings structure; statutory deductions (PF, ESI, PT, TDS) are computed in code.
-- Runs are approved through the approval engine (subject type PAYROLL_RUN).
-- Oracle syntax; runs on H2 (Oracle mode) too.
-- ============================================================================

INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'PAYROLL_APPROVE', 'Approve monthly payroll runs');
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'PAYROLL_APPROVE'
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN');

CREATE SEQUENCE salary_component_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE payroll_run_seq        START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE payslip_seq            START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE payslip_line_seq       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE payroll_adjustment_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- Earnings structure. calc_type:
--   PERCENT_OF_CTC   calc_value % of monthly CTC            (e.g. BASIC 40)
--   PERCENT_OF_BASIC calc_value % of the BASIC component    (e.g. HRA 50)
--   FIXED_MONTHLY    calc_value rupees per month             (e.g. CONVEYANCE 1600)
--   BALANCING        whatever remains of monthly gross       (exactly one: SPECIAL)
CREATE TABLE salary_components (
    id                   NUMBER(19)     NOT NULL,
    code                 VARCHAR2(20)   NOT NULL,
    name                 VARCHAR2(80)   NOT NULL,
    calc_type            VARCHAR2(20)   NOT NULL,
    calc_value           NUMBER(12,4)   DEFAULT 0 NOT NULL,
    taxable              NUMBER(1)      DEFAULT 1 NOT NULL,
    sort_order           NUMBER(5)      DEFAULT 0 NOT NULL,
    active               NUMBER(1)      DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_salary_components PRIMARY KEY (id),
    CONSTRAINT uq_salary_component_code UNIQUE (code)
);

-- One run per calendar month. Status: DRAFT -> CALCULATED -> PENDING_APPROVAL -> APPROVED -> PAID;
-- a rejected approval returns the run to CALCULATED.
CREATE TABLE payroll_runs (
    id                   NUMBER(19)     NOT NULL,
    period               VARCHAR2(7)    NOT NULL,              -- YYYY-MM
    period_start         DATE           NOT NULL,
    period_end           DATE           NOT NULL,
    status               VARCHAR2(20)   DEFAULT 'DRAFT' NOT NULL,
    employee_count       NUMBER(10)     DEFAULT 0 NOT NULL,
    total_gross          NUMBER(16,2)   DEFAULT 0 NOT NULL,
    total_deductions     NUMBER(16,2)   DEFAULT 0 NOT NULL,
    total_net            NUMBER(16,2)   DEFAULT 0 NOT NULL,
    total_employer_cost  NUMBER(16,2)   DEFAULT 0 NOT NULL,     -- gross + employer PF/ESI
    approval_request_id  NUMBER(19),
    calculated_at        TIMESTAMP,
    approved_at          TIMESTAMP,
    paid_at              TIMESTAMP,
    payment_reference    VARCHAR2(80),
    notes                VARCHAR2(500),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_payroll_runs PRIMARY KEY (id),
    CONSTRAINT uq_payroll_period UNIQUE (period),
    CONSTRAINT fk_run_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id)
);

-- One payslip per employee per run. Bank details are a masked snapshot for display;
-- the bank-transfer export reads the encrypted statutory record at export time.
CREATE TABLE payslips (
    id                   NUMBER(19)     NOT NULL,
    run_id               NUMBER(19)     NOT NULL,
    employee_id          NUMBER(19)     NOT NULL,
    days_in_period       NUMBER(5)      NOT NULL,
    lop_days             NUMBER(6,2)    DEFAULT 0 NOT NULL,
    paid_days            NUMBER(6,2)    NOT NULL,
    annual_ctc           NUMBER(14,2)   NOT NULL,
    gross_earnings       NUMBER(14,2)   NOT NULL,
    total_deductions     NUMBER(14,2)   NOT NULL,
    net_pay              NUMBER(14,2)   NOT NULL,
    employer_pf          NUMBER(14,2)   DEFAULT 0 NOT NULL,
    employer_esi         NUMBER(14,2)   DEFAULT 0 NOT NULL,
    tax_regime           VARCHAR2(10),
    bank_name            VARCHAR2(120),
    account_masked       VARCHAR2(40),
    bank_ifsc            VARCHAR2(20),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_payslips PRIMARY KEY (id),
    CONSTRAINT fk_payslip_run      FOREIGN KEY (run_id)      REFERENCES payroll_runs (id),
    CONSTRAINT fk_payslip_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT uq_payslip UNIQUE (run_id, employee_id)
);
CREATE INDEX ix_payslip_employee ON payslips (employee_id);

-- line_type: EARNING, DEDUCTION, or EMPLOYER (employer contributions, not part of net pay).
CREATE TABLE payslip_lines (
    id                   NUMBER(19)     NOT NULL,
    payslip_id           NUMBER(19)     NOT NULL,
    code                 VARCHAR2(20)   NOT NULL,
    name                 VARCHAR2(80)   NOT NULL,
    line_type            VARCHAR2(10)   NOT NULL,
    amount               NUMBER(14,2)   NOT NULL,
    sort_order           NUMBER(5)      DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_payslip_lines PRIMARY KEY (id),
    CONSTRAINT fk_line_payslip FOREIGN KEY (payslip_id) REFERENCES payslips (id)
);
CREATE INDEX ix_line_payslip ON payslip_lines (payslip_id);

-- One-off additions/deductions for a run (bonus, reimbursement, recovery...). Not pro-rated.
CREATE TABLE payroll_adjustments (
    id                   NUMBER(19)     NOT NULL,
    run_id               NUMBER(19)     NOT NULL,
    employee_id          NUMBER(19)     NOT NULL,
    adjustment_type      VARCHAR2(10)   NOT NULL,              -- EARNING / DEDUCTION
    label                VARCHAR2(80)   NOT NULL,
    amount               NUMBER(14,2)   NOT NULL,
    taxable              NUMBER(1)      DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_payroll_adjustments PRIMARY KEY (id),
    CONSTRAINT fk_adj_run      FOREIGN KEY (run_id)      REFERENCES payroll_runs (id),
    CONSTRAINT fk_adj_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT ck_adj_amount CHECK (amount > 0)
);
CREATE INDEX ix_adj_run ON payroll_adjustments (run_id, employee_id);

-- ---- Default earnings structure ---------------------------------------------
INSERT INTO salary_components (id, code, name, calc_type, calc_value, taxable, sort_order, active, created_at, created_by, deleted)
VALUES (salary_component_seq.NEXTVAL, 'BASIC', 'Basic', 'PERCENT_OF_CTC', 40, 1, 10, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO salary_components (id, code, name, calc_type, calc_value, taxable, sort_order, active, created_at, created_by, deleted)
VALUES (salary_component_seq.NEXTVAL, 'HRA', 'House rent allowance', 'PERCENT_OF_BASIC', 50, 1, 20, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO salary_components (id, code, name, calc_type, calc_value, taxable, sort_order, active, created_at, created_by, deleted)
VALUES (salary_component_seq.NEXTVAL, 'CONV', 'Conveyance allowance', 'FIXED_MONTHLY', 1600, 1, 30, 1, SYSTIMESTAMP, 'system', 0);
INSERT INTO salary_components (id, code, name, calc_type, calc_value, taxable, sort_order, active, created_at, created_by, deleted)
VALUES (salary_component_seq.NEXTVAL, 'SPECIAL', 'Special allowance', 'BALANCING', 0, 1, 90, 1, SYSTIMESTAMP, 'system', 0);
