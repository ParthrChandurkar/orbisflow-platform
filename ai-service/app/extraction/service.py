from uuid import UUID

from app.extraction.engine import (
    UnreadableDocument,
    UnsupportedDocument,
    ocr_document,
)
from app.extraction.response_mapper import extract_fields
from app.extraction.schemas import ExtractionResponse


def extract_invoice(
    request_id: UUID,
    schema_version: str,
    mime_type: str,
    content: bytes,
) -> ExtractionResponse:
    try:
        text = ocr_document(content, mime_type)
        vendor, total, invoice_date, items, flags = extract_fields(text)
        return ExtractionResponse(
            request_id=request_id,
            schema_version=schema_version,
            status="succeeded",
            vendor=vendor,
            total_amount=total,
            invoice_date=invoice_date,
            line_items=items,
            validation_flags=flags,
            failure_category=None,
        )
    except UnreadableDocument:
        return _failed(request_id, schema_version, "unreadable_document")
    except UnsupportedDocument:
        return _failed(request_id, schema_version, "unsupported_content")
    except RuntimeError:
        return _failed(request_id, schema_version, "ocr_error")


def _failed(
    request_id: UUID,
    schema_version: str,
    category: str,
) -> ExtractionResponse:
    return ExtractionResponse(
        request_id=request_id,
        schema_version=schema_version,
        status="failed",
        vendor=None,
        total_amount=None,
        invoice_date=None,
        line_items=[],
        validation_flags=[],
        failure_category=category,
    )
