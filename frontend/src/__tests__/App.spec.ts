import { describe, it, expect } from 'vitest'
import App from '../App.vue'

describe('App', () => {
  it('ist als Komponente definiert', () => {
    expect(App).toBeTruthy()
  })
})
