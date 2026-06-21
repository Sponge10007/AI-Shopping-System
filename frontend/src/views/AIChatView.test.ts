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
  getChatMessages: vi.fn(),
  listChatSessions: vi.fn(),
  recordBehavior: vi.fn(() => Promise.resolve({ accepted: true })),
  streamChatMessage: vi.fn(),
}))

const relatedProduct = {
  product_id: '10001',
  name: '蓝牙降噪耳机',
  price: '299.00',
  stock: 120,
  image_url: 'https://example.com/headphones.jpg',
}

describe('AIChatView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.listChatSessions).mockResolvedValue([])
    vi.mocked(api.getChatMessages).mockResolvedValue([])
  })

  it('creates a session, sends a message, renders the AI reply, and opens related products', async () => {
    vi.mocked(api.createChatSession).mockResolvedValue({ session_id: 's10001', title: '新对话', created_at: '2026-06-16T00:00:00Z' })
    vi.mocked(api.streamChatMessage).mockImplementation(async (_sessionId, _content, handlers) => {
      handlers?.onDelta?.('推荐这款')
      handlers?.onDelta?.('蓝牙降噪耳机')
      return {
        session_id: 's10001',
        answer: '推荐这款蓝牙降噪耳机',
        image_list: [],
        link_list: [],
        related_products: [relatedProduct],
      }
    })

    const wrapper = mount(AIChatView)
    await wrapper.findAll('button').find((button) => button.text().includes('开始新对话'))!.trigger('click')
    await flushPromises()

    await wrapper.get('input[placeholder^="输入你的购物需求"]').setValue('推荐通勤耳机')
    await wrapper.get('form.chat-input-area').trigger('submit')
    await flushPromises()

    expect(api.streamChatMessage).toHaveBeenCalledWith(
      's10001',
      '推荐通勤耳机',
      expect.objectContaining({ onDelta: expect.any(Function) }),
    )
    expect(wrapper.text()).toContain('推荐通勤耳机')
    expect(wrapper.text()).toContain('推荐这款蓝牙降噪耳机')
    expect(wrapper.text()).toContain('相关商品推荐')

    await wrapper.get('button.related-card').trigger('click')
    expect(routerMock.push).toHaveBeenCalledWith('/detail/10001')
  })

  it('renders a graceful assistant fallback when sending fails', async () => {
    vi.mocked(api.createChatSession).mockResolvedValue({ session_id: 's10001', title: '新对话' })
    vi.mocked(api.streamChatMessage).mockRejectedValue(new Error('ai down'))

    const wrapper = mount(AIChatView)
    await wrapper.findAll('button').find((button) => button.text().includes('开始新对话'))!.trigger('click')
    await flushPromises()
    await wrapper.get('input[placeholder^="输入你的购物需求"]').setValue('推荐杯子')
    await wrapper.get('form.chat-input-area').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('AI 助手暂时无法响应')
  })

  it('restores the latest saved session and messages on mount', async () => {
    vi.mocked(api.listChatSessions).mockResolvedValue([
      { session_id: 's-history', title: '通勤耳机', created_at: '2026-06-22T01:00:00Z' },
    ])
    vi.mocked(api.getChatMessages).mockResolvedValue([
      {
        role: 'user',
        content: '推荐通勤耳机',
        created_at: '2026-06-22T01:01:00Z',
      },
      {
        role: 'assistant',
        content: '推荐数据库中的耳机',
        related_products: [relatedProduct],
        created_at: '2026-06-22T01:01:02Z',
      },
    ])

    const wrapper = mount(AIChatView)
    await flushPromises()

    expect(api.listChatSessions).toHaveBeenCalled()
    expect(api.getChatMessages).toHaveBeenCalledWith('s-history')
    expect(wrapper.text()).toContain('通勤耳机')
    expect(wrapper.text()).toContain('推荐通勤耳机')
    expect(wrapper.text()).toContain('推荐数据库中的耳机')
    expect(wrapper.text()).toContain('相关商品推荐')
  })
})
