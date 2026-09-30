-- ============================================================================
-- V3: Company profile and org masters (Phase 2)
-- Every table backing a BaseEntity carries: created_at, created_by, updated_at,
-- updated_by, deleted (soft delete).
-- ============================================================================

CREATE SEQUENCE company_seq       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE business_unit_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE cost_center_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE department_seq    START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE designation_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE grade_seq         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE location_seq      START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---- Company (single row) --------------------------------------------------
CREATE TABLE company (
    id                   NUMBER(19)    NOT NULL,
    name                 VARCHAR2(160) NOT NULL,
    legal_name           VARCHAR2(200),
    registration_no      VARCHAR2(60),
    website              VARCHAR2(200),
    country              VARCHAR2(80),
    currency             VARCHAR2(3),
    fy_start_month       NUMBER(10),
    employee_code_prefix VARCHAR2(10),
    created_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_company PRIMARY KEY (id)
);

-- ---- Business units --------------------------------------------------------
CREATE TABLE business_units (
    id          NUMBER(19)    NOT NULL,
    code        VARCHAR2(30)  NOT NULL,
    name        VARCHAR2(120) NOT NULL,
    description VARCHAR2(500),
    active      NUMBER(1)     DEFAULT 1 NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    created_by  VARCHAR2(120),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR2(120),
    deleted     NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_business_units PRIMARY KEY (id),
    CONSTRAINT uq_business_units_code UNIQUE (code)
);

-- ---- Cost centers ----------------------------------------------------------
CREATE TABLE cost_centers (
    id          NUMBER(19)    NOT NULL,
    code        VARCHAR2(30)  NOT NULL,
    name        VARCHAR2(120) NOT NULL,
    description VARCHAR2(500),
    active      NUMBER(1)     DEFAULT 1 NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    created_by  VARCHAR2(120),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR2(120),
    deleted     NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_cost_centers PRIMARY KEY (id),
    CONSTRAINT uq_cost_centers_code UNIQUE (code)
);

-- ---- Designations ----------------------------------------------------------
CREATE TABLE designations (
    id          NUMBER(19)    NOT NULL,
    code        VARCHAR2(30)  NOT NULL,
    name        VARCHAR2(120) NOT NULL,
    description VARCHAR2(500),
    active      NUMBER(1)     DEFAULT 1 NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    created_by  VARCHAR2(120),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR2(120),
    deleted     NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_designations PRIMARY KEY (id),
    CONSTRAINT uq_designations_code UNIQUE (code)
);

-- ---- Grades ----------------------------------------------------------------
CREATE TABLE grades (
    id          NUMBER(19)    NOT NULL,
    code        VARCHAR2(30)  NOT NULL,
    name        VARCHAR2(120) NOT NULL,
    description VARCHAR2(500),
    active      NUMBER(1)     DEFAULT 1 NOT NULL,
    level_no    NUMBER(10),
    created_at  TIMESTAMP     NOT NULL,
    created_by  VARCHAR2(120),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR2(120),
    deleted     NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_grades PRIMARY KEY (id),
    CONSTRAINT uq_grades_code UNIQUE (code)
);

-- ---- Locations -------------------------------------------------------------
CREATE TABLE locations (
    id           NUMBER(19)    NOT NULL,
    code         VARCHAR2(30)  NOT NULL,
    name         VARCHAR2(120) NOT NULL,
    description  VARCHAR2(500),
    active       NUMBER(1)     DEFAULT 1 NOT NULL,
    address_line VARCHAR2(300),
    city         VARCHAR2(80),
    state        VARCHAR2(80),
    country      VARCHAR2(80),
    postal_code  VARCHAR2(20),
    timezone     VARCHAR2(50),
    created_at   TIMESTAMP     NOT NULL,
    created_by   VARCHAR2(120),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR2(120),
    deleted      NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT uq_locations_code UNIQUE (code)
);

-- ---- Departments -----------------------------------------------------------
-- head_employee_id gets its FK in V4, once the employees table exists.
CREATE TABLE departments (
    id               NUMBER(19)    NOT NULL,
    code             VARCHAR2(30)  NOT NULL,
    name             VARCHAR2(120) NOT NULL,
    description      VARCHAR2(500),
    active           NUMBER(1)     DEFAULT 1 NOT NULL,
    business_unit_id NUMBER(19),
    cost_center_id   NUMBER(19),
    parent_id        NUMBER(19),
    head_employee_id NUMBER(19),
    created_at       TIMESTAMP     NOT NULL,
    created_by       VARCHAR2(120),
    updated_at       TIMESTAMP,
    updated_by       VARCHAR2(120),
    deleted          NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uq_departments_code UNIQUE (code),
    CONSTRAINT fk_dept_business_unit FOREIGN KEY (business_unit_id) REFERENCES business_units (id),
    CONSTRAINT fk_dept_cost_center   FOREIGN KEY (cost_center_id)   REFERENCES cost_centers (id),
    CONSTRAINT fk_dept_parent        FOREIGN KEY (parent_id)        REFERENCES departments (id)
);
CREATE INDEX ix_dept_business_unit ON departments (business_unit_id);
CREATE INDEX ix_dept_cost_center   ON departments (cost_center_id);
CREATE INDEX ix_dept_parent        ON departments (parent_id);
CREATE INDEX ix_dept_head          ON departments (head_employee_id);
