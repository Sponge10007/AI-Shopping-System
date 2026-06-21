import { createRouter, createWebHistory } from 'vue-router'
import AdminView from '../views/AdminView.vue'
import CompareView from '../views/CompareView.vue'
import LoginView from '../views/LoginView.vue'
import MerchantView from '../views/MerchantView.vue'
import OrdersView from '../views/OrdersView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import ProfileView from '../views/ProfileView.vue'
import ShoppingView from '../views/ShoppingView.vue'
import AIChatView from '../views/AIChatView.vue'
import CheckoutView from '../views/CheckoutView.vue'
import AdminUsersView from '../views/AdminUsersView.vue'
import AdminMetricsView from '../views/AdminMetricsView.vue'
import MerchantUploadView from '../views/MerchantUploadView.vue'
import MerchantEditView from '../views/MerchantEditView.vue'
import MerchantRestockView from '../views/MerchantRestockView.vue'
import { sessionState } from '../stores/session.ts'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // ── Customer (Frontend 1) ──
    { path: '/', name: 'shopping', component: ShoppingView },
    { path: '/login', name: 'login', component: LoginView, meta: { guest: true } },
    { path: '/detail/:id', name: 'detail', component: ProductDetailView },
    { path: '/compare', name: 'compare', component: CompareView, meta: { auth: true } },
    { path: '/profile', name: 'profile', component: ProfileView, meta: { auth: true } },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { auth: true } },
    { path: '/chat', name: 'chat', component: AIChatView, meta: { auth: true } },
    { path: '/checkout', name: 'checkout', component: CheckoutView, meta: { auth: true } },

    // ── Merchant (Frontend 2) ──
    { path: '/merchant', name: 'merchant', component: MerchantView, meta: { auth: true, roles: ['MERCHANT', 'ADMIN'] } },
    { path: '/merchant/uploads', name: 'product-upload', component: MerchantUploadView, meta: { auth: true, roles: ['MERCHANT', 'ADMIN'] } },
    { path: '/merchant/products/:id/edit', name: 'product-edit', component: MerchantEditView, meta: { auth: true, roles: ['MERCHANT', 'ADMIN'] } },
    { path: '/merchant/products/:id/restock', name: 'product-restock', component: MerchantRestockView, meta: { auth: true, roles: ['MERCHANT', 'ADMIN'] } },

    // ── Admin (Frontend 2) ──
    { path: '/admin', name: 'admin', component: AdminView, meta: { auth: true, roles: ['ADMIN'] } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { auth: true, roles: ['ADMIN'] } },
    { path: '/admin/metrics', name: 'admin-metrics', component: AdminMetricsView, meta: { auth: true, roles: ['ADMIN'] } },
  ],
})

// Global navigation guard
router.beforeEach((to, _from, next) => {
  const meta = to.meta as {
    auth?: boolean
    guest?: boolean
    roles?: string[]
  }

  // Guest-only routes (like login) — redirect to home if already logged in
  if (meta.guest && sessionState.token) {
    return next({ name: 'shopping' })
  }

  // Auth-required routes
  if (meta.auth && !sessionState.token) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }

  // Role-restricted routes
  if (meta.roles && meta.roles.length > 0) {
    const userRole = sessionState.role || 'CUSTOMER'
    if (!meta.roles.includes(userRole)) {
      return next({ name: 'shopping' })
    }
  }

  next()
})

export default router
