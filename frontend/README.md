# Frontend

Vue 3 + Vite client for the shopping user, merchant, and admin roles.

## Run

```bash
cd frontend
npm install
npm run dev
```

The API client in `src/services/api.ts` calls `/api/v1` and provides local fallback data so pages can still render while the backend is not running.

## Extension Points

- Customer flow: `src/views/ShoppingView.vue`, `src/views/OrdersView.vue`
- Merchant flow: `src/views/MerchantView.vue`
- Admin flow: `src/views/AdminView.vue`
- Shared API types and calls: `src/services/api.ts`
- Shared product cards: `src/components/ProductGrid.vue`

