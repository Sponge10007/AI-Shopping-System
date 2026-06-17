import router from './index'
import { sessionState } from '../stores/session'

describe('router guards', () => {
  beforeEach(async () => {
    sessionState.token = ''
    sessionState.role = 'CUSTOMER'
    sessionState.userId = ''
    sessionState.userInfo = null
    await router.push('/').catch(() => {})
    await router.isReady()
  })

  it('redirects unauthenticated users away from protected routes', async () => {
    await router.push('/orders')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/orders')
  })

  it('redirects customers away from admin routes and allows admins', async () => {
    sessionState.token = 'token'
    sessionState.role = 'CUSTOMER'
    await router.push('/admin')
    expect(router.currentRoute.value.name).toBe('shopping')

    sessionState.role = 'ADMIN'
    await router.push('/admin')
    expect(router.currentRoute.value.name).toBe('admin')
  })
})
