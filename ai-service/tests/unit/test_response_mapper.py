from decimal import Decimal

from app.extraction.response_mapper import extract_fields


def test_extract_fields_reports_missing_date_and_parses_items() -> None:
    vendor, total, invoice_date, items, flags = extract_fields(
        "Vendor: Example Ltd\nItem: Widget 10.00\nTotal Amount: 10.00"
    )

    assert vendor == "Example Ltd"
    assert total == Decimal("10.00")
    assert invoice_date is None
    assert [item.description for item in items] == ["Widget"]
    assert [flag.code for flag in flags] == ["MISSING_INVOICE_DATE"]
