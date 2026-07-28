from dataclasses import dataclass
from uuid import UUID


@dataclass
class ExtractionHttpError(Exception):
    status_code: int
    code: str
    message: str
    correlation_id: UUID
