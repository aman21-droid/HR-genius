-- ============================================================================
-- V9: Recruitment (ATS) + Onboarding (Phase 4)
-- Every table backing a BaseEntity carries: created_at, created_by, updated_at,
-- updated_by, deleted (soft delete). Oracle syntax; runs on H2 (Oracle mode) too.
-- Requisitions and offers route through the Phase 3 approval engine
-- (subject types REQUISITION and OFFER).
-- ============================================================================

-- ---- New permissions -------------------------------------------------------
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'RECRUITMENT_APPROVE', 'Approve job requisitions and offers (HR sign-off)');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'ONBOARDING_MANAGE',   'Manage onboarding templates and new-hire plans');

-- SUPER_ADMIN holds every permission; grant the new ones explicitly (V2 cross join ran before they existed).
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('RECRUITMENT_APPROVE','ONBOARDING_MANAGE')
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN');
-- HR_MANAGER runs onboarding day to day.
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code = 'ONBOARDING_MANAGE'
    WHERE r.code = 'HR_MANAGER';

-- ============================================================================
-- Recruitment
-- ============================================================================
CREATE SEQUENCE requisition_seq       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE requisition_code_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE candidate_seq         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE application_seq       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE application_event_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE interview_seq         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE interview_feedback_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE offer_seq             START WITH 1 INCREMENT BY 1 NOCACHE;

-- An approved headcount request. Status: DRAFT -> PENDING_APPROVAL -> OPEN <-> ON_HOLD -> CLOSED;
-- REJECTED when the approval flow rejects, CANCELLED when withdrawn.
CREATE TABLE job_requisitions (
    id                   NUMBER(19)     NOT NULL,
    req_code             VARCHAR2(20)   NOT NULL,              -- REQ-0001
    title                VARCHAR2(160)  NOT NULL,
    department_id        NUMBER(19)     NOT NULL,
    designation_id       NUMBER(19)     NOT NULL,
    location_id          NUMBER(19)     NOT NULL,
    grade_id             NUMBER(19),
    hiring_manager_id    NUMBER(19)     NOT NULL,
    employment_type      VARCHAR2(20)   DEFAULT 'FULL_TIME' NOT NULL,
    openings             NUMBER(5)      DEFAULT 1 NOT NULL,
    filled               NUMBER(5)      DEFAULT 0 NOT NULL,
    min_experience       NUMBER(4,1),
    max_experience       NUMBER(4,1),
    salary_min           NUMBER(14,2),
    salary_max           NUMBER(14,2),
    skills               VARCHAR2(500),
    description          VARCHAR2(4000),
    target_date          DATE,
    publish_on_careers   NUMBER(1)      DEFAULT 1 NOT NULL,
    status               VARCHAR2(20)   DEFAULT 'DRAFT' NOT NULL,
    approval_request_id  NUMBER(19),
    opened_at            TIMESTAMP,
    closed_at            TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_job_requisitions PRIMARY KEY (id),
    CONSTRAINT uq_req_code UNIQUE (req_code),
    CONSTRAINT fk_req_department  FOREIGN KEY (department_id)       REFERENCES departments (id),
    CONSTRAINT fk_req_designation FOREIGN KEY (designation_id)      REFERENCES designations (id),
    CONSTRAINT fk_req_location    FOREIGN KEY (location_id)         REFERENCES locations (id),
    CONSTRAINT fk_req_grade       FOREIGN KEY (grade_id)            REFERENCES grades (id),
    CONSTRAINT fk_req_manager     FOREIGN KEY (hiring_manager_id)   REFERENCES employees (id),
    CONSTRAINT fk_req_approval    FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT ck_req_openings CHECK (openings >= 1 AND filled >= 0)
);
CREATE INDEX ix_req_status  ON job_requisitions (status);
CREATE INDEX ix_req_manager ON job_requisitions (hiring_manager_id);

-- A person in the talent pool; one row per email, reused across applications.
CREATE TABLE candidates (
    id                   NUMBER(19)     NOT NULL,
    first_name           VARCHAR2(80)   NOT NULL,
    last_name            VARCHAR2(80)   NOT NULL,
    email                VARCHAR2(160)  NOT NULL,
    phone                VARCHAR2(30),
    current_company      VARCHAR2(120),
    current_title        VARCHAR2(120),
    total_experience     NUMBER(4,1),
    current_ctc          NUMBER(14,2),
    expected_ctc         NUMBER(14,2),
    notice_period_days   NUMBER(5),
    city                 VARCHAR2(80),
    linkedin_url         VARCHAR2(300),
    source               VARCHAR2(20)   DEFAULT 'DIRECT' NOT NULL, -- CAREERS_PAGE/REFERRAL/LINKEDIN/AGENCY/DIRECT/OTHER
    referred_by_emp_id   NUMBER(19),
    resume_key           VARCHAR2(40),                           -- FileStorageService key
    resume_file_name     VARCHAR2(255),
    resume_content_type  VARCHAR2(120),
    resume_size          NUMBER(12),
    notes                VARCHAR2(2000),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_candidates PRIMARY KEY (id),
    CONSTRAINT uq_candidate_email UNIQUE (email),
    CONSTRAINT fk_cand_referrer FOREIGN KEY (referred_by_emp_id) REFERENCES employees (id)
);

-- A candidate in a requisition's pipeline.
-- Stage: APPLIED -> SCREENING -> INTERVIEW -> OFFER -> HIRED; REJECTED / WITHDRAWN are terminal exits.
CREATE TABLE job_applications (
    id                   NUMBER(19)     NOT NULL,
    requisition_id       NUMBER(19)     NOT NULL,
    candidate_id         NUMBER(19)     NOT NULL,
    stage                VARCHAR2(20)   DEFAULT 'APPLIED' NOT NULL,
    stage_changed_at     TIMESTAMP      NOT NULL,
    source               VARCHAR2(20)   DEFAULT 'DIRECT' NOT NULL,
    cover_note           VARCHAR2(2000),
    rejection_reason     VARCHAR2(500),
    employee_id          NUMBER(19),                             -- set once converted to an employee
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_job_applications PRIMARY KEY (id),
    CONSTRAINT fk_app_requisition FOREIGN KEY (requisition_id) REFERENCES job_requisitions (id),
    CONSTRAINT fk_app_candidate   FOREIGN KEY (candidate_id)   REFERENCES candidates (id),
    CONSTRAINT fk_app_employee    FOREIGN KEY (employee_id)    REFERENCES employees (id),
    CONSTRAINT uq_application UNIQUE (requisition_id, candidate_id)
);
CREATE INDEX ix_app_req_stage ON job_applications (requisition_id, stage);
CREATE INDEX ix_app_candidate ON job_applications (candidate_id);

-- Append-only activity trail for an application (stage moves, notes, interview/offer events).
CREATE TABLE application_events (
    id                   NUMBER(19)     NOT NULL,
    application_id       NUMBER(19)     NOT NULL,
    event_type           VARCHAR2(30)   NOT NULL,  -- STAGE_CHANGED/NOTE/INTERVIEW_SCHEDULED/FEEDBACK/OFFER/HIRED
    from_stage           VARCHAR2(20),
    to_stage             VARCHAR2(20),
    message              VARCHAR2(1000),
    actor_name           VARCHAR2(160),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_application_events PRIMARY KEY (id),
    CONSTRAINT fk_evt_application FOREIGN KEY (application_id) REFERENCES job_applications (id)
);
CREATE INDEX ix_evt_application ON application_events (application_id);

-- Status: SCHEDULED -> COMPLETED (all feedback in) | CANCELLED | NO_SHOW
CREATE TABLE interviews (
    id                   NUMBER(19)     NOT NULL,
    application_id       NUMBER(19)     NOT NULL,
    round_name           VARCHAR2(80)   NOT NULL,
    interview_mode       VARCHAR2(20)   DEFAULT 'VIDEO' NOT NULL, -- IN_PERSON/VIDEO/PHONE
    scheduled_at         TIMESTAMP      NOT NULL,
    duration_minutes     NUMBER(5)      DEFAULT 60 NOT NULL,
    location_or_link     VARCHAR2(300),
    status               VARCHAR2(20)   DEFAULT 'SCHEDULED' NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_interviews PRIMARY KEY (id),
    CONSTRAINT fk_int_application FOREIGN KEY (application_id) REFERENCES job_applications (id)
);
CREATE INDEX ix_int_application ON interviews (application_id);
CREATE INDEX ix_int_scheduled   ON interviews (scheduled_at);

-- One row per panelist, created at scheduling; submitted_at NULL = feedback still owed.
CREATE TABLE interview_feedback (
    id                   NUMBER(19)     NOT NULL,
    interview_id         NUMBER(19)     NOT NULL,
    interviewer_emp_id   NUMBER(19)     NOT NULL,
    rating               NUMBER(1),                              -- 1..5
    recommendation       VARCHAR2(20),                           -- STRONG_HIRE/HIRE/NO_HIRE/STRONG_NO_HIRE
    strengths            VARCHAR2(2000),
    concerns             VARCHAR2(2000),
    submitted_at         TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_interview_feedback PRIMARY KEY (id),
    CONSTRAINT fk_fb_interview   FOREIGN KEY (interview_id)       REFERENCES interviews (id),
    CONSTRAINT fk_fb_interviewer FOREIGN KEY (interviewer_emp_id) REFERENCES employees (id),
    CONSTRAINT uq_feedback UNIQUE (interview_id, interviewer_emp_id),
    CONSTRAINT ck_fb_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5))
);
CREATE INDEX ix_fb_interviewer ON interview_feedback (interviewer_emp_id);

-- Status: DRAFT -> PENDING_APPROVAL -> APPROVED -> SENT -> ACCEPTED | DECLINED;
-- REJECTED (approval flow said no), WITHDRAWN (company pulled it).
CREATE TABLE offers (
    id                   NUMBER(19)     NOT NULL,
    application_id       NUMBER(19)     NOT NULL,
    designation_id       NUMBER(19)     NOT NULL,
    department_id        NUMBER(19)     NOT NULL,
    location_id          NUMBER(19)     NOT NULL,
    grade_id             NUMBER(19),
    employment_type      VARCHAR2(20)   DEFAULT 'FULL_TIME' NOT NULL,
    annual_ctc           NUMBER(14,2)   NOT NULL,
    joining_date         DATE           NOT NULL,
    expiry_date          DATE,
    notes                VARCHAR2(1000),
    status               VARCHAR2(20)   DEFAULT 'DRAFT' NOT NULL,
    approval_request_id  NUMBER(19),
    sent_at              TIMESTAMP,
    responded_at         TIMESTAMP,
    decline_reason       VARCHAR2(500),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_offers PRIMARY KEY (id),
    CONSTRAINT fk_offer_application FOREIGN KEY (application_id)      REFERENCES job_applications (id),
    CONSTRAINT fk_offer_designation FOREIGN KEY (designation_id)      REFERENCES designations (id),
    CONSTRAINT fk_offer_department  FOREIGN KEY (department_id)       REFERENCES departments (id),
    CONSTRAINT fk_offer_location    FOREIGN KEY (location_id)         REFERENCES locations (id),
    CONSTRAINT fk_offer_grade       FOREIGN KEY (grade_id)            REFERENCES grades (id),
    CONSTRAINT fk_offer_approval    FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id),
    CONSTRAINT ck_offer_ctc CHECK (annual_ctc >= 0)
);
CREATE INDEX ix_offer_application ON offers (application_id);

-- ============================================================================
-- Onboarding
-- ============================================================================
CREATE SEQUENCE onboarding_template_seq      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE onboarding_template_task_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE onboarding_plan_seq          START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE onboarding_task_seq          START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE onboarding_templates (
    id                   NUMBER(19)     NOT NULL,
    name                 VARCHAR2(120)  NOT NULL,
    description          VARCHAR2(500),
    is_default           NUMBER(1)      DEFAULT 0 NOT NULL,
    active               NUMBER(1)      DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_onboarding_templates PRIMARY KEY (id)
);

-- owner_role decides who the generated task is assigned to:
-- HR (the person starting onboarding), MANAGER (new hire's manager), EMPLOYEE (the new hire),
-- IT / ADMIN / FINANCE (team queues, left unassigned for anyone with ONBOARDING_MANAGE).
-- due_offset_days is relative to the joining date (negative = before day one).
CREATE TABLE onboarding_template_tasks (
    id                   NUMBER(19)     NOT NULL,
    template_id          NUMBER(19)     NOT NULL,
    title                VARCHAR2(160)  NOT NULL,
    description          VARCHAR2(1000),
    owner_role           VARCHAR2(20)   NOT NULL,
    due_offset_days      NUMBER(5)      DEFAULT 0 NOT NULL,
    sort_order           NUMBER(5)      DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_onboarding_template_tasks PRIMARY KEY (id),
    CONSTRAINT fk_ott_template FOREIGN KEY (template_id) REFERENCES onboarding_templates (id)
);
CREATE INDEX ix_ott_template ON onboarding_template_tasks (template_id);

-- A new hire's checklist instance. Status: IN_PROGRESS -> COMPLETED
CREATE TABLE onboarding_plans (
    id                   NUMBER(19)     NOT NULL,
    employee_id          NUMBER(19)     NOT NULL,
    application_id       NUMBER(19),
    template_id          NUMBER(19),
    start_date           DATE           NOT NULL,
    status               VARCHAR2(20)   DEFAULT 'IN_PROGRESS' NOT NULL,
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_onboarding_plans PRIMARY KEY (id),
    CONSTRAINT fk_op_employee    FOREIGN KEY (employee_id)    REFERENCES employees (id),
    CONSTRAINT fk_op_application FOREIGN KEY (application_id) REFERENCES job_applications (id),
    CONSTRAINT fk_op_template    FOREIGN KEY (template_id)    REFERENCES onboarding_templates (id)
);
CREATE INDEX ix_op_employee ON onboarding_plans (employee_id);
CREATE INDEX ix_op_status   ON onboarding_plans (status);

-- Status: PENDING -> DONE | SKIPPED
CREATE TABLE onboarding_tasks (
    id                   NUMBER(19)     NOT NULL,
    plan_id              NUMBER(19)     NOT NULL,
    title                VARCHAR2(160)  NOT NULL,
    description          VARCHAR2(1000),
    owner_role           VARCHAR2(20)   NOT NULL,
    assignee_emp_id      NUMBER(19),
    due_date             DATE,
    status               VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    completed_at         TIMESTAMP,
    completed_by         VARCHAR2(160),
    sort_order           NUMBER(5)      DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_onboarding_tasks PRIMARY KEY (id),
    CONSTRAINT fk_ot_plan     FOREIGN KEY (plan_id)         REFERENCES onboarding_plans (id),
    CONSTRAINT fk_ot_assignee FOREIGN KEY (assignee_emp_id) REFERENCES employees (id)
);
CREATE INDEX ix_ot_plan     ON onboarding_tasks (plan_id);
CREATE INDEX ix_ot_assignee ON onboarding_tasks (assignee_emp_id, status);
