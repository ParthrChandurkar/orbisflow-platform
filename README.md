# Orbis Flow

Orbis Flow is a three-service, AI-assisted invoice workflow MVP. A Next.js frontend supports Employee, Manager, and Finance experiences; Spring Boot owns authentication, business rules, persistence, documents, and workflow state; and an internal FastAPI service is reserved for invoice OCR and structured extraction.

## Prerequisites

- Docker Desktop with Docker Compose
- Git
- Optional for running services outside Docker: Node.js 22+, Java 17 with Maven 3.9+, and Python 3.11+

## Run locally

Optional local overrides can be created from the examples:

```powershell
Copy-Item .env.example .env
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env
Copy-Item ai-service/.env.example ai-service/.env
```

The Compose defaults work without those copies. From the repository root, run:

```sh
docker compose up --build
```

Then open:

- Frontend: http://localhost:3000
- Backend health: http://localhost:8080/api/v1/health
- AI service health: http://localhost:8000/internal/v1/health

Stop the stack with `Ctrl+C`, followed by:

```sh
docker compose down
```
