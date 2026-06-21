<script setup lang="ts">
import { Check, GitCompareArrows, ShoppingBag, Sparkles } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail, recordBehavior, type Product } from '../services/api'
import { isLoggedIn } from '../stores/session'
import { addCompareProduct } from '../stores/compare'

const route = useRoute()
const router = useRouter()

const product = ref<Product | null>(null)
const loading = ref(true)
const error = ref('')
const selectedImage = ref(0)

onMounted(async () => {
  const id = route.params.id as string
  loading.value = true
  try {
    product.value = await getProductDetail(id)
    // Record view behavior
    if (isLoggedIn()) {
      recordBehavior({
        event_type: 'VIEW',
        product_id: id,
        metadata: { page: 'product_detail' },
      }).catch(() => {})
    }
  } catch (e: any) {
    error.value = e.message || '加载商品详情失败'
  } finally {
    loading.value = false
  }
})

function buyNow() {
  if (!isLoggedIn()) {
    router.push('/login')
    return
  }
  if (product.value) {
    router.push({
      path: '/checkout',
      query: {
        product_id: product.value.product_id,
        name: product.value.name,
        price: product.value.price,
        image: product.value.image_urls?.[0] || '',
      },
    })
  }
}

function addToCompare() {
  if (!product.value) return
  const added = addCompareProduct(product.value.product_id)
  router.push({
    path: '/compare',
    query: added ? {} : { notice: '对比清单最多支持4件商品' },
  })
}
</script>

<template>
  <div class="page detail-page">
    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <p>加载商品详情中...</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <RouterLink to="/" class="black-button">返回首页</RouterLink>
    </div>

    <!-- Product Detail -->
    <template v-else-if="product">
      <section class="detail-gallery">
        <div class="detail-hero-image">
          <img
            :src="product.image_urls[selectedImage] || product.image_urls[0]"
            :alt="product.name"
          />
        </div>
        <div v-if="product.image_urls.length > 1" class="thumb-grid">
          <button
            v-for="(url, idx) in product.image_urls"
            :key="idx"
            type="button"
            class="thumb-tile"
            :class="{ active: selectedImage === idx }"
            :title="`缩略图 ${idx + 1}`"
            @click="selectedImage = idx"
          >
            <img :src="url" alt="" />
          </button>
        </div>
      </section>

      <aside class="detail-info">
        <span class="ai-chip">AI Verified</span>
        <h1>{{ product.name }}</h1>
        <p class="detail-price">¥ {{ product.price }}</p>

        <section class="ai-reason-card">
          <Sparkles :size="18" />
          <div>
            <h3>为什么它适合你？</h3>
            <p>
              {{ product.description || 'AI 分析认为该商品与你的浏览偏好和购买历史高度匹配。' }}
            </p>
          </div>
        </section>

        <div class="score-grid">
          <article>
            <span>AI 匹配度</span>
            <strong>{{ Math.round(product.rating * 20) }}%</strong>
          </article>
          <article>
            <span>用户评分</span>
            <strong>{{ product.rating }} / 5.0</strong>
          </article>
        </div>

        <ul class="detail-points">
          <li v-if="product.stock > 0">
            <Check :size="17" /> 库存充足（{{ product.stock }} 件）
          </li>
          <li v-else>
            <Check :size="17" style="color: #ff375f;" /> 暂时缺货
          </li>
          <li><Check :size="17" /> 已售 {{ product.sales }} 件</li>
          <li>
            <Check :size="17" />
            标签：{{ product.tags?.join('、') || '暂无' }}
          </li>
        </ul>

        <div class="detail-actions">
          <button
            type="button"
            class="black-button full-button"
            :disabled="product.stock <= 0"
            @click="buyNow"
          >
            <ShoppingBag :size="18" />
            <span>{{ product.stock > 0 ? '立即选购' : '暂时缺货' }}</span>
          </button>
          <button type="button" class="soft-button full-button" @click="addToCompare">
            <GitCompareArrows :size="18" />
            <span>加入对比清单</span>
          </button>
        </div>
      </aside>
    </template>
  </div>
</template>

<style scoped>
.loading-state,
.error-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
  color: #86868b;
}

.error-state p {
  color: #d32f2f;
}

.thumb-tile.active {
  border-color: #0071e3;
}

.black-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.black-button:disabled:hover {
  transform: none;
  background: #1d1d1f;
}
</style>
