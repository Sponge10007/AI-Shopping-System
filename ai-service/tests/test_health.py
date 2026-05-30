import pytest
from fastapi import HTTPException

from app.main import app
from app.core.security import verify_internal_token
from app.schemas.search import ProductSearchRequest
from app.services.search_service import search_service


def test_health() -> None:
    assert app.title == "AI Shopping Internal Service"


def test_internal_token_required() -> None:
    with pytest.raises(HTTPException) as exc_info:
        verify_internal_token(None)
    assert exc_info.value.status_code == 401


def test_semantic_search() -> None:
    response = search_service.search_products(
        ProductSearchRequest(user_id="u10001", query="耳机", limit=5)
    )
    assert response.hits[0].product_id == "10001"
