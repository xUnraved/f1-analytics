import { describe, it, expect } from 'vitest'
import { countryLabel } from '@/i18n/countries'

describe('countryLabel', () => {
  it('übersetzt deutsche Ländernamen nach Englisch', () => {
    expect(countryLabel('Deutschland', 'en')).toBe('Germany')
    expect(countryLabel('Großbritannien', 'en')).toBe('United Kingdom')
    expect(countryLabel('Niederlande', 'en')).toBe('Netherlands')
  })

  it('übersetzt englische Ländernamen nach Deutsch', () => {
    expect(countryLabel('Italy', 'de')).toBe('Italien')
    expect(countryLabel('United Kingdom', 'de')).toBe('Großbritannien')
    expect(countryLabel('Great Britain', 'de')).toBe('Großbritannien')
    expect(countryLabel('United Arab Emirates', 'de')).toBe('Vereinigte Arabische Emirate')
  })

  it('lässt unbekannte Namen unverändert', () => {
    expect(countryLabel('Atlantis', 'en')).toBe('Atlantis')
    expect(countryLabel('Atlantis', 'de')).toBe('Atlantis')
  })

  it('behandelt leere Werte robust', () => {
    expect(countryLabel(null, 'de')).toBe('')
    expect(countryLabel(undefined, 'en')).toBe('')
  })

  it('identische Namen bleiben in beiden Sprachen stabil', () => {
    expect(countryLabel('Monaco', 'en')).toBe('Monaco')
    expect(countryLabel('Monaco', 'de')).toBe('Monaco')
  })
})
