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
    <div class="max-w-[1000px] mx-auto px-6">
      <header class="page-title">
        <p style="color:#6b7280">Admin</p>
        <h1 style="font-size:26px;margin-top:6px">平台监控概览</h1>
      </header>

      <section class="bento-card" style="display:flex;gap:12px;padding:14px">
        <div style="flex:1">
          <h3 style="margin-bottom:8px;color:#6b7280">主要指标</h3>
          <div class="metric-row" v-for="m in metrics" :key="m.label" style="display:flex;justify-content:space-between;padding:8px;border-bottom:1px dashed #eef2f7">
            <span>{{ m.label }}</span>
            <strong>{{ m.value }}</strong>
          </div>
        </div>
        <div style="width:260px">
          <h3 style="margin-bottom:8px;color:#6b7280">服务状态</h3>
          <div v-for="s in serviceState" :key="s.label" class="service-row" style="display:flex;justify-content:space-between;padding:8px;border-bottom:1px dashed #eef2f7">
            <span>{{ s.label }}</span>
            <strong>{{ s.value }}</strong>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
