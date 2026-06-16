import { mount } from '@vue/test-utils'
import ProductGrid from './ProductGrid.vue'

const products = [
  {
    product_id: '10001',
    name: '蓝牙降噪耳机',
    price: '299.00',
    stock: 120,
    image_url: 'https://example.com/headphones.jpg',
    rating: 4.8,
    score: 0.93,
    reason: '适合通勤',
  },
]

describe('ProductGrid', () => {
  it('renders product image, name, price, rating, and match reason', () => {
    const wrapper = mount(ProductGrid, { props: { products } })

    expect(wrapper.text()).toContain('蓝牙降噪耳机')
    expect(wrapper.text()).toContain('¥299.00')
    expect(wrapper.text()).toContain('4.8')
    expect(wrapper.text()).toContain('适合通勤')
    expect(wrapper.get('img').attributes('src')).toBe('https://example.com/headphones.jpg')
    expect(wrapper.get('img').attributes('alt')).toBe('蓝牙降噪耳机')
  })

  it('shows an empty state when there are no products', () => {
    const wrapper = mount(ProductGrid, { props: { products: [] } })

    expect(wrapper.text()).toContain('暂无商品')
    expect(wrapper.find('.product-card').exists()).toBe(false)
  })

  it('emits select and add events from the card and action button', async () => {
    const wrapper = mount(ProductGrid, { props: { products } })

    await wrapper.get('.product-card').trigger('click')
    await wrapper.get('button.circle-button').trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual([products[0]])
    expect(wrapper.emitted('add')?.[0]).toEqual([products[0]])
  })
})
