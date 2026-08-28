import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import App from './App.vue'

describe('App', () => {
  it('affiche le titre du portail salon', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('Apontaja — Portail salon')
  })
})
