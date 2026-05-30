from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    internal_token: str = Field(default="dev-internal-token", alias="AI_INTERNAL_TOKEN")
    backend_internal_base_url: str = Field(
        default="http://localhost:8080/internal/v1",
        alias="BACKEND_INTERNAL_BASE_URL",
    )


settings = Settings()

