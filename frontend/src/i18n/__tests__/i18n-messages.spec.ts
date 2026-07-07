import { describe, it, expect } from 'vitest'
import de from '@/i18n/de'
import en from '@/i18n/en'

type Msg = Record<string, unknown>

function flatten(obj: Msg, prefix = ''): Map<string, string> {
  const out = new Map<string, string>()
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object') {
      for (const [ck, cv] of flatten(v as Msg, path)) out.set(ck, cv)
    } else {
      out.set(path, String(v))
    }
  }
  return out
}

function params(msg: string): string {
  return [...msg.matchAll(/\{(\w+)\}/g)]
    .map((m) => m[1]!)
    .sort()
    .join(',')
}

describe('i18n-Sprachdateien', () => {
  const deFlat = flatten(de as Msg)
  const enFlat = flatten(en as Msg)

  it('en.ts enthält jeden Key aus de.ts', () => {
    const missing = [...deFlat.keys()].filter((k) => !enFlat.has(k))
    expect(missing).toEqual([])
  })

  it('de.ts enthält jeden Key aus en.ts', () => {
    const missing = [...enFlat.keys()].filter((k) => !deFlat.has(k))
    expect(missing).toEqual([])
  })

  it('Platzhalter stimmen zwischen de und en überein', () => {
    const mismatches: string[] = []
    for (const [k, v] of deFlat) {
      const other = enFlat.get(k)
      if (other !== undefined && params(v) !== params(other)) mismatches.push(k)
    }
    expect(mismatches).toEqual([])
  })

  it('keine leeren Übersetzungen', () => {
    const empty = [...deFlat.entries(), ...enFlat.entries()]
      .filter(([, v]) => v.trim() === '')
      .map(([k]) => k)
    expect(empty).toEqual([])
  })
})
