<script setup lang="ts">
import { Check, Plus, Save, Sparkles, Trophy, X } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  compareProducts,
  getProductDetail,
  getProductList,
  type CompareReport,
  type ProductSummary,
} from '../services/api'
import {
  MAX_COMPARE_PRODUCTS,
  addCompareProduct,
  clearCompareProducts,
  compareState,
  removeCompareProduct,
} from '../stores/compare'

const route = useRoute()
const router = useRouter()

const catalog = ref<ProductSummary[]>([])
const loadingCatalog = ref(true)
const comparing = ref(false)
const error = ref('')
const savedMessage = ref('')
const report = ref<CompareReport | null>(null)
const intent = ref('适合日常使用，兼顾价格、口碑和实用性')

const selectedProducts = computed(() =>
  compareState.productIds
    .map((id) => catalog.value.find((product) => product.product_id === id))
    .filter((product): product is ProductSummary => Boolean(product)),
)
const winner = computed(() =>
  selectedProducts.value.find((product) => product.product_id === report.value?.winner_product_id),
)

onMounted(async () => {
  if (route.query.notice) {
    error.value = String(route.query.notice)
  }
  const queryProductId = String(route.query.product_id || '')
  if (queryProductId) addCompareProduct(queryProductId)

  try {
    const page = await getProductList({ size: 100 })
    catalog.value = page.items

    const missingIds = compareState.productIds.filter(
      (id) => !catalog.value.some((product) => product.product_id === id),
    )
    const missingProducts = await Promise.all(missingIds.map(async (id) => {
      const product = await getProductDetail(id)
      return {
        product_id: product.product_id,
        name: product.name,
        price: product.price,
        stock: product.stock,
        image_url: product.image_urls?.[0] || '',
        sales: product.sales,
        rating: product.rating,
        tags: product.tags,
      } satisfies ProductSummary
    }))
    catalog.value.push(...missingProducts)
  } catch (e: any) {
    error.value = e.message || '商品列表加载失败'
  } finally {
    loadingCatalog.value = false
  }
})

function isSelected(productId: string) {
  return compareState.productIds.includes(productId)
}

function toggleProduct(productId: string) {
  error.value = ''
  savedMessage.value = ''
  report.value = null
  if (isSelected(productId)) {
    removeCompareProduct(productId)
    return
  }
  if (!addCompareProduct(productId)) {
    error.value = `最多只能同时对比 ${MAX_COMPARE_PRODUCTS} 件商品`
  }
}

function removeProduct(productId: string) {
  removeCompareProduct(productId)
  report.value = null
}

function clearAll() {
  clearCompareProducts()
  report.value = null
  error.value = ''
}

async function runComparison() {
  if (compareState.productIds.length < 2) {
    error.value = '请至少选择两件商品'
    return
  }

  error.value = ''
  savedMessage.value = ''
  comparing.value = true
  try {
    report.value = await compareProducts(compareState.productIds, intent.value.trim())
  } catch (e: any) {
    error.value = e.message || '生成对比报告失败，请稍后重试'
  } finally {
    comparing.value = false
  }
}

function reportItem(productId: string) {
  return report.value?.items.find((item) => item.product_id === productId)
}

function buyWinner() {
  if (!winner.value) return
  router.push({
    path: '/checkout',
    query: {
      product_id: winner.value.product_id,
      name: winner.value.name,
      price: winner.value.price,
      image: winner.value.image_url,
    },
  })
}

function saveReport() {
  if (!report.value) return
  const saved = {
    saved_at: new Date().toISOString(),
    products: selectedProducts.value,
    report: report.value,
  }
  try {
    const stored = JSON.parse(localStorage.getItem('ai-shopping-compare-reports') || '[]')
    const reports = Array.isArray(stored) ? stored : []
    reports.unshift(saved)
    localStorage.setItem('ai-shopping-compare-reports', JSON.stringify(reports.slice(0, 10)))
  } catch {
    // Downloading the report still works if local storage is disabled or malformed.
  }

  const blob = new Blob([JSON.stringify(saved, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `AI商品对比-${Date.now()}.json`
  link.click()
  URL.revokeObjectURL(url)
  savedMessage.value = '报告已保存到本机'
}
</script>

<template>
  <div class="page compare-page">
    <header class="page-title centered">
      <p>AI Product Comparison</p>
      <h1>让商品自己把差异说清楚</h1>
      <span>选择 2–4 件商品，再告诉 AI 你真正关心什么。</span>
    </header>

    <section class="bento-card compare-builder">
      <div class="builder-heading">
        <div>
          <h2>对比清单</h2>
          <p>已选择 {{ selectedProducts.length }} / {{ MAX_COMPARE_PRODUCTS }} 件</p>
        </div>
        <button v-if="selectedProducts.length" type="button" class="text-button" @click="clearAll">
          清空
        </button>
      </div>

      <div v-if="selectedProducts.length" class="selected-strip">
        <article v-for="product in selectedProducts" :key="product.product_id">
          <img :src="product.image_url" :alt="product.name" />
          <div>
            <strong>{{ product.name }}</strong>
            <span>¥{{ product.price }}</span>
          </div>
          <button type="button" :aria-label="`移除${product.name}`" @click="removeProduct(product.product_id)">
            <X :size="15" />
          </button>
        </article>
      </div>
      <p v-else class="empty-compare">还没有商品，先从下方挑两件。</p>

      <label class="intent-field">
        <span>你的购买需求</span>
        <textarea
          v-model="intent"
          maxlength="500"
          rows="2"
          placeholder="例如：预算 500 元以内，主要用于通勤，优先轻便和续航"
        />
      </label>

      <button
        type="button"
        class="black-button compare-submit"
        :disabled="comparing || selectedProducts.length < 2"
        @click="runComparison"
      >
        <Sparkles :size="18" />
        {{ comparing ? 'AI 正在分析...' : '生成 AI 对比报告' }}
      </button>
      <p v-if="error" class="compare-error">{{ error }}</p>
    </section>

    <section class="catalog-section">
      <div class="section-heading">
        <div>
          <p>选择商品</p>
          <h2>可加入对比的商品</h2>
        </div>
      </div>
      <div v-if="loadingCatalog" class="compare-loading">正在加载商品...</div>
      <div v-else class="compare-picker">
        <button
          v-for="product in catalog"
          :key="product.product_id"
          type="button"
          :class="{ selected: isSelected(product.product_id) }"
          @click="toggleProduct(product.product_id)"
        >
          <img :src="product.image_url" :alt="product.name" />
          <span>
            <strong>{{ product.name }}</strong>
            <small>¥{{ product.price }} · {{ product.rating ?? '暂无评分' }}</small>
          </span>
          <Check v-if="isSelected(product.product_id)" :size="18" />
          <Plus v-else :size="18" />
        </button>
      </div>
    </section>

    <section v-if="report" class="compare-layout">
      <aside class="compare-sidebar">
        <article class="bento-card ai-final">
          <div class="trophy-box"><Trophy :size="26" /></div>
          <span class="report-source">{{ report.source === 'AI' ? 'AI 模型分析' : '真实数据规则分析' }}</span>
          <h2>最终建议：{{ winner?.name }}</h2>
          <p>{{ report.summary }}</p>
          <div class="point-list">
            <span v-for="point in report.highlights" :key="point">
              <Check :size="15" />
              {{ point }}
            </span>
          </div>
        </article>

        <article class="bento-card attr-card">
          <h3>核心属性分布</h3>
          <div v-for="dimension in report.dimensions" :key="dimension.name" class="dimension-block">
            <strong>{{ dimension.name }}</strong>
            <div v-for="product in selectedProducts" :key="product.product_id" class="dimension-row">
              <span>{{ product.name }}</span>
              <div class="bar-track">
                <div :style="{ width: `${dimension.scores[product.product_id] ?? 0}%` }"></div>
              </div>
              <b>{{ dimension.scores[product.product_id] ?? 0 }}</b>
            </div>
          </div>
        </article>
      </aside>

      <section class="compare-main">
        <div class="compare-card">
          <article
            v-for="product in selectedProducts"
            :key="product.product_id"
            class="compare-product"
            :class="{ winner: product.product_id === report.winner_product_id }"
          >
            <span v-if="product.product_id === report.winner_product_id" class="winner-badge">
              Best Match
            </span>
            <div class="compare-image">
              <img :src="product.image_url" :alt="product.name" />
            </div>
            <h3>{{ product.name }}</h3>
            <p :class="{ 'blue-price': product.product_id === report.winner_product_id }">
              ¥ {{ product.price }}
            </p>
            <strong class="compare-score">{{ reportItem(product.product_id)?.score ?? 0 }} 分</strong>
            <div class="comment-box">{{ reportItem(product.product_id)?.verdict }}</div>
            <div class="pros-cons">
              <div>
                <b>优势</b>
                <span v-for="item in reportItem(product.product_id)?.strengths" :key="item">{{ item }}</span>
              </div>
              <div>
                <b>注意</b>
                <span v-for="item in reportItem(product.product_id)?.weaknesses" :key="item">{{ item }}</span>
              </div>
            </div>
          </article>
        </div>

        <div class="compare-actions">
          <button type="button" class="black-button" :disabled="!winner" @click="buyWinner">
            购买 Best Match
          </button>
          <button type="button" class="soft-button" @click="saveReport">
            <Save :size="17" />
            <span>保存报告</span>
          </button>
          <span v-if="savedMessage" class="saved-message">{{ savedMessage }}</span>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.compare-builder {
  max-width: 980px;
  margin: 0 auto 44px;
  padding: 28px;
}

.builder-heading,
.selected-strip article,
.intent-field,
.dimension-row {
  display: flex;
  align-items: center;
}

.builder-heading {
  justify-content: space-between;
}

.builder-heading h2,
.builder-heading p {
  margin: 0;
}

.builder-heading p,
.empty-compare {
  color: #86868b;
  font-size: 0.9rem;
}

.text-button {
  border: 0;
  background: transparent;
  color: #d32f2f;
  cursor: pointer;
}

.selected-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
  margin: 22px 0;
}

.selected-strip article {
  position: relative;
  gap: 10px;
  padding: 10px;
  border-radius: 14px;
  background: #f5f5f7;
}

.selected-strip img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
}

.selected-strip div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.selected-strip strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-strip article button {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.intent-field {
  align-items: stretch;
  flex-direction: column;
  gap: 8px;
  margin: 20px 0 16px;
  font-weight: 700;
}

.intent-field textarea {
  resize: vertical;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 14px;
  padding: 13px 15px;
  font: inherit;
}

.compare-submit {
  gap: 8px;
}

.compare-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.compare-error {
  color: #d32f2f;
}

.catalog-section {
  margin-bottom: 48px;
}

.compare-picker {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px;
}

.compare-picker button {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 16px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.compare-picker button.selected {
  border-color: #0071e3;
  background: #f0f7ff;
}

.compare-picker img {
  width: 58px;
  height: 58px;
  border-radius: 12px;
  object-fit: cover;
}

.compare-picker span {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.compare-picker small {
  margin-top: 5px;
  color: #86868b;
}

.compare-loading {
  padding: 40px;
  text-align: center;
  color: #86868b;
}

.report-source {
  color: #0071e3;
  font-size: 0.78rem;
  font-weight: 800;
  text-transform: uppercase;
}

.dimension-block {
  margin-top: 18px;
}

.dimension-row {
  gap: 8px;
  margin-top: 8px;
  font-size: 0.78rem;
}

.dimension-row > span {
  width: 78px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dimension-row .bar-track {
  flex: 1;
}

.dimension-row b {
  width: 24px;
  text-align: right;
}

.compare-score {
  display: block;
  margin-bottom: 12px;
  color: #0071e3;
}

.pros-cons {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  text-align: left;
}

.pros-cons div,
.pros-cons span {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.pros-cons span {
  color: #6e6e73;
  font-size: 0.78rem;
}

.saved-message {
  color: #248a3d;
  font-size: 0.85rem;
}

@media (max-width: 720px) {
  .compare-builder {
    padding: 20px;
  }

  .selected-strip,
  .compare-picker {
    grid-template-columns: 1fr;
  }
}
</style>
