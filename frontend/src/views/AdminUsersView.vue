<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { listAdminUsers, updateUserStatus } from '../services/api'

const users = ref<Array<{ user_id: string; name: string; role: string; status: string }>>([])

onMounted(async () => {
  const data = await listAdminUsers()
  users.value = data.items
})

const admins = computed(() => users.value.filter((u) => u.role === 'ADMIN'))
const merchants = computed(() => users.value.filter((u) => u.role === 'MERCHANT'))
const customers = computed(() => users.value.filter((u) => u.role !== 'ADMIN' && u.role !== 'MERCHANT'))

async function toggle(u: { user_id: string; status: string }) {
  const newStatus = u.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
  await updateUserStatus(u.user_id, newStatus)
  u.status = newStatus
}
</script>

<template>
  <div class="page">
    <div class="max-w-[1000px] mx-auto px-6">
      <header class="page-title">
        <p style="color:#6b7280">Admin</p>
        <h1 style="font-size:26px;margin-top:6px">用户管理</h1>
      </header>

      <section class="bento-card" style="padding:14px;margin-bottom:12px">
        <h2 style="margin-bottom:8px">管理员</h2>
        <div style="overflow:auto">
          <table class="admin-table" style="width:100%;border-collapse:collapse">
            <thead><tr style="color:#6b7280;border-bottom:1px solid #e5e7eb"><th style="padding:8px">用户ID</th><th style="padding:8px">姓名</th><th style="padding:8px">状态</th><th style="padding:8px">操作</th></tr></thead>
            <tbody>
              <tr v-for="u in admins" :key="u.user_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px">{{ u.user_id }}</td>
                <td style="padding:10px">{{ u.name }}</td>
                <td style="padding:10px">{{ u.status }}</td>
                <td style="padding:10px"><button class="soft-button" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '激活' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="bento-card" style="padding:14px;margin-bottom:12px">
        <h2 style="margin-bottom:8px">商家</h2>
        <div style="overflow:auto">
          <table class="admin-table" style="width:100%;border-collapse:collapse">
            <thead><tr style="color:#6b7280;border-bottom:1px solid #e5e7eb"><th style="padding:8px">用户ID</th><th style="padding:8px">姓名</th><th style="padding:8px">状态</th><th style="padding:8px">操作</th></tr></thead>
            <tbody>
              <tr v-for="u in merchants" :key="u.user_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px">{{ u.user_id }}</td>
                <td style="padding:10px">{{ u.name }}</td>
                <td style="padding:10px">{{ u.status }}</td>
                <td style="padding:10px"><button class="soft-button" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '激活' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="bento-card" style="padding:14px">
        <h2 style="margin-bottom:8px">普通用户</h2>
        <div style="overflow:auto">
          <table class="admin-table" style="width:100%;border-collapse:collapse">
            <thead><tr style="color:#6b7280;border-bottom:1px solid #e5e7eb"><th style="padding:8px">用户ID</th><th style="padding:8px">姓名</th><th style="padding:8px">状态</th><th style="padding:8px">操作</th></tr></thead>
            <tbody>
              <tr v-for="u in customers" :key="u.user_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px">{{ u.user_id }}</td>
                <td style="padding:10px">{{ u.name }}</td>
                <td style="padding:10px">{{ u.status }}</td>
                <td style="padding:10px"><button class="soft-button" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '激活' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>
