<script setup lang="ts">
import { CreditCard, PackageCheck, Truck } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { getOrders, payOrder, type Order } from '../services/api'

const orders = ref<Order[]>([])
const loading = ref(true)
const error = ref('')
const statusFilter = ref('')
const payingOrder = ref<string | null>(null)

const statusLabels: Record<string, string> = {
  CREATED: '待支付',
  PAID: '已支付',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
}

const statusList = ['', 'CREATED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED']

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const params: { status?: string } = {}
    if (statusFilter.value) params.status = statusFilter.value
    const page = await getOrders(params)
    orders.value = page.items
  } catch (e: any) {
    error.value = e.message || '加载订单失败'
  } finally {
    loading.value = false
  }
}

async function handlePay(orderId: string) {
  payingOrder.value = orderId
  try {
    await payOrder(orderId)
    // Refresh order list after payment
    await loadOrders()
  } catch (e: any) {
    error.value = e.message || '支付失败'
  } finally {
    payingOrder.value = null
  }
}

function getStatusIcon(status: string) {
  switch (status) {
    case 'PAID':
    case 'SHIPPED':
      return Truck
    case 'COMPLETED':
      return PackageCheck
    default:
      return CreditCard
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="page narrow-page">
    <header class="page-title">
      <p>Orders</p>
      <h1>我的订单</h1>
    </header>

    <!-- Status filter -->
    <div class="filter-strip">
      <button
        v-for="s in statusList"
        :key="s"
        type="button"
        :class="{ active: statusFilter === s }"
        @click="statusFilter = s; loadOrders()"
      >
        {{ s ? statusLabels[s] : '全部' }}
      </button>
    </div>

    <!-- Order summary -->
    <section class="bento-card order-summary">
      <div>
        <PackageCheck :size="28" />
        <h2>近期购物状态</h2>
        <p>
          {{
            orders.length > 0
              ? `共有 ${orders.length} 笔订单，AI 已根据物流、售后和库存状态整理。`
              : '暂无订单，去首页发现心仪商品吧。'
          }}
        </p>
      </div>
      <RouterLink to="/" class="black-button">继续购物</RouterLink>
    </section>

    <!-- Error -->
    <div v-if="error" class="error-banner">{{ error }}</div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <p>加载订单中...</p>
    </div>

    <!-- Order list -->
    <section v-else-if="orders.length > 0" class="order-stack">
      <article v-for="order in orders" :key="order.order_id" class="apple-row-card">
        <div class="row-icon">
          <component :is="getStatusIcon(order.status)" :size="22" />
        </div>
        <div class="row-main">
          <strong>
            {{ order.items?.map((i) => `${i.name} x${i.quantity}`).join('、') || '订单商品' }}
          </strong>
          <span>{{ order.receiver?.name }} / {{ order.receiver?.address }}</span>
        </div>
        <div class="row-meta">
          <span>{{ statusLabels[order.status] || order.status }}</span>
          <strong>¥{{ order.total_amount }}</strong>
        </div>
        <button
          v-if="order.status === 'CREATED'"
          type="button"
          class="circle-button"
          title="立即支付"
          :disabled="payingOrder === order.order_id"
          @click="handlePay(order.order_id)"
        >
          <CreditCard :size="18" />
        </button>
      </article>
    </section>

    <!-- Empty state -->
    <div v-else class="empty-state">
      <PackageCheck :size="48" />
      <p>暂无{{ statusFilter ? statusLabels[statusFilter] : '' }}订单</p>
      <RouterLink to="/" class="black-button">去购物</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.filter-strip {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  margin-bottom: 22px;
  padding-bottom: 4px;
}

.filter-strip button {
  flex: 0 0 auto;
  min-height: 36px;
  border: 0;
  border-radius: 999px;
  padding: 0 18px;
  background: #f5f5f7;
  color: #6e6e73;
  font-size: 0.82rem;
  font-weight: 700;
  transition: all 0.2s ease;
}

.filter-strip button.active {
  background: #1d1d1f;
  color: #ffffff;
}

.filter-strip button:hover:not(.active) {
  background: #e8e8ed;
}

.error-banner {
  margin-bottom: 16px;
  border-radius: 12px;
  background: #fff0f0;
  color: #d32f2f;
  padding: 10px 14px;
  font-size: 0.85rem;
  font-weight: 600;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 60px 0;
  color: #86868b;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 0;
  color: #86868b;
}

.empty-state svg {
  color: #c7c7cc;
}

.circle-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
