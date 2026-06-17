import { describe, expect, it, beforeEach, vi } from 'vitest'
import {
  createMerchantProduct,
  createOrder,
  editMerchantProduct,
  getAdminOverview,
  getProductDetail,
  getProductList,
  homeRecommendations,
  listAdminUsers,
  listMerchantProducts,
  loginApi,
  payOrder,
  register,
  restockProduct,
  semanticSearch,
  sendChatMessage,
  updateUserStatus,
  uploadProductImage,
  uploadSearchImage,
  deleteMerchantProduct,
} from './api'

function apiOk<T>(data: T): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'ok', data, trace_id: 't-1' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function apiFailure(message: string): Response {
  return new Response(JSON.stringify({ success: false, code: 'BAD_REQUEST', message, data: null, trace_id: 't-1' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('frontend API service', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  it('calls login and register endpoints with JSON payloads', async () => {
    fetchMock
      .mockResolvedValueOnce(apiOk({ user_id: 'u1', username: 'alice', role: 'CUSTOMER' }))
      .mockResolvedValueOnce(apiOk({
        access_token: 'access-token',
        refresh_token: 'refresh-token',
        expires_in: 7200,
        user: { user_id: 'u1', username: 'alice', role: 'CUSTOMER' },
      }))

    await expect(register({ username: 'alice', phone: '13800000000', password: 'Password123!', role: 'CUSTOMER' }))
      .resolves.toMatchObject({ user_id: 'u1' })
    await expect(loginApi({ account: 'alice', password: 'Password123!' }))
      .resolves.toMatchObject({ access_token: 'access-token' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ username: 'alice', phone: '13800000000', password: 'Password123!', role: 'CUSTOMER' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ account: 'alice', password: 'Password123!' }),
    }))
  })

  it('returns fallback data for product, search, recommendation, chat, order, and payment flows when backend is unavailable', async () => {
    fetchMock.mockRejectedValue(new Error('backend down'))

    await expect(getProductList({ keyword: '耳机' })).resolves.toMatchObject({ total: 3 })
    await expect(getProductDetail('p-offline')).resolves.toMatchObject({ product_id: 'p-offline', name: '蓝牙降噪耳机' })
    await expect(getProductDetail('10002')).resolves.toMatchObject({ product_id: '10002', name: '智能保温杯' })
    await expect(semanticSearch('通勤耳机')).resolves.toMatchObject({ query: '通勤耳机', items: expect.any(Array) })
    await expect(homeRecommendations(3)).resolves.toMatchObject({ strategy: 'FALLBACK', items: expect.any(Array) })
    await expect(sendChatMessage('s1', '怎么选耳机')).resolves.toMatchObject({ session_id: 's1', answer: expect.stringContaining('本地示例回复') })
    await expect(createOrder({
      items: [{ product_id: '10001', quantity: 2 }],
      receiver: { name: 'Alice', phone: '13800000000', address: 'Hangzhou' },
    })).resolves.toMatchObject({ status: 'CREATED', items: [{ product_id: '10001', quantity: 2 }] })
    await expect(payOrder('o10001')).resolves.toMatchObject({ order_id: 'o10001', payment_status: 'PAID' })
  })

  it('normalizes backend2 camelCase product payloads for existing views', async () => {
    fetchMock
      .mockResolvedValueOnce(apiOk({
        items: [{
          productId: 'p10001',
          name: '机械键盘',
          price: '399.00',
          stock: 12,
          imageUrl: 'https://example.com/keyboard.jpg',
          detailUrl: '/api/v1/products/p10001',
          hasNext: false,
        }],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
      }))
      .mockResolvedValueOnce(apiOk({
        productId: 'p10001',
        merchantId: 'm10001',
        name: '机械键盘',
        description: '热插拔轴体',
        categoryId: 'c_digital',
        categoryName: '数码周边',
        price: '399.00',
        stock: 12,
        sales: 8,
        rating: 4.9,
        status: 'ON_SALE',
        tags: ['办公'],
        imageUrls: ['https://example.com/keyboard.jpg'],
        detailUrl: '/api/v1/products/p10001',
      }))

    await expect(getProductList()).resolves.toMatchObject({
      items: [{ product_id: 'p10001', image_url: 'https://example.com/keyboard.jpg' }],
      has_next: false,
    })
    await expect(getProductDetail('p10001')).resolves.toMatchObject({
      product_id: 'p10001',
      merchant_id: 'm10001',
      image_urls: ['https://example.com/keyboard.jpg'],
      category_id: 'c_digital',
    })
  })

  it('surfaces non-fallback request failures as readable errors', async () => {
    fetchMock.mockResolvedValueOnce(apiFailure('账号或密码错误'))

    await expect(loginApi({ account: 'bad', password: 'bad' })).rejects.toThrow('账号或密码错误')
  })

  it('calls merchant, admin, and upload endpoints with expected paths and multipart field names', async () => {
    const product = {
      product_id: '10001',
      merchant_id: 'm10001',
      name: '耳机',
      description: 'desc',
      category_id: 'c1',
      category_name: '耳机',
      price: '299.00',
      stock: 10,
      sales: 1,
      rating: 4.8,
      status: 'ON_SALE',
      tags: ['音频'],
      image_urls: ['https://example.com/1.jpg'],
    }
    fetchMock
      .mockResolvedValueOnce(apiOk({ product_id: '10099', status: 'ON_SALE', vector_index_status: 'PENDING' }))
      .mockResolvedValueOnce(apiOk({ items: [product], page: 1, size: 20, total: 1, has_next: false }))
      .mockResolvedValueOnce(apiOk(product))
      .mockResolvedValueOnce(apiOk({ product_id: '10001', stock: 20 }))
      .mockResolvedValueOnce(apiOk({ product_id: '10001', status: 'OFF_SALE', vector_index_status: 'DELETED' }))
      .mockResolvedValueOnce(apiOk({ url: 'https://example.com/uploads/products/1.jpg', object_key: 'products/1.jpg' }))
      .mockResolvedValueOnce(apiOk({ temp_url: 'https://example.com/uploads/search/1.jpg', expires_at: '2026-06-16T00:00:00Z' }))
      .mockResolvedValueOnce(apiOk({ items: [{ user_id: 'u1', username: 'alice', role: 'CUSTOMER', status: 'ACTIVE' }], page: 1, size: 20, total: 1, has_next: false }))
      .mockResolvedValueOnce(apiOk({ user_id: 'u1', status: 'DISABLED' }))
      .mockResolvedValueOnce(apiOk({ user_count: 1, product_count: 1, order_count: 1, today_order_count: 1, search_count_today: 2, ai_chat_count_today: 3, ai_service_status: 'UP', vector_db_status: 'UP' }))

    await createMerchantProduct({ name: '耳机', description: 'desc', price: '299.00', stock: 10, tags: ['音频'], image_urls: ['https://example.com/1.jpg'] })
    await listMerchantProducts({ status: 'ON_SALE', page: 2, size: 10 })
    await editMerchantProduct('10001', { price: '288.00' })
    await restockProduct('10001', 10)
    await deleteMerchantProduct('10001')

    const productImage = new File(['x'], 'product.png', { type: 'image/png' })
    const searchImage = new File(['x'], 'search.png', { type: 'image/png' })
    await uploadProductImage(productImage)
    await uploadSearchImage(searchImage)
    await listAdminUsers({ role: 'CUSTOMER' })
    await updateUserStatus('u1', 'DISABLED')
    await getAdminOverview()

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/merchant/products',
      '/api/v1/merchant/products?status=ON_SALE&page=2&size=10',
      '/api/v1/merchant/products/10001',
      '/api/v1/merchant/products/10001/restock',
      '/api/v1/merchant/products/10001',
      '/api/v1/uploads/product-images',
      '/api/v1/uploads/search-images',
      '/api/v1/admin/users?role=CUSTOMER',
      '/api/v1/admin/users/u1/status',
      '/api/v1/admin/metrics/overview',
    ])
    expect((fetchMock.mock.calls[5][1].body as FormData).get('image')).toBe(productImage)
    expect((fetchMock.mock.calls[6][1].body as FormData).get('image')).toBe(searchImage)
  })
})
