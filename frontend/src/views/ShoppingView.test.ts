import { flushPromises, mount } from '@vue/test-utils'
import ShoppingView from './ShoppingView.vue'
import * as api from '../services/api.ts'

vi.mock('../services/api.ts', () => ({
  getProductList: vi.fn(),
  homeRecommendations: vi.fn(),
  imageSearch: vi.fn(),
  recordBehavior: vi.fn(() => Promise.resolve({ accepted: true })),
  semanticSearch: vi.fn(),
}))

const product = {
  product_id: '10001',
  name: '蓝牙降噪耳机',
  price: '299.00',
  stock: 120,
  image_url: 'https://example.com/headphones.jpg',
  rating: 4.8,
  score: 0.93,
  reason: '适合通勤',
}

const cup = {
  product_id: '10002',
  name: '智能保温杯',
  price: '129.00',
  stock: 80,
  image_url: 'https://example.com/cup.jpg',
  rating: 4.6,
  score: 0.86,
  reason: '适合办公',
}

function mockHome(products = [product], recommendations = [product]) {
  vi.mocked(api.getProductList).mockResolvedValue({ items: products, page: 1, size: 20, total: products.length, has_next: false })
  vi.mocked(api.homeRecommendations).mockResolvedValue({ strategy: 'USER_PROFILE', items: recommendations })
}

describe('ShoppingView', () => {
  it('renders product list, cards, and recommendations from APIs', async () => {
    mockHome()

    const wrapper = mount(ShoppingView)
    await flushPromises()

    expect(wrapper.text()).toContain('蓝牙降噪耳机')
    expect(wrapper.text()).toContain('¥299.00')
    expect(wrapper.get('img[alt="蓝牙降噪耳机"]').attributes('src')).toBe(product.image_url)
    expect(wrapper.get('.feature-card h3').text()).toBe(product.name)
    expect(wrapper.get('.feature-card .white-button').attributes('href')).toBe('/detail/10001')
    expect(api.getProductList).toHaveBeenCalled()
    expect(api.homeRecommendations).toHaveBeenCalled()
  })

  it('keeps the focus card title, image, and detail link tied to the first product', async () => {
    mockHome([cup, product])

    const wrapper = mount(ShoppingView)
    await flushPromises()

    expect(wrapper.get('.feature-card h3').text()).toBe('智能保温杯')
    expect(wrapper.get('.feature-card img').attributes('src')).toBe(cup.image_url)
    expect(wrapper.get('.feature-card .white-button').attributes('href')).toBe('/detail/10002')
  })

  it('uses backend2 category ids when switching categories', async () => {
    mockHome()
    vi.mocked(api.getProductList).mockResolvedValueOnce({ items: [product], page: 1, size: 20, total: 1, has_next: false })
      .mockResolvedValueOnce({ items: [cup], page: 1, size: 20, total: 1, has_next: false })

    const wrapper = mount(ShoppingView)
    await flushPromises()
    await wrapper.findAll('.category-strip button')[1].trigger('click')
    await flushPromises()

    expect(api.getProductList).toHaveBeenLastCalledWith({ category_id: 'c_headphone' })
    expect(wrapper.get('.feature-card h3').text()).toBe('智能保温杯')
  })

  it('submits semantic search when a keyword is entered', async () => {
    mockHome()
    vi.mocked(api.semanticSearch).mockResolvedValue({ query: '通勤耳机', relaxed: false, items: [product] })

    const wrapper = mount(ShoppingView)
    await flushPromises()
    await wrapper.get('input[aria-label="AI 搜索"]').setValue('通勤耳机')
    await wrapper.get('form.ai-search-box').trigger('submit')
    await flushPromises()

    expect(api.semanticSearch).toHaveBeenCalledWith('通勤耳机')
    expect(wrapper.text()).toContain('蓝牙降噪耳机')
  })

  it('shows when the backend returned relaxed rather than precise matches', async () => {
    mockHome()
    vi.mocked(api.semanticSearch).mockResolvedValue({ query: '不存在的商品', relaxed: true, items: [product] })

    const wrapper = mount(ShoppingView)
    await flushPromises()
    await wrapper.get('input[aria-label="AI 搜索"]').setValue('不存在的商品')
    await wrapper.get('form.ai-search-box').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('没有找到精确匹配')
  })

  it('shows empty and error states without crashing when data is missing or search fails', async () => {
    mockHome([], [])
    vi.mocked(api.semanticSearch).mockRejectedValue(new Error('search down'))

    const wrapper = mount(ShoppingView)
    await flushPromises()
    expect(wrapper.text()).toContain('暂无商品')
    expect(wrapper.text()).toContain('暂无个性化推荐')

    await wrapper.get('input[aria-label="AI 搜索"]').setValue('坏查询')
    await wrapper.get('form.ai-search-box').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('搜索失败，请重试')
  })
})
