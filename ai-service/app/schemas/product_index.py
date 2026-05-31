from pydantic import BaseModel, Field


class ProductIndexRequest(BaseModel):
    description: str = Field(..., min_length=1)
    tags: list[str] = Field(default_factory=list)

