from uuid import UUID

from fastapi import Header


def require_correlation_id(
    value: str = Header(alias="X-Correlation-ID"),
) -> UUID:
    correlation_id = UUID(value)
    if str(correlation_id) != value.lower():
        raise ValueError("X-Correlation-ID must be a canonical UUID")
    return correlation_id
