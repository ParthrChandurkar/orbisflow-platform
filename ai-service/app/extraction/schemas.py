from datetime import date
from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel


class LineItem(BaseModel):
    line_number: int
    description: str
    amount: Decimal


class ValidationFlag(BaseModel):
    code: Literal[
        "MISSING_VENDOR",
        "MISSING_TOTAL_AMOUNT",
        "MISSING_INVOICE_DATE",
    ]
    field: Literal["vendor", "total_amount", "invoice_date"]
    message: str


class ExtractionResponse(BaseModel):
    request_id: UUID
    schema_version: str
    status: Literal["succeeded", "failed"]
    vendor: str | None
    total_amount: Decimal | None
    invoice_date: date | None
    line_items: list[LineItem]
    validation_flags: list[ValidationFlag]
    failure_category: Literal[
        "unreadable_document",
        "unsupported_content",
        "ocr_error",
    ] | None
