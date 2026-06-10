import { createRouter, createWebHistory } from 'vue-router'
import AdminView from '../views/AdminView.vue'
import CompareView from '../views/CompareView.vue'
import LoginView from '../views/LoginView.vue'
import MerchantView from '../views/MerchantView.vue'
import OrdersView from '../views/OrdersView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import ProfileView from '../views/ProfileView.vue'
import ShoppingView from '../views/ShoppingView.vue'
import AdminUsersView from '../views/AdminUsersView.vue'
import AdminMetricsView from '../views/AdminMetricsView.vue'
import MerchantUploadView from '../views/MerchantUploadView.vue'
import MerchantEditView from '../views/MerchantEditView.vue'
import MerchantRestockView from '../views/MerchantRestockView.vue'
import { sessionState } from '../stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'shopping', component: ShoppingView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/detail/:id', name: 'detail', component: ProductDetailView },
    { path: '/compare', name: 'compare', component: CompareView },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/orders', name: 'orders', component: OrdersView },
    { path: '/merchant', name: 'merchant', component: MerchantView, meta: { roles: ['MERCHANT','ADMIN'] } },
    { path: '/merchant/uploads', name: 'product-upload', component: MerchantUploadView, meta: { roles: ['MERCHANT','ADMIN'] } },
    { path: '/merchant/products/:id/edit', name: 'product-edit', component: MerchantEditView, meta: { roles: ['MERCHANT','ADMIN'] } },
    { path: '/merchant/products/:id/restock', name: 'product-restock', component: MerchantRestockView, meta: { roles: ['MERCHANT','ADMIN'] } },
    { path: '/admin', name: 'admin', component: AdminView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/metrics', name: 'admin-metrics', component: AdminMetricsView, meta: { roles: ['ADMIN'] } },
  ],
})

// router.beforeEach((to, from, next) => {
//   const required = (to.meta as any)?.roles as string[] | undefined
//   if (!required || required.length === 0) return next()
//   const role = sessionState.role || 'CUSTOMER'
//   if (required.includes(role)) return next()
//   return next({ name: 'login' })
// })

export default router
