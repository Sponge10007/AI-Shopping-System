<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAdminUsers, updateUserStatus } from '../services/api'

const users = ref<Array<{ user_id: string; name: string; role: string; status: string }>>([])

onMounted(async () => {
  const data = await listAdminUsers()
  users.value = data.items
})

async function setStatus(u: { user_id: string; status: string }, s: string) {
  await updateUserStatus(u.user_id, s)
  u.status = s
}
</script>

<template>
  <div class="page">
    <header class="page-title">
      <p>Admin</p>
      <h1>用户管理</h1>
    </header>

    <section class="bento-card">
      <table class="admin-table">
        <thead>
          <tr>
            <th>用户ID</th>
            <th>姓名</th>
            <th>角色</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.user_id">
            <td>{{ u.user_id }}</td>
            <td>{{ u.name }}</td>
            <td>{{ u.role }}</td>
            <td>{{ u.status }}</td>
            <td>
              <button class="soft-button" @click="setStatus(u, u.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE')">
                {{ u.status === 'ACTIVE' ? '禁用' : '激活' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
