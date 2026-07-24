# Orbis Flow — Stage 3 User Stories

- **Source:** `docs/PRD.md` Sections 3, 6, and 9; `docs/rbac.md`
- **Roles:** Employee, Manager, Finance
- **Acceptance-criteria style:** Given/When/Then

## 1. Access & Session

### US-01 — Sign in

As an Employee, Manager, or Finance user, I want to sign in with my credentials, so that I can access work permitted to my role.

**Source:** AUTH-01, AUTH-04

- **Given** valid credentials, **when** I sign in, **then** I receive a time-limited JWT and enter my role-appropriate experience.
- **Given** invalid credentials or an invalid/expired session, **when** I request protected content, **then** I receive a consistent unauthorized result, see no protected data, and am directed to sign in.

### US-02 — Protect identity and credentials

As the system, I want to validate identity and protect credentials, so that accounts and protected resources remain secure.

**Source:** AUTH-02, AUTH-03, AUTH-05; RBAC enforcement: READ User

- **Given** a protected action, **when** it is requested, **then** the JWT signature, expiry, user identity, and exact role are validated before authorization.
- **Given** a stored password, **when** it is persisted or handled, **then** only a strong salted hash is stored and neither the password nor its value appears in logs or responses.
- **Given** a control hidden in the frontend, **when** its protected action is requested directly, **then** the backend still performs the full authorization check.

## 2. Submission & Extraction

### US-03 — Upload a supported invoice

As an Employee, I want to upload an invoice, so that I can create a Request for review.

**Source:** DOC-01, DOC-02, DOC-03, DOC-05

- **Given** a PDF, JPG, or PNG no larger than 10 MB, **when** I select or drop it, **then** the frontend and backend accept it and show upload progress and receipt.
- **Given** an empty, corrupt, unsupported, or oversized file, **when** I attempt upload, **then** both applicable validation layers reject it with an actionable error and no routable Request.
- **Given** a retryable upload failure, **when** it occurs, **then** I see the failure state and can retry.

### US-04 — Store and access my document securely

As an Employee, I want to access my securely stored invoice on its Request, so that I can review the submitted source.

**Source:** DOC-04, DOC-06; RBAC matrix: Document

- **Given** an accepted invoice, **when** storage completes, **then** the private S3 object uses a non-guessable key and PostgreSQL records ownership, file metadata, timestamps, and workflow state.
- **Given** my own Request, **when** I view or download its document, **then** access is authorized and time-limited.
- **Given** a Request I do not own, **when** I request its document, **then** access is denied unless another role-specific RBAC scope explicitly permits it.

### US-05 — Extract structured invoice data

As an Employee, I want to have invoice fields extracted automatically, so that I avoid re-entering available data.

**Source:** AI-01, AI-02, AI-03

- **Given** a successfully uploaded invoice, **when** extraction runs, **then** it returns vendor, total amount, invoice date, and available line items.
- **Given** extracted line items, **when** structured output is produced, **then** each available item includes at least description and amount.
- **Given** any extraction result, **when** Spring Boot receives it, **then** it follows the versioned schema and includes field values, extraction status, and validation flags.

### US-06 — Compare extraction with the source

As an Employee, Manager, or Finance user, I want to compare extracted data with the original invoice within my permitted scope, so that I can verify the information used for a decision.

**Source:** AI-04; RBAC matrix: Document and Extracted Invoice Data

- **Given** a Request within my ownership, assignment, or Finance-visible state, **when** I open its detail, **then** I can read both the preserved document and extracted output.
- **Given** a Request outside my RBAC scope, **when** I attempt the same comparison, **then** neither resource is exposed.

### US-07 — Validate completeness and totals

As an Employee, I want to see clear validation of extracted invoice data, so that only valid and complete Requests reach my Manager.

**Source:** AI-05

- **Given** vendor, total amount, or invoice date is missing, **when** validation runs, **then** the invoice is incomplete/flagged.
- **Given** line items are present and their sum differs from the extracted total, **when** validation runs, **then** the invoice is incomplete/flagged.
- **Given** no line items, **when** validation runs, **then** only the three required fields are checked.
- **Given** none of the flagged conditions apply, **when** validation completes, **then** the invoice is valid and complete.

### US-08 — Recover from extraction failure

As an Employee, I want to recover from a failed extraction, so that I can retry without losing or misrouting my Request.

**Source:** AI-07

- **Given** extraction fails or times out, **when** the failure is recorded, **then** the Request does not route to a Manager and I see a non-technical message.
- **Given** a recoverable extraction failure, **when** I retry, **then** the existing Request can re-enter extraction without a silent duplicate routing action.

## 3. Employee Correction & Routing

### US-09 — Correct and resubmit returned data

As an Employee, I want to correct extracted values on my returned Request, so that validation can run again.

**Source:** AI-06, WF-02; RBAC matrix: Employee UPDATE Request and Extracted Invoice Data

- **Given** my Request is in `employee_review` or `rejected`, **when** I change a field permitted by the form, **then** the correction is saved and audited.
- **Given** my corrected Request, **when** I resubmit it, **then** validation runs again before routing.
- **Given** a Request outside those states or not owned by me, **when** I try to update it, **then** the update is denied.

### US-10 — Route by fixed workflow

As an Employee, I want to have my Request routed according to its validation result, so that the correct person receives the next action.

**Source:** WF-01, WF-02, WF-07

- **Given** my Request is valid and complete and my `manager_id` is preassigned, **when** routing runs, **then** it enters `manager_review` for that Manager.
- **Given** my Request is incomplete/flagged, **when** routing runs, **then** it returns to me in `employee_review`.
- **Given** a stale, duplicate, unauthorized, or out-of-order transition, **when** it is attempted, **then** it is rejected without changing the persisted state.

## 4. Manager Review

### US-11 — Review an assigned Request

As a Manager, I want to review Requests assigned to me, so that I can make an informed decision.

**Source:** WF-03, WF-08; RBAC matrix: Manager READ Request

- **Given** a Request assigned to me, **when** I open it, **then** I can see its current status, owner role, extracted data, source document, and chronological history.
- **Given** a Request not assigned to me, **when** I attempt to open its detail, **then** access is denied.

### US-12 — Approve an assigned Request

As a Manager, I want to approve an eligible assigned Request, so that Finance can process it.

**Source:** WF-03, WF-05, WF-07

- **Given** an assigned Request in `manager_review`, **when** I approve it once, **then** the decision is recorded atomically and the Request enters `finance_review`.
- **Given** a stale Request, wrong state, prior decision, or missing assignment, **when** I attempt approval, **then** the transition is rejected.

### US-13 — Reject an assigned Request

As a Manager, I want to reject an eligible assigned Request with a reason, so that the Employee can understand and correct it.

**Source:** WF-03, WF-04, WF-07

- **Given** an assigned Request in `manager_review`, **when** I reject it with a reason, **then** the reason is recorded, the Employee can see it, and the Request does not enter Finance.
- **Given** no rejection reason or an ineligible Request, **when** I attempt rejection, **then** the decision is not recorded.

## 5. Finance Processing

### US-14 — Review and process an approved invoice

As a Finance user, I want to process a Manager-approved Request, so that its payment outcome is recorded.

**Source:** WF-06, WF-07, WF-08; RBAC matrix: Finance READ/PROCESS Request

- **Given** a Request in `finance_review`, **when** I open it, **then** I can review its status, extracted data, source document, and history.
- **Given** that Request, **when** I process it with `paid` or `scheduled`, **then** the status, processing timestamp, my actor identity, and payment status are recorded.
- **Given** an invalid state, unsupported payment status, missing required processing value, partial payment, or multiple installments, **when** processing is attempted, **then** it is rejected.

## 6. Dashboards & Notifications

### US-15 — Track my submissions

As an Employee, I want to view a dashboard of my Requests, so that I can track status and required actions.

**Source:** DASH-01, DASH-04, DASH-05

- **Given** my Requests exist, **when** I open the dashboard, **then** I see only my Requests with status, submission date, amount, and latest required action.
- **Given** multiple results, **when** I paginate, filter by status, or sort, **then** persisted backend data produces deterministic results.
- **Given** no results, loading, or an error, **when** that state occurs, **then** the dashboard explains what I can do next.

### US-16 — Manage the approval queue

As a Manager, I want to view my approval queue and team activity summary, so that I can prioritize reviews.

**Source:** DASH-02, DASH-04, DASH-05; RBAC matrix: Manager READ Request

- **Given** Requests assigned to me, **when** I open the dashboard, **then** I see my actionable queue and aggregate pending, approved, and rejected activity only for Employees mapped to me.
- **Given** multiple results or a non-success state, **when** I paginate, filter, sort, load, or encounter an error/empty result, **then** the result is deterministic and the next action is clear.

### US-17 — Manage the Finance queue

As a Finance user, I want to view approved and recently processed invoices, so that I can manage processing work.

**Source:** DASH-03, DASH-04, DASH-05; RBAC matrix: Finance READ Request

- **Given** Requests in `finance_review` or `processed`, **when** I open the dashboard, **then** I see approved invoices awaiting processing and recently processed invoices only.
- **Given** multiple results or a non-success state, **when** I paginate, filter, sort, load, or encounter an error/empty result, **then** the result is deterministic and the next action is clear.

### US-18 — Receive and manage in-app notifications

As an Employee, Manager, or Finance user, I want to receive scoped in-app notifications, so that I know when an action or status change concerns me.

**Source:** NOTIF-01, NOTIF-02, NOTIF-03, NOTIF-04; RBAC matrix: Notification

- **Given** Employee correction/rejection, Manager assignment, Finance assignment, or final processing occurs, **when** I am the affected user, **then** an in-app notification is created for me.
- **Given** my notifications, **when** I open the notification view, **then** I can read unread and recent items and mark my items as read.
- **Given** a notification link, **when** I follow it, **then** the linked Request opens only if I am independently authorized.

## 7. Audit & Security

### US-19 — Record material workflow events

As the system, I want to append complete audit events, so that every material workflow action is traceable.

**Source:** AUD-01, AUD-02, AUD-04

- **Given** upload, extraction, validation, correction, routing, resubmission, approval, rejection, or processing succeeds, **when** its state change commits, **then** an audit event is appended.
- **Given** an audit event, **when** it is stored, **then** it includes Request ID, event type, timestamp, actor or system actor, previous state, resulting state, and relevant non-sensitive context.
- **Given** audit context, **when** it is written, **then** credentials, JWTs, and raw secrets are excluded.

### US-20 — Read immutable Request history

As an Employee, Manager, or Finance user, I want to read authorized chronological history, so that I can understand a Request's lifecycle.

**Source:** AUD-03, WF-08; RBAC matrix: Audit Log

- **Given** a Request within my ownership, assignment, or Finance-visible state, **when** I open its history, **then** audit events appear chronologically.
- **Given** any product action, **when** it attempts to update or delete an audit event, **then** the mutation is denied.

### US-21 — Deny Employee approval decisions

As the system, I want to reject an Employee's approval or rejection attempt, so that Manager authority cannot be bypassed.

**Source:** RBAC negative case: Employee approves/rejects; AUTH-03, WF-03

- **Given** an Employee JWT, **when** approval or rejection is attempted on any Request, including their own, **then** access is denied.
- **Given** the denial, **when** state is checked, **then** no decision or workflow change exists.

### US-22 — Deny Employee processing

As the system, I want to reject an Employee's processing attempt, so that Finance authority remains exclusive.

**Source:** RBAC negative case: Employee processes; AUTH-03, WF-06

- **Given** an Employee JWT, **when** processing is attempted, **then** access is denied.
- **Given** the denial, **when** the Request is read, **then** processing fields and state are unchanged.

### US-23 — Deny Manager submission changes

As the system, I want to reject a Manager's attempt to create, edit, or resubmit an Employee Request, so that Employee ownership is enforced.

**Source:** RBAC negative case: Manager creates/edits/resubmits; AI-06, WF-03

- **Given** a Manager JWT, **when** Request creation, extracted-data editing, or Employee resubmission is attempted, **then** access is denied.
- **Given** the denial, **when** resources are checked, **then** no Request or extraction value changed.

### US-24 — Deny Manager processing

As the system, I want to reject a Manager's payment-processing attempt, so that Manager and Finance duties remain separate.

**Source:** RBAC negative case: Manager processes; WF-06

- **Given** a Manager JWT, **when** processing or setting `payment_status` is attempted, **then** access is denied.
- **Given** the denial, **when** the Request is checked, **then** processing state and fields are unchanged.

### US-25 — Deny Finance approval decisions

As the system, I want to reject a Finance user's Manager-stage decision attempt, so that approval authority remains with the assigned Manager.

**Source:** RBAC negative case: Finance approves/rejects; WF-03, WF-06

- **Given** a Finance JWT, **when** approval or rejection is attempted, **then** access is denied.
- **Given** the denial, **when** the Request is checked, **then** its decision and workflow state are unchanged.

### US-26 — Deny Finance extraction edits

As the system, I want to reject Finance edits to extracted data, so that correction remains an Employee action.

**Source:** RBAC negative case: Finance edits extraction; AI-06

- **Given** a Finance JWT, **when** an extracted field is edited, **then** access is denied.
- **Given** the denial, **when** extracted data is read, **then** all values are unchanged.

### US-27 — Deny guessed-ID access

As the system, I want to reject out-of-scope direct resource access, so that guessed identifiers cannot expose protected data.

**Source:** RBAC negative case: guessed Request/Document/Notification ID; DOC-06, NOTIF-04, AUTH-03

- **Given** an authenticated user, **when** they request a Request or Document outside their ownership, assignment, or Finance state scope, **then** access is denied with no protected fields.
- **Given** an authenticated user, **when** they request another user's Notification, **then** access is denied regardless of knowing its identifier.

### US-28 — Deny cross-user account reads

As the system, I want to reject attempts to read another user's User record, so that account identity cannot be enumerated.

**Source:** RBAC negative case: cross-user User read; AUTH-02, AUTH-03

- **Given** an authenticated user, **when** the requested User ID differs from the JWT subject, **then** access is denied.
- **Given** a guessed or enumerated User ID, **when** access is denied, **then** no account fields are exposed.

### US-29 — Deny unassigned Manager reads

As the system, I want to reject a Manager's access to unassigned Request detail, so that assignment scope is enforced.

**Source:** RBAC negative case: Manager reads unassigned Request; WF-03

- **Given** a Manager, **when** `request.manager_id` does not equal their user ID, **then** Request detail and attached resources are denied.
- **Given** the Manager's team dashboard, **when** aggregate team activity is shown, **then** it does not grant detail access to an unassigned Request.

### US-30 — Deny invalid or repeated Manager decisions

As the system, I want to reject ineligible or repeated Manager decisions, so that each Request follows the fixed workflow once.

**Source:** RBAC negative case: wrong-state/repeated Manager decision; WF-03, WF-07

- **Given** an assigned Request outside `manager_review`, **when** the Manager attempts approval or rejection, **then** the transition is rejected.
- **Given** an already decided Request, **when** the Manager repeats or reverses the decision, **then** the stale transition is rejected without mutation.

### US-31 — Deny premature Finance reads

As the system, I want to reject Finance access before Manager approval, so that unapproved Requests remain outside Finance scope.

**Source:** RBAC negative case: Finance reads before approval; WF-05, WF-06, DASH-03

- **Given** a Request not in `finance_review` or `processed`, **when** Finance requests its detail or attachments, **then** access is denied.
- **Given** the Finance dashboard, **when** it loads, **then** the unapproved Request is absent.

### US-32 — Deny invalid Finance processing

As the system, I want to reject invalid Finance processing, so that processed Requests contain a complete valid outcome.

**Source:** RBAC negative case: invalid Finance processing; WF-06, WF-07

- **Given** a Request outside `finance_review`, **when** Finance attempts processing, **then** the transition is rejected.
- **Given** a missing timestamp, missing Finance actor identity, or `payment_status` outside `paid`/`scheduled`, **when** processing is attempted, **then** no processing state is committed.

### US-33 — Deny ineligible Employee updates

As the system, I want to reject Employee updates outside correction states, so that reviewed data cannot be changed out of order.

**Source:** RBAC negative case: Employee updates outside permitted states; AI-06, WF-07

- **Given** an Employee's Request outside `employee_review` or `rejected`, **when** Request or extraction update is attempted, **then** the change is denied.
- **Given** another Employee's Request in an otherwise editable state, **when** update is attempted, **then** ownership enforcement denies it.

### US-34 — Deny product deletion

As the system, I want to reject deletion of MVP resources, so that no role receives an unspecified destructive permission.

**Source:** RBAC negative case: DELETE any resource; AUTH-03, AUD-03

- **Given** any role, **when** deletion of a Request, Document, Notification, User, Extracted Invoice Data, or Audit Log is attempted through the product, **then** access is denied.
- **Given** the denial, **when** persistence is checked, **then** the targeted resource remains unchanged.

### US-35 — Deny direct audit mutation

As the system, I want to reject direct audit creation or modification, so that audit history remains system-controlled and immutable.

**Source:** RBAC negative case: direct Audit Log mutation; AUD-01, AUD-03

- **Given** any role, **when** direct creation, update, or deletion of an audit event is attempted, **then** access is denied.
- **Given** an authorized material workflow action, **when** it succeeds, **then** only the system may append its corresponding event.

### US-36 — Deny tampered or unsupported role claims

As the system, I want to reject invalid role claims, so that a JWT cannot be altered to gain authority.

**Source:** RBAC negative case: changed/unsupported role claim; AUTH-02, AUTH-03

- **Given** a JWT with a changed claim or invalid signature, **when** protected access is attempted, **then** the token is rejected.
- **Given** a validly signed JWT with a role outside Employee, Manager, or Finance, **when** protected access is attempted, **then** deny-by-default authorization rejects it.

## 8. Functional Requirement Coverage

| Requirement ID | Covering story ID(s) |
|---|---|
| AUTH-01 | US-01 |
| AUTH-02 | US-02, US-28, US-36 |
| AUTH-03 | US-02, US-21, US-22, US-27, US-28, US-34, US-36 |
| AUTH-04 | US-01 |
| AUTH-05 | US-02 |
| DOC-01 | US-03 |
| DOC-02 | US-03 |
| DOC-03 | US-03 |
| DOC-04 | US-04 |
| DOC-05 | US-03 |
| DOC-06 | US-04, US-27 |
| AI-01 | US-05 |
| AI-02 | US-05 |
| AI-03 | US-05 |
| AI-04 | US-06 |
| AI-05 | US-07 |
| AI-06 | US-09, US-23, US-26, US-33 |
| AI-07 | US-08 |
| WF-01 | US-10 |
| WF-02 | US-09, US-10 |
| WF-03 | US-11, US-12, US-13, US-21, US-23, US-25, US-29, US-30 |
| WF-04 | US-13 |
| WF-05 | US-12, US-31 |
| WF-06 | US-14, US-22, US-24, US-25, US-31, US-32 |
| WF-07 | US-10, US-12, US-13, US-14, US-30, US-32, US-33 |
| WF-08 | US-11, US-14, US-20 |
| DASH-01 | US-15 |
| DASH-02 | US-16 |
| DASH-03 | US-17, US-31 |
| DASH-04 | US-15, US-16, US-17 |
| DASH-05 | US-15, US-16, US-17 |
| AUD-01 | US-19, US-35 |
| AUD-02 | US-19 |
| AUD-03 | US-20, US-34, US-35 |
| AUD-04 | US-19 |
| NOTIF-01 | US-18 |
| NOTIF-02 | US-18 |
| NOTIF-03 | US-18 |
| NOTIF-04 | US-18, US-27 |

**Coverage gaps:** None.

## 9. Open Questions

1. AI-06 permits correction of fields allowed by the form, but neither source document specifies exactly which extracted fields are editable.
2. DASH-04 requires pagination, status filtering, and deterministic sorting, but does not define page size, default sort field, or sort direction.
3. NOTIF-03 requires “recent” notifications, but does not define the time window or item limit.

These questions require later decisions; no answer is assumed by these stories.
