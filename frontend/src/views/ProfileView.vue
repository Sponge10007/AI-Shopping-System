<script setup lang="ts">
import { Heart, Package, Sparkles, UserRound } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import { logout as doLogout, isLoggedIn, updateUserInfo, type UserInfo } from '../stores/session'
import { getUserProfile, updateUserProfile } from '../services/api'
import { useRouter } from 'vue-router'

const router = useRouter()

const user = ref<UserInfo | null>(null)
const loading = ref(true)
const editing = ref(false)
const saving = ref(false)
const error = ref('')

const editForm = reactive({
  nickname: '',
  phone: '',
  avatar_url: '',
})

const settings = reactive([
  { name: '自动多模态识别', value: true },
  { name: '模糊意图增强', value: true },
  { name: '隐私数据加密', value: true },
  { name: '实时降价提醒', value: false },
])

const tags = ['极简主义', '高性能数码', '环保材质', '北欧色彩', '人体工学', '智能家居']

onMounted(async () => {
  await loadProfile()
})

async function loadProfile() {
  loading.value = true
  try {
    user.value = await getUserProfile()
    editForm.nickname = user.value.nickname || ''
    editForm.phone = user.value.phone || ''
    editForm.avatar_url = user.value.avatar_url || ''
  } catch (e: any) {
    error.value = e.message || '加载用户信息失败'
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (user.value) {
    editForm.nickname = user.value.nickname || ''
    editForm.phone = user.value.phone || ''
    editForm.avatar_url = user.value.avatar_url || ''
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function saveProfile() {
  saving.value = true
  error.value = ''
  try {
    const updated = await updateUserProfile({
      nickname: editForm.nickname || undefined,
      phone: editForm.phone || undefined,
      avatar_url: editForm.avatar_url || undefined,
    })
    user.value = updated
    updateUserInfo(updated)
    editing.value = false
  } catch (e: any) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function handleLogout() {
  doLogout()
  router.push('/login')
}
</script>

<template>
  <div class="page profile-page">
    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <p>加载中...</p>
    </div>

    <template v-else-if="user">
      <header class="profile-header">
        <div class="avatar-mark">
          <img v-if="user.avatar_url" :src="user.avatar_url" alt="" class="avatar-img" />
          <UserRound v-else :size="40" />
        </div>
        <div>
          <p>Profile</p>
          <h1>你好，{{ user.nickname || user.username }}</h1>
          <span>{{ user.role === 'ADMIN' ? '管理员' : user.role === 'MERCHANT' ? '商家' : '购物用户' }} · {{ user.phone || '未绑定手机' }}</span>
        </div>
        <div class="profile-actions">
          <button v-if="!editing" type="button" class="soft-button" @click="startEdit">编辑资料</button>
          <button type="button" class="black-button" @click="handleLogout">退出登录</button>
        </div>
      </header>

      <!-- Edit mode -->
      <section v-if="editing" class="bento-card edit-card">
        <div v-if="error" class="error-banner">{{ error }}</div>
        <form class="edit-form" @submit.prevent="saveProfile">
          <label>
            昵称
            <input v-model="editForm.nickname" placeholder="请输入昵称" />
          </label>
          <label>
            手机号
            <input v-model="editForm.phone" placeholder="请输入手机号" />
          </label>
          <label class="span-2">
            头像 URL
            <input v-model="editForm.avatar_url" placeholder="请输入头像图片链接（可选）" />
          </label>
          <div class="form-actions span-2">
            <button type="button" class="soft-button" @click="cancelEdit">取消</button>
            <button type="submit" class="black-button" :disabled="saving">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </form>
      </section>

      <!-- Profile grid -->
      <section v-else class="profile-grid">
        <article class="bento-card gene-card">
          <div class="card-topline">
            <div>
              <h2>AI 购物基因</h2>
              <p>基于 124 次语义检索生成的画像</p>
            </div>
            <div class="gradient-icon">
              <Sparkles :size="20" />
            </div>
          </div>
          <div class="tag-cloud">
            <span v-for="tag in tags" :key="tag">{{ tag }}</span>
            <strong>+ 正在挖掘新偏好</strong>
          </div>
          <p class="blue-note">AI 观察到你近期对"户外办公"表现出兴趣，已调整首页推荐权重。</p>
        </article>

        <article class="bento-card profile-side-card">
          <Package :size="24" />
          <h2>进行中的订单</h2>
          <p>查看你的订单状态与物流信息。</p>
          <RouterLink to="/orders" class="soft-button full-button">查看全部订单</RouterLink>
        </article>

        <article class="bento-card settings-card">
          <h2>智能助理偏好</h2>
          <div v-for="setting in settings" :key="setting.name" class="setting-row">
            <span>{{ setting.name }}</span>
            <button
              type="button"
              class="switch"
              :class="{ on: setting.value }"
              :title="setting.name"
              @click="setting.value = !setting.value"
            >
              <i></i>
            </button>
          </div>
        </article>

        <article class="bento-card wish-card">
          <div class="card-topline">
            <h2>愿望清单</h2>
            <Heart :size="20" />
          </div>
          <div class="wish-row">
            <div v-for="item in 4" :key="item">
              <div class="wish-image">ITEM</div>
              <strong>智能配件 {{ item }}</strong>
              <span>¥ 1,299</span>
            </div>
          </div>
        </article>
      </section>
    </template>

    <!-- Not logged in -->
    <div v-else class="empty-state">
      <p>请先登录以查看个人中心</p>
      <RouterLink to="/login" class="black-button">去登录</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
  color: #86868b;
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.edit-card {
  margin-bottom: 24px;
  padding: 28px;
}

.edit-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.error-banner {
  margin-bottom: 12px;
  border-radius: 12px;
  background: #fff0f0;
  color: #d32f2f;
  padding: 10px 14px;
  font-size: 0.85rem;
  font-weight: 600;
}

.black-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
