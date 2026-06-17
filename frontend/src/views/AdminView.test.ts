import { flushPromises, mount } from '@vue/test-utils'
import AdminView from './AdminView.vue'
import AdminUsersView from './AdminUsersView.vue'
import AdminMetricsView from './AdminMetricsView.vue'
import * as api from '../services/api'

vi.mock('../services/api', () => ({
  getAdminOverview: vi.fn(),
  listAdminUsers: vi.fn(),
  updateUserStatus: vi.fn(),
}))

const users = [
  { user_id: 'admin10001', username: 'admin', phone: '13800000002', role: 'ADMIN', status: 'ACTIVE', created_at: '2026-06-16T00:00:00Z' },
  { user_id: 'm10001', username: 'merchant', phone: '13800000001', role: 'MERCHANT', status: 'ACTIVE', created_at: '2026-06-16T00:00:00Z' },
  { user_id: 'u10001', username: 'alice', phone: '13800000000', role: 'CUSTOMER', status: 'DISABLED', created_at: '2026-06-16T00:00:00Z' },
]

describe('Admin views', () => {
  beforeEach(() => {
    vi.stubGlobal('alert', vi.fn())
  })

  it('renders admin entry cards', () => {
    const wrapper = mount(AdminView)

    expect(wrapper.text()).toContain('平台智能监控')
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('平台监控')
  })

  it('renders users by role and updates user status', async () => {
    vi.mocked(api.listAdminUsers).mockResolvedValue({ items: users, page: 1, size: 20, total: 3, has_next: false })
    vi.mocked(api.updateUserStatus).mockResolvedValue({ user_id: 'admin10001', status: 'DISABLED' })

    const wrapper = mount(AdminUsersView)
    await flushPromises()

    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('商家')
    expect(wrapper.text()).toContain('普通用户')
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('merchant')
    expect(wrapper.text()).toContain('alice')

    await wrapper.findAll('button').find((button) => button.text().includes('禁用'))!.trigger('click')
    await flushPromises()

    expect(api.updateUserStatus).toHaveBeenCalledWith('admin10001', 'DISABLED')
  })

  it('renders platform metrics and handles API failure gracefully', async () => {
    vi.mocked(api.getAdminOverview).mockResolvedValueOnce({
      user_count: 3,
      product_count: 2,
      order_count: 1,
      today_order_count: 1,
      search_count_today: 5,
      ai_chat_count_today: 6,
      ai_service_status: 'UP',
      vector_db_status: 'UP',
    })

    const wrapper = mount(AdminMetricsView)
    await flushPromises()

    expect(wrapper.text()).toContain('用户')
    expect(wrapper.text()).toContain('商品')
    expect(wrapper.text()).toContain('AI Service')
    expect(wrapper.text()).toContain('Vector DB')

    vi.mocked(api.getAdminOverview).mockRejectedValueOnce(new Error('metrics down'))
    mount(AdminMetricsView)
    await flushPromises()
    expect(alert).toHaveBeenCalledWith('无法获得数据！')
  })
})
