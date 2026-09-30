# Orbis Flow

Orbis Flow is an invoice approval application for employees, managers, and finance teams. It extracts invoice fields with OCR, applies a controlled approval workflow, stores documents in object storage, and records material actions in an append-only audit trail.

## Features

- PDF, JPG, and PNG invoice upload with file validation
- Tesseract-based extraction of invoice fields and line items
- Employee correction and resubmission workflow
- Manager review and finance processing queues
- JWT authentication, role-based access control, and CSRF protection
- Optimistic locking for concurrent workflow updates
- Append-only audit and notification records
- Short-lived, authorized document access links

## Architecture

The Next.js browser client calls the Spring Boot API, which owns authentication, authorization, workflow state, persistence, and document access. Spring Boot delegates OCR to an internal FastAPI service. PostgreSQL is the durable system of record, Redis supports transient application concerns, and MinIO provides S3-compatible storage for local development.

The browser does not call the OCR service or object store directly. See [`docs/architecture.md`](docs/architecture.md) for request, security, storage, and consistency flows.

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Frontend | Next.js, React, TypeScript, Tailwind CSS |
| Business API | Java 17, Spring Boot, Spring Security, JDBC, Flyway |
| OCR service | Python 3.11, FastAPI, Tesseract, Pillow, pypdfium2 |
| Data | PostgreSQL, Redis, MinIO locally, S3-compatible object storage |
| Delivery and testing | Docker Compose, GitHub Actions, Maven, Testcontainers, pytest, Vitest, Playwright |

## Getting Started

Prerequisites: Git and Docker with the Compose plugin. The full stack uses ports `3000`, `5432`, `6379`, `8000`, `8080`, `9000`, and `9001` by default.

Copy the development configuration:

```powershell
Copy-Item .env.example .env
```

Build and start all services:

```bash
docker compose up --build -d
docker compose ps
```

Open `http://localhost:3000`. Supporting endpoints include:

- Spring Boot health: `http://localhost:8080/api/v1/health`
- FastAPI health: `http://localhost:8000/internal/v1/health`
- MinIO console: `http://localhost:9001`

Flyway applies the database migrations and creates local fixture accounts. Development credentials are defined in the seed migration and must not be reused in a deployed environment.

Stop the stack with:

```bash
docker compose down
```

## Configuration

The root `.env.example` documents database, JWT, CSRF, Redis, service-port, and S3-compatible storage settings. Service-specific examples are available under `backend/`, `ai-service/`, and `frontend/`. Replace all development secrets and storage credentials before any hosted deployment.

## Testing

```bash
cd backend
mvn verify

cd ../ai-service
python -m pytest -q

cd ../frontend
npm test
npm run test:e2e
```

Backend integration tests require Docker because they use Testcontainers and an OCR service image. GitHub Actions separately verifies the frontend, backend, and OCR service.

## Project Status and Limitations

- Docker Compose provides the verified local workflow, including MinIO-based object storage.
- AWS-oriented S3 configuration is present, but this repository does not contain infrastructure-as-code for a complete AWS deployment.
- OCR output requires user review and correction.
- Local development credentials are fixtures, not production secrets.


