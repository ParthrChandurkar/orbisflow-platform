# Orbis Flow — Stage 6 Backend Architecture and API Contract

- **Service:** Spring Boot core backend
- **API base path:** `/api/v1`
- **Internal AI base path:** `/internal/v1`
- **Status:** Design contract; no implementation code or migration files
- **Sources:** `docs/PRD.md`, `docs/rbac.md`, `docs/user-stories.md`, `docs/architecture.md`, `docs/db-schema.md`

## 1. Module and Package Structure

Spring Boot uses a **feature-first modular monolith**. The modules are `auth`, `users`, `requests`, `documents`, `dashboards`, `notifications`, `audit`, and `integration.ai`, with `common.security` and `common.errors` limited to cross-cutting concerns. Within each feature, HTTP adapters, application services, domain policy, and persistence adapters remain internally separated.

Feature-first organization keeps a complete business capability together while preserving controller/service/repository boundaries inside it. This is easier for one developer to navigate and test than global layer packages, and it prevents the fixed workflow from being scattered across unrelated folders. It also retains one Spring service and one transaction boundary rather than creating additional services. (Architecture §§1, 5; WF-01 through WF-07; PRD §7 Scalability and Reliability)

Module responsibilities:

| Module | Responsibility | Primary trace |
|---|---|---|
| `auth` | Credential verification, JWT issuance/validation, CSRF token lifecycle | AUTH-01 through AUTH-05 |
| `users` | Read-only current-user identity | RBAC READ User; US-02, US-28 |
| `requests` | Request lifecycle, extraction orchestration, correction, routing, Manager decisions, Finance processing | AI-01 through AI-07, WF-01 through WF-08 |
| `documents` | Validated upload/replacement, private S3 access, signed application links | DOC-01 through DOC-06 |
| `dashboards` | Role-scoped Request projections and Manager team aggregate | DASH-01 through DASH-05 |
| `notifications` | Own-notification reads and mark-read action | NOTIF-01 through NOTIF-04 |
| `audit` | System-only append and authorized chronological reads | AUD-01 through AUD-04 |
| `integration.ai` | Internal FastAPI client and response validation | AI-01, AI-03, AI-07; Architecture §2 |

## 2. Public REST API Surface

### 2.1 Common Conventions and Shapes

All protected endpoints require the `ORBIS_SESSION` JWT cookie. State-changing endpoints additionally require the `X-XSRF-TOKEN` header to equal the readable `XSRF-TOKEN` cookie. JSON requests reject unknown fields. GET endpoints have no request body; their inputs are path and query parameters only. UUID, date, date-time, and integer below mean canonical UUID text, ISO `YYYY-MM-DD`, ISO-8601 UTC timestamp, and signed JSON integer respectively. `decimal` is a base-10 JSON string with an optional leading minus, up to 15 integer digits, and up to four fractional digits; it maps to `BigDecimal` and is never a floating-point number. (AUTH-02 through AUTH-04; Architecture §3; DB Schema §2)

All mutations of an existing Request require `expected_version: int64` greater than or equal to zero. A success returns the new `version`; a mismatch returns `409 VERSION_CONFLICT`. Clients treat version as an opaque monotonic value rather than assuming `+1`, because a composite rejected-resubmission action records two legal transitions. This implements the Stage 5 conditional update. (WF-07; DB Schema §4)

Shared response shapes:

- `UserView`: `id: uuid`, `login_identifier: string`, `role: employee|manager|finance`, `manager_id: uuid|null`.
- `DocumentView`: `id: uuid`, `original_filename: string`, `mime_type: application/pdf|image/jpeg|image/png`, `file_size_bytes: int64`, `created_at: datetime`.
- `LineItemView`: `line_number: integer`, `description: string`, `amount: decimal`.
- `ExtractionView`: `status: pending|succeeded|failed`, `schema_version: string`, `vendor: string|null`, `total_amount: decimal|null`, `invoice_date: date|null`, `line_items: LineItemView[]`, `validation_flags: ValidationFlag[]`, `failure_category: unreadable_document|unsupported_content|ocr_error|timeout|service_unavailable|invalid_response|null`.
- `ValidationFlag`: `code: MISSING_VENDOR|MISSING_TOTAL_AMOUNT|MISSING_INVOICE_DATE|LINE_ITEM_SUM_MISMATCH`, `field: vendor|total_amount|invoice_date|line_items|null`, `message: string`.
- `RequestSummary`: `id: uuid`, `status: request_status`, `version: int64`, `employee_id: uuid`, `manager_id: uuid`, `vendor: string|null`, `total_amount: decimal|null`, `submitted_at: datetime`, `updated_at: datetime`, `latest_required_action: retry_extraction|correct_or_resubmit|manager_review|finance_process|null`.
- `RequestDetail`: all `RequestSummary` fields plus `current_owner_role: employee|manager|finance|null`, `manager_decision: {decision: approved|rejected, decided_by_user_id: uuid, decided_at: datetime, rejection_reason: string|null}|null`, `processing: {payment_status: paid|scheduled, processed_by_user_id: uuid, processed_at: datetime}|null`, `document: DocumentView|null`, and `extracted_data: ExtractionView|null`.
- `Page<T>`: `items: T[]`, `page: integer`, `size: integer`, `total_elements: int64`, `total_pages: integer`, `sort: {field: string, direction: asc|desc}`.

`request_status` is `uploaded_extracting|employee_review|manager_review|rejected|finance_review|processed`. (WF-01; DB Schema §2)

`current_owner_role` is Employee for `employee_review|rejected`, Manager for `manager_review`, Finance for `finance_review`, and null for `uploaded_extracting|processed`. `latest_required_action` follows the same mapping, except a failed extraction produces `retry_extraction` for the Employee and a pending extraction produces null. (WF-01, WF-02, WF-06, DASH-01)

### 2.2 Auth and Users

#### `POST /api/v1/auth/login`

- **Auth:** Public; CSRF-exempt because no authenticated cookie exists yet.
- **Body:** `login_identifier: string`, `password: string`.
- **Success:** `200 OK` with `UserView`; sets an eight-hour, host-only `ORBIS_SESSION` cookie (`Path=/api`, `Secure`, `HttpOnly`, `SameSite=Strict`) and an eight-hour readable `XSRF-TOKEN` cookie (`Path=/`, `Secure`, `SameSite=Strict`, configured shared app/API parent domain so Next.js can read it).
- **Errors:** `400 INVALID_REQUEST` for malformed/missing fields; `401 INVALID_CREDENTIALS` for any credential failure, without identifying which value failed.
- **Trace:** AUTH-01, AUTH-04, AUTH-05; US-01, US-02.

#### `POST /api/v1/auth/logout`

- **Auth:** Any valid Employee, Manager, or Finance JWT; CSRF required (state-changing).
- **Request:** No body.
- **Success:** `200 OK` with empty body; response clears the `ORBIS_SESSION` cookie (Set-Cookie with Max-Age=0 / expired) and the `XSRF-TOKEN` cookie the same way. No server-side session store exists to invalidate, so a JWT already copied out of the cookie remains technically valid until its natural expiry — this is a known MVP tradeoff, acceptable since HttpOnly prevents JS-based exfiltration in the first place.
- **Errors:** `401 AUTH_REQUIRED`; `403 CSRF_INVALID`.
- **Trace:** AUTH-01, AUTH-04.

There is no refresh-token endpoint. Expiry requires sign-in again; this avoids adding an unspecified token lifecycle.

#### `GET /api/v1/users/me`

- **Auth:** Any valid Employee, Manager, or Finance JWT; JWT subject is the only lookup key.
- **Request:** No body.
- **Success:** `200 OK` with `UserView`.
- **Errors:** `401 AUTH_REQUIRED`.
- **Trace:** AUTH-02, AUTH-03; RBAC READ User; US-02, US-28.

No `/users/{user_id}` route exists. Guessed or enumerated cross-user paths return `404 RESOURCE_NOT_FOUND`.

### 2.3 Requests and Workflow

#### `POST /api/v1/requests`

- **Auth:** Employee only; CSRF required.
- **Request:** `multipart/form-data` with `file: binary`. Accepted types are PDF, JPG, PNG; maximum 10 MB.
- **Success:** `202 Accepted` with `RequestSummary` in `uploaded_extracting`; the current document is stored and asynchronous extraction is started once.
- **Errors:** `400 INVALID_FILE` for empty/corrupt content; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED` for Manager/Finance; `403 CSRF_INVALID`; `409 MANAGER_NOT_ASSIGNED`; `413 FILE_TOO_LARGE`; `415 UNSUPPORTED_MEDIA_TYPE`.
- **Trace:** DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, WF-02; RBAC Employee CREATE Request/Document; US-03, US-04, US-10.

#### `GET /api/v1/requests/{request_id}`

- **Auth/scope:** Employee owner; assigned Manager only after the Request has reached `manager_review`; Finance only in `finance_review` or `processed`.
- **Success:** `200 OK` with `RequestDetail`.
- **Errors:** `401 AUTH_REQUIRED`; `404 RESOURCE_NOT_FOUND` for absent or out-of-scope Request.
- **Trace:** AI-04, WF-03, WF-06, WF-08; RBAC READ Request; US-06, US-11, US-14, US-27, US-29, US-31.

An assigned Manager retains detail access after decision while status is `rejected`, `finance_review`, or `processed`; a snapshotted Manager cannot read pre-routing `uploaded_extracting` or `employee_review` detail. (WF-03; DB Schema §3.2)

#### `GET /api/v1/requests/{request_id}/extracted-data`

- **Auth/scope:** Same scope as Request detail.
- **Success:** `200 OK` with `ExtractionView`, including `pending` or `failed` state when applicable.
- **Errors:** `401 AUTH_REQUIRED`; `404 RESOURCE_NOT_FOUND`.
- **Trace:** AI-03, AI-04; RBAC READ Extracted Invoice Data; US-05, US-06, US-11, US-14.

#### `PATCH /api/v1/requests/{request_id}/extracted-data`

- **Auth/scope:** Employee owner only, status `employee_review` or `rejected`; CSRF required.
- **Body:** `expected_version: int64` plus at least one of `vendor: string|null`, `total_amount: decimal|null`, `invoice_date: date|null`, `line_items: [{description: string, amount: decimal}]`. When supplied, `line_items` replaces the full ordered collection; an empty array means no line items.
- **Success:** `200 OK` with `{request_id: uuid, version: int64, extracted_data: ExtractionView}`. Validation flags are recalculated and a field-correction audit event is appended; workflow status does not change.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED` for wrong role; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND` for absent/non-owned Request; `409 STATE_CONFLICT` outside editable states; `409 VERSION_CONFLICT`.
- **Trace:** AI-05, AI-06, WF-07, AUD-01; RBAC Employee UPDATE; US-09, US-23, US-26, US-33.

#### `POST /api/v1/requests/{request_id}/resubmit`

- **Auth/scope:** Employee owner only, status `employee_review` or `rejected`; CSRF required.
- **Body:** `expected_version: int64`.
- **Success:** `200 OK` with updated `RequestDetail` in `manager_review`. Validation runs first and routing uses the snapshotted `requests.manager_id`.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND`; `409 STATE_CONFLICT`; `409 VERSION_CONFLICT`; `422 VALIDATION_FAILED` when required fields or line-item total rules fail.
- **Trace:** AI-05, AI-06, WF-01, WF-02, WF-07; RBAC Employee UPDATE; US-07, US-09, US-10, US-23, US-33.

When resubmission starts in `rejected`, the service applies the legal `rejected -> employee_review` reset and then `employee_review -> manager_review` routing as two ordered transitions in one transaction, with an audit event and version increment for each transition. Manager-decision fields are cleared during the first transition; rejection history remains in `audit_log`. (DB Schema §§3.2, 4)

#### `POST /api/v1/requests/{request_id}/extraction/retry`

- **Auth/scope:** Employee owner only; Request status `uploaded_extracting` and extraction status `failed`; CSRF required.
- **Body:** `expected_version: int64`.
- **Success:** `202 Accepted` with updated `RequestSummary`; extraction status becomes `pending` and one background attempt starts.
- **Errors:** `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND`; `409 EXTRACTION_IN_PROGRESS` for pending work; `409 STATE_CONFLICT`; `409 VERSION_CONFLICT`.
- **Trace:** AI-07, WF-07; Architecture §2; US-08.

#### `POST /api/v1/requests/{request_id}/approve`

- **Auth/scope:** Assigned Manager only; the Request must be post-routing (`manager_review|rejected|finance_review|processed`), and approval is allowed only in `manager_review`; CSRF required.
- **Body:** `expected_version: int64`.
- **Success:** `200 OK` with `RequestDetail` in `finance_review`; approval fields, state, audit event, Finance notification, and version commit atomically.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED` for Employee/Finance; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND` for absent, unassigned, or pre-routing Request; `409 STATE_CONFLICT` for an assigned post-routing Request already decided; `409 VERSION_CONFLICT`.
- **Trace:** WF-03, WF-05, WF-07, AUD-01, NOTIF-02; RBAC Manager APPROVE; US-12, US-21, US-25, US-29, US-30.

#### `POST /api/v1/requests/{request_id}/reject`

- **Auth/scope:** Assigned Manager only; the Request must be post-routing (`manager_review|rejected|finance_review|processed`), and rejection is allowed only in `manager_review`; CSRF required.
- **Body:** `expected_version: int64`, `reason: string` (trimmed, non-empty).
- **Success:** `200 OK` with `RequestDetail` in `rejected`; decision/reason, audit event, Employee notification, and version commit atomically.
- **Errors:** `400 INVALID_REQUEST` for missing/blank reason; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND` for absent, unassigned, or pre-routing Request; `409 STATE_CONFLICT` for an assigned post-routing Request already decided; `409 VERSION_CONFLICT`.
- **Trace:** WF-03, WF-04, WF-07, AUD-01, NOTIF-02; RBAC Manager REJECT; US-13, US-21, US-25, US-29, US-30.

#### `POST /api/v1/requests/{request_id}/process`

- **Auth/scope:** Finance only; the Request must be Finance-visible (`finance_review|processed`), and processing is allowed only in `finance_review`; CSRF required.
- **Body:** `expected_version: int64`, `payment_status: paid|scheduled`. Actor identity and timestamp are server-derived; unknown fields such as partial-payment/installment data are rejected.
- **Success:** `200 OK` with `RequestDetail` in `processed`; payment status, JWT-subject actor, server timestamp, audit event, Employee notification, and version commit atomically.
- **Errors:** `400 INVALID_REQUEST` for missing/invalid/unknown fields; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED` for Employee/Manager; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND` for absent/out-of-Finance-scope Request; `409 STATE_CONFLICT`; `409 VERSION_CONFLICT`.
- **Trace:** WF-06, WF-07, AUD-01, NOTIF-02; RBAC Finance PROCESS; US-14, US-22, US-24, US-25, US-32.

### 2.4 Documents

#### `POST /api/v1/requests/{request_id}/documents`

- **Auth/scope:** Employee owner only, status `employee_review` or `rejected`; CSRF required.
- **Request:** `multipart/form-data` with `expected_version: int64`, `file: binary`; same type/size rules as initial upload.
- **Success:** `202 Accepted` with updated `RequestSummary` in `uploaded_extracting`; the new file becomes current, previous document remains historical, extraction becomes `pending`, and one attempt starts. From `rejected`, the service first applies and audits `rejected -> employee_review`, including the required decision-field reset, then applies `employee_review -> uploaded_extracting` in the same transaction.
- **Errors:** `400 INVALID_FILE`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND`; `409 STATE_CONFLICT`; `409 VERSION_CONFLICT`; `413 FILE_TOO_LARGE`; `415 UNSUPPORTED_MEDIA_TYPE`.
- **Trace:** DOC-02 through DOC-05, AI-07, WF-07; RBAC Employee CREATE Document; US-03, US-04, US-08, US-23, US-33.

#### `GET /api/v1/documents/{document_id}/access-link`

- **Auth/scope:** Parent Request must satisfy Employee-owner, assigned-Manager/post-routing, or Finance-state scope.
- **Success:** `200 OK` with `url: string`, `expires_at: datetime` and `Cache-Control: no-store`. `url` targets `/api/v1/documents/{document_id}/content?token=...`; its token is HMAC-signed with an environment-managed key, contains JWT subject/document ID/expiry, and expires after 60 seconds.
- **Errors:** `401 AUTH_REQUIRED`; `404 RESOURCE_NOT_FOUND`.
- **Trace:** DOC-06, AI-04; RBAC READ Document; Architecture §4; US-04, US-06, US-27, US-29, US-31.

#### `GET /api/v1/documents/{document_id}/content?token={signed_token}`

- **Auth/scope:** Valid JWT, valid unexpired signed token bound to the JWT subject/document, and current parent-Request scope.
- **Success:** `200 OK` streaming bytes with stored `Content-Type`, safe `Content-Disposition`, and no-store caching headers.
- **Errors:** `401 AUTH_REQUIRED`; `403 SIGNED_LINK_INVALID` for malformed, expired, or subject-mismatched token; `404 RESOURCE_NOT_FOUND` for missing/out-of-scope document.
- **Trace:** DOC-06; RBAC READ Document; Architecture §4; US-04, US-27.

The `token` query value is redacted from application/access logs.

### 2.5 Dashboards

All dashboard list responses use `Page<RequestSummary>` sourced from PostgreSQL/scoped Redis cache. Cache keys include role, user, filter, page, size, and sort; cache failure falls back to PostgreSQL. (DASH-04; Architecture §5)

#### `GET /api/v1/dashboards/employee/requests`

- **Auth/scope:** Employee only; `requests.employee_id = JWT subject`.
- **Query:** `status?: request_status`, `page?: integer`, `size?: integer`, `sort?: employee-sort`, `direction?: asc|desc`.
- **Success:** `200 OK` with `Page<RequestSummary>`.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`.
- **Trace:** DASH-01, DASH-04, DASH-05; RBAC Employee READ Request; US-15.

#### `GET /api/v1/dashboards/manager/requests`

- **Auth/scope:** Manager only; `requests.manager_id = JWT subject`; detailed rows are limited to `manager_review|rejected|finance_review|processed`.
- **Query:** `status?: manager_review|rejected|finance_review|processed` (default `manager_review`), `page?`, `size?`, `sort?: manager-sort`, `direction?`.
- **Success:** `200 OK` with `Page<RequestSummary>`.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`.
- **Trace:** WF-03, DASH-02, DASH-04, DASH-05; RBAC Manager READ Request; US-16, US-29.

#### `GET /api/v1/dashboards/manager/team-activity`

- **Auth/scope:** Manager only; aggregate over Employees whose current `users.manager_id` equals the JWT subject; no Request identifiers are returned.
- **Success:** `200 OK` with `pending: int64`, `approved: int64`, `rejected: int64`. Pending is `manager_review`; approved is `finance_review|processed`; rejected is `rejected`.
- **Errors:** `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`.
- **Trace:** DASH-02; RBAC Manager aggregate team activity; US-16, US-29.

#### `GET /api/v1/dashboards/finance/requests`

- **Auth/scope:** Finance only; status limited to `finance_review|processed`.
- **Query:** `status?: finance_review|processed` (default `finance_review`), `page?`, `size?`, `sort?: finance-sort`, `direction?`.
- **Success:** `200 OK` with `Page<RequestSummary>`.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `403 ACCESS_DENIED`.
- **Trace:** DASH-03, DASH-04, DASH-05; RBAC Finance READ Request; US-17, US-31.

Loading indicators are frontend behavior. Empty list responses are `200` with `items: []`; backend failures use the Section 3 envelope, allowing DASH-05 UI states.

### 2.6 Notifications

#### `GET /api/v1/notifications`

- **Auth/scope:** Any valid role; only `notifications.user_id = JWT subject`.
- **Query:** `view?: recent|unread` (default `recent`), `page?: integer`, `size?: integer` (default `20`, maximum `50`).
- **Success:** `200 OK` with `Page<NotificationView>`, where `NotificationView` is `id: uuid`, `request_id: uuid`, `type: employee_correction|employee_rejection|manager_assignment|finance_assignment|processed`, `read_at: datetime|null`, `created_at: datetime`. Results sort by `created_at DESC, id DESC`.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`.
- **Trace:** NOTIF-01 through NOTIF-04; RBAC READ Notification; US-18, US-27.

#### `PATCH /api/v1/notifications/{notification_id}/read`

- **Auth/scope:** Notification owner only; CSRF required.
- **Request:** No body.
- **Success:** `200 OK` with `NotificationView`; idempotent when already read.
- **Errors:** `401 AUTH_REQUIRED`; `403 CSRF_INVALID`; `404 RESOURCE_NOT_FOUND` for absent/other user's notification.
- **Trace:** NOTIF-03, NOTIF-04; RBAC UPDATE Notification; US-18, US-27.

### 2.7 Audit

#### `GET /api/v1/requests/{request_id}/audit`

- **Auth/scope:** Same owner/assigned-Manager/post-routing/Finance-state scope as Request detail.
- **Query:** `page?: integer`, `size?: integer`; chronological sort is fixed.
- **Success:** `200 OK` with `Page<AuditEventView>`, where `AuditEventView` is `id: uuid`, `event_type: string`, `actor_kind: user|system`, `actor_user_id: uuid|null`, `previous_status: request_status|null`, `resulting_status: request_status|null`, `context: object`, `created_at: datetime`.
- **Errors:** `400 INVALID_REQUEST`; `401 AUTH_REQUIRED`; `404 RESOURCE_NOT_FOUND`.
- **Trace:** AUD-01, AUD-02, AUD-03, AUD-04, WF-08; RBAC READ Audit Log; US-19, US-20, US-35.

No public Audit Log create/update/delete endpoint exists. Authorized workflow services append audit events inside their business transaction. (AUD-01, AUD-03; DB Schema §5)

### 2.8 Denial and Negative-Case Status Map

This table is normative. A `404` intentionally makes missing and out-of-scope identifiers indistinguishable.

| Story | RBAC Section 4 denial case | Affected route/action | Required result |
|---|---|---|---|
| US-21 | Employee approves/rejects | `POST .../approve`, `POST .../reject` | `403 ACCESS_DENIED`; no lookup-dependent detail or mutation |
| US-22 | Employee processes | `POST .../process` | `403 ACCESS_DENIED` |
| US-23 | Manager creates/edits/resubmits Employee Request | Create Request, patch extraction, resubmit, replace document | `403 ACCESS_DENIED` |
| US-24 | Manager processes or sets payment status | `POST .../process` | `403 ACCESS_DENIED` |
| US-25 | Finance approves/rejects | Decision routes | `403 ACCESS_DENIED` |
| US-26 | Finance edits extraction | `PATCH .../extracted-data` | `403 ACCESS_DENIED` |
| US-27 | Guessed out-of-scope Request/Document/Notification ID | All scoped reads | `404 RESOURCE_NOT_FOUND` |
| US-28 | Cross-user User read | `/users/{user_id}` | `404 RESOURCE_NOT_FOUND`; route is not exposed |
| US-29 | Manager reads unassigned Request | Request, extraction, document, audit reads | `404 RESOURCE_NOT_FOUND` |
| US-30 | Manager decides outside `manager_review` or twice | Decision routes on a snapshotted assigned Request | `404 RESOURCE_NOT_FOUND` before routing; `409 STATE_CONFLICT` after a prior decision/in another post-routing state |
| US-31 | Finance reads before Manager approval | Request, extraction, document, audit reads | `404 RESOURCE_NOT_FOUND` |
| US-32 | Finance processes wrong state | Process route after Finance role/scope is verified | `409 STATE_CONFLICT` |
| US-32 | Finance omits/invalidates processing data | Process route | `400 INVALID_REQUEST`; actor/time are server-derived, never client fields |
| US-33 | Employee updates outside editable states | Patch/resubmit/replace after ownership is verified | `409 STATE_CONFLICT` |
| US-33 | Employee updates another user's Request | Same mutation routes | `404 RESOURCE_NOT_FOUND` |
| US-34 | Any role deletes a product resource | Any `/api/v1` `DELETE` matching a resource path | `405 METHOD_NOT_ALLOWED`; no delete handler exists |
| US-35 | Any role directly creates/modifies Audit Log | Audit `POST`, `PATCH`, `PUT`, `DELETE` | `405 METHOD_NOT_ALLOWED` |
| US-36 | Changed signature, expired JWT, or unsupported role claim | Any protected route | `401 AUTH_REQUIRED` |
| Architecture §3 | Missing/mismatched CSRF token | Any state-changing protected route | `403 CSRF_INVALID` |

This map covers US-21 through US-36 and the RBAC negative-case table.

## 3. Error Response Envelope

Every non-streaming error uses:

```json
{
  "error": {
    "code": "STATE_CONFLICT",
    "message": "The request is not in a state that permits this action.",
    "field_errors": [
      {
        "field": "payment_status",
        "code": "INVALID_VALUE",
        "message": "Must be paid or scheduled."
      }
    ]
  },
  "correlation_id": "8f4db4a3-6f14-45b4-9b61-b1e748ea7052"
}
```

`field_errors` is omitted when not applicable. Spring accepts a valid incoming `X-Correlation-ID` or creates a UUID, returns it in the response header, includes it in this envelope, and passes it to FastAPI. Messages are safe for users and never include SQL, stack traces, credentials, JWTs, S3 keys, or out-of-scope resource facts. (AUTH-04, AUD-04; PRD §7 Security)

Canonical HTTP mapping:

| HTTP | Error codes | Meaning |
|---|---|---|
| `400` | `INVALID_REQUEST`, `INVALID_FILE` | Malformed syntax, DTO/parameter failure, blank rejection reason, invalid enum, unknown field, corrupt file |
| `401` | `AUTH_REQUIRED`, `INVALID_CREDENTIALS` | Missing/invalid/expired JWT or failed login |
| `403` | `ACCESS_DENIED`, `CSRF_INVALID`, `SIGNED_LINK_INVALID` | Authenticated but wrong role/action, invalid CSRF, or invalid signed capability |
| `404` | `RESOURCE_NOT_FOUND` | Missing or outside row-level read/mutation scope |
| `405` | `METHOD_NOT_ALLOWED` | Unsupported mutation such as DELETE/direct audit write |
| `409` | `STATE_CONFLICT`, `VERSION_CONFLICT`, `MANAGER_NOT_ASSIGNED`, `EXTRACTION_IN_PROGRESS` | Current persisted state conflicts with a validly shaped action |
| `413` | `FILE_TOO_LARGE` | More than 10 MB |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Not PDF/JPG/PNG |
| `422` | `VALIDATION_FAILED` | Correctly shaped resubmission remains incomplete/flagged |
| `500` | `INTERNAL_ERROR` | Unexpected server failure; details logged only with correlation ID |

## 4. Validation Approach

Validation is split deliberately:

| Layer | Responsibilities | Failure |
|---|---|---|
| HTTP/DTO boundary | JSON/multipart syntax; required body fields; UUID/date/decimal/enum types; query bounds; unknown-field rejection; `expected_version`; nonblank rejection reason; upload declared MIME and byte count | `400`, `413`, or `415` |
| Application service | JWT-derived actor; role and row scope; file signature/corruption; Manager assignment; editable fields; required invoice fields; exact line-item sum; fixed transition; current version; processing invariants | `403`, `404`, `409`, or `422` by Section 2.8 |
| Persistence | FK/UNIQUE/CHECK/ENUM constraints; conditional `status + version` update; all-or-nothing business/audit transaction | Conflict translated to `409`; unexpected constraint defect to `500` |

File acceptance requires both allowed MIME and matching PDF/JPEG/PNG signature; filename extension alone is never trusted. The frontend may pre-check, but Spring repeats all checks before S3 storage. (DOC-02, DOC-03; US-03)

`total_amount` and line-item `amount` accept at most 15 integer digits and four fractional digits, matching `numeric(19,4)`; excess precision is rejected rather than rounded. Vendor and line-item descriptions are trimmed, and values required at resubmission must remain nonblank. (AI-02, AI-05; DB Schema §§3.4, 3.5)

FastAPI output is untrusted integration input. Spring validates correlation/request ID, schema version, enum values, decimal/date parseability, line ordering, and response invariants before persistence. Invalid output becomes a recoverable `invalid_response` extraction failure, not a workflow transition. (AI-03, AI-07; Architecture §2)

## 5. Spring Security Configuration Shape

The security pipeline composes controls in this order:

1. Correlation ID is accepted/created.
2. The JWT cookie filter validates HS256 signature, eight-hour expiry, subject, and the exact role allow-list.
3. Double-submit CSRF is checked on authenticated state-changing requests and bound to the validated JWT subject.
4. Method-level security performs coarse role/action gating (for example, only Manager may reach a decision operation).
5. A reusable service-layer authorization policy applies ownership, snapshotted assignment, allowed read-state, and current workflow-state checks using scoped repository queries.
6. The application service performs the conditional state/version update and audit append in one transaction.

Ownership/assignment is **not** implemented as controller logic or repository calls hidden inside authorization expressions. Coarse role checks remain declarative; row scope and state checks live in the application-service transaction where race conditions can be controlled and tested. Scoped reads query by both ID and permitted owner/manager/state and therefore naturally return `404`; mutations gate the role first, then distinguish out-of-scope `404` from in-scope wrong-state `409`. (AUTH-02, AUTH-03; RBAC §3; WF-07; Architecture §5)

JWTs are signed with HS256 using an environment-managed secret of at least 256 bits. Claims are standard `sub`, `iat`, and `exp` plus one custom `role` value; no client-supplied claim is trusted. Passwords use BCrypt with work factor 12. The CSRF value contains the JWT subject plus a random 256-bit nonce and is HMAC-signed with a separate environment-managed secret; Spring verifies that signature/binding and compares cookie/header values in constant time, without a server-side session record. (AUTH-01 through AUTH-05; Architecture §3; PRD §7 Security)

Credentialed browser requests are accepted only from the configured Next.js origin; wildcard origins are forbidden. FastAPI receives no end-user JWT and remains reachable only from Spring on the internal network. (AUTH-02, AUTH-03; Architecture §§1, 3)

## 6. Final Internal FastAPI Contract

### `POST /internal/v1/extractions`

- **Caller:** Spring Boot only; FastAPI is not publicly routed and does not validate end-user JWTs.
- **Required header:** `X-Correlation-ID: uuid`. FastAPI echoes it in every response.
- **Content type:** `multipart/form-data`.
- **Parts:** `request_id: uuid`, `schema_version: string` (MVP value `1`), `mime_type: application/pdf|image/jpeg|image/png`, `file: binary` (1 byte through 10 MB).

Completed response (`200 OK`):

```text
request_id: uuid
schema_version: string
status: succeeded | failed
vendor: string | null
total_amount: decimal | null
invoice_date: date | null
line_items: [
  { line_number: integer, description: string, amount: decimal }
]
validation_flags: [
  {
    code: MISSING_VENDOR | MISSING_TOTAL_AMOUNT | MISSING_INVOICE_DATE,
    field: vendor | total_amount | invoice_date,
    message: string
  }
]
failure_category: unreadable_document | unsupported_content | ocr_error | null
```

Internal `decimal` fields use the same base-10 JSON string encoding as the public API. For `succeeded`, `failure_category` is null; missing fields remain null and are flagged rather than treated as transport failure. For `failed`, extracted fields/line items are empty and `failure_category` is required. Spring applies the authoritative AI-05 required-field and line-sum business rules after response validation.

Transport responses are `400` invalid metadata, `413` too large, `415` unsupported MIME/signature, and `500` unexpected AI-service failure. Their body is `code: string`, `message: string`, `correlation_id: uuid`; Spring never forwards the internal message directly to users.

Spring client configuration is a 2-second connection timeout and 60-second response timeout. There are **zero automatic retries and no automatic backoff**: replaying a timed-out extraction could duplicate expensive work because FastAPI has no durable idempotency store. Spring persists `timeout`, `service_unavailable`, or `invalid_response` as a recoverable failure, and only the Employee retry endpoint starts another attempt after a state/version check. (AI-01, AI-02, AI-03, AI-05, AI-07; US-05, US-08; Architecture §2)

After a validated `succeeded` response, one Spring transaction replaces extracted fields/line items, recalculates validation flags, appends extraction/validation/routing audit events, and routes valid data to `manager_review` or flagged data to `employee_review`; the next actor receives an in-app notification. A `failed` response or Spring-derived failure leaves workflow status `uploaded_extracting`, persists extraction status `failed`, and exposes retry without creating a second Request. (AI-04 through AI-07, WF-02, AUD-01, NOTIF-01, NOTIF-02; Architecture §§2, 5)

## 7. Pagination and Sorting Defaults

Common list rules are `page=0`, `size=20`, minimum size `1`, maximum size `100`. Invalid page/size/sort parameters return `400 INVALID_REQUEST`. Ordering always adds `id` in the same direction as a deterministic tie-breaker. (DASH-04; US-15 through US-17; DB Schema §6)

| Dashboard | Default filter | Default order | Allowed sort fields |
|---|---|---|---|
| Employee | All owned statuses | `submitted_at DESC, id DESC` | `submitted_at`, `updated_at`, `total_amount`, `status` |
| Manager | `manager_review` | `updated_at ASC, id ASC` (oldest routed action first) | `submitted_at`, `updated_at`, `total_amount`, `status` |
| Finance queue | `finance_review` | `updated_at ASC, id ASC` (oldest awaiting first) | `updated_at`, `total_amount`, `status` |
| Finance recent (`status=processed`) | `processed` | `processed_at DESC, id DESC` | `processed_at`, `updated_at`, `total_amount` |

An explicitly supplied allowed direction overrides the default. Status filters remain limited by each role's RBAC scope. Manager team activity is an aggregate and is not paginated. Audit history defaults to `page=0`, `size=50`, maximum `100`, fixed `created_at ASC, id ASC`. (DASH-01 through DASH-04; AUD-03; RBAC §2)

Nullable sort values use `NULLS LAST` in both directions. Status sorting uses the WF-01 order shown in Section 2.1 for ascending order and its reverse for descending order.

## 8. Employee-Editable Extracted Fields

Employees may correct exactly:

- `vendor`
- `total_amount`
- `invoice_date`
- the complete ordered `line_items` collection (`description`, `amount`)

They may not edit extraction status, schema version, validation flags, failure category, Request owner/Manager, workflow status, Manager decision, audit events, or Finance fields. PATCH may temporarily persist missing or inconsistent editable values so the Employee can correct in steps; validation flags update after each patch and the Request remains in its editable state. (AI-05, AI-06; RBAC §2; US-09)

When `line_items` is supplied, array order becomes one-based `line_number`; every item requires a nonblank description and valid amount. On resubmission, `vendor`, `total_amount`, and `invoice_date` are required. If line items are non-empty, their `BigDecimal` amounts—normalized to the database's four-decimal scale—must sum **exactly** to `total_amount`; no tolerance is applied. An empty line-item collection skips only the sum check. Failure returns `422 VALIDATION_FAILED` and does not route the Request. (AI-02, AI-05, WF-02; DB Schema §§3.4, 3.5)

## 9. Notification “Recent” Definition

“Recent” means the **50 newest notifications for the current user**, ordered by `created_at DESC, id DESC`, regardless of age. The recent dataset is capped at 50; normal page size is 20, so pages beyond those 50 return empty. `view=unread` is not capped to 50 and paginates all unread notifications with the same ordering. (NOTIF-03; US-18; DB Schema §6)

Notifications remain persisted indefinitely for the MVP unless a later retention decision changes the policy; the 50-item rule is display/query scope, not deletion.

## 10. Story and Permission Traceability

| Coverage | Endpoint or rule |
|---|---|
| US-01–US-02 | Login, logout, current user, JWT/CSRF/security pipeline |
| US-03–US-04 | Create Request, replacement Document, access link/content |
| US-05–US-08 | Internal extraction, extracted-data read, validation, retry |
| US-09–US-10 | Correction, resubmission, fixed routing/state rules |
| US-11–US-14 | Request detail, approve, reject, process |
| US-15–US-17 | Three role-scoped dashboard APIs and Manager aggregate |
| US-18 | Notification list and mark-read |
| US-19 | System-only audit append on each material workflow endpoint |
| US-20 | Authorized audit read |
| US-21–US-36 | Section 2.8 normative denial map |
| RBAC Request permissions | Create/read/update/approve/reject/process routes |
| RBAC Document permissions | Initial/replacement upload and scoped access routes |
| RBAC Extracted Data permissions | Scoped read and Employee patch |
| RBAC User permission | `/users/me` only |
| RBAC Notification permissions | Own list and mark-read |
| RBAC Audit permission | Scoped read; no public mutation route |

This stage adds no configurable workflow, new role, public AI endpoint, direct S3 credential flow, or product DELETE operation.
