import { flushPromises, mount } from '@vue/test-utils'
import MerchantUploadView from './MerchantUploadView.vue'
import * as api from '../services/api'

vi.mock('../services/api', () => ({
  createMerchantProduct: vi.fn(),
  uploadProductImage: vi.fn(),
}))

class SuccessfulImage {
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  set src(_value: string) {
    setTimeout(() => this.onload?.(), 0)
  }
}

describe('MerchantUploadView', () => {
  beforeEach(() => {
    vi.stubGlobal('Image', SuccessfulImage)
    vi.stubGlobal('alert', vi.fn())
  })

  it('validates empty name, invalid price, and negative stock', async () => {
    const wrapper = mount(MerchantUploadView)
    const inputs = wrapper.findAll('input:not([type="file"])')
    await inputs[0].setValue('')
    await inputs[1].setValue('-1')
    await inputs[2].setValue(-2)
    await inputs[4].setValue('https://example.com/uploads/products/test.jpg')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('商品名不能为空')
    expect(wrapper.text()).toContain('价格必须为大于 0 的数字')
    expect(wrapper.text()).toContain('库存必须为非负整数')
    expect(api.createMerchantProduct).not.toHaveBeenCalled()
  })

  it('submits valid product data to the merchant create API', async () => {
    vi.mocked(api.createMerchantProduct).mockResolvedValue({ product_id: '10099', status: 'ON_SALE', vector_index_status: 'PENDING' })
    const wrapper = mount(MerchantUploadView)
    const inputs = wrapper.findAll('input:not([type="file"])')
    await inputs[0].setValue('智能保温杯')
    await inputs[1].setValue('129.00')
    await inputs[2].setValue(80)
    await inputs[3].setValue('办公,保温')
    await inputs[4].setValue('https://example.com/uploads/products/cup.jpg')
    await wrapper.get('textarea').setValue('适合办公的保温杯')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.createMerchantProduct).toHaveBeenCalledWith(expect.objectContaining({
      name: '智能保温杯',
      description: '适合办公的保温杯',
      price: '129.00',
      stock: 80,
      tags: ['办公', '保温'],
      image_urls: ['https://example.com/uploads/products/cup.jpg'],
    }))
    expect(wrapper.text()).toContain('10099 / ON_SALE / PENDING')
  })
})
