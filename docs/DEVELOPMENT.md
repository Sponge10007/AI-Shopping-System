# Development Guide

## Architecture Rule

The frontend only calls the Spring Boot backend under `/api/v1`.

The Spring Boot backend is the only public API service. It handles authentication, authorization, transactions, database access, response wrapping, and gateway calls to the Python AI service.

The Python AI service only exposes internal APIs under `/internal/v1/ai`. It should return product IDs, scores, explanations, and AI text. Product details still come from the backend.

## Module Rule

Each backend module follows this shape:

- `*Controller`: HTTP interface and request validation.
- `*Service`: business orchestration and future transaction boundary.
- `dto/*`: request and response objects for that module.

Avoid sharing database entities directly with controllers. Add DTOs instead.

## Extension Order

1. Replace sample responses with repositories and database migrations.
2. Add JWT validation and role checks.
3. Connect product create/update/delete to AI indexing.
4. Replace AI service stubs with the existing Python functions from `readme.txt`.
5. Add integration tests for each completed API.

## Branch Ownership

Small, module-scoped changes are preferred. A typical merge request should touch one backend module, one frontend page group, or one AI capability.

