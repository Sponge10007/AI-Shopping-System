from pydantic import BaseModel, Field

from app.schemas.common import ProductHit


class ProductSearchRequest(BaseModel):
    user_id: str
    query: str
    distance_threshold: float = 0.9
    limit: int = Field(default=20, ge=1, le=100)


class ProductSearchResponse(BaseModel):
    query: str
    hits: list[ProductHit]


class ImageSearchRequest(BaseModel):
    user_id: str
    image_url: str
    limit: int = Field(default=20, ge=1, le=100)


class ImageSearchResponse(BaseModel):
    detected_object: str | None = None
    hits: list[ProductHit]


class UserRecommendationResponse(BaseModel):
    strategy: str
    hits: list[ProductHit]

