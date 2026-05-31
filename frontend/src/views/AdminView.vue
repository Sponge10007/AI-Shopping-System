<script setup lang="ts">
import { Activity, Bot, Database, Package, ShoppingBag, Users } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { getAdminOverview } from '../services/api'

const metrics = ref([
  { label: '用户', value: '0', icon: Users },
  { label: '商品', value: '0', icon: Package },
  { label: '订单', value: '0', icon: ShoppingBag },
  { label: '今日订单', value: '0', icon: Activity },
])
const serviceState = ref([
  { label: 'AI Service', value: 'UNKNOWN', icon: Bot },
  { label: 'Vector DB', value: 'UNKNOWN', icon: Database },
])

onMounted(async () => {
  const data = await getAdminOverview()
  metrics.value = [
    { label: '用户', value: String(data.user_count), icon: Users },
    { label: '商品', value: String(data.product_count), icon: Package },
    { label: '订单', value: String(data.order_count), icon: ShoppingBag },
    { label: '今日订单', value: String(data.today_order_count), icon: Activity },
  ]
  serviceState.value = [
    { label: 'AI Service', value: data.ai_service_status, icon: Bot },
    { label: 'Vector DB', value: data.vector_db_status, icon: Database },
  ]
})
</script>

<template>
  <div class="page admin-page">
    <header class="page-title centered">
      <p>Admin Console</p>
      <h1>平台智能监控</h1>
      <span>用户、商品、交易和 AI 服务状态集中展示。</span>
    </header>

    <section class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" class="bento-card metric-card">
        <component :is="metric.icon" :size="22" />
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </article>
    </section>

    <section class="admin-grid">
      <article class="bento-card admin-insight">
        <span class="ai-chip small-chip">AI Insight</span>
        <h2>平台运行建议</h2>
        <p>
          当前商品和订单规模适合先开启语义搜索缓存。推荐将 AI 搜索、首页推荐和商品详情摘要作为第一批监控指标。
        </p>
      </article>

      <article class="bento-card service-panel">
        <h2>服务状态</h2>
        <div v-for="service in serviceState" :key="service.label" class="service-row">
          <component :is="service.icon" :size="18" />
          <span>{{ service.label }}</span>
          <strong>{{ service.value }}</strong>
        </div>
      </article>
    </section>
  </div>
</template>
