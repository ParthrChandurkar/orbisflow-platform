import re
from datetime import date, datetime
from decimal import Decimal, InvalidOperation

from app.extraction.schemas import LineItem, ValidationFlag

MONEY = r"[-+]?\$?\s*\d[\d,]*(?:\.\d{1,4})?"


def extract_fields(
    text: str,
) -> tuple[str | None, Decimal | None, date | None, list[LineItem], list[ValidationFlag]]:
    lines = [" ".join(line.split()) for line in text.splitlines() if line.strip()]
    vendor = _labeled_text(lines, r"^(?:vendor|from|supplier)\s*[:\-]\s*(.+)$")
    if vendor is None:
        vendor = next(
            (
                line
                for line in lines
                if not re.search(
                    r"\b(invoice|date|total|amount|item|bill\s+to)\b",
                    line,
                    re.IGNORECASE,
                )
            ),
            None,
        )

    total_amount = _extract_total(lines)
    invoice_date = _extract_date(lines)
    line_items = _extract_line_items(lines)
    flags = _required_flags(vendor, total_amount, invoice_date)
    return vendor, total_amount, invoice_date, line_items, flags


def _labeled_text(lines: list[str], pattern: str) -> str | None:
    for line in lines:
        match = re.search(pattern, line, re.IGNORECASE)
        if match and match.group(1).strip():
            return match.group(1).strip()
    return None


def _extract_total(lines: list[str]) -> Decimal | None:
    patterns = (
        rf"^(?:grand\s+total|total\s+amount|amount\s+due|invoice\s+total)\s*[:\-]?\s*({MONEY})\s*$",
        rf"^total\s*[:\-]?\s*({MONEY})\s*$",
    )
    matches: list[Decimal] = []
    for line in lines:
        for pattern in patterns:
            match = re.search(pattern, line, re.IGNORECASE)
            if match:
                value = _decimal(match.group(1))
                if value is not None:
                    matches.append(value)
                break
    return matches[-1] if matches else None


def _extract_date(lines: list[str]) -> date | None:
    for line in lines:
        match = re.search(
            r"(?:invoice\s+date|date)\s*[:\-]?\s*"
            r"(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{4})",
            line,
            re.IGNORECASE,
        )
        if not match:
            continue
        raw = match.group(1)
        for pattern in ("%Y-%m-%d", "%Y/%m/%d", "%d/%m/%Y", "%d-%m-%Y"):
            try:
                return datetime.strptime(raw, pattern).date()
            except ValueError:
                pass
    return None


def _extract_line_items(lines: list[str]) -> list[LineItem]:
    result: list[LineItem] = []
    pattern = re.compile(rf"^item\s*[:#\-]?\s*(.+?)\s+({MONEY})\s*$", re.IGNORECASE)
    for line in lines:
        match = pattern.search(line)
        if not match:
            continue
        amount = _decimal(match.group(2))
        description = match.group(1).strip(" :-")
        if amount is not None and description:
            result.append(
                LineItem(
                    line_number=len(result) + 1,
                    description=description,
                    amount=amount,
                )
            )
    return result


def _decimal(raw: str) -> Decimal | None:
    try:
        return Decimal(raw.replace("$", "").replace(",", "").replace(" ", ""))
    except InvalidOperation:
        return None


def _required_flags(
    vendor: str | None,
    total_amount: Decimal | None,
    invoice_date: date | None,
) -> list[ValidationFlag]:
    flags: list[ValidationFlag] = []
    if not vendor:
        flags.append(
            ValidationFlag(
                code="MISSING_VENDOR",
                field="vendor",
                message="Vendor is required.",
            )
        )
    if total_amount is None:
        flags.append(
            ValidationFlag(
                code="MISSING_TOTAL_AMOUNT",
                field="total_amount",
                message="Total amount is required.",
            )
        )
    if invoice_date is None:
        flags.append(
            ValidationFlag(
                code="MISSING_INVOICE_DATE",
                field="invoice_date",
                message="Invoice date is required.",
            )
        )
    return flags
