<script setup lang="ts">
import { Activity, Bot, Box, CheckCircle2, Clock3, Search, ShoppingBag, Users } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { getAdminOverview, type AdminOverview } from '../services/api'

const data = ref<AdminOverview | null>(null)
const loading = ref(true)
const error = ref('')

const summaryMetrics = computed(() => {
  if (!data.value) return []
  return [
    { label: '用户总数', value: data.value.user_count, icon: Users },
    { label: '商品总数', value: data.value.product_count, icon: Box },
    { label: '订单总数', value: data.value.order_count, icon: ShoppingBag },
    { label: '今日订单', value: data.value.today_order_count, icon: Activity },
  ]
})

const todayActivity = computed(() => {
  if (!data.value) return []
  const items = [
    { label: '自然语言搜索', value: data.value.search_count_today, icon: Search, color: '#0071e3' },
    { label: 'AI 导购对话', value: data.value.ai_chat_count_today, icon: Bot, color: '#7c3aed' },
    { label: '新增订单', value: data.value.today_order_count, icon: ShoppingBag, color: '#059669' },
  ]
  const max = Math.max(...items.map((item) => item.value), 1)
  return items.map((item) => ({
    ...item,
    width: item.value === 0 ? 0 : Math.max((item.value / max) * 100, 8),
  }))
})

const businessStatus = computed(() => {
  if (!data.value) return []
  return [
    { label: '活跃账号', value: data.value.active_user_count, total: data.value.user_count, icon: Users },
    { label: '在售商品', value: data.value.on_sale_product_count, total: data.value.product_count, icon: CheckCircle2 },
    { label: '待支付订单', value: data.value.pending_order_count, total: data.value.order_count, icon: Clock3 },
    { label: '已支付订单', value: data.value.paid_order_count, total: data.value.order_count, icon: ShoppingBag },
  ]
})

const serviceState = computed(() => {
  if (!data.value) return []
  return [
    { label: 'AI Service', value: data.value.ai_service_status },
    { label: 'Vector DB', value: data.value.vector_db_status },
  ]
})

function statusClass(status: string) {
  if (status === 'UP') return 'status-up'
  if (status === 'UNKNOWN') return 'status-unknown'
  return 'status-down'
}

onMounted(async () => {
  try {
    data.value = await getAdminOverview()
  } catch {
    error.value = '监控数据暂时不可用'
    alert('无法获得数据！')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page admin-metrics">
    <div class="admin-metrics-shell">
      <header class="page-title">
        <p>Admin</p>
        <h1>平台监控概览</h1>
        <span>统计口径：当前累计数据与今日 00:00 起的业务活动</span>
      </header>

      <div v-if="loading" class="bento-card metrics-message">正在加载监控数据…</div>
      <div v-else-if="error" class="bento-card metrics-message metrics-error">{{ error }}</div>

      <template v-else>
        <section class="metric-grid">
          <article v-for="metric in summaryMetrics" :key="metric.label" class="bento-card metric-card">
            <component :is="metric.icon" :size="22" />
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </article>
        </section>

        <section class="monitor-grid">
          <article class="bento-card realtime-panel">
            <div class="panel-heading">
              <div>
                <p>今日业务活跃度</p>
                <h2>实时指标</h2>
              </div>
              <Activity :size="24" />
            </div>

            <div class="activity-list">
              <div v-for="item in todayActivity" :key="item.label" class="activity-row">
                <div class="activity-meta">
                  <span><component :is="item.icon" :size="17" />{{ item.label }}</span>
                  <strong>{{ item.value }} 次</strong>
                </div>
                <div class="activity-track">
                  <div class="activity-bar" :style="{ width: `${item.width}%`, background: item.color }" />
                </div>
              </div>
            </div>
          </article>

          <aside class="bento-card service-panel">
            <div class="panel-heading compact">
              <div>
                <p>基础设施</p>
                <h2>服务状态</h2>
              </div>
            </div>
            <div v-for="service in serviceState" :key="service.label" class="service-row">
              <span>{{ service.label }}</span>
              <strong :class="['status-pill', statusClass(service.value)]">{{ service.value }}</strong>
            </div>
          </aside>
        </section>

        <section class="bento-card business-panel">
          <div class="panel-heading compact">
            <div>
              <p>当前数据分布</p>
              <h2>业务状态</h2>
            </div>
          </div>
          <div class="business-grid">
            <div v-for="item in businessStatus" :key="item.label" class="business-item">
              <component :is="item.icon" :size="20" />
              <div>
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <small>占总量 {{ item.total ? Math.round(item.value / item.total * 100) : 0 }}%</small>
              </div>
            </div>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.admin-metrics-shell { width: min(1080px, calc(100% - 40px)); margin: 0 auto; }
.page-title p { color: #6b7280; }
.page-title h1 { margin-top: 6px; font-size: 28px; }
.page-title span { display: block; margin-top: 8px; color: #86868b; font-size: 13px; }
.metrics-message { padding: 28px; text-align: center; color: #6b7280; }
.metrics-error { color: #b42318; }
.monitor-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(250px, .8fr); gap: 18px; margin-top: 18px; }
.realtime-panel, .business-panel { padding: 26px; }
.panel-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.panel-heading p { color: #86868b; font-size: 13px; font-weight: 700; }
.panel-heading h2 { margin-top: 5px; font-size: 22px; }
.panel-heading svg { color: #0071e3; }
.panel-heading.compact { margin-bottom: 14px; }
.activity-list { display: grid; gap: 22px; }
.activity-meta { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 9px; }
.activity-meta span { display: flex; align-items: center; gap: 8px; color: #4b5563; font-weight: 700; }
.activity-meta strong { font-size: 15px; }
.activity-track { height: 10px; overflow: hidden; border-radius: 999px; background: #eef0f3; }
.activity-bar { height: 100%; border-radius: inherit; transition: width .35s ease; }
.service-row { display: flex; justify-content: space-between; align-items: center; min-height: 58px; }
.status-pill { padding: 6px 10px; border-radius: 999px; font-size: 12px; }
.status-up { color: #067647; background: #ecfdf3; }
.status-unknown { color: #b54708; background: #fffaeb; }
.status-down { color: #b42318; background: #fef3f2; }
.business-panel { margin-top: 18px; }
.business-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.business-item { display: flex; gap: 13px; padding: 18px; border-radius: 16px; background: #f7f8fa; }
.business-item svg { flex: none; color: #0071e3; }
.business-item div { display: grid; gap: 4px; }
.business-item span, .business-item small { color: #86868b; }
.business-item strong { font-size: 24px; }
.business-item small { font-size: 12px; }
@media (max-width: 800px) {
  .monitor-grid { grid-template-columns: 1fr; }
  .business-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .admin-metrics-shell { width: min(100% - 24px, 1080px); }
  .business-grid { grid-template-columns: 1fr; }
}
</style>
