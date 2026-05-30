<script setup lang="ts">
import { Plus, Sparkles, Star } from 'lucide-vue-next'
import type { ProductSummary } from '../services/api'

defineProps<{
  products: ProductSummary[]
}>()
</script>

<template>
  <div class="product-grid">
    <RouterLink
      v-for="product in products"
      :key="product.product_id"
      :to="`/detail/${product.product_id}`"
      class="product-card"
    >
      <div class="product-media">
        <img :src="product.image_url" :alt="product.name" loading="lazy" />
      </div>
      <div class="product-content">
        <div class="match-row">
          <span>
            <Sparkles :size="13" />
            AI 匹配度 {{ product.score ? Math.round(product.score * 100) : 98 }}%
          </span>
          <span>
            <Star :size="13" />
            {{ product.rating ?? '4.8' }}
          </span>
        </div>
        <h3>{{ product.name }}</h3>
        <p>{{ product.reason || '符合你的空间美学与使用偏好' }}</p>
        <div class="product-footer">
          <strong>¥{{ product.price }}</strong>
          <span class="circle-button" :title="`加入购物车：${product.name}`">
            <Plus :size="18" />
          </span>
        </div>
      </div>
    </RouterLink>
  </div>
</template>
