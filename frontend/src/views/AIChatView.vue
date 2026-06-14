<script setup lang="ts">
import { Bot, Plus, Sparkles, Trash2, Send } from 'lucide-vue-next'
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createChatSession,
  deleteChatHistory,
  sendChatMessage,
  recordBehavior,
  type ChatSession,
  type ProductSummary,
} from '../services/api'

const router = useRouter()

interface Message {
  role: 'user' | 'assistant'
  content: string
  relatedProducts?: ProductSummary[]
  timestamp: string
}

const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messages = ref<Message[]>([])
const inputText = ref('')
const loading = ref(false)
const creatingSession = ref(false)
const chatContainer = ref<HTMLElement | null>(null)

onMounted(() => {
  // Start with a default session ready
})

async function handleCreateSession() {
  creatingSession.value = true
  try {
    const session = await createChatSession('新对话')
    sessions.value.unshift(session)
    currentSession.value = session
    messages.value = []
  } catch {
    // fallback already handled in api
  } finally {
    creatingSession.value = false
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  // Auto-create session if none exists
  if (!currentSession.value) {
    await handleCreateSession()
    if (!currentSession.value) return
  }

  // Add user message
  messages.value.push({
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString(),
  })
  inputText.value = ''

  // Record behavior
  recordBehavior({ event_type: 'CHAT', query: text }).catch(() => {})

  loading.value = true
  try {
    const response = await sendChatMessage(currentSession.value.session_id, text)
    messages.value.push({
      role: 'assistant',
      content: response.answer,
      relatedProducts: response.related_products,
      timestamp: new Date().toLocaleTimeString(),
    })
  } catch {
    messages.value.push({
      role: 'assistant',
      content: 'AI 助手暂时无法响应，请稍后重试。',
      timestamp: new Date().toLocaleTimeString(),
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

async function handleClearHistory() {
  if (!currentSession.value) return
  try {
    await deleteChatHistory(currentSession.value.session_id)
    messages.value = []
  } catch {
    // fallback
  }
}

function switchSession(session: ChatSession) {
  currentSession.value = session
  messages.value = []
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

function goToProduct(productId: string) {
  router.push(`/detail/${productId}`)
}
</script>

<template>
  <div class="page chat-page">
    <!-- Sidebar: Sessions -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <h2>
          <Bot :size="20" />
          AI 助手
        </h2>
        <button
          type="button"
          class="soft-button"
          :disabled="creatingSession"
          @click="handleCreateSession"
        >
          <Plus :size="16" />
          <span>{{ creatingSession ? '创建中...' : '新对话' }}</span>
        </button>
      </div>

      <div class="session-list">
        <button
          v-for="session in sessions"
          :key="session.session_id"
          type="button"
          class="session-item"
          :class="{ active: currentSession?.session_id === session.session_id }"
          @click="switchSession(session)"
        >
          <span class="session-title">{{ session.title }}</span>
          <span class="session-time">{{ session.created_at?.slice(0, 10) || '' }}</span>
        </button>

        <p v-if="sessions.length === 0" class="empty-hint">
          点击「新对话」开始与 AI 助手交流
        </p>
      </div>
    </aside>

    <!-- Main: Chat Area -->
    <section class="chat-main">
      <!-- No session placeholder -->
      <div v-if="!currentSession" class="chat-placeholder">
        <div class="placeholder-content">
          <div class="chat-icon-circle">
            <Sparkles :size="36" />
          </div>
          <h1>AI 智能购物助手</h1>
          <p>我可以帮你：</p>
          <ul>
            <li>根据预算和场景推荐商品</li>
            <li>对比不同商品的特点</li>
            <li>解答购物相关的问题</li>
          </ul>
          <button type="button" class="black-button" @click="handleCreateSession">
            <Plus :size="18" />
            开始新对话
          </button>
        </div>
      </div>

      <!-- Active chat -->
      <template v-else>
        <div class="chat-header">
          <div>
            <h2>{{ currentSession.title }}</h2>
            <span>{{ messages.length }} 条消息</span>
          </div>
          <button type="button" class="soft-button" @click="handleClearHistory">
            <Trash2 :size="15" />
            清除历史
          </button>
        </div>

        <div ref="chatContainer" class="chat-messages">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="message"
            :class="msg.role"
          >
            <div class="message-avatar">
              <Bot v-if="msg.role === 'assistant'" :size="18" />
              <span v-else>我</span>
            </div>
            <div class="message-body">
              <!-- AI answers may contain HTML from backend (already sanitized) -->
              <div
                v-if="msg.role === 'assistant'"
                class="message-text"
                v-html="msg.content"
              ></div>
              <div v-else class="message-text">{{ msg.content }}</div>

              <!-- Related products -->
              <div v-if="msg.relatedProducts?.length" class="related-products">
                <p class="related-label">相关商品推荐：</p>
                <div class="related-grid">
                  <button
                    v-for="rp in msg.relatedProducts"
                    :key="rp.product_id"
                    type="button"
                    class="related-card"
                    @click="goToProduct(rp.product_id)"
                  >
                    <img :src="rp.image_url" :alt="rp.name" />
                    <div>
                      <strong>{{ rp.name }}</strong>
                      <span>¥{{ rp.price }}</span>
                    </div>
                  </button>
                </div>
              </div>

              <span class="message-time">{{ msg.timestamp }}</span>
            </div>
          </div>

          <!-- Loading indicator -->
          <div v-if="loading" class="message assistant">
            <div class="message-avatar">
              <Bot :size="18" />
            </div>
            <div class="message-body">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- Input area -->
        <form class="chat-input-area" @submit.prevent="handleSend">
          <input
            v-model="inputText"
            placeholder="输入你的购物需求，例如：推荐一款适合通勤的蓝牙耳机..."
            :disabled="loading"
            autofocus
          />
          <button type="submit" class="black-button" :disabled="loading || !inputText.trim()">
            <Send :size="18" />
          </button>
        </form>
      </template>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 0;
  height: calc(100vh - 56px);
  padding-top: 56px;
  padding-bottom: 0;
  width: 100%;
  max-width: 100%;
}

/* ── Sidebar ──────────────────────── */
.chat-sidebar {
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  background: #fafafa;
  overflow-y: auto;
}

.sidebar-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.sidebar-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 1.1rem;
  color: #0071e3;
}

.sidebar-header .soft-button {
  justify-content: center;
  font-size: 0.82rem;
}

.session-list {
  flex: 1;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  border: 0;
  border-radius: 12px;
  background: transparent;
  padding: 12px 14px;
  text-align: left;
  color: #1d1d1f;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

.session-item.active {
  background: rgba(0, 113, 227, 0.08);
  color: #0071e3;
}

.session-title {
  font-weight: 700;
  font-size: 0.88rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 0.7rem;
  color: #86868b;
}

.empty-hint {
  padding: 20px;
  text-align: center;
  color: #86868b;
  font-size: 0.85rem;
}

/* ── Main Chat ────────────────────── */
.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-content {
  text-align: center;
  max-width: 440px;
}

.chat-icon-circle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  margin-bottom: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0071e3, #5e5ce6);
  color: #ffffff;
}

.placeholder-content h1 {
  margin: 0 0 10px;
  font-size: 1.8rem;
}

.placeholder-content p {
  color: #86868b;
  margin: 16px 0 8px;
}

.placeholder-content ul {
  text-align: left;
  margin: 0 auto 24px;
  padding: 0 0 0 20px;
  color: #6e6e73;
  font-size: 0.9rem;
  max-width: 300px;
}

.placeholder-content li {
  margin-bottom: 6px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
}

.chat-header h2 {
  margin: 0;
  font-size: 1.05rem;
}

.chat-header span {
  color: #86868b;
  font-size: 0.75rem;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-avatar {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  font-size: 0.7rem;
  font-weight: 800;
}

.message.assistant .message-avatar {
  background: linear-gradient(135deg, #0071e3, #5e5ce6);
  color: #ffffff;
}

.message.user .message-avatar {
  background: #f5f5f7;
  color: #1d1d1f;
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-text {
  border-radius: 18px;
  padding: 12px 16px;
  font-size: 0.92rem;
  line-height: 1.65;
}

.message.assistant .message-text {
  background: #f5f5f7;
  color: #1d1d1f;
  border-top-left-radius: 4px;
}

.message.user .message-text {
  background: #0071e3;
  color: #ffffff;
  border-top-right-radius: 4px;
}

.message-time {
  font-size: 0.68rem;
  color: #86868b;
  padding: 0 4px;
}

.message.user .message-time {
  text-align: right;
}

/* Related products */
.related-products {
  margin-top: 4px;
}

.related-label {
  font-size: 0.75rem;
  color: #86868b;
  margin: 0 0 6px;
  padding: 0 4px;
}

.related-grid {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.related-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  background: #ffffff;
  padding: 8px 12px 8px 8px;
  text-align: left;
  cursor: pointer;
  transition: box-shadow 0.2s ease;
}

.related-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.related-card img {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
}

.related-card strong {
  display: block;
  font-size: 0.82rem;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-card span {
  color: #86868b;
  font-size: 0.78rem;
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 12px 16px;
  background: #f5f5f7;
  border-radius: 18px;
  border-top-left-radius: 4px;
  width: fit-content;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c7c7cc;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* Input area */
.chat-input-area {
  display: flex;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.9);
}

.chat-input-area input {
  flex: 1;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 16px;
  background: #f5f5f7;
  padding: 12px 16px;
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.chat-input-area input:focus {
  border-color: #0071e3;
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.12);
  background: #ffffff;
}

.chat-input-area .black-button {
  width: 44px;
  height: 44px;
  padding: 0;
  flex-shrink: 0;
}

.chat-input-area .black-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .chat-page {
    grid-template-columns: 1fr;
  }
  .chat-sidebar {
    display: none;
  }
}
</style>
