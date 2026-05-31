<script setup lang="ts">
import { Activity, Bot, Database, Package, ShoppingBag, Users } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { getAdminOverview } from '../services/api'
</script>

<template>
  <div class="page admin-page">
    <div class="max-w-[1000px] mx-auto px-6">
      <header style="margin-bottom:20px">
        <div style="background:linear-gradient(90deg,#0b1220 0%,#071029 100%);color:#fff;padding:22px;border-radius:12px;box-shadow:0 6px 20px rgba(2,6,23,0.45)">
          <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
            <div style="flex:1;min-width:260px">
              <p style="opacity:0.85;margin:0">Admin Console</p>
              <h1 style="font-size:30px;margin:6px 0 0 0">平台智能监控</h1>
              <p style="color:rgba(255,255,255,0.85);margin-top:8px">集中展示用户、商品、订单与 AI 服务状态，支持一键跳转至管理面板。</p>
            </div>
          </div>
        </div>
      </header>

      <section style="display:flex;gap:16px;flex-wrap:wrap;margin-top:12px">
        <div class="bento-card action-card" style="flex:1;min-width:280px;padding:18px;display:flex;flex-direction:column;gap:12px">
          <h2 style="margin:0">用户管理</h2>
          <p style="color:#6b7280;margin:0">管理平台用户、商家和管理员的状态与权限。</p>
          <div style="margin-top:auto">
            <RouterLink to="/admin/users" class="black-button">进入用户管理</RouterLink>
          </div>
        </div>

        <div class="bento-card action-card" style="flex:1;min-width:280px;padding:18px;display:flex;flex-direction:column;gap:12px">
          <h2 style="margin:0">平台监控</h2>
          <p style="color:#6b7280;margin:0">查看商品、订单、AI 服务和向量库的实时状态概览。</p>
          <div style="margin-top:auto">
            <RouterLink to="/admin/metrics" class="black-button">查看监控概览</RouterLink>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
