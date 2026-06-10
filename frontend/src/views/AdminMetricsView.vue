<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminOverview } from '../services/api'

const metrics = ref([] as Array<{ label: string; value: string; icon?: any }>)
const serviceState = ref([] as Array<{ label: string; value: string; icon?: any }>)

onMounted(async () => {
  try{
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
  }
  catch{
    alert('无法获得数据！')
  }
  
})
</script>

<template>
  <div class="page admin-metrics">
    <div class="max-w-[1000px] mx-auto px-6">
      <header class="page-title">
        <p style="color:#6b7280">Admin</p>
        <h1 style="font-size:26px;margin-top:6px">平台监控概览</h1>
      </header>

      <section style="display:flex;gap:20px;align-items:flex-start;padding:0;margin-top:6px">
        <div style="flex:1">
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:14px">
            <div v-for="m in metrics" :key="m.label" style="background:#fff;padding:14px;border-radius:10px;box-shadow:0 4px 12px rgba(15,23,42,0.06);text-align:center">
              <div style="color:#6b7280;font-size:12px">{{ m.label }}</div>
              <div style="font-size:20px;font-weight:700;margin-top:8px">{{ m.value }}</div>
            </div>
          </div>

          <div class="bento-card" style="padding:14px;background:#fff;border-radius:10px;box-shadow:0 6px 20px rgba(2,6,23,0.04)">
            <h3 style="margin:0 0 8px 0;color:#111827">实时指标（占位）</h3>
            <p style="color:#6b7280;margin:0">此处可放置图表或时间序列趋势视图，展示关键指标变化。</p>
          </div>
        </div>

        <aside style="width:300px">
          <div style="background:linear-gradient(180deg,#fff,#f8fafc);padding:12px;border-radius:10px;box-shadow:0 6px 18px rgba(2,6,23,0.04)">
            <h3 style="margin:0 0 10px 0;color:#111827">服务状态</h3>
            <div v-for="s in serviceState" :key="s.label" style="display:flex;justify-content:space-between;align-items:center;padding:10px;border-radius:8px;background:#fff;margin-bottom:8px;box-shadow:0 2px 8px rgba(2,6,23,0.03)">
              <div style="font-weight:600">{{ s.label }}</div>
              <div :style="{padding:'6px 10px',borderRadius:'999px',fontWeight:600,color: s.value==='OK' ? '#065f46' : s.value==='UNKNOWN' ? '#92400e' : '#7f1d1d',background: s.value==='OK' ? '#ecfdf5' : s.value==='UNKNOWN' ? '#fff7ed' : '#fff1f2'}">{{ s.value }}</div>
            </div>
          </div>
        </aside>
      </section>
    </div>
  </div>
</template>
