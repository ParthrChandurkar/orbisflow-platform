import logging
from uuid import UUID

from fastapi import APIRouter, File, Form, Header, UploadFile

from app.core.config import settings
from app.core.errors import ExtractionHttpError
from app.extraction.engine import UnsupportedDocument, _decode
from app.extraction.schemas import ExtractionResponse
from app.extraction.service import extract_invoice

router = APIRouter(prefix="/internal/v1")
logger = logging.getLogger("uvicorn.error")


@router.post("/extractions", response_model=ExtractionResponse)
async def create_extraction(
    request_id: UUID = Form(),
    schema_version: str = Form(),
    mime_type: str = Form(),
    file: UploadFile = File(),
    correlation_value: str = Header(alias="X-Correlation-ID"),
) -> ExtractionResponse:
    try:
        correlation_id = UUID(correlation_value)
    except ValueError as exception:
        raise ExtractionHttpError(
            400, "INVALID_METADATA", "X-Correlation-ID must be a UUID.", UUID(int=0)
        ) from exception
    if str(correlation_id) != correlation_value.lower() or schema_version != "1":
        raise ExtractionHttpError(
            400, "INVALID_METADATA", "Extraction metadata is invalid.", correlation_id
        )
    if mime_type not in {"application/pdf", "image/jpeg", "image/png"}:
        raise ExtractionHttpError(
            415, "UNSUPPORTED_MEDIA_TYPE", "The MIME type is unsupported.", correlation_id
        )

    content = await file.read(settings.max_file_bytes + 1)
    if not content:
        raise ExtractionHttpError(
            400, "INVALID_FILE", "The document is empty.", correlation_id
        )
    if len(content) > settings.max_file_bytes:
        raise ExtractionHttpError(
            413, "FILE_TOO_LARGE", "The document exceeds 10 MB.", correlation_id
        )
    try:
        images = _decode(content, mime_type)
        for image in images:
            image.close()
    except UnsupportedDocument as exception:
        raise ExtractionHttpError(
            415, "UNSUPPORTED_MEDIA_TYPE", "The document signature is invalid.", correlation_id
        ) from exception

    logger.info("extraction_attempt request_id=%s", request_id)
    return extract_invoice(request_id, schema_version, mime_type, content)
