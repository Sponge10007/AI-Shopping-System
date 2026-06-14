import { reactive } from 'vue'

export interface UserInfo {
  user_id: string
  username: string
  role: string
  nickname?: string
  phone?: string
  avatar_url?: string
  created_at?: string
}

interface SessionState {
  token: string
  refreshToken: string
  userId: string
  role: string
  userInfo: UserInfo | null
  devMode: boolean
}

const STORAGE_KEY = 'ai-shopping-session'

function loadFromStorage(): SessionState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        token: parsed.token || '',
        refreshToken: parsed.refreshToken || '',
        userId: parsed.userId || '',
        role: parsed.role || 'CUSTOMER',
        userInfo: parsed.userInfo || null,
        devMode: parsed.devMode || false,
      }
    }
  } catch {
    // corrupted data, ignore
  }
  return {
    token: '',
    refreshToken: '',
    userId: '',
    role: 'CUSTOMER',
    userInfo: null,
    devMode: false,
  }
}

function saveToStorage(state: SessionState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // storage full or unavailable
  }
}

const initial = loadFromStorage()

export const sessionState = reactive<SessionState>(initial)

export function persistSession() {
  saveToStorage({
    token: sessionState.token,
    refreshToken: sessionState.refreshToken,
    userId: sessionState.userId,
    role: sessionState.role,
    userInfo: sessionState.userInfo,
    devMode: sessionState.devMode,
  })
}

export function isLoggedIn(): boolean {
  return !!sessionState.token
}

export function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (sessionState.token) {
    headers['Authorization'] = `Bearer ${sessionState.token}`
  }
  return headers
}

export function login(token: string, refreshToken: string, user: UserInfo) {
  sessionState.token = token
  sessionState.refreshToken = refreshToken
  sessionState.userId = user.user_id
  sessionState.role = user.role
  sessionState.userInfo = user
  persistSession()
}

export function setDevMode() {
  sessionState.devMode = true
  persistSession()
}

export function logout() {
  // Keep devMode across sessions so the indicator persists on next login
  const wasDevMode = sessionState.devMode
  sessionState.token = ''
  sessionState.refreshToken = ''
  sessionState.userId = ''
  sessionState.role = 'CUSTOMER'
  sessionState.userInfo = null
  sessionState.devMode = wasDevMode
  localStorage.removeItem(STORAGE_KEY)
  // Re-save devMode
  if (wasDevMode) {
    sessionState.devMode = true
    persistSession()
  }
}

export function updateUserInfo(info: Partial<UserInfo>) {
  if (sessionState.userInfo) {
    Object.assign(sessionState.userInfo, info)
  }
  persistSession()
}
