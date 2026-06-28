<script setup lang="ts">
import { Camera, Search, Sparkles } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import ProductGrid from '../components/ProductGrid.vue'
import {
  getProductList,
  homeRecommendations,
  imageSearch,
  recordBehavior,
  semanticSearch,
  type ProductSummary,
} from '../services/api.ts'
import { isLoggedIn } from '../stores/session.ts'

const searchQuery = ref('')
const products = ref<ProductSummary[]>([])
const recommendations = ref<ProductSummary[]>([])
const loading = ref(false)
const activeCategory = ref('全部')
const searchMode = ref<'semantic' | 'image'>('semantic')
const imageFile = ref<File | null>(null)
const searchError = ref('')
const searchNotice = ref('')
const focusProduct = computed(() => products.value[0])

const categories = [
  { id: '', label: '全部' },
  { id: 'c_headphone', label: '耳机' },
  { id: 'c_phone', label: '手机' },
  { id: 'c_computer', label: '电脑' },
  { id: 'c_accessory', label: '配件' },
  { id: 'c_home', label: '家居' },
  { id: 'c_food', label: '食品' },
  { id: 'c_clothing', label: '服装' },
  { id: 'c_books', label: '图书' },
]

onMounted(loadHome)

async function loadHome() {
  loading.value = true
  searchError.value = ''
  searchNotice.value = ''
  try {
    const [page, recs] = await Promise.all([
      getProductList(),
      homeRecommendations(),
    ])
    products.value = page.items
    recommendations.value = recs.items
  } catch {
    // fallback data handled in API layer
  } finally {
    loading.value = false
  }
}

async function handleCategoryClick(category: (typeof categories)[0]) {
  activeCategory.value = category.label
  loading.value = true
  searchError.value = ''
  searchNotice.value = ''
  try {
    const params: any = {}
    if (category.id) params.category_id = category.id
    const page = await getProductList(params)
    products.value = page.items

    if (isLoggedIn()) {
      recordBehavior({
        event_type: 'CLICK',
        metadata: { category: category.id || 'all', page: 'home' },
      }).catch(() => {})
    }
  } catch {
    // fallback
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  const query = searchQuery.value.trim()
  if (!query) return

  loading.value = true
  searchError.value = ''
  searchNotice.value = ''

  try {
    if (searchMode.value === 'semantic') {
      const result = await semanticSearch(query)
      products.value = result.items
      if (result.relaxed) {
        searchNotice.value = result.items.length
          ? '没有找到精确匹配，下面展示的是热门商品。'
          : '没有找到符合品类或预算条件的商品，请调整描述后重试。'
      }
    } else if (imageFile.value) {
      const result = await imageSearch(imageFile.value)
      products.value = result.items
    }

    if (isLoggedIn()) {
      recordBehavior({
        event_type: 'SEARCH',
        query,
        metadata: { mode: searchMode.value, source: 'home_search' },
      }).catch(() => {})
    }
  } catch {
    searchError.value = '搜索失败，请重试'
  } finally {
    loading.value = false
  }
}

function handleImageUpload(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files?.[0]) {
    imageFile.value = input.files[0]
    searchMode.value = 'image'
    handleSearch()
  }
}
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
          <input
            v-model="searchQuery"
            aria-label="AI 搜索"
            placeholder="用一句话描述你想要的..."
          />
          <label class="ghost-icon-button" title="视觉识图">
            <Camera :size="22" />
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              style="display: none"
              @change="handleImageUpload"
            />
          </label>
          <button type="submit" class="black-button" :disabled="loading">
            <Search :size="18" />
            <span>{{ loading ? '搜索中' : '搜索' }}</span>
          </button>
        </form>

        <p v-if="searchError" class="search-error">{{ searchError }}</p>
        <p v-else-if="searchNotice" class="search-notice">{{ searchNotice }}</p>
        <p v-else class="hero-hint">试着说："适合上班通勤、预算三百元以内的降噪耳机"</p>
      </div>
    </section>

    <section class="category-strip" aria-label="商品分类">
      <button
        v-for="category in categories"
        :key="category.id"
        type="button"
        :class="{ active: activeCategory === category.label }"
        @click="handleCategoryClick(category)"
      >
        {{ category.label }}
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
        <article v-if="focusProduct" class="feature-card">
          <div>
            <span>今日焦点</span>
            <h3>{{ focusProduct.name }}</h3>
            <p>{{ focusProduct.reason || `热度 ${focusProduct.sales ?? 0}，评分 ${focusProduct.rating ?? '暂无'}` }}</p>
            <RouterLink :to="`/detail/${focusProduct.product_id}`" class="white-button">了解详情</RouterLink>
          </div>
          <div class="feature-image">
            <img :src="focusProduct.image_url" :alt="focusProduct.name" />
          </div>
        </article>

        <!-- Empty state -->
        <div v-if="!loading && products.length === 0" class="empty-products">
          <p>暂无商品，试试其他关键词或分类</p>
        </div>

        <ProductGrid v-else :products="products" />
      </div>
    </section>

    <section class="content-section compact-section">
      <div class="section-heading">
        <div>
          <p>Personalized</p>
          <h2>为你保留的灵感</h2>
        </div>
      </div>

      <!-- Loading skeleton for recommendations -->
      <div v-if="loading && recommendations.length === 0" class="loading-row">
        <span>加载推荐中...</span>
      </div>

      <div v-else-if="recommendations.length > 0" class="recommendation-row">
        <RouterLink
          v-for="item in recommendations"
          :key="item.product_id"
          :to="`/detail/${item.product_id}`"
          class="mini-product"
        >
          <img :src="item.image_url" :alt="item.name" loading="lazy" />
          <div>
            <strong>{{ item.name }}</strong>
            <span>¥{{ item.price }}</span>
          </div>
        </RouterLink>
      </div>

      <div v-else class="loading-row">
        <span>暂无个性化推荐，多浏览商品以获取推荐</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.category-strip button.active {
  background: #1d1d1f;
  color: #ffffff;
}

.search-error {
  margin: 18px 0 0;
  color: #d32f2f;
  font-size: 0.88rem;
}

.search-notice {
  margin: 18px 0 0;
  color: #8a5a00;
  font-size: 0.88rem;
}

.empty-products {
  display: flex;
  justify-content: center;
  padding: 60px 0;
  color: #86868b;
  font-size: 0.95rem;
}

.loading-row {
  display: flex;
  justify-content: center;
  padding: 40px 0;
  color: #86868b;
  font-size: 0.9rem;
}

.black-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
