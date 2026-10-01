"""Build the printable SRS PDF from the interactive SRS page.

The requirement, role, probe and lifecycle data are read from HRGenius_SRS.html so the
printed copy can never drift from the live one. Usage:

    python build_srs_pdf.py [output.pdf]

Writes srs_print.html next to this script and prints it to PDF with headless Edge.
"""
from pathlib import Path
import subprocess, sys

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "HRGenius_SRS.html"
PRINT_HTML = ROOT / "srs_print.html"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "Downloads" / "HRGenius_SRS_Aman_Arora.pdf"
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")

START, END = "/* ---------- data ---------- */", "/* ---------- section 0 figures ---------- */"

TEMPLATE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>HRGenius SRS - Aman Arora</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400;12..96,600;12..96,800&family=Source+Serif+4:ital,opsz,wght@0,8..60,400;0,8..60,600;1,8..60,400&family=IBM+Plex+Mono:wght@400;600&display=swap">
<script src="https://cdn.jsdelivr.net/npm/mermaid@11.6.0/dist/mermaid.min.js"></script>
<style>
@page { size: A4; margin: 17mm 15mm 15mm 15mm;
  @top-left { content: "HRGenius · Software Requirements Specification"; font: 600 7.5pt "Bricolage Grotesque", sans-serif; color: #586661; }
  @top-right { content: "HRG-SRS-001 · v1.0"; font: 7.5pt "IBM Plex Mono", monospace; color: #586661; }
  @bottom-left { content: "Aman Arora · Roll No. 2400320100152"; font: 7.5pt "Bricolage Grotesque", sans-serif; color: #586661; }
  @bottom-right { content: "Page " counter(page) " of " counter(pages); font: 7.5pt "IBM Plex Mono", monospace; color: #586661; }
}
@page :first { margin: 16mm; @top-left { content: none; } @top-right { content: none; } @bottom-left { content: none; } @bottom-right { content: none; } }
:root { --ink: #15201e; --muted: #586661; --line: #c9d3cf; --sunk: #edf1ee; --accent: #0e6b63; --soft: #dbeee9; --stamp: #9a610f; --ok: #2c7a39; --bad: #b23a3a; }
* { box-sizing: border-box; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { margin: 0; color: var(--ink); font: 400 9.4pt/1.42 "Source Serif 4", Georgia, serif; }
h1, h2, h3, h4 { font-family: "Bricolage Grotesque", "Segoe UI", sans-serif; line-height: 1.2; margin: 0; break-after: avoid; }
h2 { font-size: 13.5pt; font-weight: 800; border-bottom: 1.4pt solid var(--ink); padding-bottom: 3pt; margin: 14pt 0 6pt; display: flex; gap: 8pt; align-items: baseline; }
h2 .n { font: 600 10.5pt "IBM Plex Mono", monospace; color: var(--accent); }
h3 { font-size: 10.4pt; font-weight: 600; margin: 8pt 0 3pt; }
h4 { font-size: 9.4pt; font-weight: 600; margin: 6pt 0 2pt; color: var(--accent); }
p { margin: 3pt 0; orphans: 3; widows: 3; }
ul { margin: 3pt 0; padding-left: 13pt; } li { margin: 1pt 0; }
code, .mono { font-family: "IBM Plex Mono", monospace; font-size: .86em; }
.muted { color: var(--muted); } .small { font-size: 8.8pt; }
.shall { font-family: "Bricolage Grotesque", sans-serif; font-weight: 800; font-size: .82em; letter-spacing: .05em; color: var(--accent); }
.label { font: 600 6.8pt/1.3 "Bricolage Grotesque", sans-serif; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); }
.pb { break-before: page; }
table { border-collapse: collapse; width: 100%; font-size: 7.9pt; line-height: 1.32; margin: 4pt 0; }
th, td { border: .5pt solid var(--line); padding: 2.4pt 4.5pt; text-align: left; vertical-align: top; }
th { background: var(--sunk); font: 600 6.6pt/1.3 "Bricolage Grotesque", sans-serif; letter-spacing: .07em; text-transform: uppercase; color: var(--muted); }
thead { display: table-header-group; }
tr { break-inside: avoid; }
.diagram { border: .5pt solid var(--line); border-radius: 3pt; padding: 5pt; margin: 4pt 0 6pt; text-align: center; break-inside: avoid; }
.diagram svg { max-width: 100% !important; max-height: 78mm; width: auto; height: auto; }
.diagram.tall svg { max-height: 118mm; }
.cap { font-size: 7.6pt; color: var(--muted); text-align: center; margin-top: 2pt; }
.cols2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 8mm; }
.rq td:first-child { font: 600 7.2pt "IBM Plex Mono", monospace; color: var(--accent); white-space: nowrap; }
.rq td.c { text-align: center; white-space: nowrap; }
.rq .ti { font-family: "Bricolage Grotesque", sans-serif; font-weight: 600; }
.rq tr.mod td { background: var(--soft); font: 600 7pt "Bricolage Grotesque", sans-serif; letter-spacing: .07em; text-transform: uppercase; color: var(--accent); }
.path { font: 7.2pt "IBM Plex Mono", monospace; }

/* title page */
.title { height: 265mm; border: 2.4pt double var(--ink); padding: 18mm 14mm; display: flex; flex-direction: column; text-align: center; }
.title .inst { font: 800 20pt/1.15 "Bricolage Grotesque", sans-serif; letter-spacing: .04em; text-transform: uppercase; }
.title .course { font: italic 400 11.5pt "Source Serif 4", serif; color: var(--muted); margin-top: 6pt; }
.title .rule { width: 40mm; height: 2pt; background: var(--accent); margin: 16mm auto 12mm; }
.title .kind { font: 600 9pt "Bricolage Grotesque", sans-serif; letter-spacing: .2em; text-transform: uppercase; color: var(--accent); }
.title h1 { font-size: 34pt; font-weight: 800; letter-spacing: -.02em; margin-top: 8pt; }
.title .subt { font: 400 15pt "Source Serif 4", serif; margin-top: 4pt; }
.title .ful { font: italic 400 10.5pt "Source Serif 4", serif; color: var(--muted); margin: 12mm auto 0; max-width: 120mm; }
.title .people { margin-top: auto; display: grid; grid-template-columns: 1fr 1fr; gap: 10mm; text-align: left; }
.title .people b { display: block; font: 600 13pt "Bricolage Grotesque", sans-serif; margin-top: 3pt; }
.title .people span { font: 9pt "IBM Plex Mono", monospace; color: var(--muted); }
.title .when { margin-top: 12mm; padding-top: 6mm; border-top: .8pt solid var(--line); display: flex; justify-content: space-between; font: 9.5pt "Bricolage Grotesque", sans-serif; }

/* certificate / declaration */
.formal { padding-top: 4mm; }
.formal + .formal { margin-top: 14mm; padding-top: 10mm; border-top: .6pt solid var(--line); }
.formal h2 { justify-content: center; border: 0; font-size: 16pt; letter-spacing: .14em; text-transform: uppercase; margin-top: 0; }
.formal p { font-size: 10.6pt; line-height: 1.7; text-align: justify; margin: 6pt 0; }
.sigs { display: grid; grid-template-columns: 1fr 1fr; gap: 16mm; margin-top: 18mm; }
.sig { border-top: .9pt solid var(--ink); padding-top: 4pt; font-size: 9.4pt; }
.sig b { display: block; font-family: "Bricolage Grotesque", sans-serif; }
.meta-lines { margin-top: 7mm; display: flex; gap: 30mm; font-size: 9.6pt; }

/* requirements */
.req { border: .6pt solid var(--line); border-left: 2.4pt solid var(--accent); border-radius: 3pt; padding: 5pt 8pt; margin: 5pt 0; break-inside: avoid; }
.req.should { border-left-color: var(--stamp); }
.req .hd { display: flex; gap: 8pt; align-items: baseline; }
.req .id { font: 600 8.4pt "IBM Plex Mono", monospace; color: var(--accent); white-space: nowrap; }
.req .t { font: 600 10pt "Bricolage Grotesque", sans-serif; flex: 1; }
.tag { font: 600 6.8pt/1 "Bricolage Grotesque", sans-serif; padding: 2.5pt 5pt; border-radius: 2pt; background: var(--sunk); color: var(--muted); white-space: nowrap; }
.tag.must { background: var(--soft); color: var(--accent); }
.tag.test { color: var(--ok); }
.req .st { margin: 3pt 0; }
.req .kv { display: grid; grid-template-columns: 17mm 1fr; gap: 1pt 6pt; font-size: 8.6pt; }
.req .kv b { font: 600 6.8pt/1.7 "Bricolage Grotesque", sans-serif; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }

.y { color: var(--ok); font-weight: 600; } .n0 { color: var(--bad); font-weight: 600; }
.matrix td, .matrix th { text-align: center; } .matrix td:first-child, .matrix th:first-child { text-align: left; }
.grp td { background: var(--sunk); font: 600 7pt "Bricolage Grotesque", sans-serif; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
.ok { color: var(--ok); font-weight: 600; } .part { color: var(--stamp); font-weight: 600; }
.ctrl td:first-child { width: 34mm; font: 600 7pt "Bricolage Grotesque", sans-serif; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); background: var(--sunk); }
.toc { list-style: none; padding: 0; columns: 2; column-gap: 12mm; }
.toc li { padding: 3pt 0; border-bottom: .5pt dotted var(--line); display: flex; gap: 8pt; break-inside: avoid; }
.toc b { font: 600 9pt "IBM Plex Mono", monospace; color: var(--accent); width: 8mm; }
.box { display: inline-block; width: 8pt; height: 8pt; border: .8pt solid var(--ink); margin: 0 3pt -1pt 0; }
.signsheet td { height: 11mm; vertical-align: middle; }
.lines div { border-bottom: .6pt solid var(--line); height: 8mm; }
.figs { display: grid; grid-template-columns: repeat(4, 1fr); border: .5pt solid var(--line); margin: 6pt 0; }
.figs div { padding: 4pt 7pt; border-right: .5pt solid var(--line); } .figs div:last-child { border-right: 0; }
.figs b { display: block; font: 800 13pt/1.1 "Bricolage Grotesque", sans-serif; }
.figs span { font-size: 8pt; color: var(--muted); }
</style></head>
<body>

<!-- TITLE -->
<div class="title">
  <div class="inst">ABES Engineering College</div>
  <div class="course">Bachelor of Technology (B.Tech) · Academic Year 2026–2027</div>
  <div class="rule"></div>
  <div class="kind">Software Requirements Specification</div>
  <h1>HRGenius</h1>
  <div class="subt">Human Resource Management System</div>
  <p class="ful">Submitted in partial fulfilment of the requirements for the award of the degree of Bachelor of Technology</p>
  <div class="people">
    <div><div class="label">Submitted by</div><b>Aman Arora</b><span>Roll No. 2400320100152</span></div>
    <div><div class="label">Under the guidance of</div><b>Dr Radhika Singhal</b><span>Project Guide</span></div>
  </div>
  <div class="when"><span>Date of submission: <b>1 October 2026</b></span><span>Document HRG-SRS-001 · Version 1.0</span></div>
</div>

<!-- CERTIFICATE -->
<div class="pb formal">
  <h2>Certificate</h2>
  <p>This is to certify that the Software Requirements Specification titled <b>“HRGenius: Human Resource Management System”</b>, submitted by <b>Aman Arora (Roll No. 2400320100152)</b> to ABES Engineering College in partial fulfilment of the requirements for the degree of Bachelor of Technology, is a record of work carried out under my guidance during the academic year 2026–2027.</p>
  <div class="sigs">
    <div class="sig"><b>Dr Radhika Singhal</b>Project Guide<br>ABES Engineering College</div>
    <div class="sig"><b>Head of Department</b>&nbsp;<br>ABES Engineering College</div>
  </div>
  <div class="meta-lines"><div>Date: ____________________</div><div>Place: ____________________</div></div>
</div>

<!-- DECLARATION -->
<div class="formal">
  <h2>Declaration</h2>
  <p>I, <b>Aman Arora</b>, Roll No. <b>2400320100152</b>, a student of Bachelor of Technology at ABES Engineering College, declare that this specification accurately describes the HRGenius system submitted for evaluation, that the verification evidence it cites reflects the delivered source at the stated baseline (git commit 79f36fb), and that all sources and tools used have been acknowledged as my institution requires.</p>
  <div class="sigs">
    <div class="sig"><b>Aman Arora</b>Roll No. 2400320100152</div>
    <div></div>
  </div>
  <div class="meta-lines"><div>Date: 1 October 2026</div><div>Place: ____________________</div></div>
</div>

<!-- CONTROL + CONTENTS -->
<div class="pb">
  <h2>Document control</h2>
  <table class="ctrl"><tbody>
    <tr><td>Document</td><td>HRG-SRS-001 · Software Requirements Specification, structured after ISO/IEC/IEEE 29148</td></tr>
    <tr><td>Version</td><td>1.0, issued for academic review</td></tr>
    <tr><td>Submitted by</td><td>Aman Arora · Roll No. 2400320100152 · B.Tech · ABES Engineering College</td></tr>
    <tr><td>Guide</td><td>Dr Radhika Singhal</td></tr>
    <tr><td>Academic year</td><td>2026–2027</td></tr>
    <tr><td>Date of submission</td><td>1 October 2026</td></tr>
    <tr><td>Baseline</td><td><span class="mono">git 79f36fb · master</span></td></tr>
  </tbody></table>
  <h3>Contents</h3>
  <ol class="toc">
    <li><b>1</b>Introduction</li><li><b>2</b>Overall description</li><li><b>3</b>Functional requirements</li>
    <li><b>4</b>Access control requirements</li><li><b>5</b>External interfaces and data</li><li><b>6</b>Non-functional requirements</li>
    <li><b>7</b>Verification and traceability</li><li><b>8</b>Academic review</li><li><b>9</b>Review and sign-off</li>
  </ol>
</div>

<!-- 1 -->
<section class="sec">
  <h2><span class="n">1</span>Introduction</h2>
  <h3>1.1 Purpose</h3>
  <p>This document specifies the functional and non-functional requirements of HRGenius, a web-based HR management system covering the employee lifecycle from requisition to payslip. It is written for three readers: the <b>evaluating faculty</b>, who judge whether the system meets its stated objectives; the <b>developer</b>, who treats each <span class="shall">SHALL</span> statement as a commitment; and any <b>future maintainer</b>, who needs to know where each requirement is verified.</p>
  <h3>1.2 Scope</h3>
  <p>HRGenius centralises employee records, organisation structure, leave and attendance, a reusable approval workflow, recruitment and onboarding, payroll with Indian statutory deductions, performance reviews, helpdesk, policy compliance, analytics and employee self-service. Access is role-based and enforced on the server.</p>
  <p><b>Out of scope</b> for this release: single sign-on and multi-factor authentication, biometric attendance devices, bank file integration for salary disbursement, statutory return filing, and a native mobile app.</p>
  <h3>1.3 Definitions and acronyms</h3>
  <div class="cols2"><table><tbody>
    <tr><td class="mono">RBAC</td><td>Role-based access control: roles grant named permissions.</td></tr>
    <tr><td class="mono">JWT</td><td>JSON Web Token: the short-lived signed access credential.</td></tr>
    <tr><td class="mono">CTC</td><td>Cost to company, including employer contributions.</td></tr>
    <tr><td class="mono">PF / ESI</td><td>Provident Fund and Employees' State Insurance.</td></tr>
    <tr><td class="mono">TDS</td><td>Tax deducted at source, old or new regime.</td></tr>
  </tbody></table><table><tbody>
    <tr><td class="mono">LWP</td><td>Leave without pay, deducted as loss of pay.</td></tr>
    <tr><td class="mono">ATS</td><td>Applicant tracking system for recruitment.</td></tr>
    <tr><td class="mono">Requisition</td><td>An approved request to open a position.</td></tr>
    <tr><td class="mono">Regularization</td><td>A correction to a missed attendance punch.</td></tr>
    <tr><td class="mono">SHALL</td><td>Binding requirement; "should" is desirable.</td></tr>
  </tbody></table></div>
  <h3>1.4 References</h3>
  <ul>
    <li>ISO/IEC/IEEE 29148:2018, <i>Systems and software engineering: Life cycle processes, Requirements engineering</i> (successor to IEEE 830-1998).</li>
    <li>HRGenius Detailed Project Report, <code>reports/HRGenius_Project_Report.pdf</code>.</li>
    <li>HRGenius source repository, baseline commit <code>79f36fb</code>; OpenAPI description served at <code>/swagger-ui.html</code>.</li>
  </ul>
</section>

<!-- 2 -->
<section class="sec">
  <h2><span class="n">2</span>Overall description</h2>
  <h3>2.1 Product perspective</h3>
  <p>HRGenius is a standalone, three-tier web application: an Angular single-page client, a Spring Boot REST API, and an Oracle database. Figure 1 shows the actors and external systems it exchanges data with.</p>
  <div class="diagram"><pre class="mermaid">
flowchart LR
  EMP([Employee]) -->|leave, attendance, tickets| HRG
  MGR([Manager]) -->|approvals, team reviews| HRG
  HR([HR Admin]) -->|employees, masters, policies| HRG
  PAY([Payroll Admin]) -->|payroll runs| HRG
  REC([Recruiter]) -->|requisitions, candidates, offers| HRG
  APP([Job applicant]) -->|public applications| HRG
  HRG{{HRGenius}} -->|payslips, offer letters, to-dos| EMP
  HRG -->|notifications over SMTP| MAIL[(Mail server)]
  HRG ---|JDBC, Flyway migrations| DB[(Oracle database)]
  </pre><div class="cap">Figure 1. Context diagram</div></div>
  <h3>2.2 Product functions</h3>
  <div class="cols2" id="mod-tables"></div>
  <h3>2.3 User classes and characteristics</h3>
  <p>Seven roles are seeded and a user may hold more than one. A seventh role, HR Manager, exists for organisations that split HR duties and has no demo login.</p>
  <table id="role-table"><thead><tr><th style="width:30mm">Role</th><th>Characteristics</th><th style="width:52mm">Permissions held</th></tr></thead><tbody></tbody></table>
  <h3>2.4 Operating environment</h3>
  <table><tbody>
    <tr><td><b>Server</b></td><td>Java 21 runtime, Spring Boot 3.3, Spring Security 6; packaged as a container image</td></tr>
    <tr><td><b>Database</b></td><td>Oracle Database (Oracle Free container) with Flyway migrations V1 to V14; H2 in Oracle mode for the offline demo and tests</td></tr>
    <tr><td><b>Client</b></td><td>Angular 18 standalone components with Angular Material 3; current Chrome, Edge, Firefox and Safari, on desktop and phone screens</td></tr>
    <tr><td><b>Deployment</b></td><td>Docker Compose: oracle, backend, frontend (nginx) and a MailHog SMTP catcher for development</td></tr>
  </tbody></table>
  <h3>2.5 Design and implementation constraints</h3>
  <ul>
    <li>The database schema is owned by versioned migrations; the ORM validates against it and never alters it.</li>
    <li>Every list endpoint is paginated, with a hard maximum of 100 rows per page.</li>
    <li>Statutory identifiers (PAN, Aadhaar, bank account) must be stored encrypted and shown masked by default.</li>
    <li>Payroll follows Indian statutory rules (PF, ESI, professional tax, TDS) and the Indian rupee format.</li>
    <li>Uploads are limited to 10 MB per file and 12 MB per request.</li>
  </ul>
  <h3>2.6 Assumptions and dependencies</h3>
  <ul>
    <li>Each login is linked to at most one employee record; self-service features need that link.</li>
    <li>An SMTP server is reachable for notifications; in development MailHog captures all mail.</li>
    <li>Tax rules implemented are the standard deduction under both regimes, plus employee PF under section 80C in the old regime; other exemptions are assumed to be handled outside the system.</li>
  </ul>
</section>

<!-- 3 -->
<section class="sec">
  <h2><span class="n">3</span>Functional requirements</h2>
  <p>Each requirement is a single verifiable statement with its priority and the test or artefact that verifies it. Acceptance criteria for every requirement are recorded in the interactive edition of this specification.</p>
  <table class="rq"><thead><tr><th style="width:15mm">ID</th><th>Requirement</th><th style="width:11mm">Priority</th><th style="width:44mm">Verified by</th></tr></thead><tbody id="reqs"></tbody></table>
</section>

<!-- 4 -->
<section class="sec">
  <h2><span class="n">4</span>Access control requirements</h2>
  <p><b>FR-AUTH-05</b> requires the server, not the user interface, to decide who sees what. Each cell below is the outcome of a real request made by that role's demo login against the running system on 01 Oct 2026. All 90 outcomes matched the intended rule. The private-record rows use Emma Lopez (Employee), who reports to Manuel Garcia (Manager), and Hana Reddy (HR Admin), who does not.</p>
  <table class="matrix" id="matrix"></table>
  <p class="small muted">✓ = request allowed (HTTP 200) · ✗ = request blocked (HTTP 403)</p>
  <h3>4.1 Rules the table demonstrates</h3>
  <ul>
    <li><b>Own data:</b> every employee <span class="shall">SHALL</span> be able to read their own payslips, reviews, tickets, documents and statutory details.</li>
    <li><b>Team scope:</b> a manager <span class="shall">SHALL</span> see their direct reports' documents, contacts and reviews, and nobody else's.</li>
    <li><b>Separation of duties:</b> the payroll administrator runs payroll and the HR administrator approves it; neither role <span class="shall">SHALL</span> hold both.</li>
    <li><b>Least privilege:</b> the recruiter <span class="shall">SHALL</span> reach recruitment and the shared directory only.</li>
  </ul>
</section>

<!-- 5 -->
<section class="sec">
  <h2><span class="n">5</span>External interfaces and data</h2>
  <table><tbody>
    <tr><td style="width:28mm"><b>5.1 User</b></td><td>A responsive Material 3 interface with light and dark themes. A role-aware navigation shell shows only the modules the signed-in user may use; a to-do bell gathers pending actions from every module. Public careers pages sit outside the authenticated shell.</td></tr>
    <tr><td><b>5.2 Software</b></td><td>A versioned REST API under <code>/api/v1</code> exchanging JSON, documented with OpenAPI. Requests carry a <code>Bearer</code> access token; errors return a consistent problem body with HTTP 400, 401, 403, 404 or 409.</td></tr>
    <tr><td><b>5.3 Documents</b></td><td>Generated PDFs for payslips and offer letters; Excel workbooks for employee import and export, payroll registers and headcount analytics.</td></tr>
    <tr><td><b>5.4 Communication</b></td><td>HTTPS between browser and server in deployment; SMTP for outbound notifications; JDBC between the API and the database.</td></tr>
  </tbody></table>
  <h3>5.5 Logical data model</h3>
  <p>Figure 2 shows the people and organisation core, simplified from the 14 migrations; the table after it lists the workflow entities that hang off EMPLOYEE. Audit columns, lookup tables and join tables are omitted.</p>
  <div class="diagram"><pre class="mermaid">
erDiagram
  USER }o--o{ ROLE : holds
  ROLE }o--o{ PERMISSION : grants
  USER |o--o| EMPLOYEE : "signs in as"
  DEPARTMENT ||--o{ EMPLOYEE : employs
  EMPLOYEE |o--o{ EMPLOYEE : manages
  EMPLOYEE ||--o| STATUTORY : "has (encrypted)"
  EMPLOYEE ||--o{ EMERGENCY_CONTACT : lists
  EMPLOYEE ||--o{ DOCUMENT : owns
  EMPLOYEE |o--o{ ASSET : "is assigned"
  </pre><div class="cap">Figure 2. People and organisation</div></div>
  <table><thead><tr><th style="width:28mm">Workflow area</th><th>Entities and relationships (1–n = one to many)</th></tr></thead><tbody>
    <tr><td>Leave</td><td class="path">LEAVE_TYPE 1–n LEAVE_BALANCE n–1 EMPLOYEE · EMPLOYEE 1–n LEAVE_REQUEST</td></tr>
    <tr><td>Attendance</td><td class="path">EMPLOYEE 1–n ATTENDANCE_DAY · EMPLOYEE 1–n REGULARIZATION_REQUEST</td></tr>
    <tr><td>Approvals</td><td class="path">APPROVAL_REQUEST 1–n APPROVAL_STEP · subject = leave, regularization, requisition, offer or payroll run</td></tr>
    <tr><td>Recruitment</td><td class="path">REQUISITION 1–n APPLICATION n–1 CANDIDATE · APPLICATION 1–0..1 OFFER · INTERVIEW 1–n PANEL_FEEDBACK</td></tr>
    <tr><td>Payroll</td><td class="path">PAYROLL_RUN 1–n PAYSLIP n–1 EMPLOYEE</td></tr>
    <tr><td>Performance</td><td class="path">REVIEW_CYCLE 1–n REVIEW n–1 EMPLOYEE · REVIEW 1–n GOAL</td></tr>
    <tr><td>Services</td><td class="path">EMPLOYEE 1–n TICKET 1–n TICKET_COMMENT · POLICY 1–n POLICY_ACK n–1 EMPLOYEE</td></tr>
  </tbody></table>
</section>

<!-- 6 -->
<section class="sec">
  <h2><span class="n">6</span>Non-functional requirements</h2>
  <p>Each quality requirement is stated with a measurable target, so it can be checked rather than argued.</p>
  <table><thead><tr><th>ID</th><th>Requirement</th><th>Target</th><th>Verified by</th></tr></thead><tbody id="nfr-body"></tbody></table>
</section>

<!-- 7 -->
<section class="sec">
  <h2><span class="n">7</span>Verification and traceability</h2>
  <p>The suite ran in full on 01 Oct 2026: <b>64 tests in 14 classes, 0 failures, 0 errors</b>. Integration tests boot the full application against an in-memory database built by the real migrations, then call the API as each seeded role.</p>
  <p>Of the <span id="c-all"></span> functional requirements, <span id="c-test"></span> are verified by these tests, <span id="c-insp"></span> by inspection of source and configuration, and <span id="c-demo"></span> by demonstration; the test behind each one is named in section 3. The access-control table in section 4 was produced by a further 90 live requests against the running system.</p>
</section>

<!-- 8 -->
<section class="sec">
  <h2><span class="n">8</span>Academic review</h2>
  <p>A critical assessment of the project against its own objectives, written for the reviewers who sign section 9.</p>
  <h3>8.1 Objectives against outcomes</h3>
  <table><thead><tr><th>Objective</th><th>Outcome</th><th>Status</th></tr></thead><tbody>
    <tr><td>Centralise employee records with a full profile and change history</td><td>Directory, tabbed profile, timeline of job changes, Excel import and export</td><td class="ok">Met</td></tr>
    <tr><td>Protect personal and financial data</td><td>Statutory identifiers encrypted at rest with AES-GCM, masked by default, reveals audited; object-level checks verified by live probe</td><td class="ok">Met</td></tr>
    <tr><td>Automate leave, attendance and approvals</td><td>Configurable leave types with accrual, punch attendance, one approval engine reused by five workflows</td><td class="ok">Met</td></tr>
    <tr><td>Cover hiring through onboarding</td><td>Requisitions, pipeline, panel interviews, approved offers with PDF letters, hire-to-employee conversion, onboarding plans</td><td class="ok">Met</td></tr>
    <tr><td>Compute payroll with Indian statutory rules</td><td>Run lifecycle with approval, PF, ESI and TDS under both regimes, PDF payslips. Tax exemptions beyond the standard deduction and 80C PF are not modelled.</td><td class="part">Partially met</td></tr>
    <tr><td>Support performance management</td><td>Cycles, weighted goals, self and manager review, acknowledgement, feedback wall</td><td class="ok">Met</td></tr>
    <tr><td>Give employees self-service</td><td>Personal dashboard with cross-module to-dos; password change. Password change exists in the API but has no screen yet.</td><td class="part">Partially met</td></tr>
    <tr><td>Demonstrate quality through testing</td><td>64 automated tests passing; 90-check live access probe; production build passing</td><td class="ok">Met</td></tr>
  </tbody></table>
  <h3>8.2 Methodology</h3>
  <p>The system was built in eight incremental phases. Each phase delivered a usable vertical slice (schema, API, tests and screens) and was committed only after its build and tests passed. Shared mechanisms were built once and reused: the approval engine introduced in phase 3 now carries leave, regularization, requisitions, offers and payroll runs. That reuse is the main architectural decision of the project.</p>
  <h3>8.3 Strengths</h3>
  <ul>
    <li><b>Security designed in, then measured:</b> encryption, masking, lockout and object-level checks, each backed by a test or the live probe rather than by assertion.</li>
    <li><b>One engine, many workflows:</b> a generic approval model avoided five separate implementations and keeps the approver inbox uniform.</li>
    <li><b>Database discipline:</b> schema changes only through migrations, validated at start-up, with a smoke test that builds the whole schema from scratch.</li>
    <li><b>Realistic domain:</b> payroll follows Indian statutory structure, and seed data produces a believable organisation to evaluate against.</li>
  </ul>
  <h3>8.4 Limitations and threats to validity</h3>
  <ul>
    <li><b>Test environment:</b> automated tests and the demo run on H2 in Oracle compatibility mode. The production Oracle container is defined but was not exercised on the development machine, so database-specific behaviour is verified by inspection only.</li>
    <li><b>No automated UI tests:</b> screens were checked by hand. Payroll and self-service screens have had the least manual coverage.</li>
    <li><b>Simplified tax:</b> only the standard deduction and 80C PF are modelled, so payslips understate deductions for employees with other exemptions.</li>
    <li><b>Analytics scale:</b> analytics are computed in memory from repositories, which is adequate for a few hundred employees but not for a large enterprise.</li>
    <li><b>Payroll visibility:</b> as the section 4 table shows, the payroll role can read all employees' documents and emergency contacts. This is wider than payroll strictly needs.</li>
    <li><b>Authentication:</b> there is no single sign-on and no multi-factor authentication.</li>
  </ul>
  <h3>8.5 Future scope</h3>
  <ul>
    <li>A password-change screen, and an end-to-end UI test suite (for example Playwright) in the build.</li>
    <li>Continuous integration that runs the suite against a real Oracle container.</li>
    <li>Full tax-declaration workflow (HRA, 80D and similar) and bank disbursement files.</li>
    <li>Single sign-on with multi-factor authentication.</li>
    <li>Narrow the payroll role to pay-related data only.</li>
  </ul>
</section>

<!-- 9 -->
<section class="sec" style="break-inside: avoid">
  <h2><span class="n">9</span>Review and sign-off</h2>
  <p>The specification is approved when the student has signed the declaration and the project guide, head of department and external examiner have each recorded a verdict below.</p>
  <table class="signsheet"><thead><tr><th style="width:24%">Role</th><th style="width:22%">Name</th><th style="width:28%">Verdict</th><th style="width:16%">Signature</th><th style="width:10%">Date</th></tr></thead><tbody>
    <tr><td>Student (declaration)</td><td>Aman Arora<br><span class="mono small">2400320100152</span></td><td class="small">Declaration made</td><td></td><td></td></tr>
    <tr><td>Project Guide</td><td>Dr Radhika Singhal</td><td class="small"><span class="box"></span>Approved &nbsp;<span class="box"></span>With changes &nbsp;<span class="box"></span>Revise</td><td></td><td></td></tr>
    <tr><td>Head of Department</td><td></td><td class="small"><span class="box"></span>Approved &nbsp;<span class="box"></span>With changes &nbsp;<span class="box"></span>Revise</td><td></td><td></td></tr>
    <tr><td>External Examiner</td><td></td><td class="small"><span class="box"></span>Approved &nbsp;<span class="box"></span>With changes &nbsp;<span class="box"></span>Revise</td><td></td><td></td></tr>
  </tbody></table>
  <h3>9.1 Evaluation rubric</h3>
  <p class="small">Score each criterion from 1 (inadequate) to 5 (excellent).</p>
  <table id="rubric"></table>
  <h3>9.2 Reviewers' remarks</h3>
  <div class="lines"><div></div><div></div><div></div></div>
</section>

<script>
__DATA__

(function () {
  const $ = s => document.querySelector(s) || document.createElement("div");
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const shall = s => esc(s).replace(/\bshall\b/g, '<span class="shall">SHALL</span>');
  $("#fig-fr").textContent = FR.length;
  $("#c-all").textContent = FR.length;
  $("#c-test").textContent = FR.filter(r => r[5] === "Test").length;
  $("#c-insp").textContent = FR.filter(r => r[5] === "Inspection").length;
  $("#c-demo").textContent = FR.filter(r => r[5] === "Demonstration").length;

  const modRow = ([c, n, b]) => `<tr><td><b>${n}</b><br><span class="small muted">${b}</span></td><td class="mono">${FR.filter(r => r[1] === c).length}</td></tr>`;
  const modHead = `<thead><tr><th>Module</th><th style="width:9mm">Req.</th></tr></thead>`;
  $("#mod-tables").innerHTML = `<table>${modHead}<tbody>${MODULES.slice(0, 8).map(modRow).join("")}</tbody></table><table>${modHead}<tbody>${MODULES.slice(8).map(modRow).join("")}</tbody></table>`;
  const permText = r => {
    const missing = PERMS.filter(p => !r.perms.includes(p));
    if (!missing.length) return `All ${PERMS.length}`;
    if (missing.length <= 2) return `All except ${missing.join(", ")}`;
    return r.perms.join(", ");
  };
  $("#role-table tbody").innerHTML = ROLES.map(r => `<tr><td><b>${r.name}</b><br><span class="path muted">${r.login}</span></td><td>${r.desc}</td><td class="path">${permText(r)}</td></tr>`).join("");

  const evShort = ev => esc(ev.split(" · ")[0].split(";")[0].replace(/\s*\(.*\)$/, ""));
  $("#reqs").innerHTML = MODULES.map(([code, name]) =>
    `<tr class="mod"><td colspan="4">${name}</td></tr>` +
    FR.filter(r => r[1] === code).map(([id, , t, s, p, m, ev]) =>
      `<tr><td>${id}</td><td><span class="ti">${esc(t)}.</span> ${shall(s)}</td><td class="c">${p}</td><td><b>${m}</b><br><span class="path">${evShort(ev)}</span></td></tr>`).join("")
  ).join("");

  $("#lifecycles").innerHTML = Object.entries(LIFECYCLES).map(([k, lc]) =>
    `<tr><td><b>${k}</b></td><td class="path">${lc.main.map(m => m[0]).join(" → ")}</td><td class="path">${lc.branches.map(b => b[0]).join(", ") || "–"}</td></tr>`).join("");

  $("#matrix").innerHTML = `<thead><tr><th>Request</th>${ROLES.map(r => `<th>${r.name}</th>`).join("")}</tr></thead><tbody>` +
    PROBES.map(p => p[1] === null ? `<tr class="grp"><td colspan="7">${esc(p[0])}</td></tr>` :
      `<tr><td>${esc(p[0])}</td>${p.slice(2).map(v => v ? '<td class="y">✓</td>' : '<td class="n0">✗</td>').join("")}</tr>`).join("") + "</tbody>";

  $("#nfr-body").innerHTML = NFR.map(([id, r, t, b]) => `<tr><td class="mono" style="color:var(--accent);white-space:nowrap">${id}</td><td>${r}</td><td>${t}</td><td class="small">${b}</td></tr>`).join("");
  const c = (i, v) => FR.filter(r => r[i] === v).length;
  $("#counts").innerHTML = `<thead><tr><th>Verification method</th><th>Requirements</th><th>Priority</th><th>Requirements</th></tr></thead><tbody>
    <tr><td>Test</td><td class="mono">${c(5, "Test")}</td><td>Must</td><td class="mono">${c(4, "Must")}</td></tr>
    <tr><td>Inspection</td><td class="mono">${c(5, "Inspection")}</td><td>Should</td><td class="mono">${c(4, "Should")}</td></tr>
    <tr><td>Demonstration</td><td class="mono">${c(5, "Demonstration")}</td><td><b>Total</b></td><td class="mono"><b>${FR.length}</b></td></tr></tbody>`;
  $("#tests-body").innerHTML = TESTS.map(([k, w, r]) => `<tr><td class="mono">${k}</td><td>${w}</td><td class="mono small">${r}</td></tr>`).join("");
  $("#timeline").innerHTML = TIMELINE.map(([d, k, w]) => `<tr><td style="white-space:nowrap">${d} 2026</td><td class="mono">${k}</td><td>${w}</td></tr>`).join("");
  $("#rubric").innerHTML = `<thead><tr><th>Criterion</th><th style="width:15%">Project Guide</th><th style="width:15%">Head of Dept.</th><th style="width:15%">Ext. Examiner</th></tr></thead><tbody>` +
    RUBRIC.map(([k]) => `<tr><td style="height:7mm;vertical-align:middle"><b>${k}</b></td><td></td><td></td><td></td></tr>`).join("") +
    `<tr><td><b>Total (out of 30)</b></td><td></td><td></td><td></td></tr></tbody>`;

  window.mermaid && mermaid.initialize({ startOnLoad: false, theme: "neutral", fontFamily: "Segoe UI, Arial, sans-serif", themeVariables: { fontFamily: "Segoe UI, Arial, sans-serif", fontSize: "13px" } });
  const done = () => document.body.setAttribute("data-ready", "1");
  if (window.mermaid) mermaid.run().then(done, done); else done();
})();
</script>
</body></html>
"""


def main():
    src = SRC.read_text(encoding="utf-8")
    i, j = src.index(START), src.index(END)
    data = src[i + len(START):j]
    PRINT_HTML.write_text(TEMPLATE.replace("__DATA__", data), encoding="utf-8")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    cmd = [str(EDGE), "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
           "--run-all-compositor-stages-before-draw", "--virtual-time-budget=30000",
           f"--print-to-pdf={OUT}", PRINT_HTML.as_uri()]
    subprocess.run(cmd, check=True, timeout=180, capture_output=True)
    if not OUT.exists() or OUT.stat().st_size < 10_000:
        raise SystemExit(f"PDF was not produced at {OUT}")
    print(f"Wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
