-- ============================================================================
-- V10: Phase 4 demo data — default onboarding checklist, open requisitions,
-- a candidate pool spread across pipeline stages, and interviews (one upcoming
-- with feedback owed by manager@ and employee@, one completed).
-- Seeded OPEN requisitions are treated as already approved (no approval flow row).
-- ============================================================================

-- ---- Default onboarding template -------------------------------------------
INSERT INTO onboarding_templates (id, name, description, is_default, active, created_at, created_by, deleted)
VALUES (onboarding_template_seq.NEXTVAL, 'Standard onboarding', 'Default checklist for every new joiner', 1, 1, SYSTIMESTAMP, 'system', 0);

INSERT INTO onboarding_template_tasks (id, template_id, title, description, owner_role, due_offset_days, sort_order, created_at, created_by, deleted)
    SELECT onboarding_template_task_seq.NEXTVAL, t.id, v.title, v.descr, v.owner_role, v.offset_days, v.sort_no, SYSTIMESTAMP, 'system', 0
    FROM onboarding_templates t,
         (SELECT 'Send welcome email and day-one agenda' AS title, 'Share reporting time, address and first-week plan' AS descr, 'HR' AS owner_role, -3 AS offset_days, 10 AS sort_no FROM dual UNION ALL
          SELECT 'Collect joining documents', 'ID proof, address proof, education and previous employment letters', 'HR', 0, 20 FROM dual UNION ALL
          SELECT 'Provision laptop and accessories', 'Assign from the asset register', 'IT', -2, 30 FROM dual UNION ALL
          SELECT 'Create email, SSO and tool accounts', 'Email, chat, VPN and team tools', 'IT', -1, 40 FROM dual UNION ALL
          SELECT 'Issue ID card and access badge', NULL, 'ADMIN', 0, 50 FROM dual UNION ALL
          SELECT 'Add to payroll and collect bank details', 'Bank account, PAN and tax declaration', 'FINANCE', 3, 60 FROM dual UNION ALL
          SELECT 'Complete your profile and emergency contacts', 'Update personal details in HRGenius', 'EMPLOYEE', 1, 70 FROM dual UNION ALL
          SELECT 'Read and acknowledge company policies', 'Code of conduct, leave and IT security policies', 'EMPLOYEE', 5, 80 FROM dual UNION ALL
          SELECT 'Set up a 30-60-90 day plan', 'Agree goals and a buddy with the new hire', 'MANAGER', 7, 90 FROM dual UNION ALL
          SELECT 'First-week check-in', 'Quick 1:1 on how the first week went', 'MANAGER', 5, 100 FROM dual UNION ALL
          SELECT '30-day HR check-in', NULL, 'HR', 30, 110 FROM dual) v
    WHERE t.name = 'Standard onboarding';

-- ---- Requisitions ----------------------------------------------------------
INSERT INTO job_requisitions (id, req_code, title, department_id, designation_id, location_id, grade_id, hiring_manager_id,
        employment_type, openings, filled, min_experience, max_experience, salary_min, salary_max, skills, description,
        target_date, publish_on_careers, status, opened_at, created_at, created_by, deleted)
    SELECT requisition_seq.NEXTVAL, 'REQ-' || LPAD(TO_CHAR(requisition_code_seq.NEXTVAL), 4, '0'),
           'Senior Software Engineer (Backend)', d.id, ds.id, l.id, g.id, m.id, 'FULL_TIME', 2, 0, 4, 8, 2400000, 3600000,
           'Java, Spring Boot, SQL, REST, AWS',
           'Build and scale the services behind HRGenius. You will own APIs end to end, mentor engineers and shape our architecture.',
           DATE '2026-12-15', 1, 'OPEN', SYSTIMESTAMP, SYSTIMESTAMP, 'system', 0
    FROM departments d, designations ds, locations l, grades g, employees m
    WHERE d.code = 'ENG' AND ds.code = 'SSE' AND l.code = 'BLR' AND g.code = 'G3' AND m.work_email = 'manager@hrgenius.com';

INSERT INTO job_requisitions (id, req_code, title, department_id, designation_id, location_id, grade_id, hiring_manager_id,
        employment_type, openings, filled, min_experience, max_experience, salary_min, salary_max, skills, description,
        target_date, publish_on_careers, status, opened_at, created_at, created_by, deleted)
    SELECT requisition_seq.NEXTVAL, 'REQ-' || LPAD(TO_CHAR(requisition_code_seq.NEXTVAL), 4, '0'),
           'Product Manager', d.id, ds.id, l.id, g.id, m.id, 'FULL_TIME', 1, 0, 3, 7, 2200000, 3200000,
           'Roadmapping, discovery, analytics, stakeholder management',
           'Own the roadmap for our employee self-service experience, working closely with design and engineering.',
           DATE '2026-11-30', 1, 'OPEN', SYSTIMESTAMP, SYSTIMESTAMP, 'system', 0
    FROM departments d, designations ds, locations l, grades g, employees m
    WHERE d.code = 'PRD' AND ds.code = 'PM' AND l.code = 'BLR' AND g.code = 'G3' AND m.work_email = 'manager@hrgenius.com';

INSERT INTO job_requisitions (id, req_code, title, department_id, designation_id, location_id, grade_id, hiring_manager_id,
        employment_type, openings, filled, min_experience, max_experience, salary_min, salary_max, skills, description,
        target_date, publish_on_careers, status, opened_at, created_at, created_by, deleted)
    SELECT requisition_seq.NEXTVAL, 'REQ-' || LPAD(TO_CHAR(requisition_code_seq.NEXTVAL), 4, '0'),
           'Account Executive', d.id, ds.id, l.id, g.id, m.id, 'FULL_TIME', 3, 0, 2, 5, 1200000, 1800000,
           'B2B SaaS sales, pipeline management, CRM',
           'Grow our mid-market customer base across West India.',
           DATE '2026-12-31', 1, 'OPEN', SYSTIMESTAMP, SYSTIMESTAMP, 'system', 0
    FROM departments d, designations ds, locations l, grades g, employees m
    WHERE d.code = 'SAL' AND ds.code = 'AE' AND l.code = 'MUM' AND g.code = 'G2' AND m.work_email = 'neha.kapoor@hrgenius.com';

INSERT INTO job_requisitions (id, req_code, title, department_id, designation_id, location_id, grade_id, hiring_manager_id,
        employment_type, openings, filled, min_experience, max_experience, salary_min, salary_max, skills, description,
        target_date, publish_on_careers, status, created_at, created_by, deleted)
    SELECT requisition_seq.NEXTVAL, 'REQ-' || LPAD(TO_CHAR(requisition_code_seq.NEXTVAL), 4, '0'),
           'DevOps Engineer', d.id, ds.id, l.id, g.id, m.id, 'FULL_TIME', 1, 0, 2, 6, 1800000, 2800000,
           'Kubernetes, Terraform, CI/CD, observability',
           'Draft: platform team expansion, pending budget confirmation.',
           DATE '2027-01-31', 0, 'DRAFT', SYSTIMESTAMP, 'recruiter@hrgenius.com', 0
    FROM departments d, designations ds, locations l, grades g, employees m
    WHERE d.code = 'ENG' AND ds.code = 'DEVOPS' AND l.code = 'PUN' AND g.code = 'G2' AND m.work_email = 'manager@hrgenius.com';

-- ---- Candidates ------------------------------------------------------------
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, linkedin_url, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Arjun', 'Mehta', 'arjun.mehta@example.com', '+91 9810011001', 'Flipkart', 'Software Engineer II', 5.5, 2100000, 2900000, 60, 'Bengaluru', 'https://www.linkedin.com/in/arjun-mehta-demo', 'LINKEDIN', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Sneha', 'Rao', 'sneha.rao@example.com', '+91 9810011002', 'Infosys', 'Senior Engineer', 6.0, 1900000, 2700000, 90, 'Hyderabad', 'CAREERS_PAGE', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Kabir', 'Singh', 'kabir.singh@example.com', '+91 9810011003', 'Zoho', 'Member Technical Staff', 4.0, 1600000, 2400000, 30, 'Chennai', 'REFERRAL', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Meera', 'Pillai', 'meera.pillai@example.com', '+91 9810011004', 'Swiggy', 'Backend Engineer', 7.0, 2600000, 3300000, 60, 'Bengaluru', 'AGENCY', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Rohan', 'Das', 'rohan.das@example.com', '+91 9810011005', 'TCS', 'Engineer', 3.0, 900000, 1600000, 90, 'Kolkata', 'CAREERS_PAGE', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Isha', 'Kulkarni', 'isha.kulkarni@example.com', '+91 9810011006', 'Freshworks', 'Associate PM', 4.5, 2000000, 2800000, 60, 'Pune', 'LINKEDIN', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Dev', 'Malhotra', 'dev.malhotra@example.com', '+91 9810011007', 'Paytm', 'Product Analyst', 3.5, 1500000, 2300000, 45, 'Noida', 'DIRECT', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Tanya', 'Bose', 'tanya.bose@example.com', '+91 9810011008', 'HubSpot', 'Sales Development Rep', 2.5, 900000, 1300000, 30, 'Mumbai', 'CAREERS_PAGE', SYSTIMESTAMP, 'system', 0);
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_title, total_experience, current_ctc, expected_ctc, notice_period_days, city, source, created_at, created_by, deleted)
VALUES (candidate_seq.NEXTVAL, 'Farhan', 'Ali', 'farhan.ali@example.com', '+91 9810011009', 'Salesforce', 'Account Executive', 4.0, 1400000, 1800000, 60, 'Mumbai', 'REFERRAL', SYSTIMESTAMP, 'system', 0);

-- ---- Applications (candidate email, requisition title, stage) --------------
INSERT INTO job_applications (id, requisition_id, candidate_id, stage, stage_changed_at, source, created_at, created_by, deleted)
    SELECT application_seq.NEXTVAL, r.id, c.id, v.stage, SYSTIMESTAMP, c.source, SYSTIMESTAMP, 'system', 0
    FROM job_requisitions r, candidates c,
         (SELECT 'arjun.mehta@example.com' AS email, 'Senior Software Engineer (Backend)' AS title, 'INTERVIEW' AS stage FROM dual UNION ALL
          SELECT 'sneha.rao@example.com',     'Senior Software Engineer (Backend)', 'SCREENING' FROM dual UNION ALL
          SELECT 'kabir.singh@example.com',   'Senior Software Engineer (Backend)', 'APPLIED'   FROM dual UNION ALL
          SELECT 'meera.pillai@example.com',  'Senior Software Engineer (Backend)', 'OFFER'     FROM dual UNION ALL
          SELECT 'rohan.das@example.com',     'Senior Software Engineer (Backend)', 'REJECTED'  FROM dual UNION ALL
          SELECT 'isha.kulkarni@example.com', 'Product Manager',                    'INTERVIEW' FROM dual UNION ALL
          SELECT 'dev.malhotra@example.com',  'Product Manager',                    'APPLIED'   FROM dual UNION ALL
          SELECT 'tanya.bose@example.com',    'Account Executive',                  'SCREENING' FROM dual UNION ALL
          SELECT 'farhan.ali@example.com',    'Account Executive',                  'APPLIED'   FROM dual) v
    WHERE c.email = v.email AND r.title = v.title;

UPDATE job_applications SET rejection_reason = 'Experience below the bar for a senior role'
    WHERE stage = 'REJECTED';

-- Activity trail: an "applied" event for every application, plus the move to its current stage.
INSERT INTO application_events (id, application_id, event_type, from_stage, to_stage, message, actor_name, created_at, created_by, deleted)
    SELECT application_event_seq.NEXTVAL, a.id, 'STAGE_CHANGED', NULL, 'APPLIED', 'Application received', 'System', SYSTIMESTAMP, 'system', 0
    FROM job_applications a;
INSERT INTO application_events (id, application_id, event_type, from_stage, to_stage, message, actor_name, created_at, created_by, deleted)
    SELECT application_event_seq.NEXTVAL, a.id, 'STAGE_CHANGED', 'APPLIED', a.stage, 'Moved to ' || LOWER(a.stage), 'Riya Chen', SYSTIMESTAMP, 'system', 0
    FROM job_applications a WHERE a.stage <> 'APPLIED';

-- ---- Interviews ------------------------------------------------------------
-- Arjun: upcoming technical round, panel = manager@ + employee@ (feedback owed).
INSERT INTO interviews (id, application_id, round_name, interview_mode, scheduled_at, duration_minutes, location_or_link, status, created_at, created_by, deleted)
    SELECT interview_seq.NEXTVAL, a.id, 'Technical round 1', 'VIDEO', CURRENT_TIMESTAMP + INTERVAL '2' DAY, 60,
           'https://meet.example.com/hrg-tech-1', 'SCHEDULED', SYSTIMESTAMP, 'system', 0
    FROM job_applications a JOIN candidates c ON c.id = a.candidate_id
    WHERE c.email = 'arjun.mehta@example.com';
INSERT INTO interview_feedback (id, interview_id, interviewer_emp_id, created_at, created_by, deleted)
    SELECT interview_feedback_seq.NEXTVAL, i.id, e.id, SYSTIMESTAMP, 'system', 0
    FROM interviews i JOIN job_applications a ON a.id = i.application_id
         JOIN candidates c ON c.id = a.candidate_id, employees e
    WHERE c.email = 'arjun.mehta@example.com' AND i.round_name = 'Technical round 1'
      AND e.work_email IN ('manager@hrgenius.com', 'employee@hrgenius.com');

-- Meera (now at OFFER): completed round with submitted feedback.
INSERT INTO interviews (id, application_id, round_name, interview_mode, scheduled_at, duration_minutes, location_or_link, status, created_at, created_by, deleted)
    SELECT interview_seq.NEXTVAL, a.id, 'Technical round 1', 'IN_PERSON', CURRENT_TIMESTAMP - INTERVAL '5' DAY, 60,
           'Bengaluru HQ, Room Nilgiri', 'COMPLETED', SYSTIMESTAMP, 'system', 0
    FROM job_applications a JOIN candidates c ON c.id = a.candidate_id
    WHERE c.email = 'meera.pillai@example.com';
INSERT INTO interview_feedback (id, interview_id, interviewer_emp_id, rating, recommendation, strengths, concerns, submitted_at, created_at, created_by, deleted)
    SELECT interview_feedback_seq.NEXTVAL, i.id, e.id, 5, 'STRONG_HIRE',
           'Deep distributed-systems knowledge; clear communicator; strong ownership.',
           'Limited frontend exposure (not needed for this role).',
           CURRENT_TIMESTAMP - INTERVAL '5' DAY, SYSTIMESTAMP, 'system', 0
    FROM interviews i JOIN job_applications a ON a.id = i.application_id
         JOIN candidates c ON c.id = a.candidate_id, employees e
    WHERE c.email = 'meera.pillai@example.com' AND e.work_email = 'manager@hrgenius.com';

-- Isha (PM): upcoming product-sense round with manager@.
INSERT INTO interviews (id, application_id, round_name, interview_mode, scheduled_at, duration_minutes, location_or_link, status, created_at, created_by, deleted)
    SELECT interview_seq.NEXTVAL, a.id, 'Product sense', 'VIDEO', CURRENT_TIMESTAMP + INTERVAL '3' DAY, 45,
           'https://meet.example.com/hrg-pm-1', 'SCHEDULED', SYSTIMESTAMP, 'system', 0
    FROM job_applications a JOIN candidates c ON c.id = a.candidate_id
    WHERE c.email = 'isha.kulkarni@example.com';
INSERT INTO interview_feedback (id, interview_id, interviewer_emp_id, created_at, created_by, deleted)
    SELECT interview_feedback_seq.NEXTVAL, i.id, e.id, SYSTIMESTAMP, 'system', 0
    FROM interviews i JOIN job_applications a ON a.id = i.application_id
         JOIN candidates c ON c.id = a.candidate_id, employees e
    WHERE c.email = 'isha.kulkarni@example.com' AND e.work_email = 'manager@hrgenius.com';

-- Interview events on the activity trail.
INSERT INTO application_events (id, application_id, event_type, message, actor_name, created_at, created_by, deleted)
    SELECT application_event_seq.NEXTVAL, i.application_id, 'INTERVIEW_SCHEDULED', i.round_name || ' scheduled', 'Riya Chen', SYSTIMESTAMP, 'system', 0
    FROM interviews i;
