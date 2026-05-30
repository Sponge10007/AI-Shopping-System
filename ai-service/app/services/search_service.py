from app.schemas.common import ProductHit
from app.schemas.search import (
    ImageSearchRequest,
    ImageSearchResponse,
    ProductSearchRequest,
    ProductSearchResponse,
    UserRecommendationResponse,
)


class SearchService:
    def search_products(self, request: ProductSearchRequest) -> ProductSearchResponse:
        # Replace this with labelDB.prod_search(user_id, query, distance_threshold, limit).
        hits = [
            ProductHit(product_id="10001", score=0.93, reason="语义匹配通勤、降噪和预算要求"),
            ProductHit(product_id="10002", score=0.86, reason="与办公和便携需求相关"),
        ]
        return ProductSearchResponse(query=request.query, hits=hits[: request.limit])

    def recommend_for_user(self, user_id: str, maxnum: int) -> UserRecommendationResponse:
        # Replace this with labelDB.user_search(user_id, maxnum).
        hits = [
            ProductHit(product_id="10001", score=0.91, reason="根据用户画像推荐"),
            ProductHit(product_id="10002", score=0.84, reason="与近期浏览偏好相似"),
        ]
        return UserRecommendationResponse(strategy="USER_PROFILE", hits=hits[: maxnum * 10])

    def search_by_image(self, request: ImageSearchRequest) -> ImageSearchResponse:
        # Reserved for future multimodal/image vector search.
        hits = [ProductHit(product_id="10001", score=0.88, reason="外观与上传图片相似")]
        return ImageSearchResponse(detected_object="耳机", hits=hits[: request.limit])


search_service = SearchService()

