from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ORBISFLOW_AI_")

    max_file_bytes: int = 10 * 1024 * 1024
    max_pdf_pages: int = 5
    tesseract_config: str = "--psm 6"


settings = Settings()
