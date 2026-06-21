import { flushPromises, mount } from '@vue/test-utils'
import CompareView from './CompareView.vue'
import * as api from '../services/api'
import { compareState } from '../stores/compare'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push }),
}))

vi.mock('../services/api', () => ({
  getProductList: vi.fn(),
  getProductDetail: vi.fn(),
  compareProducts: vi.fn(),
}))

const products = [
  {
    product_id: '10001',
    name: '通勤耳机',
    price: '299.00',
    stock: 20,
    image_url: 'https://example.com/1.jpg',
    rating: 4.8,
  },
  {
    product_id: '10002',
    name: '监听耳机',
    price: '599.00',
    stock: 10,
    image_url: 'https://example.com/2.jpg',
    rating: 4.6,
  },
]

describe('CompareView', () => {
  beforeEach(() => {
    compareState.productIds = ['10001']
    push.mockReset()
    vi.mocked(api.getProductList).mockResolvedValue({
      items: products,
      page: 1,
      size: 100,
      total: 2,
      has_next: false,
    })
    vi.mocked(api.compareProducts).mockResolvedValue({
      source: 'AI',
      intent: '通勤',
      winner_product_id: '10001',
      summary: '通勤耳机更适合日常使用',
      highlights: ['价格更低'],
      items: [
        {
          product_id: '10001',
          score: 92,
          verdict: '更均衡',
          strengths: ['便携'],
          weaknesses: ['无'],
        },
        {
          product_id: '10002',
          score: 78,
          verdict: '音质优先',
          strengths: ['口碑好'],
          weaknesses: ['价格高'],
        },
      ],
      dimensions: [{
        name: '价格优势',
        scores: { '10001': 95, '10002': 70 },
      }],
    })
  })

  it('selects products and renders a real comparison report', async () => {
    const wrapper = mount(CompareView)
    await flushPromises()

    await wrapper.findAll('.compare-picker button')[1].trigger('click')
    await wrapper.get('.compare-submit').trigger('click')
    await flushPromises()

    expect(api.compareProducts).toHaveBeenCalledWith(
      ['10001', '10002'],
      '适合日常使用，兼顾价格、口碑和实用性',
    )
    expect(wrapper.text()).toContain('最终建议：通勤耳机')
    expect(wrapper.text()).toContain('通勤耳机更适合日常使用')
    expect(wrapper.text()).toContain('92 分')
  })

  it('routes the winning product to checkout', async () => {
    const wrapper = mount(CompareView)
    await flushPromises()
    await wrapper.findAll('.compare-picker button')[1].trigger('click')
    await wrapper.get('.compare-submit').trigger('click')
    await flushPromises()
    await wrapper.get('.compare-actions .black-button').trigger('click')

    expect(push).toHaveBeenCalledWith(expect.objectContaining({
      path: '/checkout',
      query: expect.objectContaining({ product_id: '10001' }),
    }))
  })
})
