import { afterEach, vi } from 'vitest'
import { config } from '@vue/test-utils'

config.global.stubs = {
  RouterLink: {
    props: ['to'],
    emits: ['click'],
    template: '<a :href="typeof to === \'string\' ? to : to?.path || \'#\'" @click.prevent="$emit(\'click\', $event)"><slot /></a>',
  },
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  localStorage.clear()
})
