# Orbis Flow — RBAC Permission Model

- **Stage:** 2 — RBAC
- **Source of truth:** `docs/PRD.md`, Sections 4, 6, and 7
- **Roles:** Employee, Manager, Finance

Protected operations require a valid JWT and server-side authorization. Actions must pass role, ownership/assignment, and workflow-state checks. Failure is deny-by-default; frontend visibility is not authorization.

## 1. Resource List

| Resource | Definition |
|---|---|
| Request | Invoice submission and workflow record, including status, Employee owner, assigned Manager, Finance processing fields, and dashboard-visible metadata. |
| Document | Original or replacement PDF/JPG/PNG invoice file stored in S3 and attached to a Request. |
| Extracted Invoice Data | OCR output for a Request: vendor, total amount, invoice date, line items, extraction status, and validation flags. |
| User | Authenticated account, role, and the Employee's `manager_id` assignment. |
| Notification | In-app notification addressed to one user, including read state and authorized Request link. |
| Audit Log | Immutable event history attached to a Request. |

Extracted Invoice Data is separate because the PRD permits field-level correction and review. Line items remain part of that resource. Dashboards are filtered Request projections, not independent resources. `payment_status` is part of Request processing.

## 2. Permission Matrix

“Own” means `request.employee_id == current_user.id`. “Assigned” means `request.manager_id == current_user.id`. No cell grants unscoped access.

| Resource | Employee | Manager | Finance |
|---|---|---|---|
| Request | **CREATE** own; **READ** own; **UPDATE** own only in `employee_review` or `rejected` for correction/resubmission | **READ** assigned; **APPROVE/REJECT** assigned only in `manager_review`; **READ** aggregate team activity only for Employees whose `manager_id` is the current Manager | **READ** only in `finance_review` or `processed`; **PROCESS** only in `finance_review` |
| Document | **CREATE** for own Request; **READ** only own; create replacement only in `employee_review` or `rejected` | **READ** only when attached Request is assigned | **READ** only when attached Request is in `finance_review` or `processed` |
| Extracted Invoice Data | **READ** only own; **UPDATE** only own in `employee_review` or `rejected` | **READ** only for assigned Request | **READ** only for Request in `finance_review` or `processed` |
| User | **READ** own account identity and role | **READ** own account identity and role | **READ** own account identity and role |
| Notification | **READ/UPDATE** own only; UPDATE is limited to marking read | **READ/UPDATE** own only; UPDATE is limited to marking read | **READ/UPDATE** own only; UPDATE is limited to marking read |
| Audit Log | **READ** events for own Request | **READ** events for assigned Request | **READ** events for Request in `finance_review` or `processed` |

No role can directly **CREATE**, **UPDATE**, or **DELETE** Audit Log events. The system appends them as a consequence of authorized actions. No MVP resource exposes role-authorized **DELETE**.

## 3. Enforcement Point Mapping

“JWT” means authenticated user ID plus exact role claim. All rows include JWT validation and server-side role enforcement under AUTH-02/AUTH-03.

| Permission | Required enforcement | PRD requirements |
|---|---|---|
| Employee CREATE Request | JWT role = Employee; created `employee_id` forced to current user | DOC-01, AUTH-03 |
| Employee READ/UPDATE Request | Employee role; ownership check; UPDATE state in `employee_review` or `rejected`; requested transition valid | DASH-01, AI-06, WF-02, WF-04, WF-07, WF-08 |
| Manager READ Request | Manager role; full detail requires `request.manager_id == current_user.id`; team dashboard permits only aggregates where `employee.manager_id == current_user.id` | WF-02, WF-03, DASH-02, WF-08 |
| Manager APPROVE/REJECT | Manager role; assignment check; state = `manager_review`; reject reason required; one decision only | WF-03, WF-04, WF-05, WF-07 |
| Finance READ Request | Finance role; state in `finance_review`, `processed` | DASH-03, WF-08 |
| Finance PROCESS Request | Finance role; state = `finance_review`; atomically set timestamp, actor ID, and `payment_status` in `{paid, scheduled}` | WF-06, WF-07 |
| Employee CREATE/READ Document | Employee role; parent Request ownership; replacement state in `employee_review` or `rejected`; format/size validation; authorized file access | DOC-01, DOC-02, DOC-03, DOC-04, DOC-06 |
| Manager/Finance READ Document | Role check plus parent Request scope: assigned Manager, or Finance-visible state; issue only short-lived authorized access | DOC-06, AI-04, WF-03, WF-06, WF-08 |
| Employee READ/UPDATE Extracted Invoice Data | Employee role; parent ownership; UPDATE state in `employee_review` or `rejected`; append correction audit event | AI-04, AI-05, AI-06, WF-07 |
| Manager/Finance READ Extracted Invoice Data | Role check plus parent Request scope: assigned Manager, or state in `finance_review`/`processed` | AI-04, WF-03, WF-06, WF-08 |
| READ User | JWT subject must equal requested user ID; return only fields required for authenticated identity and role | AUTH-02, AUTH-03 |
| READ/UPDATE Notification | `notification.user_id == current_user.id`; UPDATE accepts read-state change only; linked Request is independently authorized | NOTIF-03, NOTIF-04, AUTH-03 |
| READ Audit Log | Apply the same ownership, assignment, or Finance-state rule as the parent Request | AUD-03, WF-08, AUTH-03 |
| System append Audit Log | Trigger only after an authorized material action; no public mutation endpoint; record required event fields | AUD-01, AUD-02, AUD-03, AUD-04 |

## 4. Negative Cases for Integration Tests

Every case below must return an unauthorized/forbidden/conflict response as applicable and must not mutate data or expose protected fields.

| Denied case | Required guard | PRD basis |
|---|---|---|
| Employee approves or rejects any Request, including their own | Role + action check | AUTH-03, WF-03 |
| Employee processes a Request | Role + action check | AUTH-03, WF-06 |
| Manager creates, edits, or resubmits an Employee's Request | Role + ownership/action check | AI-06, WF-03 |
| Manager processes payment or sets `payment_status` | Role + action check | WF-06 |
| Finance approves or rejects the Manager-stage decision | Role + action check | WF-03, WF-06 |
| Finance edits extracted invoice fields | Role + action check | AI-06 |
| A user guesses an ID to read a Request/Document outside permitted scope, or another user's Notification | Ownership/assignment/state check on every direct lookup | DOC-06, NOTIF-04, AUTH-03 |
| Manager reads Request detail not routed to them | `request.manager_id` assignment check | WF-03 |
| Manager approves/rejects an assigned Request outside `manager_review` or decides twice | State + stale-transition check | WF-03, WF-07 |
| Finance reads a Request before Manager approval | State check | WF-05, WF-06, DASH-03 |
| Finance processes a Request outside `finance_review`, or omits actor, timestamp, or valid payment status | State + required-field/enum check | WF-06, WF-07 |
| Employee updates a Request or extraction outside `employee_review`/`rejected` | Ownership + state check | AI-06, WF-07 |
| Any role deletes a Request, Document, Notification, User, extraction, or Audit Log through product APIs | Action deny-list; no DELETE permission | AUTH-03, AUD-03 |
| Any role directly creates or modifies an Audit Log event | No public audit mutation permission | AUD-01, AUD-03 |
| A valid JWT with a changed/unsupported role claim accesses a protected endpoint | Signature validation + exact role allow-list | AUTH-02, AUTH-03 |

## 5. Open Implementation Note

Open: represent role as one enum field on `User`, or as separate role/permission tables.

**Recommendation:** Use one `role` enum field for the MVP; it matches the three fixed roles and avoids introducing configurable authorization outside the PRD scope.
