# Orbis Flow — Stage 11 Folder Structure

This is the implementation scaffold for the fixed three-service architecture. It preserves Spring Boot as the only business-data API and the only service with PostgreSQL, Redis, and S3 access; FastAPI remains an internal OCR/extraction service. (Architecture §§1–2; Backend API §1)

## 1. Repository Tree

```text
orbisflow-platform/
├── .github/
│   └── workflows/
│       └── ci.yml
├── docs/
│   ├── PRD.md
│   ├── rbac.md
│   ├── user-stories.md
│   ├── architecture.md
│   ├── db-schema.md
│   ├── backend-api.md
│   ├── frontend.md
│   └── folder-structure.md
├── frontend/
│   ├── app/
│   │   ├── (public)/
│   │   │   └── login/
│   │   │       └── page.tsx
│   │   ├── (authenticated)/
│   │   │   ├── employee/
│   │   │   │   ├── requests/
│   │   │   │   │   ├── [id]/
│   │   │   │   │   │   ├── error.tsx
│   │   │   │   │   │   ├── loading.tsx
│   │   │   │   │   │   └── page.tsx
│   │   │   │   │   ├── new/
│   │   │   │   │   │   └── page.tsx
│   │   │   │   │   ├── error.tsx
│   │   │   │   │   ├── loading.tsx
│   │   │   │   │   └── page.tsx
│   │   │   │   └── layout.tsx
│   │   │   ├── manager/
│   │   │   │   ├── queue/
│   │   │   │   │   ├── error.tsx
│   │   │   │   │   ├── loading.tsx
│   │   │   │   │   └── page.tsx
│   │   │   │   ├── requests/
│   │   │   │   │   └── [id]/
│   │   │   │   │       ├── error.tsx
│   │   │   │   │       ├── loading.tsx
│   │   │   │   │       └── page.tsx
│   │   │   │   └── layout.tsx
│   │   │   ├── finance/
│   │   │   │   ├── queue/
│   │   │   │   │   ├── error.tsx
│   │   │   │   │   ├── loading.tsx
│   │   │   │   │   └── page.tsx
│   │   │   │   ├── requests/
│   │   │   │   │   └── [id]/
│   │   │   │   │       ├── error.tsx
│   │   │   │   │       ├── loading.tsx
│   │   │   │   │       └── page.tsx
│   │   │   │   └── layout.tsx
│   │   │   ├── notifications/
│   │   │   │   ├── error.tsx
│   │   │   │   ├── loading.tsx
│   │   │   │   └── page.tsx
│   │   │   └── layout.tsx
│   │   ├── error.tsx
│   │   ├── globals.css
│   │   ├── layout.tsx
│   │   ├── not-found.tsx
│   │   └── page.tsx
│   ├── components/
│   │   ├── audit/
│   │   │   └── audit-timeline.tsx
│   │   ├── auth/
│   │   │   ├── auth-guard.tsx
│   │   │   ├── login-form.tsx
│   │   │   └── role-home-redirect.tsx
│   │   ├── dashboards/
│   │   │   ├── paginated-table.tsx
│   │   │   ├── request-summary-table.tsx
│   │   │   └── team-activity-cards.tsx
│   │   ├── documents/
│   │   │   ├── document-actions.tsx
│   │   │   └── file-upload-dropzone.tsx
│   │   ├── feedback/
│   │   │   ├── page-state.tsx
│   │   │   └── validation-banner.tsx
│   │   ├── layout/
│   │   │   └── app-shell.tsx
│   │   ├── notifications/
│   │   │   ├── notification-bell.tsx
│   │   │   └── notification-list.tsx
│   │   ├── requests/
│   │   │   ├── extraction-correction-form.tsx
│   │   │   ├── extraction-data-card.tsx
│   │   │   ├── extraction-status-poller.tsx
│   │   │   ├── finance-process-dialog.tsx
│   │   │   ├── line-items-table.tsx
│   │   │   ├── manager-decision-dialog.tsx
│   │   │   ├── request-detail-card.tsx
│   │   │   └── request-status-badge.tsx
│   │   └── ui/
│   │       └── README.md
│   ├── lib/
│   │   ├── api/
│   │   │   ├── audit.ts
│   │   │   ├── auth.ts
│   │   │   ├── browser-client.ts
│   │   │   ├── dashboards.ts
│   │   │   ├── documents.ts
│   │   │   ├── notifications.ts
│   │   │   └── requests.ts
│   │   ├── auth/
│   │   │   └── role-routes.ts
│   │   └── contracts/
│   │       ├── api-error.ts
│   │       ├── audit.ts
│   │       ├── auth.ts
│   │       ├── dashboards.ts
│   │       ├── documents.ts
│   │       ├── notifications.ts
│   │       └── requests.ts
│   ├── public/
│   │   └── .gitkeep
│   ├── tests/
│   │   ├── components/
│   │   └── pages/
│   ├── .env.example
│   ├── components.json
│   ├── Dockerfile
│   ├── eslint.config.mjs
│   ├── next.config.ts
│   ├── package.json
│   ├── postcss.config.mjs
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   └── vitest.config.ts
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/orbisflow/
│   │   │   │   ├── OrbisFlowApplication.java
│   │   │   │   ├── auth/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── AuthController.java
│   │   │   │   │   │   └── AuthDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   └── AuthService.java
│   │   │   │   │   └── domain/
│   │   │   │   │       ├── JwtService.java
│   │   │   │   │       └── PasswordHasher.java
│   │   │   │   ├── users/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── UserController.java
│   │   │   │   │   │   └── UserDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   └── UserQueryService.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── User.java
│   │   │   │   │   │   └── UserRole.java
│   │   │   │   │   └── persistence/
│   │   │   │   │       └── UserRepository.java
│   │   │   │   ├── requests/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── RequestController.java
│   │   │   │   │   │   └── RequestDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── RequestCommandService.java
│   │   │   │   │   │   ├── RequestQueryService.java
│   │   │   │   │   │   └── WorkflowService.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── PaymentStatus.java
│   │   │   │   │   │   ├── Request.java
│   │   │   │   │   │   ├── RequestAccessPolicy.java
│   │   │   │   │   │   └── RequestStatus.java
│   │   │   │   │   └── persistence/
│   │   │   │   │       ├── ExtractedInvoiceDataRepository.java
│   │   │   │   │       ├── InvoiceLineItemRepository.java
│   │   │   │   │       └── RequestRepository.java
│   │   │   │   ├── documents/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── DocumentController.java
│   │   │   │   │   │   └── DocumentDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   └── DocumentService.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   └── Document.java
│   │   │   │   │   └── persistence/
│   │   │   │   │       ├── DocumentRepository.java
│   │   │   │   │       └── S3DocumentStore.java
│   │   │   │   ├── dashboards/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── DashboardController.java
│   │   │   │   │   │   └── DashboardDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   └── DashboardQueryService.java
│   │   │   │   │   └── persistence/
│   │   │   │   │       ├── DashboardCache.java
│   │   │   │   │       └── DashboardQueryRepository.java
│   │   │   │   ├── notifications/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── NotificationController.java
│   │   │   │   │   │   └── NotificationDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   └── NotificationService.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   └── Notification.java
│   │   │   │   │   └── persistence/
│   │   │   │   │       └── NotificationRepository.java
│   │   │   │   ├── audit/
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── AuditController.java
│   │   │   │   │   │   └── AuditDtos.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   └── AuditService.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   └── AuditEvent.java
│   │   │   │   │   └── persistence/
│   │   │   │   │       └── AuditLogRepository.java
│   │   │   │   ├── integration/
│   │   │   │   │   └── ai/
│   │   │   │   │       ├── FastApiExtractionClient.java
│   │   │   │   │       ├── FastApiExtractionDtos.java
│   │   │   │   │       └── FastApiProperties.java
│   │   │   │   └── common/
│   │   │   │       ├── errors/
│   │   │   │       │   ├── ApiErrorCode.java
│   │   │   │       │   ├── ApiErrorEnvelope.java
│   │   │   │       │   └── GlobalExceptionHandler.java
│   │   │   │       └── security/
│   │   │   │           ├── CorrelationIdFilter.java
│   │   │   │           ├── CsrfConfiguration.java
│   │   │   │           ├── JwtAuthenticationFilter.java
│   │   │   │           ├── RequestAuthorizationService.java
│   │   │   │           └── SecurityConfiguration.java
│   │   │   └── resources/
│   │   │       ├── application-local.yml
│   │   │       ├── application-prod.yml
│   │   │       └── application.yml
│   │   └── test/
│   │       └── java/com/orbisflow/
│   │           ├── auth/
│   │           ├── users/
│   │           ├── requests/
│   │           ├── documents/
│   │           ├── dashboards/
│   │           ├── notifications/
│   │           ├── audit/
│   │           ├── integration/ai/
│   │           └── security/
│   ├── .env.example
│   ├── Dockerfile
│   └── pom.xml
├── ai-service/
│   ├── app/
│   │   ├── api/
│   │   │   └── internal/
│   │   │       └── extractions.py
│   │   ├── core/
│   │   │   ├── config.py
│   │   │   ├── correlation.py
│   │   │   └── errors.py
│   │   ├── extraction/
│   │   │   ├── engine.py
│   │   │   ├── response_mapper.py
│   │   │   ├── schemas.py
│   │   │   └── service.py
│   │   ├── __init__.py
│   │   └── main.py
│   ├── tests/
│   │   ├── integration/
│   │   │   └── test_extractions.py
│   │   └── unit/
│   │       ├── test_response_mapper.py
│   │       └── test_service.py
│   ├── .env.example
│   ├── Dockerfile
│   └── pyproject.toml
├── .env.example
├── .gitignore
├── docker-compose.yml
└── README.md
```

## 2. Frontend Boundaries

- `app/` owns routes, layouts, and route-level loading/error states from `frontend.md`; feature behavior remains in `components/` and `lib/api/`.
- The authenticated layout mounts `AuthGuard` and `AppShell`. Role layouts configure only navigation and the expected role; they do not replace backend authorization. No auth `middleware.ts` is present because the `ORBIS_SESSION` cookie is scoped to `/api`. (AUTH-03; Backend API §§2.2, 5)
- `lib/api/browser-client.ts` is the only low-level HTTP client. The feature API files expose typed calls to the 22 public Spring endpoints; no frontend module calls FastAPI, PostgreSQL, Redis, or S3 directly.
- `components/ui/` is reserved for generated shadcn/ui primitives. Business components stay in their named feature folders.

## 3. Spring Boot Boundaries

The backend uses the Stage 6 feature-first module structure. Each feature owns its API DTOs/controllers, application use cases, domain model, and persistence adapter; cross-feature dependencies go through application services rather than another feature's repository. `common` contains only cross-cutting security and error concerns, while `integration.ai` owns the internal FastAPI client. (Backend API §§1, 5–6)

The Maven build file is the single backend build definition. A migration-specific resources directory is intentionally absent because exact migration tooling remains deferred; it will be added beside the chosen implementation during the implementation stage, without changing feature ownership. Test packages mirror production features, with `security/` covering the RBAC negative cases from `rbac.md` §4.

## 4. FastAPI Boundaries

FastAPI exposes only the internal extraction route through `api/internal/extractions.py`. `extraction/` owns OCR, structured invoice mapping, and extraction response types; `core/` owns configuration, correlation propagation, and the internal error envelope. It contains no user/JWT/RBAC module, workflow state machine, PostgreSQL repository, Redis client, or S3 client because Spring is its sole trusted caller and data-system boundary. (AI-01 through AI-03, AI-07; Architecture §§1–3; Backend API §6)

## 5. Local and CI Composition

`docker-compose.yml` defines exactly `frontend`, `backend`, `ai-service`, `postgres`, and `redis`. S3 remains an external AWS dependency configured through environment variables; the repository does not introduce a local object-storage substitute, message queue, gateway, or additional service. `.github/workflows/ci.yml` runs the three services' formatting, tests, and builds using their own manifests; deployment detail remains outside this folder-layout stage.
