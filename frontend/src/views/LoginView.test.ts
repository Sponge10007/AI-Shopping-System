import { flushPromises, mount } from '@vue/test-utils'
import LoginView from './LoginView.vue'
import * as api from '../services/api'
import { sessionState } from '../stores/session'

const push = vi.fn()
const replace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push, replace }),
}))

vi.mock('../services/api', () => ({
  loginApi: vi.fn(),
  register: vi.fn(),
}))

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionState.token = ''
    sessionState.refreshToken = ''
    sessionState.userId = ''
    sessionState.role = 'CUSTOMER'
    sessionState.userInfo = null
    sessionState.devMode = false
  })

  it('shows the backend login error without creating a fake session', async () => {
    vi.mocked(api.loginApi).mockRejectedValue(new Error('账号或密码错误'))

    const wrapper = mount(LoginView)
    await wrapper.get('input[placeholder="手机号或用户名"]').setValue('alice')
    await wrapper.get('input[placeholder="请输入密码"]').setValue('wrong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('账号或密码错误')
    expect(sessionState.token).toBe('')
    expect(push).not.toHaveBeenCalled()
  })

  it('shows registration failures without creating a fake account', async () => {
    vi.mocked(api.register).mockRejectedValue(new Error('手机号已注册'))

    const wrapper = mount(LoginView)
    const tabs = wrapper.findAll('.segmented-control button')
    await tabs[1].trigger('click')

    await wrapper.get('input[placeholder="请输入用户名"]').setValue('alice')
    await wrapper.get('input[placeholder="请输入手机号"]').setValue('13800000000')
    await wrapper.get('input[placeholder="请输入密码（至少6位）"]').setValue('Password123!')
    await wrapper.get('input[placeholder="请再次输入密码"]').setValue('Password123!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('手机号已注册')
    expect(api.loginApi).not.toHaveBeenCalled()
    expect(sessionState.token).toBe('')
    expect(push).not.toHaveBeenCalled()
  })
})
