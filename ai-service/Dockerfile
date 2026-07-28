FROM python:3.11-slim

WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        fonts-dejavu-core \
        tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*
COPY pyproject.toml .
RUN mkdir -p app && touch app/__init__.py
RUN python -m pip install --no-cache-dir .
COPY app ./app

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
