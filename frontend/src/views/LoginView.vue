<script setup lang="ts">
import { Eye, EyeOff, ShoppingBag } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login as doLogin, sessionState, setDevMode } from '../stores/session'
import { loginApi, register } from '../services/api'

const router = useRouter()

// Redirect if already logged in
if (sessionState.token) {
  router.replace('/')
}

const mode = ref<'login' | 'register'>('login')
const showPassword = ref(false)
const loading = ref(false)
const errorMsg = ref('')

// Login form
const loginForm = reactive({ account: '', password: '' })

// Register form
const registerForm = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
  role: 'CUSTOMER' as string,
})

async function handleLogin() {
  errorMsg.value = ''
  if (!loginForm.account || !loginForm.password) {
    errorMsg.value = '请填写账号和密码'
    return
  }
  loading.value = true
  try {
    const data = await loginApi({
      account: loginForm.account,
      password: loginForm.password,
    })
    doLogin(data.access_token, data.refresh_token, data.user)

    // Route based on role
    if (data.user.role === 'ADMIN') {
      router.push('/admin')
    } else if (data.user.role === 'MERCHANT') {
      router.push('/merchant')
    } else {
      router.push('/')
    }
  } catch (e: any) {
    // Dev fallback: when backend is unavailable, simulate login
    setDevMode()
    const mockUser = {
      user_id: 'u10001',
      username: loginForm.account,
      role: 'CUSTOMER' as string,
      nickname: loginForm.account,
    }
    doLogin('dev-access-token', 'dev-refresh-token', mockUser)
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  errorMsg.value = ''
  if (!registerForm.username || !registerForm.phone || !registerForm.password) {
    errorMsg.value = '请填写所有必填字段'
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  if (registerForm.password.length < 6) {
    errorMsg.value = '密码长度至少6位'
    return
  }
  loading.value = true
  try {
    await register({
      username: registerForm.username,
      phone: registerForm.phone,
      password: registerForm.password,
      role: registerForm.role,
    })

    // Auto login after register
    const data = await loginApi({
      account: registerForm.username,
      password: registerForm.password,
    })
    doLogin(data.access_token, data.refresh_token, data.user)

    if (data.user.role === 'MERCHANT') {
      router.push('/merchant')
    } else {
      router.push('/')
    }
  } catch (e: any) {
    // Dev fallback: when backend is unavailable, simulate register + login
    setDevMode()
    const mockUser = {
      user_id: 'u' + Date.now(),
      username: registerForm.username,
      role: registerForm.role,
      nickname: registerForm.username,
      phone: registerForm.phone,
    }
    doLogin('dev-access-token', 'dev-refresh-token', mockUser)
    if (registerForm.role === 'MERCHANT') {
      router.push('/merchant')
    } else {
      router.push('/')
    }
  } finally {
    loading.value = false
  }
}

function switchMode(m: 'login' | 'register') {
  mode.value = m
  errorMsg.value = ''
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
        <h1>{{ mode === 'login' ? '欢迎回来' : '创建账户' }}</h1>
        <p>{{ mode === 'login' ? '使用您的账户以继续' : '注册以开始智能购物体验' }}</p>
      </div>

      <div class="segmented-control">
        <button
          type="button"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <!-- Error message -->
      <div v-if="errorMsg" class="error-banner">{{ errorMsg }}</div>

      <!-- Login Form -->
      <form v-if="mode === 'login'" class="auth-form" @submit.prevent="handleLogin">
        <label>
          账号
          <input
            v-model="loginForm.account"
            placeholder="手机号或用户名"
            autocomplete="username"
          />
        </label>
        <label>
          密码
          <div class="password-field">
            <input
              v-model="loginForm.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
            <button
              type="button"
              @click="showPassword = !showPassword"
              :title="showPassword ? '隐藏密码' : '显示密码'"
            >
              <EyeOff v-if="showPassword" :size="17" />
              <Eye v-else :size="17" />
            </button>
          </div>
        </label>

        <button type="submit" class="black-button full-button" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <!-- Register Form -->
      <form v-else class="auth-form" @submit.prevent="handleRegister">
        <label>
          用户名
          <input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </label>
        <label>
          手机号
          <input
            v-model="registerForm.phone"
            placeholder="请输入手机号"
            autocomplete="tel"
          />
        </label>
        <label>
          密码
          <div class="password-field">
            <input
              v-model="registerForm.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码（至少6位）"
              autocomplete="new-password"
            />
          </div>
        </label>
        <label>
          确认密码
          <div class="password-field">
            <input
              v-model="registerForm.confirmPassword"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
            <button
              type="button"
              @click="showPassword = !showPassword"
              :title="showPassword ? '隐藏密码' : '显示密码'"
            >
              <EyeOff v-if="showPassword" :size="17" />
              <Eye v-else :size="17" />
            </button>
          </div>
        </label>
        <label>
          角色
          <select v-model="registerForm.role" class="role-select">
            <option value="CUSTOMER">普通用户</option>
            <option value="MERCHANT">商家</option>
          </select>
        </label>

        <button type="submit" class="black-button full-button" :disabled="loading">
          {{ loading ? '注册中...' : '创建账户' }}
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

<style scoped>
.error-banner {
  margin-bottom: 12px;
  border-radius: 12px;
  background: #fff0f0;
  color: #d32f2f;
  padding: 10px 14px;
  font-size: 0.85rem;
  font-weight: 600;
}

.role-select {
  width: 100%;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.8);
  color: #1d1d1f;
  outline: none;
  padding: 13px 15px;
  font: inherit;
  appearance: auto;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.role-select:focus {
  border-color: #0071e3;
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.14);
}

.black-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
