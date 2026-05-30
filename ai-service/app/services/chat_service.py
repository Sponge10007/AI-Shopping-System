from app.schemas.chat import ChatMessageRequest, ChatMessageResponse, DeleteChatHistoryRequest
from app.schemas.common import AcceptedResponse, ProductHit


class ChatService:
    def chat(self, request: ChatMessageRequest) -> ChatMessageResponse:
        # Replace this with llmChat.chat(content, user_id, session_id), then sanitize at backend.
        return ChatMessageResponse(
            answer="这里是 AI 导购示例回复，后续会接入大模型并返回推荐商品。",
            image_list=[],
            link_list=["https://example.com/products/10001"],
            product_hits=[
                ProductHit(product_id="10001", score=0.9, reason="符合你的预算和通勤需求")
            ],
        )

    def delete_history(self, request: DeleteChatHistoryRequest) -> AcceptedResponse:
        # Replace this with llmChat.delete_history(user_id, session_id).
        return AcceptedResponse(accepted=True, status="CLEARED")


chat_service = ChatService()

