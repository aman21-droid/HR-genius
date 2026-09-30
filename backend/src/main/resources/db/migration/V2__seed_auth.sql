-- ============================================================================
-- V2: Seed roles, permissions, and demo users (Phase 1)
-- All demo users share password: Password@123
-- BCrypt hash below is verified against that password.
-- ============================================================================

-- ---- Permissions -----------------------------------------------------------
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'EMPLOYEE_READ',       'View employee data');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'EMPLOYEE_WRITE',      'Create/update employees');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'ORG_MANAGE',          'Manage org masters');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'LEAVE_APPROVE',       'Approve leave/attendance requests');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'PAYROLL_RUN',         'Run and publish payroll');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'RECRUITMENT_MANAGE',  'Manage recruitment (ATS)');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'PERFORMANCE_MANAGE',  'Manage performance reviews/goals');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'ANALYTICS_VIEW',      'View analytics dashboards');
INSERT INTO permissions (id, code, description) VALUES (permission_seq.NEXTVAL, 'ADMIN_MANAGE_USERS',  'Manage users and roles');

-- ---- Roles -----------------------------------------------------------------
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'SUPER_ADMIN',   'Super Admin');
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'HR_ADMIN',      'HR Admin');
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'HR_MANAGER',    'HR Manager');
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'PAYROLL_ADMIN', 'Payroll Admin');
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'RECRUITER',     'Recruiter');
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'MANAGER',       'Manager');
INSERT INTO roles (id, code, name) VALUES (role_seq.NEXTVAL, 'EMPLOYEE',      'Employee');

-- ---- Role -> Permission grants --------------------------------------------
-- SUPER_ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- HR_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_READ','EMPLOYEE_WRITE','ORG_MANAGE','LEAVE_APPROVE',
                      'RECRUITMENT_MANAGE','PERFORMANCE_MANAGE','ANALYTICS_VIEW','ADMIN_MANAGE_USERS')
    WHERE r.code = 'HR_ADMIN';

-- HR_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_READ','EMPLOYEE_WRITE','LEAVE_APPROVE','PERFORMANCE_MANAGE','ANALYTICS_VIEW')
    WHERE r.code = 'HR_MANAGER';

-- PAYROLL_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_READ','PAYROLL_RUN','ANALYTICS_VIEW')
    WHERE r.code = 'PAYROLL_ADMIN';

-- RECRUITER
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_READ','RECRUITMENT_MANAGE')
    WHERE r.code = 'RECRUITER';

-- MANAGER
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_READ','LEAVE_APPROVE','PERFORMANCE_MANAGE')
    WHERE r.code = 'MANAGER';

-- EMPLOYEE
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
        ON p.code IN ('EMPLOYEE_READ')
    WHERE r.code = 'EMPLOYEE';

-- ---- Demo users (password: Password@123) -----------------------------------
INSERT INTO users (id, email, password_hash, full_name, status, failed_attempts, created_at, created_by, deleted)
    VALUES (user_seq.NEXTVAL, 'admin@hrgenius.com',     '$2b$10$bGEBa6DAPUE5noHb4of4a.gvONHGAs4pyW9v2gPiiDHrlfGvOLDwm', 'System Administrator', 'ACTIVE', 0, SYSTIMESTAMP, 'system', 0);
INSERT INTO users (id, email, password_hash, full_name, status, failed_attempts, created_at, created_by, deleted)
    VALUES (user_seq.NEXTVAL, 'hr@hrgenius.com',        '$2b$10$bGEBa6DAPUE5noHb4of4a.gvONHGAs4pyW9v2gPiiDHrlfGvOLDwm', 'Hana Reddy (HR Admin)', 'ACTIVE', 0, SYSTIMESTAMP, 'system', 0);
INSERT INTO users (id, email, password_hash, full_name, status, failed_attempts, created_at, created_by, deleted)
    VALUES (user_seq.NEXTVAL, 'payroll@hrgenius.com',   '$2b$10$bGEBa6DAPUE5noHb4of4a.gvONHGAs4pyW9v2gPiiDHrlfGvOLDwm', 'Pat Owens (Payroll)', 'ACTIVE', 0, SYSTIMESTAMP, 'system', 0);
INSERT INTO users (id, email, password_hash, full_name, status, failed_attempts, created_at, created_by, deleted)
    VALUES (user_seq.NEXTVAL, 'recruiter@hrgenius.com', '$2b$10$bGEBa6DAPUE5noHb4of4a.gvONHGAs4pyW9v2gPiiDHrlfGvOLDwm', 'Riya Chen (Recruiter)', 'ACTIVE', 0, SYSTIMESTAMP, 'system', 0);
INSERT INTO users (id, email, password_hash, full_name, status, failed_attempts, created_at, created_by, deleted)
    VALUES (user_seq.NEXTVAL, 'manager@hrgenius.com',   '$2b$10$bGEBa6DAPUE5noHb4of4a.gvONHGAs4pyW9v2gPiiDHrlfGvOLDwm', 'Manuel Garcia (Manager)', 'ACTIVE', 0, SYSTIMESTAMP, 'system', 0);
INSERT INTO users (id, email, password_hash, full_name, status, failed_attempts, created_at, created_by, deleted)
    VALUES (user_seq.NEXTVAL, 'employee@hrgenius.com',  '$2b$10$bGEBa6DAPUE5noHb4of4a.gvONHGAs4pyW9v2gPiiDHrlfGvOLDwm', 'Emma Lopez (Employee)', 'ACTIVE', 0, SYSTIMESTAMP, 'system', 0);

-- ---- User -> Role assignments ---------------------------------------------
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u JOIN roles r ON r.code='SUPER_ADMIN'   WHERE u.email='admin@hrgenius.com';
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u JOIN roles r ON r.code='HR_ADMIN'      WHERE u.email='hr@hrgenius.com';
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u JOIN roles r ON r.code='PAYROLL_ADMIN' WHERE u.email='payroll@hrgenius.com';
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u JOIN roles r ON r.code='RECRUITER'     WHERE u.email='recruiter@hrgenius.com';
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u JOIN roles r ON r.code='MANAGER'       WHERE u.email='manager@hrgenius.com';
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u JOIN roles r ON r.code='EMPLOYEE'      WHERE u.email='employee@hrgenius.com';
