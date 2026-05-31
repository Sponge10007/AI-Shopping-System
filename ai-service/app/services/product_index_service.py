from app.schemas.common import AcceptedResponse
from app.schemas.product_index import ProductIndexRequest


class ProductIndexService:
    def upsert_product(self, product_id: str, request: ProductIndexRequest) -> AcceptedResponse:
        # Replace this with labelDB.prod_add_product(product_id, description).
        return AcceptedResponse(accepted=True, status="PENDING")

    def delete_product(self, product_id: str) -> AcceptedResponse:
        # Replace this with labelDB.prod_delete_product(product_id).
        return AcceptedResponse(accepted=True, status="DELETE_PENDING")


product_index_service = ProductIndexService()

