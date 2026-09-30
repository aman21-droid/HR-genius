# HRGenius — Human Resource Management System

A production-quality HRMS covering employee data, recruitment & onboarding, attendance &
leave, payroll, performance, HR analytics, and compliance. Built with **Java 21 / Spring Boot 3**,
**Oracle**, and **Angular 18**.

> **Status:** Phase 1 (Foundation) complete — project scaffolding, Docker Compose, Oracle +
> Flyway, JWT auth with refresh tokens & RBAC, and the Angular shell (layout, dark mode,
> routing, HTTP interceptors, login). Later phases add the feature modules.

---

## Tech stack

| Layer     | Technology |
|-----------|------------|
| Backend   | Java 21, Spring Boot 3.3, Spring Data JPA/Hibernate, Spring Security 6, Flyway, MapStruct, Lombok, springdoc-openapi, Caffeine |
| Database  | Oracle Free 23 (`gvenzl/oracle-free`), sequences for IDs |
| Frontend  | Angular 18 (standalone, signals, lazy routes), Angular Material, SCSS |
| DevOps    | Docker Compose (Oracle, backend, frontend/nginx, MailHog) |
| Auth      | JWT access + refresh tokens, BCrypt, account lockout, fine-grained RBAC |

---

## One-command setup (Docker)

Prerequisites: Docker Desktop.

```bash
cp .env.example .env      # optional: adjust secrets
docker compose up --build
```

This builds and starts everything. First run pulls the Oracle image and initializes the DB
(can take a few minutes — the backend waits for Oracle's healthcheck).

| Service        | URL |
|----------------|-----|
| Frontend (app) | http://localhost:4200 |
| Backend API    | http://localhost:8080/api/v1 |
| Swagger UI     | http://localhost:8080/swagger-ui.html |
| MailHog UI     | http://localhost:8025 |
| Oracle         | localhost:1521 / service `FREEPDB1` |

### Demo logins (password: `Password@123`)

| Email                    | Role          |
|--------------------------|---------------|
| admin@hrgenius.com       | SUPER_ADMIN   |
| hr@hrgenius.com          | HR_ADMIN      |
| payroll@hrgenius.com     | PAYROLL_ADMIN |
| recruiter@hrgenius.com   | RECRUITER     |
| manager@hrgenius.com     | MANAGER       |
| employee@hrgenius.com    | EMPLOYEE      |

---

## Local development (without full Docker)

Run only Oracle + MailHog in Docker, and the apps on your host:

```bash
docker compose up oracle mailhog
```

**Backend** (needs JDK 21+; Maven is downloaded automatically by the wrapper):
```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

**Frontend** (needs Node 20+):
```bash
cd frontend
npm install
npm start           # proxies /api to localhost:8080 (see proxy.conf.json)
```
App at http://localhost:4200.

---

## Project structure

```
hcl/
├── docker-compose.yml      # Oracle, backend, frontend, MailHog
├── .env.example
├── backend/                # Spring Boot (package-by-feature)
│   └── src/main/java/com/hrgenius/
│       ├── common/         # config, security, error handling, base entity, dtos
│       └── auth/           # login, refresh, RBAC (controller/service/repository)
│   └── src/main/resources/db/migration/   # Flyway V1 schema, V2 seed
└── frontend/               # Angular 18 standalone
    └── src/app/
        ├── core/           # auth/theme services, interceptors, guards, models
        ├── layout/         # app shell (sidebar, topbar, theme toggle)
        └── features/       # auth (login), dashboard, ...
```

---

## Architecture highlights

- **Package-by-feature** backend; layered controller → service → repository; DTOs at the API boundary.
- **REST API** versioned under `/api/v1`; standard error JSON via a global exception handler.
- **Auditing**: `createdBy/createdAt/updatedBy/updatedAt` on every entity (JPA Auditing) plus a
  dedicated `audit_log` table for sensitive changes.
- **Soft delete** flag on base entity for employees & master data.
- **Fine-grained RBAC**: roles + permission codes; `@PreAuthorize` on endpoints; role-driven
  navigation on the frontend.
- **Sensitive fields** (PAN, Aadhaar/SSN, bank account) encrypted at rest via a JPA
  `AttributeConverter` (AES-GCM) and masked in the UI.
- **Security**: stateless JWT, BCrypt passwords, refresh-token rotation & revocation, account
  lockout after 5 failed attempts.

---

## Build & test notes

- Backend tests: `cd backend && ./mvnw test` (Windows: `mvnw.cmd test`). The Maven wrapper
  pins Maven 3.9.9, so no global Maven install is needed. Unit tests (e.g. `AuthServiceTest`)
  use Mockito and need no database; integration tests use Testcontainers (Oracle) and need Docker.
- The Docker image build (`docker compose build backend`) compiles the backend inside a
  Maven container.
- Frontend production build: `cd frontend && npm install && npm run build`.

---

## Roadmap (phases)

1. ✅ **Foundation** — scaffolding, Docker, Oracle/Flyway, JWT/RBAC, Angular shell, login.
2. Core HR — org masters, employee CRUD, profile tabs, directory, org chart, documents, import/export, audit log.
3. Attendance & Leave + generic approval engine + notifications.
4. Recruitment & Onboarding/Offboarding.
5. Payroll — structures, run wizard, payslips, statutory calc, tax declarations, expenses.
6. Performance — goals, reviews, 360°, 9-box, kudos.
7. Analytics dashboards & report builder, compliance, helpdesk, announcements, command palette.
8. Polish — tests, seed data, docs, UI consistency pass.
