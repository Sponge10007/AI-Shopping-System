<script setup lang="ts">
import { Eye, EyeOff, ShoppingBag } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const showPassword = ref(false)
const form = reactive({ account: '', password: '' })

function submit() {
  router.push('/')
}
</script>

<template>
  <div class="auth-page">
    <div class="mesh-gradient"></div>
    <section class="auth-card">
      <div class="auth-brand">
        <div class="auth-icon">
          <ShoppingBag :size="36" />
        </div>
        <h1>欢迎回来</h1>
        <p>使用您的账户以继续</p>
      </div>

      <div class="segmented-control">
        <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          账号
          <input v-model="form.account" placeholder="手机号或用户名" />
        </label>
        <label>
          密码
          <div class="password-field">
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
            />
            <button type="button" @click="showPassword = !showPassword" :title="showPassword ? '隐藏密码' : '显示密码'">
              <EyeOff v-if="showPassword" :size="17" />
              <Eye v-else :size="17" />
            </button>
          </div>
        </label>

        <button type="submit" class="black-button full-button">
          {{ mode === 'login' ? '登录' : '创建账户' }}
        </button>
      </form>

      <div class="auth-options">
        <button type="button">WeChat</button>
        <button type="button">Google</button>
      </div>
      <p class="auth-policy">登录即表示您同意服务协议与隐私政策。</p>
    </section>
  </div>
</template>
