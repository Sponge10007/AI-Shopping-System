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

    <section class="admin-actions">
      <div class="bento-card action-card">
        <h2>用户管理</h2>
        <p>管理平台用户、商家和管理员的状态与权限。</p>
        <RouterLink to="/admin/users" class="black-button">进入用户管理</RouterLink>
      </div>

      <div class="bento-card action-card">
        <h2>平台监控</h2>
        <p>查看商品、订单、AI 服务和向量库的实时状态概览。</p>
        <RouterLink to="/admin/metrics" class="black-button">查看监控概览</RouterLink>
      </div>
    </section>
  </div>
</template>
