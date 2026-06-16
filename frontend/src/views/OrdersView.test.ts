import { flushPromises, mount } from '@vue/test-utils'
import OrdersView from './OrdersView.vue'
import * as api from '../services/api'

vi.mock('../services/api', () => ({
  getOrders: vi.fn(),
  payOrder: vi.fn(),
}))

const order = {
  order_id: 'o10001',
  user_id: 'u10001',
  status: 'CREATED',
  total_amount: '598.00',
  items: [{ product_id: '10001', name: '蓝牙降噪耳机', unit_price: '299.00', quantity: 2 }],
  receiver: { name: 'Alice', phone: '13800000000', address: 'Hangzhou' },
}

describe('OrdersView', () => {
  it('renders orders and pays a created order', async () => {
    vi.mocked(api.getOrders)
      .mockResolvedValueOnce({ items: [order], page: 1, size: 20, total: 1, has_next: false })
      .mockResolvedValueOnce({ items: [{ ...order, status: 'PAID' }], page: 1, size: 20, total: 1, has_next: false })
    vi.mocked(api.payOrder).mockResolvedValue({ payment_id: 'p1', order_id: 'o10001', payment_status: 'PAID' })

    const wrapper = mount(OrdersView)
    await flushPromises()

    expect(wrapper.text()).toContain('蓝牙降噪耳机 x2')
    expect(wrapper.text()).toContain('¥598.00')

    await wrapper.get('button[title="立即支付"]').trigger('click')
    await flushPromises()

    expect(api.payOrder).toHaveBeenCalledWith('o10001')
    expect(api.getOrders).toHaveBeenCalledTimes(2)
  })

  it('shows an error banner when loading orders fails', async () => {
    vi.mocked(api.getOrders).mockRejectedValue(new Error('订单服务不可用'))

    const wrapper = mount(OrdersView)
    await flushPromises()

    expect(wrapper.text()).toContain('订单服务不可用')
  })
})
