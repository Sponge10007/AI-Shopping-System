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
    expect(api.getProductList).toHaveBeenCalled()
    expect(api.homeRecommendations).toHaveBeenCalled()
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
