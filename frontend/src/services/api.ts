import { getAuthHeaders, sessionState } from '../stores/session'

const API_BASE = '/api/v1'

// ── Types ────────────────────────────────────────────

export interface ProductSummary {
  product_id: string
  name: string
  price: string
  stock: number
  image_url: string
  detail_url?: string
  sales?: number
  rating?: number
  tags?: string[]
  score?: number
  reason?: string
}

export interface Product {
  product_id: string
  merchant_id: string
  name: string
  description: string
  category_id: string
  category_name: string
  price: string
  stock: number
  sales: number
  rating: number
  status: string
  tags: string[]
  image_urls: string[]
  detail_url?: string
  created_at?: string
  updated_at?: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  has_next: boolean
}

interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  trace_id: string
}

export interface UserInfo {
  user_id: string
  username: string
  role: string
  nickname?: string
  phone?: string
  avatar_url?: string
  created_at?: string
}

export interface OrderItem {
  product_id: string
  name: string
  unit_price: string
  quantity: number
}

export interface OrderReceiver {
  name: string
  phone: string
  address: string
}

export interface Order {
  order_id: string
  user_id: string
  status: string
  total_amount: string
  items: OrderItem[]
  receiver: OrderReceiver
  created_at?: string
}

export interface ChatSession {
  session_id: string
  title: string
  created_at?: string
}

export interface ChatMessageResponse {
  session_id: string
  answer: string
  image_list?: string[]
  link_list?: string[]
  related_products?: ProductSummary[]
}

export interface ChatHistoryMessage {
  role: 'user' | 'assistant'
  content: string
  image_list?: string[]
  link_list?: string[]
  related_products?: ProductSummary[]
  created_at?: string
}

export interface ChatStreamHandlers {
  onDelta?: (content: string) => void
}

export interface PaymentResult {
  payment_id: string
  order_id: string
  payment_status: string
  paid_at?: string
}

export interface CompareItem {
  product_id: string
  score: number
  verdict: string
  strengths: string[]
  weaknesses: string[]
}

export interface CompareDimension {
  name: string
  scores: Record<string, number>
}

export interface CompareReport {
  source: 'AI' | 'RULE_BASED'
  intent: string
  winner_product_id: string
  summary: string
  highlights: string[]
  items: CompareItem[]
  dimensions: CompareDimension[]
}

export interface AdminOverview {
  user_count: number
  product_count: number
  order_count: number
  today_order_count: number
  search_count_today: number
  ai_chat_count_today: number
  ai_service_status: string
  vector_db_status: string
}

// ── Fallback data ────────────────────────────────────

const fallbackProducts: ProductSummary[] = [
  {
    product_id: '10001',
    name: '蓝牙降噪耳机',
    price: '299.00',
    stock: 120,
    image_url: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80',
    sales: 320,
    rating: 4.8,
    tags: ['蓝牙', '降噪', '通勤'],
    score: 0.93,
    reason: '符合通勤、降噪和预算要求',
  },
  {
    product_id: '10002',
    name: '智能保温杯',
    price: '129.00',
    stock: 80,
    image_url: 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=80',
    sales: 210,
    rating: 4.6,
    tags: ['办公', '保温', '便携'],
    score: 0.86,
    reason: '适合办公和日常通勤',
  },
  {
    product_id: '10003',
    name: '轻量运动背包',
    price: '189.00',
    stock: 64,
    image_url: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80',
    sales: 148,
    rating: 4.7,
    tags: ['运动', '收纳', '轻量'],
    score: 0.82,
    reason: '适合短途出行和健身携带',
  },
]

const fallbackProductDetails: Product[] = [
  {
    product_id: '10001',
    merchant_id: 'm10001',
    name: '蓝牙降噪耳机',
    description: '适合通勤和学习的主动降噪蓝牙耳机，支持蓝牙5.3，续航长达40小时，佩戴舒适。',
    category_id: 'c_headphone',
    category_name: '耳机',
    price: '299.00',
    stock: 120,
    sales: 320,
    rating: 4.8,
    status: 'ON_SALE',
    tags: ['蓝牙', '降噪', '通勤'],
    image_urls: [
      'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1484704849700-f032a568e944?auto=format&fit=crop&w=1200&q=80',
    ],
    detail_url: 'https://example.com/products/10001',
  },
  {
    product_id: '10002',
    merchant_id: 'm10001',
    name: '智能保温杯',
    description: '适合办公和通勤的智能保温杯，支持温度显示，便携防漏。',
    category_id: 'c_home',
    category_name: '家居',
    price: '129.00',
    stock: 80,
    sales: 210,
    rating: 4.6,
    status: 'ON_SALE',
    tags: ['办公', '保温', '便携'],
    image_urls: [
      'https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=1200&q=80',
    ],
    detail_url: 'https://example.com/products/10002',
  },
  {
    product_id: '10003',
    merchant_id: 'm10001',
    name: '轻量运动背包',
    description: '适合短途出行和健身的轻量运动背包，分区收纳，防泼水。',
    category_id: 'c_sports',
    category_name: '户外',
    price: '189.00',
    stock: 64,
    sales: 148,
    rating: 4.7,
    status: 'ON_SALE',
    tags: ['运动', '收纳', '轻量'],
    image_urls: [
      'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=1200&q=80',
    ],
    detail_url: 'https://example.com/products/10003',
  },
]

function fallbackProductDetail(productId: string): Product {
  const found = fallbackProductDetails.find((item) => item.product_id === productId)
  if (found) return found
  return { ...fallbackProductDetails[0], product_id: productId }
}

function normalizeProductSummary(raw: any): ProductSummary {
  const imageUrl = raw.image_url ?? raw.imageUrl ?? raw.imageUrls?.[0] ?? raw.image_urls?.[0] ?? ''
  const productId = String(raw.product_id ?? raw.productId ?? '')
  return {
    product_id: productId,
    name: raw.name ?? '',
    price: String(raw.price ?? '0.00'),
    stock: Number(raw.stock ?? 0),
    image_url: imageUrl,
    detail_url: raw.detail_url ?? raw.detailUrl,
    sales: raw.sales,
    rating: raw.rating,
    tags: raw.tags ?? [],
    score: raw.score,
    reason: raw.reason,
  }
}

function normalizeProduct(raw: any): Product {
  const summary = normalizeProductSummary(raw)
  const imageUrls = raw.image_urls ?? raw.imageUrls ?? (summary.image_url ? [summary.image_url] : [])
  return {
    product_id: summary.product_id,
    merchant_id: raw.merchant_id ?? raw.merchantId ?? '',
    name: summary.name,
    description: raw.description ?? '',
    category_id: raw.category_id ?? raw.categoryId ?? '',
    category_name: raw.category_name ?? raw.categoryName ?? '',
    price: summary.price,
    stock: summary.stock,
    sales: Number(raw.sales ?? 0),
    rating: Number(raw.rating ?? 0),
    status: raw.status ?? 'ON_SALE',
    tags: raw.tags ?? [],
    image_urls: imageUrls,
    detail_url: summary.detail_url,
    created_at: raw.created_at ?? raw.createdAt,
    updated_at: raw.updated_at ?? raw.updatedAt,
  }
}

function normalizePage<T>(raw: any, normalizeItem: (item: any) => T): PageResponse<T> {
  return {
    items: (raw.items ?? []).map(normalizeItem),
    page: raw.page ?? 1,
    size: raw.size ?? 20,
    total: raw.total ?? raw.items?.length ?? 0,
    has_next: raw.has_next ?? raw.hasNext ?? false,
  }
}

const fallbackOrders: Order[] = [
  {
    order_id: 'o10001',
    user_id: 'u10001',
    status: 'CREATED',
    total_amount: '598.00',
    items: [{ product_id: '10001', name: '蓝牙降噪耳机', unit_price: '299.00', quantity: 2 }],
    receiver: { name: '张三', phone: '13800000000', address: '浙江省杭州市西湖区' },
    created_at: '2026-06-14T10:30:00+08:00',
  },
  {
    order_id: 'o10002',
    user_id: 'u10001',
    status: 'PAID',
    total_amount: '129.00',
    items: [{ product_id: '10002', name: '智能保温杯', unit_price: '129.00', quantity: 1 }],
    receiver: { name: '张三', phone: '13800000000', address: '浙江省杭州市西湖区' },
    created_at: '2026-06-13T15:20:00+08:00',
  },
]

// ── Core request helpers ─────────────────────────────

const REQUEST_TIMEOUT_MS = 4000 // fail fast when backend is unreachable
const IMAGE_SEARCH_TIMEOUT_MS = 35000

/**
 * Tiny fetch-with-timeout wrapper.  Throws if the server doesn't respond
 * within REQUEST_TIMEOUT_MS so that every caller's catch block can serve
 * fallback data immediately.
 */
async function fetchWithTimeout(url: string, options?: RequestInit, timeoutMs = REQUEST_TIMEOUT_MS): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    })
    return response
  } finally {
    clearTimeout(timer)
  }
}

async function request<T>(path: string, options?: RequestInit, timeoutMs = REQUEST_TIMEOUT_MS): Promise<T> {
  const response = await fetchWithTimeout(`${API_BASE}${path}`, {
    headers: getAuthHeaders(),
    ...options,
  }, timeoutMs)

  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error((body as any).message || `API ${response.status}`)
  }

  const body = (await response.json()) as ApiResponse<T>
  if (!body.success) {
    throw new Error(body.message || body.code)
  }
  return body.data
}

/**
 * Multipart request helper — does NOT set Content-Type so the browser can set
 * the boundary automatically.
 */
async function requestMultipart<T>(
  path: string,
  formData: FormData,
  timeoutMs = REQUEST_TIMEOUT_MS,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (sessionState.token) {
    headers['Authorization'] = `Bearer ${sessionState.token}`
  }

  const response = await fetchWithTimeout(
    `${API_BASE}${path}`,
    {
      method: 'POST',
      headers,
      body: formData,
    },
    timeoutMs,
  )

  if (!response.ok) {
    throw new Error(`Upload failed ${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<T>
  if (!body.success) {
    throw new Error(body.message || body.code)
  }
  return body.data
}

// ── Auth ─────────────────────────────────────────────

export async function register(payload: {
  username: string
  phone: string
  password: string
  role: string
}): Promise<{ user_id: string; username: string; role: string }> {
  return request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function loginApi(payload: {
  account: string
  password: string
}): Promise<{
  access_token: string
  refresh_token: string
  expires_in: number
  user: UserInfo
}> {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function logoutApi(): Promise<{ logged_out: boolean }> {
  try {
    return await request('/auth/logout', { method: 'POST' })
  } catch {
    return { logged_out: true }
  }
}

// ── Users ────────────────────────────────────────────

export async function getUserProfile(): Promise<UserInfo> {
  try {
    return await request<UserInfo>('/users/me')
  } catch {
    // fallback for offline dev
    return {
      user_id: sessionState.userId || 'u10001',
      username: 'demo_user',
      role: 'CUSTOMER',
      nickname: '探索者',
      phone: '138****0000',
      avatar_url: '',
      created_at: '2026-01-01T00:00:00+08:00',
    }
  }
}

export async function updateUserProfile(payload: {
  nickname?: string
  phone?: string
  avatar_url?: string
}): Promise<UserInfo> {
  return request('/users/me', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

// ── Products ─────────────────────────────────────────

export async function getProductList(params?: {
  keyword?: string
  category_id?: string
  min_price?: string
  max_price?: string
  tags?: string
  sort?: string
  page?: number
  size?: number
}): Promise<PageResponse<ProductSummary>> {
  try {
    const searchParams = new URLSearchParams()
    if (params) {
      const backendParamNames: Record<string, string> = {
        category_id: 'categoryId',
        min_price: 'minPrice',
        max_price: 'maxPrice',
      }
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== '') searchParams.set(backendParamNames[k] ?? k, String(v))
      })
    }
    const qs = searchParams.toString()
    const page = await request<PageResponse<ProductSummary>>(`/products${qs ? `?${qs}` : ''}`)
    return normalizePage(page, normalizeProductSummary)
  } catch {
    return { items: fallbackProducts, page: 1, size: 20, total: fallbackProducts.length, has_next: false }
  }
}

export async function getProductDetail(productId: string): Promise<Product> {
  try {
    const product = await request<Product>(`/products/${productId}`)
    return normalizeProduct(product)
  } catch {
    return fallbackProductDetail(productId)
  }
}

// ── Search & Recommendations ─────────────────────────

export async function semanticSearch(query: string, filters?: {
  category_id?: string
  min_price?: string
  max_price?: string
  in_stock?: boolean
}): Promise<{ query: string; relaxed: boolean; items: ProductSummary[] }> {
  try {
    const result = await request<{ query: string; relaxed: boolean; items: ProductSummary[] }>('/search/semantic', {
      method: 'POST',
      body: JSON.stringify({ query, filters, distance_threshold: 0.9, limit: 20 }),
    })
    return { ...result, items: result.items.map(normalizeProductSummary) }
  } catch {
    return {
      query,
      relaxed: false,
      items: fallbackProducts.map((p) => ({ ...p, reason: `"${query}" 的本地示例结果` })),
    }
  }
}

export async function imageSearch(file: File, limit?: number): Promise<{
  detected_object: string
  items: ProductSummary[]
}> {
  const fd = new FormData()
  fd.append('image', file)
  if (limit) fd.append('limit', String(limit))
  const result = await requestMultipart<{ detected_object?: string; detectedObject?: string; items: ProductSummary[] }>(
    '/search/image',
    fd,
    IMAGE_SEARCH_TIMEOUT_MS,
  )
  return {
    detected_object: result.detected_object ?? result.detectedObject ?? '未知物体',
    items: result.items.map(normalizeProductSummary),
  }
}

export async function uploadSearchImage(file: File): Promise<{
  temp_url: string
  expires_at: string
}> {
  const fd = new FormData()
  fd.append('image', file)
  return requestMultipart('/uploads/search-images', fd)
}

export async function homeRecommendations(limit?: number): Promise<{
  strategy: string
  items: ProductSummary[]
}> {
  try {
    const qs = limit ? `?limit=${limit}` : ''
    const result = await request<{ strategy: string; items: ProductSummary[] }>(`/recommendations/home${qs}`)
    return { ...result, items: result.items.map(normalizeProductSummary) }
  } catch {
    return {
      strategy: 'FALLBACK',
      items: fallbackProducts,
    }
  }
}

// ── AI Chat ──────────────────────────────────────────

export async function createChatSession(title: string): Promise<ChatSession> {
  try {
    return await request('/ai/chat/sessions', {
      method: 'POST',
      body: JSON.stringify({ title }),
    })
  } catch {
    return {
      session_id: `s${Date.now()}`,
      title,
      created_at: new Date().toISOString(),
    }
  }
}

export async function listChatSessions(): Promise<ChatSession[]> {
  return request('/ai/chat/sessions')
}

export async function getChatMessages(sessionId: string): Promise<ChatHistoryMessage[]> {
  return request(`/ai/chat/sessions/${sessionId}/messages`)
}

export async function sendChatMessage(
  sessionId: string,
  content: string,
): Promise<ChatMessageResponse> {
  try {
    return await request(`/ai/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
  } catch {
    return {
      session_id: sessionId,
      answer: `这是本地示例回复：关于"${content}"，建议你优先比较预算、使用场景、续航和售后。你可以查看首页推荐或使用 AI 对比功能来做出更好的选择。`,
      image_list: [],
      link_list: [],
      related_products: fallbackProducts.slice(0, 2),
    }
  }
}

export async function streamChatMessage(
  sessionId: string,
  content: string,
  handlers: ChatStreamHandlers = {},
): Promise<ChatMessageResponse> {
  const response = await fetch(`${API_BASE}/ai/chat/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      ...getAuthHeaders(),
      Accept: 'application/x-ndjson',
    },
    body: JSON.stringify({ content }),
  })

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => ({}))
    throw new Error((body as any).message || `AI stream ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed: ChatMessageResponse | null = null

  const consumeLine = (line: string) => {
    if (!line.trim()) return
    const event = JSON.parse(line) as {
      type: 'delta' | 'done' | 'error'
      content?: string
      message?: string
      data?: ChatMessageResponse
    }
    if (event.type === 'delta' && event.content) {
      handlers.onDelta?.(event.content)
    } else if (event.type === 'done' && event.data) {
      completed = event.data
    } else if (event.type === 'error') {
      throw new Error(event.message || 'AI 流式响应失败')
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    lines.forEach(consumeLine)
    if (done) break
  }
  consumeLine(buffer)

  if (!completed) {
    throw new Error('AI 流式响应未正常结束')
  }
  return completed
}

export async function deleteChatHistory(sessionId: string): Promise<{
  session_id: string
  history_deleted: boolean
}> {
  try {
    return await request(`/ai/chat/sessions/${sessionId}/history`, { method: 'DELETE' })
  } catch {
    return { session_id: sessionId, history_deleted: true }
  }
}

export async function compareProducts(productIds: string[], intent: string): Promise<CompareReport> {
  return request('/ai/compare', {
    method: 'POST',
    body: JSON.stringify({
      product_ids: productIds,
      intent,
    }),
  }, 35000)
}

// ── Orders ───────────────────────────────────────────

export async function createOrder(payload: {
  items: { product_id: string; quantity: number }[]
  receiver: OrderReceiver
}): Promise<Order> {
  return request('/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function getOrders(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<Order>> {
  try {
    const searchParams = new URLSearchParams()
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== '') searchParams.set(k, String(v))
      })
    }
    const qs = searchParams.toString()
    return await request<PageResponse<Order>>(`/orders${qs ? `?${qs}` : ''}`)
  } catch {
    let items = fallbackOrders
    if (params?.status) {
      items = items.filter((o) => o.status === params.status)
    }
    return { items, page: 1, size: 20, total: items.length, has_next: false }
  }
}

export async function getOrderDetail(orderId: string): Promise<Order> {
  try {
    return await request<Order>(`/orders/${orderId}`)
  } catch {
    const found = fallbackOrders.find((o) => o.order_id === orderId)
    return found || fallbackOrders[0]
  }
}

export async function payOrder(orderId: string, paymentMethod: string = 'BALANCE'): Promise<PaymentResult> {
  return request(`/orders/${orderId}/pay`, {
    method: 'POST',
    body: JSON.stringify({ payment_method: paymentMethod }),
  })
}

// ── Behavior Events ──────────────────────────────────

export async function recordBehavior(payload: {
  event_type: string
  product_id?: string
  query?: string
  metadata?: Record<string, string>
}): Promise<{ accepted: boolean }> {
  try {
    return await request('/behavior-events', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  } catch {
    return { accepted: true }
  }
}

// ── Merchant (Frontend 2 — keep existing) ────────────

export async function createMerchantProduct(payload: {
  name: string
  description: string
  category_id?: string
  price: string
  stock: number
  tags: string[]
  image_urls: string[]
}): Promise<{ product_id: string; status: string; vector_index_status: string }> {
  return request('/merchant/products', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function listMerchantProducts(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<ProductSummary>> {
  const searchParams = new URLSearchParams()
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== '') searchParams.set(k, String(v))
    })
  }
  const qs = searchParams.toString()
  return request(`/merchant/products${qs ? `?${qs}` : ''}`)
}

export async function getMerchantProduct(productId: string): Promise<Product> {
  return request<Product>(`/products/${productId}`)
}

export async function editMerchantProduct(
  productId: string,
  payload: Partial<{
    name: string; description: string; price: string
    stock: number; tags: string[]; image_urls: string[]
  }>,
) {
  return request(`/merchant/products/${productId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export async function restockProduct(productId: string, quantity: number): Promise<{ product_id: string; stock: number }> {
  return request(`/merchant/products/${productId}/restock`, {
    method: 'POST',
    body: JSON.stringify({ quantity, remark: '' }),
  })
}

export async function deleteMerchantProduct(productId: string) {
  return request(`/merchant/products/${productId}`, { method: 'DELETE' })
}

export async function uploadProductImage(file: File): Promise<{ url: string; object_key: string }> {
  const fd = new FormData()
  fd.append('image', file)
  return requestMultipart('/uploads/product-images', fd)
}

// ── Admin (Frontend 2 — keep existing) ───────────────

export async function listAdminUsers(params?: {
  role?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResponse<UserInfo & { status: string }>> {
  const searchParams = new URLSearchParams()
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== '') searchParams.set(k, String(v))
    })
  }
  const qs = searchParams.toString()
  return request(`/admin/users${qs ? `?${qs}` : ''}`)
}

export async function updateUserStatus(userId: string, status: string): Promise<{ user_id: string; status: string }> {
  return request(`/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export async function getAdminOverview(): Promise<AdminOverview> {
  return request('/admin/metrics/overview')
}
