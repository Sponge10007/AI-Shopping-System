from pydantic import BaseModel, Field


class ProductHit(BaseModel):
    product_id: str = Field(..., examples=["10001"])
    score: float = Field(..., examples=[0.93])
    reason: str | None = Field(default=None)


class AcceptedResponse(BaseModel):
    accepted: bool
    status: str

