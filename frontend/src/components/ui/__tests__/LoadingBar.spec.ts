import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoadingBar from '../LoadingBar.vue'

describe('LoadingBar', () => {
  it('zeigt das Label an', () => {
    const wrapper = mount(LoadingBar, { props: { label: 'Rennen werden geladen …' } })
    expect(wrapper.text()).toContain('Rennen werden geladen …')
  })

  it('rundet und zeigt den Fortschritt, wenn pct gesetzt ist', () => {
    const wrapper = mount(LoadingBar, { props: { label: 'x', pct: 41.6 } })
    expect(wrapper.find('.lb-pct').text()).toBe('42%')
    expect(wrapper.find('.lb-fill').attributes('style')).toContain('width: 42%')
  })

  it('läuft ohne pct im Endlos-Modus', () => {
    const wrapper = mount(LoadingBar, { props: { label: 'x' } })
    expect(wrapper.find('.lb-pct').exists()).toBe(false)
    expect(wrapper.find('.lb-fill').classes()).toContain('indeterminate')
  })

  it('zeigt die Unterzeile nur, wenn vorhanden', () => {
    expect(mount(LoadingBar, { props: { label: 'x', sub: 'ca. 10s' } }).text()).toContain('ca. 10s')
    expect(mount(LoadingBar, { props: { label: 'x' } }).find('.lb-sub').exists()).toBe(false)
  })
})
