<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { listAdminUsers, updateUserStatus } from '../services/api'

type AdminUser = {
  user_id: string
  username: string
  phone?: string
  role: string
  status: string
  created_at?: string
}

const users = ref<AdminUser[]>([])
const keyword = ref('')
const role = ref('')
const loading = ref(false)

async function loadUsers() {
  loading.value = true
  try {
    const collected: AdminUser[] = []
    let page = 1
    let hasNext = false

    do {
      const data = await listAdminUsers({
        role: role.value || undefined,
        keyword: keyword.value.trim() || undefined,
        page,
        size: 100,
      })
      collected.push(...data.items)
      hasNext = data.has_next
      page += 1
    } while (hasNext)

    users.value = collected
  } catch {
    alert('无法取得数据！')
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)

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

async function resetFilters() {
  keyword.value = ''
  role.value = ''
  await loadUsers()
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

      <form class="bento-card" style="padding:14px;margin-bottom:12px;display:flex;gap:10px;align-items:center;flex-wrap:wrap" @submit.prevent="loadUsers">
        <input
          v-model="keyword"
          aria-label="搜索用户"
          placeholder="搜索用户ID、姓名或手机号"
          style="flex:1;min-width:220px;padding:9px 12px;border:1px solid #e5e7eb;border-radius:10px"
        >
        <select
          v-model="role"
          aria-label="筛选角色"
          style="padding:9px 12px;border:1px solid #e5e7eb;border-radius:10px"
        >
          <option value="">全部角色</option>
          <option value="ADMIN">管理员</option>
          <option value="MERCHANT">商家</option>
          <option value="CUSTOMER">普通用户</option>
        </select>
        <button class="soft-button" type="submit" :disabled="loading">
          {{ loading ? '查询中…' : '查询' }}
        </button>
        <button class="soft-button" type="button" :disabled="loading" @click="resetFilters">重置</button>
      </form>

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
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.phone || '-' }}</td>
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
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.phone || '-' }}</td>
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
                <td style="padding:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center">{{ u.phone || '-' }}</td>
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
