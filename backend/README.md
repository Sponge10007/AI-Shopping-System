# Backend

Spring Boot main API service.

## Package Layout

- `common/`: response wrapping, trace ID, exception handling, and security placeholder.
- `modules/auth`: register, login, logout.
- `modules/user`: current user profile.
- `modules/product`: public product APIs and merchant product management.
- `modules/search`: semantic search and image search public gateways.
- `modules/recommendation`: home recommendation.
- `modules/behavior`: user behavior logging entry.
- `modules/ai`: AI chat gateway.
- `modules/order`: order and payment APIs.
- `modules/admin`: user management and metrics overview.
- `modules/upload`: product/search image upload APIs.
- `modules/internal`: backend internal APIs consumed by the Python AI service.
- `infrastructure/ai`: reserved for the real HTTP client to call `ai-service`.

## Run

```bash
docker compose up -d postgres redis chroma
cd backend
mvn spring-boot:run
```

The current controllers return sample data. Replace service methods with repository calls and AI client calls module by module.

