<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { listAdminUsers, updateUserStatus } from '../services/api'

const users = ref<Array<{ user_id: string; username: string; phone?: string; role: string; status: string; created_at?: string }>>([])

onMounted(async () => {
  try{
    const data = await listAdminUsers()
    users.value = data.items
  }
  catch{
    alert("无法取得数据！")
  }
  
})

const admins = computed(() => users.value.filter((u) => u.role === 'ADMIN'))
const merchants = computed(() => users.value.filter((u) => u.role === 'MERCHANT'))
const customers = computed(() => users.value.filter((u) => u.role !== 'ADMIN' && u.role !== 'MERCHANT'))

async function toggle(u: { user_id: string; status: string }) {
  const newStatus = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try{
    await updateUserStatus(u.user_id, newStatus)
    u.status = newStatus
    if(newStatus==='ACTIVE')alert('激活成功！')
    else alert('禁用成功！')
  }
  catch{
    alert('操作失败！')
  }
  
}

function formatDate(dt?: string) {
  if (!dt) return '-'
  const d = new Date(dt)
  if (isNaN(d.getTime())) return dt
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
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
          <table class="admin-table" style="width:100%;border-collapse:collapse;table-layout:fixed">
            <colgroup>
              <col style="width:15%">
              <col style="width:25%">
              <col style="width:20%">
              <col style="width:20%">
              <col style="width:10%">
              <col style="width:10%">
            </colgroup>
            <thead>
              <tr style="color:#6b7280;border-bottom:1px solid #e5e7eb">
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">用户ID</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">姓名</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">手机</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">创建时间</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">状态</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in admins" :key="u.user_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.user_id }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.username }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.phone }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ formatDate(u.created_at) }}</td>
                <td style="padding:10px;white-space:nowrap;text-align:center">{{ u.status }}</td>
                <td style="padding:10px;white-space:nowrap;text-align:center"><button class="soft-button" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '激活' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="bento-card" style="padding:14px;margin-bottom:12px">
        <h2 style="margin-bottom:8px">商家</h2>
        <div style="overflow:auto">
          <table class="admin-table" style="width:100%;border-collapse:collapse;table-layout:fixed">
            <colgroup>
              <col style="width:15%">
              <col style="width:25%">
              <col style="width:20%">
              <col style="width:20%">
              <col style="width:10%">
              <col style="width:10%">
            </colgroup>
            <thead>
              <tr style="color:#6b7280;border-bottom:1px solid #e5e7eb">
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">用户ID</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">姓名</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">手机</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">创建时间</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">状态</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in merchants" :key="u.user_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.user_id }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.username }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.phone }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ formatDate(u.created_at) }}</td>
                <td style="padding:10px;white-space:nowrap;text-align:center">{{ u.status }}</td>
                <td style="padding:10px;white-space:nowrap;text-align:center"><button class="soft-button" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '激活' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="bento-card" style="padding:14px">
        <h2 style="margin-bottom:8px">普通用户</h2>
        <div style="overflow:auto">
          <table class="admin-table" style="width:100%;border-collapse:collapse;table-layout:fixed">
            <colgroup>
              <col style="width:15%">
              <col style="width:25%">
              <col style="width:20%">
              <col style="width:20%">
              <col style="width:10%">
              <col style="width:10%">
            </colgroup>
            <thead>
              <tr style="color:#6b7280;border-bottom:1px solid #e5e7eb">
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">用户ID</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">姓名</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">手机</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">创建时间</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">状态</th>
                <th style="padding:8px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in customers" :key="u.user_id" style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.user_id }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.username }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.phone }}</td>
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ formatDate(u.created_at) }}</td>
                <td style="padding:10px;white-space:nowrap;text-align:center">{{ u.status }}</td>
                <td style="padding:10px;white-space:nowrap;text-align:center"><button class="soft-button" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '激活' }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>
