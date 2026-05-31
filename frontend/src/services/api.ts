const API_BASE = '/api/v1'

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

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer dev-access-token',
      ...options?.headers,
    },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`API ${response.status}`)
  }

  const body = (await response.json()) as ApiResponse<T>
  if (!body.success) {
    throw new Error(body.message)
  }
  return body.data
}

export async function listProducts(): Promise<PageResponse<ProductSummary>> {
  try {
    return await request<PageResponse<ProductSummary>>('/products')
  } catch {
    return { items: fallbackProducts, page: 1, size: 20, total: fallbackProducts.length, has_next: false }
  }
}

export async function semanticSearch(query: string): Promise<ProductSummary[]> {
  try {
    const data = await request<{ items: ProductSummary[] }>('/search/semantic', {
      method: 'POST',
      body: JSON.stringify({ query, distance_threshold: 0.9, limit: 20 }),
    })
    return data.items
  } catch {
    return fallbackProducts.map((product) => ({ ...product, reason: `“${query}” 的本地示例结果` }))
  }
}

export async function homeRecommendations(): Promise<ProductSummary[]> {
  try {
    const data = await request<{ items: ProductSummary[] }>('/recommendations/home')
    return data.items
  } catch {
    return fallbackProducts
  }
}

export async function createMerchantProduct(payload: {
  name: string
  description: string
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

export async function getAdminOverview(): Promise<{
  user_count: number
  product_count: number
  order_count: number
  today_order_count: number
  ai_service_status: string
  vector_db_status: string
}> {
  try {
    return await request('/admin/metrics/overview')
  } catch {
    return {
      user_count: 2,
      product_count: 3,
      order_count: 1,
      today_order_count: 1,
      ai_service_status: 'UP',
      vector_db_status: 'UP',
    }
  }
}

export async function sendChatMessage(sessionId: string, content: string): Promise<string> {
  try {
    const data = await request<{ answer: string }>(`/ai/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
    return data.answer
  } catch {
    return '这里是本地示例回复：可以优先比较预算、使用场景、续航、售后和库存。'
  }
}

