# AI Intelligent Shopping System

This repository is structured as a multi-service project:

- `frontend/`: Vue shopping client, merchant console, and admin console.
- `backend/`: Spring Boot main API service. It owns auth, users, products, orders, uploads, admin APIs, and the AI gateway.
- `ai-service/`: Python FastAPI internal service. It wraps vector search, recommendation, chat, and future image search capabilities.
- `database/`: SQL migrations and seed data entry points.
- `docs/`: architecture and team collaboration notes.
- `tests/`: cross-service API and integration test assets.

The code is intentionally scaffolded around the existing interface document. Most business methods currently return sample data so each group can replace one module at a time without changing the public API shape.

## Quick Start

Java and Maven are required for the backend. Node.js is required for the frontend. Python 3.11+ is recommended for the AI service.

```bash
# Frontend
cd frontend
npm install
npm run dev

# Backend
cd backend
mvn spring-boot:run

# AI service
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Default ports:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080/api/v1`
- AI internal service: `http://localhost:8001/internal/v1/ai`

## Team Boundaries

- Frontend shopping flow: `frontend/src/views`, `frontend/src/services/api.ts`
- Frontend merchant/admin flow: `frontend/src/views/MerchantView.vue`, `frontend/src/views/AdminView.vue`
- Backend auth/user/product/upload/internal summaries: `backend/src/main/java/com/aishop/modules/{auth,user,product,upload,internal}`
- Backend order/search/recommendation/behavior/AI gateway/admin metrics: `backend/src/main/java/com/aishop/modules/{order,search,recommendation,behavior,ai,admin}`
- AI implementation: `ai-service/app/services`
- Database evolution: `database/migrations`

See `docs/DEVELOPMENT.md` for extension rules.

