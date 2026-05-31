<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminOverview } from '../services/api'

const metrics = ref([] as Array<{ label: string; value: string; icon?: any }>)
const serviceState = ref([] as Array<{ label: string; value: string; icon?: any }>)

onMounted(async () => {
  const data = await getAdminOverview()
  metrics.value = [
    { label: '用户', value: String(data.user_count) },
    { label: '商品', value: String(data.product_count) },
    { label: '订单', value: String(data.order_count) },
    { label: '今日订单', value: String(data.today_order_count) },
  ]
  serviceState.value = [
    { label: 'AI Service', value: data.ai_service_status },
    { label: 'Vector DB', value: data.vector_db_status },
  ]
})
</script>

<template>
  <div class="page admin-metrics">
    <header class="page-title">
      <p>Admin</p>
      <h1>平台监控概览</h1>
    </header>

    <section class="bento-card">
      <div class="metric-row" v-for="m in metrics" :key="m.label">
        <span>{{ m.label }}</span>
        <strong>{{ m.value }}</strong>
      </div>
    </section>

    <section class="bento-card">
      <h2>服务状态</h2>
      <div v-for="s in serviceState" :key="s.label" class="service-row">
        <span>{{ s.label }}</span>
        <strong>{{ s.value }}</strong>
      </div>
    </section>
  </div>
</template>
