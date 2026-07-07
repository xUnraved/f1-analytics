import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSeasonStore } from '../seasonStore'

describe('seasonStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('startet ohne ausgewähltes Rennen', () => {
    const store = useSeasonStore()
    expect(store.selectedRaceIndex).toBeNull()
    expect(store.selectedRace).toBeNull()
  })

  it('openRace setzt den Index und leert die Fahrer-Auswahl', () => {
    const store = useSeasonStore()
    store.selectedDrivers.push('VER')
    store.openRace(3)
    expect(store.selectedRaceIndex).toBe(3)
    expect(store.selectedDrivers).toEqual([])
  })

  it('clearRace setzt die Auswahl zurück', () => {
    const store = useSeasonStore()
    store.openRace(1)
    store.clearRace()
    expect(store.selectedRaceIndex).toBeNull()
  })

  it('selectedRace ist null, wenn der Index ins Leere zeigt', () => {
    const store = useSeasonStore()
    store.openRace(99)
    expect(store.selectedRace).toBeNull()
  })

  it('toggleDriver erlaubt maximal 3 Fahrer und toggelt korrekt', () => {
    const store = useSeasonStore()
    store.toggleDriver('VER')
    store.toggleDriver('NOR')
    store.toggleDriver('LEC')
    store.toggleDriver('HAM')
    expect(store.selectedDrivers).toEqual(['VER', 'NOR', 'LEC'])
    store.toggleDriver('NOR')
    expect(store.selectedDrivers).toEqual(['VER', 'LEC'])
  })
})
