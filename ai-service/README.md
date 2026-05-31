# AI Service

Python FastAPI internal service.

## Run

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Internal APIs require:

```http
X-Internal-Token: dev-internal-token
```

## Replace Stubs

- `app/services/product_index_service.py`: wrap `prod_add_product` and `prod_delete_product`.
- `app/services/search_service.py`: wrap `prod_search`, `user_search`, and future `image_search`.
- `app/services/chat_service.py`: wrap `chat` and `delete_history`.

