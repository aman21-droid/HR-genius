-- ============================================================================
-- V13: Performance management (Phase 6) — review cycles, goals, reviews,
-- continuous feedback — plus demo data (closed H1 2026, active H2 2026).
-- Oracle syntax; runs on H2 (Oracle mode) too.
-- ============================================================================

-- Running review cycles is an HR task; PERFORMANCE_MANAGE (held by line managers too) is not enough.
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'PERFORMANCE_ADMIN', 'Run review cycles and see all reviews');
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'PERFORMANCE_ADMIN'
    WHERE r.code IN ('SUPER_ADMIN','HR_ADMIN','HR_MANAGER');

CREATE SEQUENCE review_cycle_seq       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE performance_review_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE goal_seq               START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE feedback_note_seq      START WITH 1 INCREMENT BY 1 NOCACHE;

-- Status: DRAFT -> ACTIVE (reviews open) -> CLOSED
CREATE TABLE review_cycles (
    id                   NUMBER(19)     NOT NULL,
    name                 VARCHAR2(80)   NOT NULL,
    start_date           DATE           NOT NULL,
    end_date             DATE           NOT NULL,
    self_review_due      DATE,
    manager_review_due   DATE,
    status               VARCHAR2(20)   DEFAULT 'DRAFT' NOT NULL,
    launched_at          TIMESTAMP,
    closed_at            TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_review_cycles PRIMARY KEY (id),
    CONSTRAINT ck_cycle_dates CHECK (end_date >= start_date)
);

-- One review per employee per cycle.
-- Status: NOT_STARTED -> SELF_SUBMITTED -> MANAGER_SUBMITTED -> ACKNOWLEDGED
CREATE TABLE performance_reviews (
    id                   NUMBER(19)     NOT NULL,
    cycle_id             NUMBER(19)     NOT NULL,
    employee_id          NUMBER(19)     NOT NULL,
    reviewer_emp_id      NUMBER(19)     NOT NULL,
    status               VARCHAR2(20)   DEFAULT 'NOT_STARTED' NOT NULL,
    self_rating          NUMBER(1),
    self_comments        VARCHAR2(4000),
    manager_rating       NUMBER(1),
    manager_comments     VARCHAR2(4000),
    final_score          NUMBER(4,2),                 -- weighted average of manager goal ratings
    self_submitted_at    TIMESTAMP,
    manager_submitted_at TIMESTAMP,
    acknowledged_at      TIMESTAMP,
    ack_comment          VARCHAR2(1000),
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_performance_reviews PRIMARY KEY (id),
    CONSTRAINT fk_rev_cycle    FOREIGN KEY (cycle_id)        REFERENCES review_cycles (id),
    CONSTRAINT fk_rev_employee FOREIGN KEY (employee_id)     REFERENCES employees (id),
    CONSTRAINT fk_rev_reviewer FOREIGN KEY (reviewer_emp_id) REFERENCES employees (id),
    CONSTRAINT uq_review UNIQUE (cycle_id, employee_id),
    CONSTRAINT ck_rev_self CHECK (self_rating IS NULL OR self_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_rev_mgr  CHECK (manager_rating IS NULL OR manager_rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_rev_reviewer ON performance_reviews (reviewer_emp_id, status);
CREATE INDEX ix_rev_employee ON performance_reviews (employee_id);

-- Goals live inside a review; ratings for the goal are stored on the goal itself.
-- Status: NOT_STARTED / ON_TRACK / AT_RISK / OFF_TRACK / DONE
CREATE TABLE goals (
    id                   NUMBER(19)     NOT NULL,
    review_id            NUMBER(19)     NOT NULL,
    title                VARCHAR2(200)  NOT NULL,
    description          VARCHAR2(1000),
    weight               NUMBER(3)      DEFAULT 0 NOT NULL,      -- percent; a review's goals total 100
    target_date          DATE,
    progress             NUMBER(3)      DEFAULT 0 NOT NULL,      -- 0..100
    status               VARCHAR2(20)   DEFAULT 'NOT_STARTED' NOT NULL,
    self_rating          NUMBER(1),
    self_comment         VARCHAR2(1000),
    manager_rating       NUMBER(1),
    manager_comment      VARCHAR2(1000),
    sort_order           NUMBER(5)      DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_goals PRIMARY KEY (id),
    CONSTRAINT fk_goal_review FOREIGN KEY (review_id) REFERENCES performance_reviews (id),
    CONSTRAINT ck_goal_weight   CHECK (weight BETWEEN 0 AND 100),
    CONSTRAINT ck_goal_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_goal_self CHECK (self_rating IS NULL OR self_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_goal_mgr  CHECK (manager_rating IS NULL OR manager_rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_goal_review ON goals (review_id);

-- Continuous feedback. kind: PRAISE / CONSTRUCTIVE. visibility: PUBLIC (kudos wall) / PRIVATE
-- (author, recipient and the recipient's manager).
CREATE TABLE feedback_notes (
    id                   NUMBER(19)     NOT NULL,
    from_emp_id          NUMBER(19)     NOT NULL,
    to_emp_id            NUMBER(19)     NOT NULL,
    kind                 VARCHAR2(20)   NOT NULL,
    visibility           VARCHAR2(10)   NOT NULL,
    message              VARCHAR2(1000) NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    created_by           VARCHAR2(120),
    updated_at           TIMESTAMP,
    updated_by           VARCHAR2(120),
    deleted              NUMBER(1)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_feedback_notes PRIMARY KEY (id),
    CONSTRAINT fk_fbn_from FOREIGN KEY (from_emp_id) REFERENCES employees (id),
    CONSTRAINT fk_fbn_to   FOREIGN KEY (to_emp_id)   REFERENCES employees (id)
);
CREATE INDEX ix_fbn_to   ON feedback_notes (to_emp_id);
CREATE INDEX ix_fbn_from ON feedback_notes (from_emp_id);

-- ============================================================================
-- Demo data
-- ============================================================================
INSERT INTO review_cycles (id, name, start_date, end_date, self_review_due, manager_review_due, status, launched_at, closed_at, created_at, created_by, deleted)
VALUES (review_cycle_seq.NEXTVAL, 'H1 2026', DATE '2026-01-01', DATE '2026-06-30', DATE '2026-07-10', DATE '2026-07-20', 'CLOSED',
        TIMESTAMP '2026-06-15 10:00:00', TIMESTAMP '2026-07-31 18:00:00', SYSTIMESTAMP, 'system', 0);
INSERT INTO review_cycles (id, name, start_date, end_date, self_review_due, manager_review_due, status, launched_at, created_at, created_by, deleted)
VALUES (review_cycle_seq.NEXTVAL, 'H2 2026', DATE '2026-07-01', DATE '2026-12-31', DATE '2027-01-10', DATE '2027-01-20', 'ACTIVE',
        TIMESTAMP '2026-07-01 09:00:00', SYSTIMESTAMP, 'system', 0);

-- Reviews for every current employee with a manager who had joined by the cycle end.
INSERT INTO performance_reviews (id, cycle_id, employee_id, reviewer_emp_id, status, created_at, created_by, deleted)
    SELECT performance_review_seq.NEXTVAL, c.id, e.id, e.manager_id, 'NOT_STARTED', SYSTIMESTAMP, 'system', 0
    FROM review_cycles c, employees e
    WHERE c.name IN ('H1 2026', 'H2 2026') AND e.manager_id IS NOT NULL AND e.deleted = 0
      AND e.date_of_joining <= c.end_date AND (e.exit_date IS NULL OR e.exit_date > c.end_date);

-- H1 2026 is finished: a deterministic spread of ratings (mostly 3-4, some 2 and 5).
UPDATE performance_reviews r SET
    status = 'ACKNOWLEDGED',
    self_rating = 3 + MOD(r.employee_id, 2),
    manager_rating = CASE MOD(r.employee_id, 10) WHEN 0 THEN 5 WHEN 1 THEN 2 WHEN 2 THEN 5 WHEN 3 THEN 4 WHEN 4 THEN 4
                                                  WHEN 5 THEN 3 WHEN 6 THEN 4 WHEN 7 THEN 3 WHEN 8 THEN 4 ELSE 3 END,
    self_comments = 'Delivered my key goals for the half.',
    manager_comments = 'Solid half; see goal notes for specifics.',
    self_submitted_at = TIMESTAMP '2026-07-08 12:00:00',
    manager_submitted_at = TIMESTAMP '2026-07-18 12:00:00',
    acknowledged_at = TIMESTAMP '2026-07-25 12:00:00'
WHERE r.cycle_id = (SELECT id FROM review_cycles WHERE name = 'H1 2026');
UPDATE performance_reviews SET final_score = manager_rating
WHERE cycle_id = (SELECT id FROM review_cycles WHERE name = 'H1 2026');

-- One 100%-weight goal per H1 review, rated like the review.
INSERT INTO goals (id, review_id, title, weight, progress, status, self_rating, manager_rating, sort_order, created_at, created_by, deleted)
    SELECT goal_seq.NEXTVAL, r.id, 'Deliver H1 objectives', 100, 100, 'DONE', r.self_rating, r.manager_rating, 10, SYSTIMESTAMP, 'system', 0
    FROM performance_reviews r WHERE r.cycle_id = (SELECT id FROM review_cycles WHERE name = 'H1 2026');

-- H2 2026: Emma (employee@) has drafted goals and is tracking progress.
INSERT INTO goals (id, review_id, title, description, weight, target_date, progress, status, sort_order, created_at, created_by, deleted)
    SELECT goal_seq.NEXTVAL, r.id, v.title, v.descr, v.weight, v.target, v.progress, v.status, v.sort_no, SYSTIMESTAMP, 'system', 0
    FROM performance_reviews r JOIN employees e ON e.id = r.employee_id
         JOIN review_cycles c ON c.id = r.cycle_id,
         (SELECT 'Ship the leave self-service revamp' AS title, 'Own the frontend for the new leave flows' AS descr, 50 AS weight,
                 DATE '2026-11-30' AS target, 70 AS progress, 'ON_TRACK' AS status, 10 AS sort_no FROM dual UNION ALL
          SELECT 'Raise unit test coverage to 80%', 'Cover the attendance and leave modules', 30, DATE '2026-12-15', 40, 'AT_RISK', 20 FROM dual UNION ALL
          SELECT 'Mentor an intern', 'Weekly pairing with the summer intern', 20, DATE '2026-12-31', 60, 'ON_TRACK', 30 FROM dual) v
    WHERE e.work_email = 'employee@hrgenius.com' AND c.name = 'H2 2026';

-- A little kudos traffic for the wall.
INSERT INTO feedback_notes (id, from_emp_id, to_emp_id, kind, visibility, message, created_at, created_by, deleted)
    SELECT feedback_note_seq.NEXTVAL, f.id, t.id, 'PRAISE', 'PUBLIC',
           'Thanks for jumping on the payroll issue last week — saved us a late night!', CURRENT_TIMESTAMP - INTERVAL '3' DAY, 'system', 0
    FROM employees f, employees t WHERE f.work_email = 'manager@hrgenius.com' AND t.work_email = 'employee@hrgenius.com';
INSERT INTO feedback_notes (id, from_emp_id, to_emp_id, kind, visibility, message, created_at, created_by, deleted)
    SELECT feedback_note_seq.NEXTVAL, f.id, t.id, 'PRAISE', 'PUBLIC',
           'Onboarding for the new joiners was seamless. Great checklist!', CURRENT_TIMESTAMP - INTERVAL '1' DAY, 'system', 0
    FROM employees f, employees t WHERE f.work_email = 'employee@hrgenius.com' AND t.work_email = 'hr@hrgenius.com';
