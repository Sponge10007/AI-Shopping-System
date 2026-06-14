<script setup lang="ts">
import { Check, ShoppingBag } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createOrder, recordBehavior, getProductDetail, type Product } from '../services/api'
import { isLoggedIn, sessionState } from '../stores/session'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const success = ref(false)
const createdOrder = ref<{ order_id: string; total_amount: string } | null>(null)

// Product info from query params (quick buy)
const quickProduct = reactive({
  product_id: (route.query.product_id as string) || '',
  name: (route.query.name as string) || '',
  price: (route.query.price as string) || '',
  image: (route.query.image as string) || '',
  quantity: 1,
})

const productDetail = ref<Product | null>(null)

const receiver = reactive({
  name: '',
  phone: '',
  address: '',
})

onMounted(async () => {
  if (!isLoggedIn()) {
    router.push('/login')
    return
  }

  // Pre-fill receiver from user info
  if (sessionState.userInfo) {
    receiver.name = sessionState.userInfo.nickname || sessionState.userInfo.username || ''
    receiver.phone = sessionState.userInfo.phone || ''
  }

  // Load product detail if we have a product_id
  if (quickProduct.product_id) {
    try {
      productDetail.value = await getProductDetail(quickProduct.product_id)
      if (!quickProduct.name) {
        quickProduct.name = productDetail.value.name
        quickProduct.price = productDetail.value.price
        quickProduct.image = productDetail.value.image_urls?.[0] || ''
      }
    } catch {
      // use query params
    }
  }
})

function getTotalAmount(): string {
  const price = parseFloat(quickProduct.price || '0')
  return (price * quickProduct.quantity).toFixed(2)
}

async function handleSubmit() {
  error.value = ''

  if (!quickProduct.product_id) {
    error.value = '缺少商品信息，请从商品详情页进入'
    return
  }
  if (!receiver.name || !receiver.phone || !receiver.address) {
    error.value = '请填写完整的收货信息'
    return
  }
  if (quickProduct.quantity < 1) {
    error.value = '购买数量至少为1'
    return
  }

  loading.value = true
  try {
    const order = await createOrder({
      items: [{ product_id: quickProduct.product_id, quantity: quickProduct.quantity }],
      receiver: {
        name: receiver.name,
        phone: receiver.phone,
        address: receiver.address,
      },
    })

    createdOrder.value = {
      order_id: order.order_id,
      total_amount: order.total_amount,
    }
    success.value = true

    // Record behavior
    recordBehavior({
      event_type: 'ORDER',
      product_id: quickProduct.product_id,
      metadata: { order_id: order.order_id },
    }).catch(() => {})
  } catch (e: any) {
    error.value = e.message || '下单失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page narrow-page">
    <header class="page-title">
      <p>Checkout</p>
      <h1>确认订单</h1>
    </header>

    <!-- Success -->
    <section v-if="success && createdOrder" class="success-card bento-card">
      <div class="success-icon">
        <Check :size="36" />
      </div>
      <h2>下单成功！</h2>
      <p>订单号：{{ createdOrder.order_id }}</p>
      <p>订单金额：¥{{ createdOrder.total_amount }}</p>
      <div class="success-actions">
        <RouterLink to="/orders" class="black-button">查看订单</RouterLink>
        <RouterLink to="/" class="soft-button">继续购物</RouterLink>
      </div>
    </section>

    <template v-else>
      <!-- Error -->
      <div v-if="error" class="error-banner">{{ error }}</div>

      <div class="checkout-grid">
        <!-- Product summary -->
        <section class="bento-card product-summary-card">
          <h2>商品信息</h2>
          <div class="product-line">
            <div class="product-image-box">
              <img
                v-if="quickProduct.image"
                :src="quickProduct.image"
                :alt="quickProduct.name"
              />
              <ShoppingBag v-else :size="32" />
            </div>
            <div class="product-info">
              <strong>{{ quickProduct.name || '商品' }}</strong>
              <span class="price">¥{{ quickProduct.price || '0.00' }}</span>

              <div class="quantity-control">
                <button
                  type="button"
                  :disabled="quickProduct.quantity <= 1"
                  @click="quickProduct.quantity--"
                >
                  −
                </button>
                <span>{{ quickProduct.quantity }}</span>
                <button
                  type="button"
                  :disabled="!!(productDetail && quickProduct.quantity >= productDetail.stock)"
                  @click="quickProduct.quantity++"
                >
                  +
                </button>
              </div>
            </div>
          </div>
          <div class="total-row">
            <span>合计</span>
            <strong>¥{{ getTotalAmount() }}</strong>
          </div>
        </section>

        <!-- Receiver form -->
        <section class="bento-card receiver-card">
          <h2>收货信息</h2>
          <form class="receiver-form" @submit.prevent="handleSubmit">
            <label>
              收货人
              <input v-model="receiver.name" placeholder="请输入姓名" />
            </label>
            <label>
              手机号
              <input v-model="receiver.phone" placeholder="请输入手机号" />
            </label>
            <label class="span-1">
              收货地址
              <input v-model="receiver.address" placeholder="请输入详细地址" />
            </label>
            <button
              type="submit"
              class="black-button full-button span-1"
              :disabled="loading"
            >
              {{ loading ? '提交中...' : '提交订单' }}
            </button>
          </form>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.checkout-grid {
  display: grid;
  gap: 20px;
}

.product-summary-card,
.receiver-card {
  padding: 28px;
}

.product-summary-card h2,
.receiver-card h2 {
  margin: 0 0 20px;
  font-size: 1.2rem;
}

.product-line {
  display: flex;
  gap: 16px;
  align-items: center;
}

.product-image-box {
  width: 80px;
  height: 80px;
  border-radius: 16px;
  background: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #c7c7cc;
}

.product-image-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.product-info strong {
  font-size: 1.05rem;
}

.price {
  color: #0071e3;
  font-weight: 800;
  font-size: 1.1rem;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.quantity-control button {
  width: 30px;
  height: 30px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  background: #ffffff;
  font-size: 1rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.quantity-control button:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.quantity-control span {
  min-width: 24px;
  text-align: center;
  font-weight: 800;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.total-row span {
  color: #86868b;
}

.total-row strong {
  font-size: 1.4rem;
  color: #0071e3;
}

.receiver-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.span-1 {
  grid-column: span 2;
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

.black-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Success */
.success-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 48px 28px;
  gap: 10px;
}

.success-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #30d158;
  color: #ffffff;
  margin-bottom: 8px;
}

.success-card h2 {
  margin: 0;
  font-size: 1.6rem;
}

.success-card p {
  color: #86868b;
  margin: 0;
}

.success-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
</style>
