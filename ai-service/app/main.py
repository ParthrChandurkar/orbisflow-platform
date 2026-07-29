from uuid import UUID

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.internal.extractions import router as extraction_router
from app.core.errors import ExtractionHttpError

app = FastAPI(title="Orbis Flow AI Service")
app.include_router(extraction_router)


@app.exception_handler(ExtractionHttpError)
async def extraction_error(
    request: Request, exception: ExtractionHttpError
) -> JSONResponse:
    return JSONResponse(
        status_code=exception.status_code,
        content={
            "code": exception.code,
            "message": exception.message,
            "correlation_id": str(exception.correlation_id),
        },
        headers={"X-Correlation-ID": str(exception.correlation_id)},
    )


@app.exception_handler(RequestValidationError)
async def invalid_request(request: Request, exception: RequestValidationError) -> JSONResponse:
    incoming = request.headers.get("X-Correlation-ID")
    try:
        correlation_id = str(UUID(incoming)) if incoming else str(UUID(int=0))
    except ValueError:
        correlation_id = str(UUID(int=0))
    return JSONResponse(
        status_code=400,
        content={
            "code": "INVALID_METADATA",
            "message": "Extraction metadata is invalid.",
            "correlation_id": correlation_id,
        },
        headers={"X-Correlation-ID": correlation_id},
    )


@app.middleware("http")
async def echo_correlation_id(request: Request, call_next):
    response = await call_next(request)
    incoming = request.headers.get("X-Correlation-ID")
    if incoming:
        try:
            response.headers["X-Correlation-ID"] = str(UUID(incoming))
        except ValueError:
            pass
    return response


@app.get("/internal/v1/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
