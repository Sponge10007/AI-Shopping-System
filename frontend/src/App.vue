<script setup lang="ts">
import { Bot, LogOut, Search, ShoppingBag, UserRound } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { isLoggedIn, logout, sessionState } from './stores/session'

const router = useRouter()

function handleLogout() {
  logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <nav class="glass-nav">
      <div class="nav-inner">
        <RouterLink to="/" class="brand">
          AI <span>Store</span>
        </RouterLink>

        <div class="nav-links">
          <RouterLink to="/">智能选购</RouterLink>
          <RouterLink to="/compare">AI 对比</RouterLink>
          <RouterLink v-if="isLoggedIn()" to="/chat">AI 助手</RouterLink>
          <RouterLink v-if="isLoggedIn()" to="/orders">订单</RouterLink>
          <RouterLink
            v-if="sessionState.role === 'MERCHANT' || sessionState.role === 'ADMIN'"
            to="/merchant"
          >
            商家
          </RouterLink>
          <RouterLink v-if="sessionState.role === 'ADMIN'" to="/admin">管理</RouterLink>
        </div>

        <div class="nav-actions">
          <RouterLink to="/" class="nav-icon" title="搜索">
            <Search :size="18" />
          </RouterLink>
          <RouterLink v-if="isLoggedIn()" to="/orders" class="nav-icon" title="购物袋">
            <ShoppingBag :size="18" />
          </RouterLink>
          <RouterLink v-if="isLoggedIn()" to="/profile" class="nav-avatar" title="个人中心">
            <UserRound :size="16" />
          </RouterLink>
          <RouterLink v-else to="/login" class="nav-avatar" title="登录">
            <UserRound :size="16" />
          </RouterLink>
          <button
            v-if="isLoggedIn()"
            class="nav-icon logout-btn"
            title="退出登录"
            @click="handleLogout"
          >
            <LogOut :size="16" />
          </button>
        </div>
      </div>
    </nav>

    <!-- Global dev-mode indicator -->
    <div v-if="sessionState.devMode" class="dev-bar">
      离线开发模式 · 后端未连接，页面使用本地模拟数据
    </div>

    <main>
      <RouterView />
    </main>

    <!-- Floating AI button -->
    <RouterLink v-if="isLoggedIn()" to="/chat" class="floating-ai">
      <span class="pulse-dot"></span>
      <Bot :size="16" />
      <span>AI 正在根据浏览实时分析</span>
    </RouterLink>
  </div>
</template>

<style scoped>
.logout-btn {
  background: none;
  border: 0;
  cursor: pointer;
  color: #86868b;
}

.logout-btn:hover {
  color: #d32f2f;
}

.dev-bar {
  position: fixed;
  top: 56px;
  z-index: 49;
  width: 100%;
  padding: 6px 0;
  background: #fff8e1;
  color: #8d6e00;
  text-align: center;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  border-bottom: 1px solid rgba(180, 130, 0, 0.15);
}
</style>
