-- ============================================================================
-- V4: Employee aggregate, document vault, assets, timeline (Phase 2)
-- ============================================================================

-- Audit log gains an explicit action (CREATE / UPDATE / DELETE / VIEW_SENSITIVE / IMPORT).
ALTER TABLE audit_log ADD action VARCHAR2(20) DEFAULT 'UPDATE' NOT NULL;
CREATE INDEX ix_audit_changed_at ON audit_log (changed_at);

CREATE SEQUENCE employee_seq          START WITH 1 INCREMENT BY 1 NOCACHE;
-- Drives the human-readable employee code (EMP0001, ...). NOCACHE keeps codes gap-free
-- under normal operation.
CREATE SEQUENCE employee_code_seq     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE emergency_contact_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE employee_document_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE timeline_event_seq    START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE asset_seq             START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE asset_assignment_seq  START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---- Employees -------------------------------------------------------------
CREATE TABLE employees (
    id                 NUMBER(19)    NOT NULL,
    employee_code      VARCHAR2(20)  NOT NULL,
    first_name         VARCHAR2(80)  NOT NULL,
    middle_name        VARCHAR2(80),
    last_name          VARCHAR2(80)  NOT NULL,
    work_email         VARCHAR2(160) NOT NULL,
    personal_email     VARCHAR2(160),
    phone              VARCHAR2(30),
    gender             VARCHAR2(20),
    date_of_birth      DATE,
    marital_status     VARCHAR2(20),
    blood_group        VARCHAR2(5),
    nationality        VARCHAR2(60),
    current_address    VARCHAR2(500),
    permanent_address  VARCHAR2(500),
    department_id      NUMBER(19),
    designation_id     NUMBER(19),
    grade_id           NUMBER(19),
    location_id        NUMBER(19),
    business_unit_id   NUMBER(19),
    cost_center_id     NUMBER(19),
    manager_id         NUMBER(19),
    employment_type    VARCHAR2(20)  DEFAULT 'FULL_TIME' NOT NULL,
    status             VARCHAR2(20)  DEFAULT 'PROBATION' NOT NULL,
    date_of_joining    DATE          NOT NULL,
    probation_end_date DATE,
    confirmation_date  DATE,
    exit_date          DATE,
    notice_period_days NUMBER(10),
    annual_ctc         NUMBER(14,2),
    created_at         TIMESTAMP     NOT NULL,
    created_by         VARCHAR2(120),
    updated_at         TIMESTAMP,
    updated_by         VARCHAR2(120),
    deleted            NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_employees PRIMARY KEY (id),
    CONSTRAINT uq_employees_code UNIQUE (employee_code),
    CONSTRAINT uq_employees_work_email UNIQUE (work_email),
    CONSTRAINT fk_emp_department    FOREIGN KEY (department_id)    REFERENCES departments (id),
    CONSTRAINT fk_emp_designation   FOREIGN KEY (designation_id)   REFERENCES designations (id),
    CONSTRAINT fk_emp_grade         FOREIGN KEY (grade_id)         REFERENCES grades (id),
    CONSTRAINT fk_emp_location      FOREIGN KEY (location_id)      REFERENCES locations (id),
    CONSTRAINT fk_emp_business_unit FOREIGN KEY (business_unit_id) REFERENCES business_units (id),
    CONSTRAINT fk_emp_cost_center   FOREIGN KEY (cost_center_id)   REFERENCES cost_centers (id),
    CONSTRAINT fk_emp_manager       FOREIGN KEY (manager_id)       REFERENCES employees (id)
);
CREATE INDEX ix_emp_department    ON employees (department_id);
CREATE INDEX ix_emp_designation   ON employees (designation_id);
CREATE INDEX ix_emp_grade         ON employees (grade_id);
CREATE INDEX ix_emp_location      ON employees (location_id);
CREATE INDEX ix_emp_business_unit ON employees (business_unit_id);
CREATE INDEX ix_emp_cost_center   ON employees (cost_center_id);
CREATE INDEX ix_emp_manager       ON employees (manager_id);
CREATE INDEX ix_emp_status        ON employees (status);
CREATE INDEX ix_emp_name          ON employees (last_name, first_name);

-- Now that employees exist, close the loops from V1 (users) and V3 (department head).
ALTER TABLE departments ADD CONSTRAINT fk_dept_head FOREIGN KEY (head_employee_id) REFERENCES employees (id);
ALTER TABLE users ADD CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees (id);
CREATE INDEX ix_users_employee ON users (employee_id);

-- ---- Bank & statutory (1:1, encrypted columns hold Base64(IV || ciphertext)) --------
CREATE TABLE employee_statutory (
    employee_id         NUMBER(19)    NOT NULL,
    pan_enc             VARCHAR2(256),
    aadhaar_enc         VARCHAR2(256),
    uan                 VARCHAR2(20),
    esi_number          VARCHAR2(20),
    bank_name           VARCHAR2(120),
    account_holder_name VARCHAR2(160),
    bank_account_enc    VARCHAR2(256),
    bank_ifsc           VARCHAR2(15),
    tax_regime          VARCHAR2(10),
    created_at          TIMESTAMP     NOT NULL,
    created_by          VARCHAR2(120),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR2(120),
    deleted             NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_employee_statutory PRIMARY KEY (employee_id),
    CONSTRAINT fk_statutory_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

-- ---- Emergency contacts ----------------------------------------------------
CREATE TABLE emergency_contacts (
    id           NUMBER(19)    NOT NULL,
    employee_id  NUMBER(19)    NOT NULL,
    name         VARCHAR2(160) NOT NULL,
    relationship VARCHAR2(40)  NOT NULL,
    phone        VARCHAR2(30)  NOT NULL,
    email        VARCHAR2(160),
    is_primary   NUMBER(1)     DEFAULT 0 NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    created_by   VARCHAR2(120),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR2(120),
    deleted      NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_emergency_contacts PRIMARY KEY (id),
    CONSTRAINT fk_ec_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE INDEX ix_ec_employee ON emergency_contacts (employee_id);

-- ---- Document vault --------------------------------------------------------
CREATE TABLE employee_documents (
    id                 NUMBER(19)    NOT NULL,
    employee_id        NUMBER(19)    NOT NULL,
    category           VARCHAR2(30)  NOT NULL,
    title              VARCHAR2(160) NOT NULL,
    original_file_name VARCHAR2(255) NOT NULL,
    content_type       VARCHAR2(120) NOT NULL,
    size_bytes         NUMBER(19)    NOT NULL,
    storage_key        VARCHAR2(80)  NOT NULL,
    expiry_date        DATE,
    verified           NUMBER(1)     DEFAULT 0 NOT NULL,
    notes              VARCHAR2(500),
    created_at         TIMESTAMP     NOT NULL,
    created_by         VARCHAR2(120),
    updated_at         TIMESTAMP,
    updated_by         VARCHAR2(120),
    deleted            NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_employee_documents PRIMARY KEY (id),
    CONSTRAINT fk_doc_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE INDEX ix_doc_employee ON employee_documents (employee_id);
CREATE INDEX ix_doc_expiry   ON employee_documents (expiry_date);

-- ---- Lifecycle timeline ----------------------------------------------------
CREATE TABLE employee_timeline (
    id          NUMBER(19)     NOT NULL,
    employee_id NUMBER(19)     NOT NULL,
    event_type  VARCHAR2(30)   NOT NULL,
    event_date  DATE           NOT NULL,
    title       VARCHAR2(200)  NOT NULL,
    description VARCHAR2(1000),
    created_at  TIMESTAMP      NOT NULL,
    created_by  VARCHAR2(120),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR2(120),
    deleted     NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_employee_timeline PRIMARY KEY (id),
    CONSTRAINT fk_timeline_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE INDEX ix_timeline_employee ON employee_timeline (employee_id, event_date);

-- ---- Assets ----------------------------------------------------------------
CREATE TABLE assets (
    id                  NUMBER(19)    NOT NULL,
    asset_tag           VARCHAR2(40)  NOT NULL,
    name                VARCHAR2(160) NOT NULL,
    category            VARCHAR2(20)  NOT NULL,
    serial_number       VARCHAR2(80),
    status              VARCHAR2(20)  DEFAULT 'AVAILABLE' NOT NULL,
    purchase_date       DATE,
    purchase_cost       NUMBER(12,2),
    notes               VARCHAR2(500),
    current_employee_id NUMBER(19),
    created_at          TIMESTAMP     NOT NULL,
    created_by          VARCHAR2(120),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR2(120),
    deleted             NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_assets PRIMARY KEY (id),
    CONSTRAINT uq_assets_tag UNIQUE (asset_tag),
    CONSTRAINT fk_asset_current_employee FOREIGN KEY (current_employee_id) REFERENCES employees (id)
);
CREATE INDEX ix_asset_current_employee ON assets (current_employee_id);
CREATE INDEX ix_asset_status           ON assets (status);

CREATE TABLE asset_assignments (
    id               NUMBER(19)    NOT NULL,
    asset_id         NUMBER(19)    NOT NULL,
    employee_id      NUMBER(19)    NOT NULL,
    assigned_on      DATE          NOT NULL,
    returned_on      DATE,
    assign_notes     VARCHAR2(500),
    return_condition VARCHAR2(40),
    return_notes     VARCHAR2(500),
    created_at       TIMESTAMP     NOT NULL,
    created_by       VARCHAR2(120),
    updated_at       TIMESTAMP,
    updated_by       VARCHAR2(120),
    deleted          NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_asset_assignments PRIMARY KEY (id),
    CONSTRAINT fk_aa_asset    FOREIGN KEY (asset_id)    REFERENCES assets (id),
    CONSTRAINT fk_aa_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE INDEX ix_aa_asset    ON asset_assignments (asset_id);
CREATE INDEX ix_aa_employee ON asset_assignments (employee_id);

-- ---- New permissions for Phase 2 --------------------------------------------
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'EMPLOYEE_SENSITIVE_READ', 'Reveal bank/statutory IDs and compensation');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'AUDIT_VIEW',              'View the audit trail');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'ASSET_MANAGE',            'Manage and assign company assets');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_SENSITIVE_READ','AUDIT_VIEW','ASSET_MANAGE')
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code = 'EMPLOYEE_SENSITIVE_READ'
    WHERE r.code = 'PAYROLL_ADMIN';
