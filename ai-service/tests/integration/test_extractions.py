from io import BytesIO
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient
from PIL import Image, ImageDraw, ImageFont

from app.main import app

client = TestClient(app)


def test_real_ocr_extracts_readable_invoice() -> None:
    request_id = uuid4()
    correlation_id = uuid4()
    response = client.post(
        "/internal/v1/extractions",
        headers={"X-Correlation-ID": str(correlation_id)},
        data={
            "request_id": str(request_id),
            "schema_version": "1",
            "mime_type": "image/png",
        },
        files={"file": ("invoice.png", readable_invoice(), "image/png")},
    )

    assert response.status_code == 200
    assert response.headers["X-Correlation-ID"] == str(correlation_id)
    body = response.json()
    assert body["request_id"] == str(request_id)
    assert body["schema_version"] == "1"
    assert body["status"] == "succeeded"
    assert body["vendor"] == "Acme Consulting"
    assert body["total_amount"] == "125.50"
    assert body["invoice_date"] == "2026-07-28"
    assert body["line_items"] == [
        {"line_number": 1, "description": "Consulting Service", "amount": "125.50"}
    ]
    assert body["validation_flags"] == []
    assert body["failure_category"] is None


def test_blank_image_is_completed_unreadable_failure() -> None:
    response = client.post(
        "/internal/v1/extractions",
        headers={"X-Correlation-ID": str(uuid4())},
        data={
            "request_id": str(uuid4()),
            "schema_version": "1",
            "mime_type": "image/png",
        },
        files={"file": ("blank.png", blank_png(), "image/png")},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "failed"
    assert response.json()["failure_category"] == "unreadable_document"


def test_signature_mismatch_is_rejected() -> None:
    response = client.post(
        "/internal/v1/extractions",
        headers={"X-Correlation-ID": str(uuid4())},
        data={
            "request_id": str(uuid4()),
            "schema_version": "1",
            "mime_type": "image/png",
        },
        files={"file": ("bad.png", b"not-an-image", "image/png")},
    )

    assert response.status_code == 415
    assert response.json()["code"] == "UNSUPPORTED_MEDIA_TYPE"


def readable_invoice() -> bytes:
    image = Image.new("RGB", (1400, 700), "white")
    draw = ImageDraw.Draw(image)
    font_path = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    font = ImageFont.truetype(str(font_path), 48)
    lines = [
        "INVOICE",
        "Vendor: Acme Consulting",
        "Invoice Date: 2026-07-28",
        "Item: Consulting Service 125.50",
        "Total Amount: 125.50",
    ]
    for index, line in enumerate(lines):
        draw.text((60, 50 + index * 110), line, fill="black", font=font)
    output = BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def blank_png() -> bytes:
    output = BytesIO()
    Image.new("RGB", (400, 200), "white").save(output, format="PNG")
    return output.getvalue()
