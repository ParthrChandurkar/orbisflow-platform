from fastapi import FastAPI

from app.api.internal.extractions import router as extraction_router

app = FastAPI(title="Orbis Flow AI Service")
app.include_router(extraction_router)


@app.get("/internal/v1/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
