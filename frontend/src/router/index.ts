import { createRouter, createWebHistory } from 'vue-router'
import AdminView from '../views/AdminView.vue'
import CompareView from '../views/CompareView.vue'
import LoginView from '../views/LoginView.vue'
import MerchantView from '../views/MerchantView.vue'
import OrdersView from '../views/OrdersView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import ProfileView from '../views/ProfileView.vue'
import ShoppingView from '../views/ShoppingView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'shopping', component: ShoppingView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/detail/:id', name: 'detail', component: ProductDetailView },
    { path: '/compare', name: 'compare', component: CompareView },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/orders', name: 'orders', component: OrdersView },
    { path: '/merchant', name: 'merchant', component: MerchantView },
    { path: '/admin', name: 'admin', component: AdminView },
  ],
})

export default router
