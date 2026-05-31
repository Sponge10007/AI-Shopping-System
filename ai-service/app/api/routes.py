from fastapi import APIRouter, Depends

from app.core.security import verify_internal_token
from app.schemas.chat import ChatMessageRequest, ChatMessageResponse, DeleteChatHistoryRequest
from app.schemas.common import AcceptedResponse
from app.schemas.product_index import ProductIndexRequest
from app.schemas.search import (
    ImageSearchRequest,
    ImageSearchResponse,
    ProductSearchRequest,
    ProductSearchResponse,
    UserRecommendationResponse,
)
from app.services.chat_service import chat_service
from app.services.product_index_service import product_index_service
from app.services.search_service import search_service

router = APIRouter(
    prefix="/internal/v1/ai",
    dependencies=[Depends(verify_internal_token)],
    tags=["internal-ai"],
)


@router.post("/products/{product_id}/index", response_model=AcceptedResponse)
def upsert_product_index(product_id: str, request: ProductIndexRequest) -> AcceptedResponse:
    return product_index_service.upsert_product(product_id, request)


@router.delete("/products/{product_id}/index", response_model=AcceptedResponse)
def delete_product_index(product_id: str) -> AcceptedResponse:
    return product_index_service.delete_product(product_id)


@router.post("/search/products", response_model=ProductSearchResponse)
def search_products(request: ProductSearchRequest) -> ProductSearchResponse:
    return search_service.search_products(request)


@router.get("/users/{user_id}/recommendations", response_model=UserRecommendationResponse)
def recommend_for_user(user_id: str, maxnum: int = 5) -> UserRecommendationResponse:
    return search_service.recommend_for_user(user_id, maxnum)


@router.post("/chat/messages", response_model=ChatMessageResponse)
def chat(request: ChatMessageRequest) -> ChatMessageResponse:
    return chat_service.chat(request)


@router.delete("/chat/history", response_model=AcceptedResponse)
def delete_chat_history(request: DeleteChatHistoryRequest) -> AcceptedResponse:
    return chat_service.delete_history(request)


@router.post("/search/image", response_model=ImageSearchResponse)
def search_by_image(request: ImageSearchRequest) -> ImageSearchResponse:
    return search_service.search_by_image(request)

