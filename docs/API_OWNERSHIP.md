# API Ownership

This file mirrors the current interface document and gives each group a stable place to work.

| Area | Owner | Paths |
| --- | --- | --- |
| Customer frontend | Frontend 1 | `/auth`, `/products`, `/search`, `/recommendations`, `/ai/chat`, `/orders` |
| Merchant/admin frontend | Frontend 2 | `/merchant/products`, `/uploads/product-images`, `/admin/*` |
| Account/product backend | Backend 1 | auth, users, products, merchant products, uploads, internal product summaries |
| Transaction/AI gateway backend | Backend 2 | search, recommendations, behavior, AI chat, orders, payments, admin metrics |
| AI service | AI group | `/internal/v1/ai/*` |
| Testing | Test group | `tests/`, Postman collections, integration scenarios |

