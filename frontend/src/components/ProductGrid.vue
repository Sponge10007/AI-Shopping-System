<script setup lang="ts">
import { Plus, Sparkles, Star } from 'lucide-vue-next'
import type { ProductSummary } from '../services/api'

defineProps<{
  products: ProductSummary[]
}>()

const emit = defineEmits<{
  select: [product: ProductSummary]
  add: [product: ProductSummary]
}>()
</script>

<template>
  <div v-if="products.length === 0" class="empty-state">
    <p>暂无商品，试试其他关键词或分类</p>
  </div>
  <div v-else class="product-grid">
    <RouterLink
      v-for="product in products"
      :key="product.product_id"
      :to="`/detail/${product.product_id}`"
      class="product-card"
      @click="emit('select', product)"
    >
      <div class="product-media">
        <img :src="product.image_url" :alt="product.name" loading="lazy" />
      </div>
      <div class="product-content">
        <div class="match-row">
          <span>
            <Sparkles :size="13" />
            <template v-if="product.score != null">
              AI 匹配度 {{ Math.round(product.score * 100) }}%
            </template>
            <template v-else>热门推荐</template>
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
          <button
            type="button"
            class="circle-button"
            :title="`加入购物车：${product.name}`"
            @click.prevent.stop="emit('add', product)"
          >
            <Plus :size="18" />
          </button>
        </div>
      </div>
    </RouterLink>
  </div>
</template>
