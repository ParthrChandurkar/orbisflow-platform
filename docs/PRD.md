# Orbis Flow — Product Requirements Document

- **Product:** Orbis Flow
- **Repository:** `orbisflow-platform`
- **Release:** MVP
- **Delivery horizon:** Two months, solo build
- **Status:** Approved scope for implementation

## 1. Executive Summary

Orbis Flow is an AI-assisted SaaS application that moves employee invoices through a fixed review and payment-processing workflow. It replaces manual document entry, fragmented approval follow-up, and unclear request status with one traceable flow: an Employee uploads an invoice, AI extracts and validates its data, a Manager approves or rejects it, and Finance records it as processed.

The MVP is deliberately narrow. It supports exactly three roles—Employee, Manager, and Finance—and one hardcoded invoice workflow. Its only AI capabilities are invoice OCR with structured extraction and rule-based routing based on whether the extracted submission is valid and complete. Every material action is written to an audit log, role-specific dashboards reflect current state, and in-app notifications alert users to actions relevant to them.

The implementation stack is fixed: Next.js, TypeScript, Tailwind, and shadcn/ui for the frontend; Spring Boot for the backend; FastAPI for the AI service; PostgreSQL for durable data; Redis for caching; AWS S3 for documents; JWT for authentication; AWS for hosting; and Docker, Docker Compose, and GitHub Actions for delivery.

The MVP is complete when a user can securely submit a supported invoice and follow it end to end without manual database intervention, authorized reviewers can complete their assigned steps, failures are recoverable and visible, and the resulting status and history are accurate.

## 2. Problem Statement & Target Users

Small teams often receive invoices through disconnected channels and then coordinate review through messages or spreadsheets. Employees must repeatedly enter invoice details and cannot easily tell who owns the next action. Managers lack a consistent queue for decisions. Finance receives incomplete or unapproved invoices and has limited evidence of what happened before payment processing. This creates avoidable data-entry effort, slow handoffs, duplicate follow-up, and weak traceability.

Orbis Flow addresses that problem with a single system of record for invoice submission, extraction, validation, approval, and processing.

The target users are:

- **Employees** who submit invoices and need clear feedback and status visibility for their own requests.
- **Managers** who need a focused queue of requests routed to them and visibility into their team's invoice activity.
- **Finance users** who need to review manager-approved invoices and record payment-processing status.

The product is intended for a modest internal user base during the MVP. It prioritizes workflow correctness, usability, and deployability over broad organizational configuration.

## 3. Goals and Non-Goals

### Goals

1. Reduce manual invoice data entry by extracting vendor, amount, invoice date, and line items from uploaded documents.
2. Prevent incomplete or flagged invoices from entering the Manager queue until the Employee corrects and resubmits them.
3. Give each role a clear queue or status view for the work they own.
4. Enforce the fixed sequence from submission through Manager review to Finance processing.
5. Preserve a trustworthy, chronological audit trail for every invoice.
6. Deliver a production-deployable MVP within a solo two-month build.

### Non-Goals

The MVP will not include:

- Retrieval-augmented generation (RAG), an executive chat assistant, or natural-language search.
- A configurable or custom workflow engine.
- Real-time or WebSocket notifications; notifications are in-app only.
- Admin, HR, Procurement, or CEO roles.
- OAuth; authentication is JWT-only.

These exclusions are scope boundaries, not implied requirements for the MVP.

## 4. User Roles & Core Permissions

This section defines only the high-level permission boundary. The detailed resource/action RBAC matrix will be produced in Stage 2.

- **Employee:** Authenticate, upload invoices, review extracted data and validation feedback, correct or resubmit returned requests, and view the status and history of only their own requests.
- **Manager:** Authenticate, view requests routed to their approval queue, approve or reject those requests, enter a decision comment where required, and view invoice activity for their team.
- **Finance:** Authenticate, view invoices approved by Managers, review invoice details and history, and mark eligible invoices as processed.

Users may access only the data and actions allowed by their assigned role. A user cannot bypass workflow order by calling backend endpoints directly.

## 5. Core User Flow

1. An Employee signs in and uploads an invoice.
2. The platform validates the file and stores it in AWS S3 with application metadata in PostgreSQL.
3. The FastAPI AI service performs OCR and extracts vendor, amount, invoice date, and line items.
4. The platform validates the extracted output for required fields, supported values, and processing flags.
5. The Employee reviews the extracted fields. If the invoice is incomplete or flagged, the request returns to the Employee with clear validation feedback. The Employee corrects available fields or uploads a replacement and resubmits.
6. When the invoice is valid and complete, rule-based routing sends it to the appropriate Manager queue.
7. The Manager reviews the original document, extracted data, and request history, then approves or rejects the request.
8. If rejected, the request returns to the Employee with the Manager's reason and does not proceed to Finance.
9. If approved, the request moves to the Finance queue.
10. Finance reviews the approved request and marks it as processed.
11. Each upload, extraction result, validation outcome, resubmission, routing event, decision, and status change is appended to the audit log.
12. Role-specific dashboards and in-app notifications update from the persisted workflow state.

## 6. Functional Requirements

### Auth

- **AUTH-01:** Users shall sign in with credentials and receive a time-limited JWT for authenticated requests.
- **AUTH-02:** The Spring Boot backend shall validate token signature, expiry, user identity, and assigned role before serving protected resources.
- **AUTH-03:** Authorization shall be enforced server-side for every protected action; hidden frontend controls shall not be treated as access control.
- **AUTH-04:** Invalid or expired sessions shall return a consistent unauthorized response and redirect the user to sign in without exposing protected data.
- **AUTH-05:** Passwords shall be stored only as strong salted hashes and shall never appear in logs or API responses.

### Document Upload

- **DOC-01:** An Employee shall be able to create a request by uploading one supported invoice file through a clear drag-and-drop or file-picker interface.
- **DOC-02:** Only PDF, JPG, and PNG invoices up to 10 MB shall be accepted. The frontend and backend shall both enforce these format and size constraints.
- **DOC-03:** Empty, corrupt, unsupported, or oversized files shall be rejected with actionable errors and no routable request.
- **DOC-04:** Accepted documents shall be stored in a private S3 bucket under non-guessable object keys; PostgreSQL shall store ownership, file metadata, timestamps, and workflow state.
- **DOC-05:** Upload progress, successful receipt, and retryable failure states shall be visible to the Employee.
- **DOC-06:** An Employee shall be able to view or download the document attached to their own request through authorized, time-limited access.

### AI Extraction

- **AI-01:** After a successful upload, the FastAPI service shall extract vendor, total amount, invoice date, and line items from the invoice.
- **AI-02:** Each line item shall support, at minimum, a description and amount when present in the source document.
- **AI-03:** The extraction response shall use a versioned structured schema and return field-level values, extraction status, and validation flags to Spring Boot.
- **AI-04:** The system shall preserve the original document and extracted output so the Employee, Manager, and Finance can compare them within their authorized views.
- **AI-05:** An invoice shall be incomplete/flagged if vendor, total amount, or invoice date is missing, or if present line items do not sum to the extracted total. Without line items, only required fields are checked. All others are valid and complete.
- **AI-06:** The Employee shall be shown extracted fields before routing and may correct extracted values permitted by the request form. Corrections shall be audited.
- **AI-07:** If extraction times out or fails, the request shall remain recoverable, show a non-technical failure message, and allow the Employee to retry rather than silently route.

### Approval Workflow

- **WF-01:** The backend shall enforce the fixed states and transitions: uploaded/extracting, employee review, manager review, rejected, finance review, and processed.
- **WF-02:** Each Employee user record shall have a `manager_id` foreign key to the users table, set manually through seed data or an administrative database insert. No assignment UI shall exist. Valid and complete requests shall route to that Manager; incomplete/flagged requests shall return to the Employee.
- **WF-03:** A Manager shall see only actionable requests routed to them and shall be able to approve or reject each request once while it is in Manager review.
- **WF-04:** Rejection shall require a reason visible to the Employee. Rejected requests shall not enter the Finance queue.
- **WF-05:** Manager approval shall atomically record the decision and move the request to Finance review.
- **WF-06:** Finance may process only Manager-approved requests. Processing shall require a timestamp, Finance actor identity, and `payment_status` from `{paid, scheduled}`. Partial payments and multiple installments are unsupported.
- **WF-07:** The backend shall reject stale, duplicate, unauthorized, and out-of-order transitions with a clear conflict or forbidden response.
- **WF-08:** Each request detail page shall show its current status, current owner role, extracted data, source document, and chronological history as permitted by role.

### Dashboards

- **DASH-01:** The Employee dashboard shall list the Employee's requests, current status, submission date, amount, and latest required action.
- **DASH-02:** The Manager dashboard shall show an approval queue and summary of team activity, including pending, approved, and rejected requests.
- **DASH-03:** The Finance dashboard shall show Manager-approved invoices awaiting processing and recently processed invoices.
- **DASH-04:** Dashboard data shall come from persisted backend state and support basic pagination, status filtering, and deterministic sorting.
- **DASH-05:** Empty, loading, and error states shall explain what the user can do next.

### Audit Log

- **AUD-01:** The system shall append an audit event for every material workflow action, including upload, extraction, validation, field correction, routing, resubmission, approval, rejection, and processing.
- **AUD-02:** Each event shall record request ID, event type, timestamp, actor identity or system actor, previous state, resulting state, and relevant non-sensitive context.
- **AUD-03:** Audit events shall be immutable through product APIs and displayed chronologically on the request detail page to authorized users.
- **AUD-04:** Sensitive credentials, JWTs, and raw secrets shall never be recorded in audit context.

### Notifications

- **NOTIF-01:** The platform shall create an in-app notification when a user receives a required action or when a decision changes the status of their request.
- **NOTIF-02:** Notifications shall cover Employee correction/rejection, Manager review assignment, Finance review assignment, and final processed status.
- **NOTIF-03:** Users shall be able to view unread and recent notifications and mark notifications as read.
- **NOTIF-04:** Notifications shall link to an authorized request detail page and shall not reveal request data to an unauthorized user.

## 7. Non-Functional Requirements

### Security

- All external traffic shall use HTTPS in deployed environments. JWTs, database credentials, S3 credentials, and signing keys shall be supplied through environment-managed secrets and excluded from source control.
- Authorization shall be deny-by-default and verified in backend integration tests for cross-role and cross-owner access.
- S3 objects shall be private. File access shall use short-lived signed URLs or authenticated streaming, and uploads shall be checked by type, size, and safe filename handling.
- Inputs shall be validated at API boundaries. Database access shall use parameterized ORM/repository patterns. Logs shall avoid invoice contents and personally identifying data unless necessary for diagnosis.
- Production database and object storage shall have automated backups configured, with one documented restore test before MVP release.

### Scalability and Reliability

- The MVP shall support at least 100 registered users, 25 concurrent active users, and 10,000 stored invoice records without redesign.
- Spring Boot and FastAPI services shall be stateless where practical so containers can be restarted independently. PostgreSQL remains the source of truth; Redis shall not hold the only copy of workflow state.
- AI processing shall be isolated from the request UI so a slow extraction does not cause duplicate uploads or corrupt state.
- Workflow updates and audit writes that represent one user action shall be transactionally consistent.
- Docker Compose shall support reproducible local startup. GitHub Actions shall run build, lint, and automated tests on changes to the main branch.

### Performance and Usability

- Excluding file transfer and AI processing, the 95th-percentile API response time shall be under 800 ms at the stated MVP load.
- Dashboard and request-detail pages shall show meaningful content or a loading state within 2 seconds on a typical broadband connection.
- For supported, readable invoices, 90% of extraction jobs shall complete within 30 seconds. Jobs exceeding 60 seconds shall surface a recoverable timeout or failure state.
- The interface shall be responsive for current desktop and mobile browser sizes and meet practical WCAG 2.1 AA basics: keyboard access, visible focus, labels, sufficient contrast, and understandable errors.
- Operational health checks and structured error logs shall exist for the frontend deployment, Spring Boot API, FastAPI service, PostgreSQL, Redis, and S3 integration.

## 8. Success Metrics

The MVP is considered done when:

- 100% of defined workflow transitions pass automated happy-path, rejection, correction, authorization, and invalid-transition tests.
- At least 90% of a representative test set of readable invoices correctly extracts vendor, total amount, and invoice date; line-item accuracy is measured and reported before release.
- At least 95% of supported invoice uploads reach either Employee review or a visible recoverable error without manual database intervention.
- No critical or high-severity authorization defect remains open at release.
- Every tested material action produces the expected immutable audit event and dashboard status.
- A clean environment can be started from documented Docker instructions, and the main branch passes the GitHub Actions build and test workflow.
- A documented end-to-end acceptance test performed by the developer across all three roles (Employee, Manager, Finance), covering upload, extraction, correction, approval, rejection, and processing, recorded as evidence of MVP completion.

## 9. Resolved Decisions / Deferred Features

MVP decisions are resolved:

1. **File formats and size — Resolved:** PDF, JPG, and PNG only; 10 MB maximum; all others rejected by frontend and backend.
2. **Employee-to-Manager mapping — Resolved:** `manager_id` is a users-table foreign key set by seed data or administrative insert. WF-02 routes to that Manager. No assignment UI is included.
3. **Incomplete/flagged criteria — Resolved:** Vendor, total amount, or invoice date is missing; or present line items do not sum to the extracted total. Without line items, only required fields apply.
4. **Processed definition — Resolved:** Timestamp, Finance actor identity, and `payment_status` of `paid` or `scheduled` are required. Partial payments and multiple installments are unsupported.

### Deferred Post-MVP

Invoice files, request records, and audit events are retained indefinitely for the MVP with no automated deletion policy. Retention policy is deferred post-MVP.

RAG, executive chat, natural-language search, configurable workflows, real-time notifications, additional roles, and OAuth remain deferred and are not part of this PRD.
