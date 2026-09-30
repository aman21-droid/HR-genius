-- ============================================================================
-- V14: Helpdesk tickets + policy library with acknowledgements (Phase 7), with
-- demo data. Analytics reads existing tables and needs no schema.
-- Oracle syntax; runs on H2 (Oracle mode) too.
-- ============================================================================

INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'HELPDESK_AGENT', 'Work helpdesk tickets');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'POLICY_MANAGE',  'Publish policies and track acknowledgements');
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'HELPDESK_AGENT'
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN','HR_MANAGER','PAYROLL_ADMIN');
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'POLICY_MANAGE'
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN');

-- ---- Helpdesk -----------------------------------------------------------------
CREATE SEQUENCE ticket_seq         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ticket_no_seq      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ticket_comment_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- category: IT / HR / PAYROLL / FACILITIES / OTHER; priority: LOW / MEDIUM / HIGH / URGENT
-- status: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED (requester may reopen a resolved ticket)
CREATE TABLE helpdesk_tickets (
    id                   NUMBER(19)     NOT NULL,
    ticket_no            VARCHAR2(20)   NOT NULL,
    requester_emp_id     NUMBER(19)     NOT NULL,
    category             VARCHAR2(20)   NOT NULL,
    priority             VARCHAR2(10)   DEFAULT 'MEDIUM' NOT NULL,
    subject              VARCHAR2(200)  NOT NULL,
    description          VARCHAR2(4000) NOT NULL,
    status               VARCHAR2(20)   DEFAULT 'OPEN' NOT NULL,
    assignee_emp_id      NUMBER(19),
    due_at               TIMESTAMP      NOT NULL,
    first_response_at    TIMESTAMP,
    resolved_at          TIMESTAMP,
    closed_at            TIMESTAMP,
    resolution_note      VARCHAR2(2000),
    satisfaction         NUMBER(1),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_helpdesk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_ticket_no UNIQUE (ticket_no),
    CONSTRAINT fk_tkt_requester FOREIGN KEY (requester_emp_id) REFERENCES employees (id),
    CONSTRAINT fk_tkt_assignee  FOREIGN KEY (assignee_emp_id)  REFERENCES employees (id),
    CONSTRAINT ck_tkt_csat CHECK (satisfaction IS NULL OR satisfaction BETWEEN 1 AND 5)
);
CREATE INDEX ix_tkt_requester ON helpdesk_tickets (requester_emp_id);
CREATE INDEX ix_tkt_status    ON helpdesk_tickets (status, category);

-- internal = 1: agent-only note, never shown to the requester.
CREATE TABLE helpdesk_comments (
    id                   NUMBER(19)     NOT NULL,
    ticket_id            NUMBER(19)     NOT NULL,
    author_emp_id        NUMBER(19)     NOT NULL,
    body                 VARCHAR2(4000) NOT NULL,
    internal_note        NUMBER(1)      DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_helpdesk_comments PRIMARY KEY (id),
    CONSTRAINT fk_tc_ticket FOREIGN KEY (ticket_id)     REFERENCES helpdesk_tickets (id),
    CONSTRAINT fk_tc_author FOREIGN KEY (author_emp_id) REFERENCES employees (id)
);
CREATE INDEX ix_tc_ticket ON helpdesk_comments (ticket_id);

-- ---- Policies -----------------------------------------------------------------
CREATE SEQUENCE policy_seq     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE policy_ack_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- status: DRAFT -> PUBLISHED -> ARCHIVED. Publishing a change bumps version_no, which means
-- everyone must acknowledge again (acknowledgements are per version).
CREATE TABLE policies (
    id                   NUMBER(19)     NOT NULL,
    code                 VARCHAR2(30)   NOT NULL,
    title                VARCHAR2(160)  NOT NULL,
    category             VARCHAR2(40)   NOT NULL,
    summary              VARCHAR2(500),
    body                 VARCHAR2(4000) NOT NULL,
    version_no           NUMBER(5)      DEFAULT 1 NOT NULL,
    requires_ack         NUMBER(1)      DEFAULT 1 NOT NULL,
    status               VARCHAR2(20)   DEFAULT 'DRAFT' NOT NULL,
    effective_date       DATE,
    published_at         TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_policies PRIMARY KEY (id),
    CONSTRAINT uq_policy_code UNIQUE (code)
);

CREATE TABLE policy_acknowledgements (
    id                   NUMBER(19)     NOT NULL,
    policy_id            NUMBER(19)     NOT NULL,
    employee_id          NUMBER(19)     NOT NULL,
    version_no           NUMBER(5)      NOT NULL,
    acknowledged_at      TIMESTAMP      NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_policy_acks PRIMARY KEY (id),
    CONSTRAINT fk_pa_policy   FOREIGN KEY (policy_id)   REFERENCES policies (id),
    CONSTRAINT fk_pa_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT uq_policy_ack UNIQUE (policy_id, employee_id, version_no)
);
CREATE INDEX ix_pa_employee ON policy_acknowledgements (employee_id);

-- ============================================================================
-- Demo data
-- ============================================================================
INSERT INTO policies (id, code, title, category, summary, body, version_no, requires_ack, status, effective_date, published_at, created_at, created_by, deleted)
VALUES (policy_seq.NEXTVAL, 'CODE-OF-CONDUCT', 'Code of Conduct', 'Ethics',
        'How we treat each other, customers and company assets.',
        'We act with integrity, treat everyone with respect and report concerns without fear of retaliation.

1. Respect: harassment and discrimination of any kind are not tolerated.
2. Integrity: avoid conflicts of interest and declare gifts above INR 5,000.
3. Confidentiality: protect customer and employee data; share only on a need-to-know basis.
4. Speak up: raise concerns with your manager, HR, or the ethics helpdesk category.',
        2, 1, 'PUBLISHED', DATE '2026-04-01', TIMESTAMP '2026-04-01 09:00:00', SYSTIMESTAMP, 'system', 0);
INSERT INTO policies (id, code, title, category, summary, body, version_no, requires_ack, status, effective_date, published_at, created_at, created_by, deleted)
VALUES (policy_seq.NEXTVAL, 'IT-ACCEPTABLE-USE', 'IT Acceptable Use', 'IT & Security',
        'Using laptops, accounts and networks safely.',
        'Company devices and accounts are for work. Lock your screen when away, never share passwords, ' ||
        'use the VPN on public networks and report lost devices to IT within 24 hours via the helpdesk.',
        1, 1, 'PUBLISHED', DATE '2026-01-01', TIMESTAMP '2026-01-01 09:00:00', SYSTIMESTAMP, 'system', 0);
INSERT INTO policies (id, code, title, category, summary, body, version_no, requires_ack, status, effective_date, published_at, created_at, created_by, deleted)
VALUES (policy_seq.NEXTVAL, 'LEAVE-POLICY', 'Leave Policy', 'HR',
        'Leave types, accrual and how to apply.',
        'Casual 12 days, Sick 10 days and Earned 15 days per year. Apply in HRGenius; your manager and HR approve. ' ||
        'Unused Earned Leave carries forward up to the configured cap. Loss of pay applies to approved LWP days.',
        1, 0, 'PUBLISHED', DATE '2026-01-01', TIMESTAMP '2026-01-01 09:00:00', SYSTIMESTAMP, 'system', 0);
INSERT INTO policies (id, code, title, category, summary, body, version_no, requires_ack, status, created_at, created_by, deleted)
VALUES (policy_seq.NEXTVAL, 'REMOTE-WORK', 'Remote Work Guidelines', 'HR',
        'Draft: hybrid working expectations.',
        'Draft under review with leadership.', 1, 1, 'DRAFT', SYSTIMESTAMP, 'system', 0);

-- About two thirds of current employees have acknowledged the current Code of Conduct and IT policy.
INSERT INTO policy_acknowledgements (id, policy_id, employee_id, version_no, acknowledged_at, created_at, created_by, deleted)
    SELECT policy_ack_seq.NEXTVAL, p.id, e.id, p.version_no, TIMESTAMP '2026-04-05 10:00:00', SYSTIMESTAMP, 'system', 0
    FROM policies p, employees e
    WHERE p.code IN ('CODE-OF-CONDUCT', 'IT-ACCEPTABLE-USE') AND e.deleted = 0 AND e.status <> 'EXITED'
      AND MOD(e.id, 3) <> 0 AND e.work_email <> 'employee@hrgenius.com';

-- Tickets
INSERT INTO helpdesk_tickets (id, ticket_no, requester_emp_id, category, priority, subject, description, status, due_at, created_at, created_by, deleted)
    SELECT ticket_seq.NEXTVAL, 'HD-' || LPAD(TO_CHAR(ticket_no_seq.NEXTVAL), 5, '0'), e.id, 'IT', 'HIGH',
           'VPN disconnects every few minutes', 'Since yesterday the VPN drops roughly every 5 minutes on home Wi-Fi.',
           'OPEN', CURRENT_TIMESTAMP + INTERVAL '1' DAY, CURRENT_TIMESTAMP - INTERVAL '2' HOUR, 'employee@hrgenius.com', 0
    FROM employees e WHERE e.work_email = 'employee@hrgenius.com';
INSERT INTO helpdesk_tickets (id, ticket_no, requester_emp_id, category, priority, subject, description, status, assignee_emp_id, due_at, first_response_at, created_at, created_by, deleted)
    SELECT ticket_seq.NEXTVAL, 'HD-' || LPAD(TO_CHAR(ticket_no_seq.NEXTVAL), 5, '0'), e.id, 'PAYROLL', 'MEDIUM',
           'HRA missing in September payslip?', 'My September payslip shows a lower HRA than August. Can you check?',
           'IN_PROGRESS', a.id, CURRENT_TIMESTAMP + INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '20' HOUR,
           CURRENT_TIMESTAMP - INTERVAL '1' DAY, 'manager@hrgenius.com', 0
    FROM employees e, employees a WHERE e.work_email = 'manager@hrgenius.com' AND a.work_email = 'payroll@hrgenius.com';
INSERT INTO helpdesk_tickets (id, ticket_no, requester_emp_id, category, priority, subject, description, status, assignee_emp_id, due_at, first_response_at, resolved_at, resolution_note, created_at, created_by, deleted)
    SELECT ticket_seq.NEXTVAL, 'HD-' || LPAD(TO_CHAR(ticket_no_seq.NEXTVAL), 5, '0'), e.id, 'HR', 'LOW',
           'Update my address', 'I moved; please update my current address in my profile.',
           'RESOLVED', a.id, CURRENT_TIMESTAMP - INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '4' DAY,
           CURRENT_TIMESTAMP - INTERVAL '3' DAY, 'Address updated in your profile.',
           CURRENT_TIMESTAMP - INTERVAL '5' DAY, 'employee@hrgenius.com', 0
    FROM employees e, employees a WHERE e.work_email = 'employee@hrgenius.com' AND a.work_email = 'hr@hrgenius.com';
INSERT INTO helpdesk_tickets (id, ticket_no, requester_emp_id, category, priority, subject, description, status, assignee_emp_id, due_at, first_response_at, resolved_at, closed_at, resolution_note, satisfaction, created_at, created_by, deleted)
    SELECT ticket_seq.NEXTVAL, 'HD-' || LPAD(TO_CHAR(ticket_no_seq.NEXTVAL), 5, '0'), e.id, 'FACILITIES', 'MEDIUM',
           'Broken chair at desk 4B', 'The chair at 4B has a broken gas lift.',
           'CLOSED', a.id, CURRENT_TIMESTAMP - INTERVAL '7' DAY, CURRENT_TIMESTAMP - INTERVAL '9' DAY,
           CURRENT_TIMESTAMP - INTERVAL '8' DAY, CURRENT_TIMESTAMP - INTERVAL '7' DAY, 'Replaced the chair.', 5,
           CURRENT_TIMESTAMP - INTERVAL '10' DAY, 'recruiter@hrgenius.com', 0
    FROM employees e, employees a WHERE e.work_email = 'recruiter@hrgenius.com' AND a.work_email = 'hr@hrgenius.com';

INSERT INTO helpdesk_comments (id, ticket_id, author_emp_id, body, internal_note, created_at, created_by, deleted)
    SELECT ticket_comment_seq.NEXTVAL, t.id, a.id, 'Looking into it. The September run pro-rated HRA for one LWP day.', 0,
           CURRENT_TIMESTAMP - INTERVAL '20' HOUR, 'payroll@hrgenius.com', 0
    FROM helpdesk_tickets t, employees a WHERE t.subject = 'HRA missing in September payslip?' AND a.work_email = 'payroll@hrgenius.com';
INSERT INTO helpdesk_comments (id, ticket_id, author_emp_id, body, internal_note, created_at, created_by, deleted)
    SELECT ticket_comment_seq.NEXTVAL, t.id, a.id, 'Check the leave ledger before replying.', 1,
           CURRENT_TIMESTAMP - INTERVAL '19' HOUR, 'payroll@hrgenius.com', 0
    FROM helpdesk_tickets t, employees a WHERE t.subject = 'HRA missing in September payslip?' AND a.work_email = 'payroll@hrgenius.com';
