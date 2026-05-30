<script setup lang="ts">
import { Camera, Search, Sparkles } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import ProductGrid from '../components/ProductGrid.vue'
import { homeRecommendations, listProducts, semanticSearch, type ProductSummary } from '../services/api'

const searchQuery = ref('300 元以内适合通勤的蓝牙降噪耳机')
const products = ref<ProductSummary[]>([])
const recommendations = ref<ProductSummary[]>([])
const loading = ref(false)

const categories = ['全部', '数码周边', '家居美学', '穿搭灵感', '户外运动', '办公好物']

async function load() {
  loading.value = true
  const [page, recs] = await Promise.all([listProducts(), homeRecommendations()])
  products.value = page.items
  recommendations.value = recs
  loading.value = false
}

async function handleSearch() {
  loading.value = true
  products.value = await semanticSearch(searchQuery.value)
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="page home-page">
    <section class="home-hero">
      <div class="hero-copy">
        <span class="ai-chip">New Intelligence</span>
        <h1>
          遇见你的下一件
          <span>心仪之物。</span>
        </h1>

        <form class="ai-search-box" @submit.prevent="handleSearch">
          <Sparkles :size="20" class="ai-search-icon" />
          <input v-model="searchQuery" aria-label="AI 搜索" placeholder="用一句话描述你想要的..." />
          <button type="button" class="ghost-icon-button" title="视觉识图">
            <Camera :size="22" />
          </button>
          <button type="submit" class="black-button">
            <Search :size="18" />
            <span>搜索</span>
          </button>
        </form>

        <p class="hero-hint">试着说：“适合上班通勤、预算三百元以内的降噪耳机”</p>
      </div>
    </section>

    <section class="category-strip" aria-label="商品分类">
      <button v-for="category in categories" :key="category" type="button">
        {{ category }}
      </button>
    </section>

    <section class="content-section">
      <div class="section-heading">
        <div>
          <p>AI 智能发现</p>
          <h2>{{ loading ? '正在生成结果' : '基于偏好的实时推荐' }}</h2>
        </div>
        <RouterLink to="/compare">进入 AI 对比</RouterLink>
      </div>

      <div class="discovery-grid">
        <article class="feature-card">
          <div>
            <span>今日焦点</span>
            <h3>下一代音频体验</h3>
            <p>AI 会根据通勤、办公和听歌习惯，匹配更合适的声学配置。</p>
            <RouterLink to="/detail/10001" class="white-button">了解详情</RouterLink>
          </div>
          <div class="feature-image">
            <img :src="products[0]?.image_url" alt="蓝牙降噪耳机" />
          </div>
        </article>

        <ProductGrid :products="products" />
      </div>
    </section>

    <section class="content-section compact-section">
      <div class="section-heading">
        <div>
          <p>Personalized</p>
          <h2>为你保留的灵感</h2>
        </div>
      </div>
      <div class="recommendation-row">
        <RouterLink
          v-for="item in recommendations"
          :key="item.product_id"
          :to="`/detail/${item.product_id}`"
          class="mini-product"
        >
          <img :src="item.image_url" :alt="item.name" />
          <div>
            <strong>{{ item.name }}</strong>
            <span>¥{{ item.price }}</span>
          </div>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
