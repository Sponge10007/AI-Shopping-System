import { flushPromises, mount } from '@vue/test-utils'
import MerchantRestockView from './MerchantRestockView.vue'
import * as api from '../services/api'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '10001' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../services/api', () => ({
  getMerchantProduct: vi.fn(),
  restockProduct: vi.fn(),
}))

describe('MerchantRestockView', () => {
  it('validates restock quantity and calls the restock API', async () => {
    vi.mocked(api.getMerchantProduct).mockResolvedValue({
      product_id: '10001',
      merchant_id: 'm10001',
      name: '蓝牙降噪耳机',
      description: 'desc',
      category_id: 'c1',
      category_name: '耳机',
      price: '299.00',
      stock: 120,
      sales: 1,
      rating: 4.8,
      status: 'ON_SALE',
      tags: [],
      image_urls: [],
    })
    vi.mocked(api.restockProduct).mockResolvedValue({ product_id: '10001', stock: 130 })

    const wrapper = mount(MerchantRestockView)
    await flushPromises()
    expect(wrapper.text()).toContain('当前库存：120')

    await wrapper.get('button.black-button').trigger('click')
    expect(wrapper.text()).toContain('补货数量必须为正整数')

    await wrapper.get('input[type="number"]').setValue(10)
    await wrapper.get('button.black-button').trigger('click')
    await flushPromises()

    expect(api.restockProduct).toHaveBeenCalledWith('10001', 10)
    expect(wrapper.text()).toContain('当前库存：130')
    expect(wrapper.text()).toContain('补货成功')
  })
})
