import { flushPromises, mount } from '@vue/test-utils'
import MerchantView from './MerchantView.vue'
import * as api from '../services/api'

vi.mock('../services/api', () => ({
  deleteMerchantProduct: vi.fn(),
  listMerchantProducts: vi.fn(),
}))

const product = {
  product_id: '10001',
  name: '蓝牙降噪耳机',
  price: '299.00',
  stock: 120,
  image_url: 'https://example.com/headphones.jpg',
  tags: ['蓝牙', '降噪'],
}

describe('MerchantView', () => {
  beforeEach(() => {
    vi.stubGlobal('alert', vi.fn())
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it('renders merchant products and calls delete when off-sale is confirmed', async () => {
    vi.mocked(api.listMerchantProducts).mockResolvedValue({ items: [product], page: 1, size: 20, total: 1, has_next: false })
    vi.mocked(api.deleteMerchantProduct).mockResolvedValue({ product_id: '10001', status: 'OFF_SALE', vector_index_status: 'DELETED' })

    const wrapper = mount(MerchantView)
    await flushPromises()

    expect(wrapper.text()).toContain('蓝牙降噪耳机')
    expect(wrapper.text()).toContain('¥299.00')
    expect(wrapper.text()).toContain('蓝牙,降噪')

    await wrapper.findAll('button').find((button) => button.text().includes('下架'))!.trigger('click')
    await flushPromises()

    expect(api.deleteMerchantProduct).toHaveBeenCalledWith('10001')
    expect(wrapper.text()).not.toContain('蓝牙降噪耳机')
  })

  it('does not crash when the merchant API fails', async () => {
    vi.mocked(api.listMerchantProducts).mockRejectedValue(new Error('merchant down'))

    mount(MerchantView)
    await flushPromises()

    expect(alert).toHaveBeenCalledWith('无法取得数据！')
  })
})
