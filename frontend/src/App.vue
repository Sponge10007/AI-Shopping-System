<script setup lang="ts">
import { Bot, Search, ShoppingBag, UserRound } from 'lucide-vue-next'
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
          <RouterLink to="/orders">订单</RouterLink>
          <RouterLink to="/merchant">商家</RouterLink>
          <RouterLink to="/admin">管理</RouterLink>
        </div>

        <div class="nav-actions">
          <RouterLink to="/" class="nav-icon" title="搜索">
            <Search :size="18" />
          </RouterLink>
          <RouterLink to="/orders" class="nav-icon" title="购物袋">
            <ShoppingBag :size="18" />
          </RouterLink>
          <RouterLink to="/profile" class="nav-avatar" title="个人中心">
            <UserRound :size="16" />
          </RouterLink>
        </div>
      </div>
    </nav>

    <main>
      <RouterView />
    </main>

    <RouterLink to="/detail/10001" class="floating-ai">
      <span class="pulse-dot"></span>
      <Bot :size="16" />
      <span>AI 正在根据浏览实时分析</span>
    </RouterLink>
  </div>
</template>
