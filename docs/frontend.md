# Orbis Flow — Stage 7 Frontend Pages and Navigation

- **Framework:** Next.js App Router, TypeScript, Tailwind, shadcn/ui
- **API:** Spring Boot `/api/v1` only
- **Roles:** Employee, Manager, Finance
- **Sources:** `docs/PRD.md`, `docs/rbac.md`, `docs/user-stories.md`, `docs/backend-api.md`

The frontend presents only pages supported by the Stage 6 API. It contains no Admin/settings area, configurable workflow UI, chat/search assistant, direct FastAPI access, direct S3 credentials, or real-time notification channel.

## 1. Page Inventory by Role

### Public and Shared

| App route | Audience | Purpose and API mapping |
|---|---|---|
| `/` | All | Routing-only entry. Calls `GET /api/v1/users/me`; redirects Employee to `/employee/requests`, Manager to `/manager/queue`, Finance to `/finance/queue`, and an unauthenticated user to `/login`. |
| `/login` | Unauthenticated | Credential form using `POST /api/v1/auth/login`. Successful login follows the role-home redirect. An already authenticated user is redirected to their role home. |
| `/notifications` | All authenticated roles | Lists `GET /api/v1/notifications`; marks an item read with `PATCH /api/v1/notifications/{notification_id}/read`; links to the current role's authorized Request detail route. |

Logout is an AppShell action, not a page. It calls `POST /api/v1/auth/logout`, clears local UI state, and redirects to `/login`. User identity, role navigation, and route guards use `GET /api/v1/users/me`. (AUTH-01 through AUTH-04; NOTIF-03, NOTIF-04; US-01, US-02, US-18)

### Employee

| App route | Purpose | Read endpoints | Mutation endpoints |
|---|---|---|---|
| `/employee/requests` | Own Request dashboard with status filter, sort, and pagination | `GET /api/v1/dashboards/employee/requests` | None |
| `/employee/requests/new` | Upload one new invoice and create a Request | None | `POST /api/v1/requests` |
| `/employee/requests/[id]` | View own Request, extraction, current document, and audit history; correct/resubmit, retry failed extraction, or upload a replacement when permitted | `GET /api/v1/requests/{request_id}`; `GET /api/v1/requests/{request_id}/extracted-data`; `GET /api/v1/requests/{request_id}/audit`; `GET /api/v1/documents/{document_id}/access-link`; `GET /api/v1/documents/{document_id}/content?token={signed_token}` | `PATCH /api/v1/requests/{request_id}/extracted-data`; `POST /api/v1/requests/{request_id}/resubmit`; `POST /api/v1/requests/{request_id}/extraction/retry`; `POST /api/v1/requests/{request_id}/documents` |

The detail page shows mutation controls only for states allowed by the API, but the backend remains authoritative and may still return `409` for stale state/version. (DOC-01 through DOC-06, AI-04 through AI-07, WF-02, WF-07; RBAC Employee rows; US-03 through US-10, US-15)

### Manager

| App route | Purpose | Read endpoints | Mutation endpoints |
|---|---|---|---|
| `/manager/queue` | Assigned Request queue plus aggregate team activity | `GET /api/v1/dashboards/manager/requests`; `GET /api/v1/dashboards/manager/team-activity` | None |
| `/manager/requests/[id]` | Review assigned post-routing Request, extraction, document, and history; approve or reject while in `manager_review` | `GET /api/v1/requests/{request_id}`; `GET /api/v1/requests/{request_id}/extracted-data`; `GET /api/v1/requests/{request_id}/audit`; `GET /api/v1/documents/{document_id}/access-link`; `GET /api/v1/documents/{document_id}/content?token={signed_token}` | `POST /api/v1/requests/{request_id}/approve`; `POST /api/v1/requests/{request_id}/reject` |

No Manager page exposes Employee correction, upload, resubmission, or Finance processing actions. (WF-03 through WF-05, DASH-02; RBAC Manager rows; US-11 through US-13, US-16, US-23, US-24)

### Finance

| App route | Purpose | Read endpoints | Mutation endpoints |
|---|---|---|---|
| `/finance/queue` | Manager-approved queue and processed-history view using the status filter | `GET /api/v1/dashboards/finance/requests` | None |
| `/finance/requests/[id]` | Review Finance-visible Request, extraction, document, and history; mark `finance_review` Request processed | `GET /api/v1/requests/{request_id}`; `GET /api/v1/requests/{request_id}/extracted-data`; `GET /api/v1/requests/{request_id}/audit`; `GET /api/v1/documents/{document_id}/access-link`; `GET /api/v1/documents/{document_id}/content?token={signed_token}` | `POST /api/v1/requests/{request_id}/process` |

No Finance page exposes Manager approval/rejection or Employee extraction editing. (WF-06, DASH-03; RBAC Finance rows; US-14, US-17, US-25, US-26)

## 2. App Router Structure and Navigation

| Route segment | Guard | Primary navigation |
|---|---|---|
| `/login` | Public; redirect authenticated users by validated role | Sign in only |
| `/employee/*` | Authenticated role must be `employee` | Requests, Submit invoice, Notifications, Logout |
| `/manager/*` | Authenticated role must be `manager` | Approval queue, Notifications, Logout |
| `/finance/*` | Authenticated role must be `finance` | Finance queue, Notifications, Logout |
| `/notifications` | Any of the three valid roles | Back to role home, Logout |

The `ORBIS_SESSION` cookie is host-only and `Path=/api`, so page middleware cannot read it and must not pretend to authenticate a page route. Each protected route group uses a layout-level Client Component guard that calls `GET /api/v1/users/me` with credentials before rendering page content. Spring validates the JWT and role claim; the guard redirects a `401` to `/login` and a role mismatch to that user's correct role home. The guard is a navigation/privacy control only—Spring still enforces every API permission. This avoids sharing the JWT signing secret with Next.js and keeps backend RBAC authoritative. (AUTH-02 through AUTH-04; Backend API §§2.2, 5)

Notification Request links are role-aware:

- Employee → `/employee/requests/[id]`
- Manager → `/manager/requests/[id]`
- Finance → `/finance/requests/[id]`

The target detail API still applies ownership/assignment/state scope and can return `404`; the notification link never grants access by itself. (NOTIF-04; RBAC §3)

## 3. Shared Components

These are component contracts/names only; Stage 7 creates no implementation.

| Component | Responsibility | Used by |
|---|---|---|
| `AppShell` | Role-aware navigation, user identity, notification entry, logout action | All authenticated routes |
| `AuthGuard` | Calls `/users/me`, holds protected content until validated, redirects unauthenticated/wrong-role users | Authenticated and role layouts |
| `RoleHomeRedirect` | Maps validated `/users/me` role to its landing route | `/`, `/login` |
| `NotificationBell` | Entry to notifications with scoped unread/recent state | `AppShell` |
| `PageState` | Standard loading, empty, and error presentation with retry action | All major pages |
| `RequestStatusBadge` | Consistent label/color for six fixed Request statuses | All dashboards and detail pages |
| `PaginatedTable` | URL-driven page, filter, sort controls and deterministic rows | Employee, Manager, Finance dashboard lists |
| `RequestSummaryTable` | Columns/actions shared across role-specific Request lists | All three dashboard pages |
| `RequestDetailCard` | Status, owner role, vendor, amount, submission/update metadata | All Request detail pages |
| `ExtractionDataCard` | Read-only extracted vendor/amount/date/flags | Manager and Finance detail; Employee detail when not editing |
| `ExtractionCorrectionForm` | Employee-editable vendor, total, date, complete line-item collection, version-aware save/resubmit | Employee detail |
| `LineItemsTable` | Ordered description/amount rows and displayed sum | All Request detail pages |
| `ValidationBanner` | Required-field, total mismatch, extraction failure, and `422` feedback | Employee detail |
| `FileUploadDropzone` | PDF/JPG/PNG, 10 MB pre-check, progress, upload/replacement errors | New Request and Employee detail |
| `DocumentActions` | Requests 60-second access link, then view/download through Spring | All Request detail pages |
| `AuditTimeline` | Paginated chronological immutable events | All Request detail pages |
| `ExtractionStatusPoller` | Polls Spring while extraction is pending and stops on success/failure | Employee detail after upload/retry/replacement |
| `ManagerDecisionDialog` | Approve confirmation or required rejection reason; submits current version | Manager detail |
| `FinanceProcessDialog` | Fixed `paid|scheduled` choice; submits current version | Finance detail |
| `TeamActivityCards` | Pending/approved/rejected aggregate counts | Manager queue |
| `NotificationList` | Recent/unread switch, pagination, mark-read, role-aware links | Notifications |

Shared shadcn/ui primitives remain under `components/ui`; feature components compose them rather than duplicating buttons, dialogs, forms, tables, and alerts.

## 4. State and Data-Fetching Approach

The frontend uses **Client Components plus native `fetch`** for authenticated API data, with no additional data-fetching library:

1. Server Components provide static route shells and metadata only. `AuthGuard` and page-level Client Components call Spring directly with `credentials: include`, allowing the browser to attach the API-scoped HttpOnly session cookie.
2. A shared browser API client uses `cache: no-store`, reads `XSRF-TOKEN` for mutations, attaches `X-XSRF-TOKEN`, and includes `expected_version` where required. It parses the common success/error contracts in one place.
3. Dashboard filter/page/sort values live in URL search parameters. Changing them updates the URL and triggers the page component to refetch, preserving shareable/back-button behavior.
4. After mutation success, the owning page refetches its Request/list/audit data from Spring. `409 VERSION_CONFLICT` performs that refetch before inviting the user to retry; `404` uses the scoped not-found state.

A 401 AUTH_REQUIRED response from any API call, at any point after initial route-guard validation (e.g. JWT naturally expiring mid-session during an 8-hour window), is handled globally by the shared browser API client rather than per-page: it clears local user-identity state and redirects to /login, optionally preserving the current path so the user returns to it after re-authenticating. This is distinct from the initial AuthGuard check, which only runs on route mount. Reference: AUTH-04.

5. `ExtractionStatusPoller` uses the same client against Spring every three seconds while status is `pending`; it pauses in a hidden tab and stops on `succeeded` or `failed`. No WebSocket or FastAPI call is used. (AI-07; Architecture §§1, 2; Backend API §§2–6)

API response/error types mirror `backend-api.md` in a single frontend contracts module. The common error envelope drives field errors, page errors, and correlation-ID display; pages never infer permission from hidden controls alone.

## 5. Key UI States

Every major page uses the same `PageState` vocabulary and preserves the DASH-05 behavior already defined by the backend contract.

| Page | Loading | Empty/success-with-no-data | Error/action state |
|---|---|---|---|
| `/login` | Disable submit and show progress | Not applicable | Invalid credentials remain generic; expired-session redirect may show a sign-in-required notice |
| Employee dashboard | Table skeleton | “No requests yet” with link to Submit invoice | Error envelope message, correlation ID, Retry |
| New Request | Upload progress | Initial dropzone instructions | Type/size/corrupt/retryable errors; preserve selected file where browser permits |
| Employee detail | Detail/audit skeleton; extraction polling indicator | No line items is valid; no audit page items after last page | `404` scoped not found; extraction failed with Retry; `409` refresh prompt; `422` field/validation flags |
| Manager queue | Queue and activity-card skeletons | “No requests awaiting review”; zeroed team cards | Each backend read has retry; no stale cached decision controls |
| Manager detail | Detail/audit skeleton | Empty line items/history page handled explicitly | `404` unassigned; `409` already decided/stale; rejection field error |
| Finance queue | Queue skeleton | “No invoices awaiting processing”; processed filter may also be empty | Retry with correlation ID |
| Finance detail | Detail/audit skeleton | Empty line items allowed | `404` outside Finance scope; `409` already processed/stale; payment-status field error |
| Notifications | List skeleton | “No recent notifications” or “No unread notifications” | Retry; a failed linked Request opens scoped not-found without exposing data |

Route-segment `loading.tsx` and `error.tsx` files supply page-level states; components provide local mutation/loading feedback. Empty API pages remain successful `200` responses with empty items, exactly as specified in Backend API §2.5. (DASH-05; US-15 through US-18)

## 6. Endpoint-to-Page Coverage

| Backend API surface | Frontend consumer |
|---|---|
| Login, logout, current user | `/login`, `/`, authenticated layouts, `AppShell` |
| Create Request | `/employee/requests/new` |
| Request detail and extracted-data GET | All three role-specific Request detail pages |
| Employee extraction PATCH/resubmit/retry | Employee Request detail |
| Manager approve/reject | Manager Request detail |
| Finance process | Finance Request detail |
| Replacement Document | Employee Request detail |
| Document access-link/content | All three role-specific Request detail pages |
| Employee dashboard | Employee Request dashboard |
| Manager dashboard and team activity | Manager queue |
| Finance dashboard | Finance queue |
| Notification list/mark-read | `/notifications`, `NotificationBell` |
| Audit history | All three role-specific Request detail pages |

All 22 public Stage 6 endpoints have a page, shared layout, or component consumer; none requires an additional page.
