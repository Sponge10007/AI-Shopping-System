# Cross-Service Tests

Place black-box API tests here after the backend and AI service are connected.

Suggested first scenarios:

- Register, login, get current user.
- Merchant creates a product and receives `vector_index_status`.
- Customer performs semantic search and receives product summaries.
- Customer creates and pays an order.
- AI internal routes reject missing `X-Internal-Token`.

