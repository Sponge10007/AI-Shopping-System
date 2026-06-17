import { flushPromises, mount } from '@vue/test-utils'
import AIChatView from './AIChatView.vue'
import * as api from '../services/api'

const routerMock = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
}))

vi.mock('../services/api', () => ({
  createChatSession: vi.fn(),
  deleteChatHistory: vi.fn(),
  recordBehavior: vi.fn(() => Promise.resolve({ accepted: true })),
  sendChatMessage: vi.fn(),
}))

const relatedProduct = {
  product_id: '10001',
  name: '蓝牙降噪耳机',
  price: '299.00',
  stock: 120,
  image_url: 'https://example.com/headphones.jpg',
}

describe('AIChatView', () => {
  it('creates a session, sends a message, renders the AI reply, and opens related products', async () => {
    vi.mocked(api.createChatSession).mockResolvedValue({ session_id: 's10001', title: '新对话', created_at: '2026-06-16T00:00:00Z' })
    vi.mocked(api.sendChatMessage).mockResolvedValue({
      session_id: 's10001',
      answer: '推荐这款蓝牙降噪耳机',
      image_list: [],
      link_list: [],
      related_products: [relatedProduct],
    })

    const wrapper = mount(AIChatView)
    await wrapper.findAll('button').find((button) => button.text().includes('开始新对话'))!.trigger('click')
    await flushPromises()

    await wrapper.get('input[placeholder^="输入你的购物需求"]').setValue('推荐通勤耳机')
    await wrapper.get('form.chat-input-area').trigger('submit')
    await flushPromises()

    expect(api.sendChatMessage).toHaveBeenCalledWith('s10001', '推荐通勤耳机')
    expect(wrapper.text()).toContain('推荐通勤耳机')
    expect(wrapper.text()).toContain('推荐这款蓝牙降噪耳机')
    expect(wrapper.text()).toContain('相关商品推荐')

    await wrapper.get('button.related-card').trigger('click')
    expect(routerMock.push).toHaveBeenCalledWith('/detail/10001')
  })

  it('renders a graceful assistant fallback when sending fails', async () => {
    vi.mocked(api.createChatSession).mockResolvedValue({ session_id: 's10001', title: '新对话' })
    vi.mocked(api.sendChatMessage).mockRejectedValue(new Error('ai down'))

    const wrapper = mount(AIChatView)
    await wrapper.findAll('button').find((button) => button.text().includes('开始新对话'))!.trigger('click')
    await flushPromises()
    await wrapper.get('input[placeholder^="输入你的购物需求"]').setValue('推荐杯子')
    await wrapper.get('form.chat-input-area').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('AI 助手暂时无法响应')
  })
})
