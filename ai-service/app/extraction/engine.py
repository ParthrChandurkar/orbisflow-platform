from io import BytesIO

import pypdfium2 as pdfium
import pytesseract
from PIL import Image, UnidentifiedImageError

from app.core.config import settings


class UnsupportedDocument(ValueError):
    pass


class UnreadableDocument(ValueError):
    pass


def ocr_document(content: bytes, mime_type: str) -> str:
    images = _decode(content, mime_type)
    try:
        text = "\n".join(
            pytesseract.image_to_string(image, config=settings.tesseract_config)
            for image in images
        ).strip()
    except pytesseract.TesseractError as exception:
        raise RuntimeError("Tesseract OCR failed") from exception
    finally:
        for image in images:
            image.close()
    if not text:
        raise UnreadableDocument("OCR produced no readable text")
    return text


def _decode(content: bytes, mime_type: str) -> list[Image.Image]:
    if mime_type == "application/pdf":
        if not content.startswith(b"%PDF-") or b"%%EOF" not in content[-1024:]:
            raise UnsupportedDocument("PDF signature is invalid")
        try:
            pdf = pdfium.PdfDocument(content)
            if len(pdf) == 0:
                raise UnreadableDocument("PDF has no pages")
            images = [
                pdf[index].render(scale=2.0).to_pil().convert("RGB")
                for index in range(min(len(pdf), settings.max_pdf_pages))
            ]
            pdf.close()
            return images
        except (pdfium.PdfiumError, ValueError) as exception:
            raise UnreadableDocument("PDF cannot be rendered") from exception

    if mime_type not in {"image/jpeg", "image/png"}:
        raise UnsupportedDocument("MIME type is unsupported")
    try:
        image = Image.open(BytesIO(content))
        image.verify()
        image = Image.open(BytesIO(content)).convert("RGB")
    except (UnidentifiedImageError, OSError) as exception:
        raise UnsupportedDocument("Image signature is invalid") from exception
    expected = "JPEG" if mime_type == "image/jpeg" else "PNG"
    detected = Image.open(BytesIO(content)).format
    if detected != expected:
        image.close()
        raise UnsupportedDocument("Image content does not match MIME type")
    return [image]
