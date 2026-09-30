-- ============================================================================
-- V1: Authentication & RBAC baseline (Phase 1)
-- Oracle DDL. IDs use sequences (allocationSize = 1 -> INCREMENT BY 1).
-- ============================================================================

-- ---- Sequences -------------------------------------------------------------
CREATE SEQUENCE permission_seq    START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE role_seq          START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE user_seq          START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE refresh_token_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE audit_log_seq     START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---- Permissions -----------------------------------------------------------
CREATE TABLE permissions (
    id          NUMBER(19)    NOT NULL,
    code        VARCHAR2(80)  NOT NULL,
    description VARCHAR2(200),
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

-- ---- Roles -----------------------------------------------------------------
CREATE TABLE roles (
    id   NUMBER(19)   NOT NULL,
    code VARCHAR2(40) NOT NULL,
    name VARCHAR2(100) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

-- ---- Role <-> Permission (M:N) ---------------------------------------------
CREATE TABLE role_permissions (
    role_id       NUMBER(19) NOT NULL,
    permission_id NUMBER(19) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);
CREATE INDEX ix_rp_permission ON role_permissions (permission_id);

-- ---- Users -----------------------------------------------------------------
CREATE TABLE users (
    id              NUMBER(19)    NOT NULL,
    email           VARCHAR2(160) NOT NULL,
    password_hash   VARCHAR2(100) NOT NULL,
    full_name       VARCHAR2(160),
    status          VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL,
    employee_id     NUMBER(19),
    failed_attempts NUMBER(10)    DEFAULT 0 NOT NULL,
    locked_until    TIMESTAMP,
    -- audit columns (BaseEntity)
    created_at      TIMESTAMP     NOT NULL,
    created_by      VARCHAR2(120),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR2(120),
    deleted         NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---- User <-> Role (M:N) ---------------------------------------------------
CREATE TABLE user_roles (
    user_id NUMBER(19) NOT NULL,
    role_id NUMBER(19) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
);
CREATE INDEX ix_ur_role ON user_roles (role_id);

-- ---- Refresh tokens --------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         NUMBER(19)    NOT NULL,
    token      VARCHAR2(200) NOT NULL,
    user_id    NUMBER(19)    NOT NULL,
    expires_at TIMESTAMP     NOT NULL,
    revoked    NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX ix_rt_user ON refresh_tokens (user_id);

-- ---- Audit log (sensitive changes: salary, bank, roles, ...) ---------------
CREATE TABLE audit_log (
    id         NUMBER(19)     NOT NULL,
    entity     VARCHAR2(100)  NOT NULL,
    entity_id  VARCHAR2(60),
    field      VARCHAR2(100),
    old_value  VARCHAR2(2000),
    new_value  VARCHAR2(2000),
    actor      VARCHAR2(120),
    changed_at TIMESTAMP      NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);
CREATE INDEX ix_audit_entity ON audit_log (entity, entity_id);
