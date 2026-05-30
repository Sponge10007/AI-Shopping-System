from pydantic import BaseModel, Field

from app.schemas.common import ProductHit


class ChatMessageRequest(BaseModel):
    user_id: str
    session_id: str
    content: str = Field(..., min_length=1)


class ChatMessageResponse(BaseModel):
    answer: str
    image_list: list[str] = Field(default_factory=list)
    link_list: list[str] = Field(default_factory=list)
    product_hits: list[ProductHit] = Field(default_factory=list)


class DeleteChatHistoryRequest(BaseModel):
    user_id: str
    session_id: str

