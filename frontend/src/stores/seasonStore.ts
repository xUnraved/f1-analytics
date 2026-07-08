/**
 * Pinia-Store für alle Saison- und Renndaten.
 *
 * Hält einen RAM-Cache (Map Jahr → SeasonStats), um wiederholte API-Aufrufe
 * beim Wechsel zwischen Saisons zu vermeiden. Solange das Backend den Cache
 * aufbaut (loading=true in der Antwort), wird alle 6 Sekunden erneut abgefragt.
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchSeasons, fetchSeason, refreshSeason as apiRefreshSeason, refreshRace as apiRefreshRace, refreshRaceSingle as apiRefreshRaceSingle } from '@/services/f1Api'
import type { SeasonStats, Race, DriverStanding, TeamStanding } from '@/types/f1'

export const useSeasonStore = defineStore('season', () => {
  /** RAM-Cache: Jahr → fertig geladene SeasonStats (kein Polling-Zustand). */
  const cache = new Map<number, SeasonStats>()
  const years = ref<number[]>([])
  const year = ref(new Date().getFullYear())
  const stats = ref<SeasonStats | null>(null)
  const selectedRaceIndex = ref<number | null>(null)
  /** Kürzel der für den Vergleich ausgewählten Fahrer (max. 3). */
  const selectedDrivers = ref<string[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const races = computed<Race[]>(() => stats.value?.races ?? [])
  const drivers = computed<DriverStanding[]>(() => stats.value?.drivers ?? [])
  const teams = computed<TeamStanding[]>(() => stats.value?.teams ?? [])
  const totalRaces = computed<number>(() => stats.value?.totalRaces ?? 0)
  const liveSessionBlocked = computed<boolean>(() => stats.value?.liveSessionBlocked ?? false)
  const selectedRace = computed<Race | null>(() =>
    selectedRaceIndex.value === null ? null : (races.value[selectedRaceIndex.value] ?? null),
  )

  /** Lädt die verfügbaren Saisonjahre; setzt year auf das neueste wenn aktuelles fehlt. */
  async function loadYears() {
    try {
      years.value = await fetchSeasons()
      if (years.value.length && !years.value.includes(year.value)) {
        year.value = years.value[years.value.length - 1]!
      }
    } catch {
      error.value = 'Saisons konnten nicht geladen werden.'
    }
  }

  let pollTimer: ReturnType<typeof setTimeout> | null = null

  /** Bricht einen laufenden Polling-Timer ab. */
  function stopPoll() {
    if (pollTimer !== null) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  /**
   * Lädt Saisondaten aus RAM-Cache oder vom Backend.
   * Falls Backend loading=true meldet, wird nach 6 s erneut versucht.
   */
  async function loadSeason(y: number) {
    stopPoll()
    const cached = cache.get(y)
    if (cached && !cached.loading) {
      stats.value = cached
      loading.value = false
      return
    }
    loading.value = true
    error.value = null
    try {
      const data = await fetchSeason(y)
      if (data.loading) {
        // Backend lädt noch im Hintergrund — nach 6s erneut versuchen
        stats.value = data
        pollTimer = setTimeout(() => loadSeason(y), 6000)
      } else {
        cache.set(y, data)
        stats.value = data
        loading.value = false
      }
    } catch {
      error.value = 'Saisondaten konnten nicht geladen werden.'
      loading.value = false
    }
  }

  /** Wechselt das aktive Jahr und setzt Renn- und Fahrerauswahl zurück. */
  async function selectYear(y: number) {
    year.value = y
    selectedRaceIndex.value = null
    selectedDrivers.value = []
    await loadSeason(y)
  }

  /** Öffnet ein Rennen anhand seines Index in races[]; setzt Fahrerauswahl zurück. */
  function openRace(index: number) {
    selectedRaceIndex.value = index
    selectedDrivers.value = []
  }

  /** Schließt die Rennendetail-Ansicht. */
  function clearRace() {
    selectedRaceIndex.value = null
  }

  /** Schaltet einen Fahrer in der Vergleichsauswahl um (max. 3 gleichzeitig). */
  function toggleDriver(abbr: string) {
    const i = selectedDrivers.value.indexOf(abbr)
    if (i >= 0) selectedDrivers.value.splice(i, 1)
    else if (selectedDrivers.value.length < 3) selectedDrivers.value.push(abbr)
  }

  /** Löscht den DB-Cache der Saison im Backend und lädt neu. */
  async function refreshSeason() {
    const y = year.value
    cache.delete(y)
    await apiRefreshSeason(y)
    await loadSeason(y)
  }

  /** Löscht den Race-Cache einer Session im Backend und lädt die Saison neu. */
  async function refreshRace(sessionKey: number) {
    const y = year.value
    cache.delete(y)
    await apiRefreshRace(sessionKey, y)
    await loadSeason(y)
  }

  /** Lädt nur dieses eine Rennen neu (synchron, kein Polling). */
  async function refreshRaceSingle(sessionKey: number) {
    const y = year.value
    loading.value = true
    error.value = null
    try {
      const data = await apiRefreshRaceSingle(sessionKey, y)
      cache.set(y, data)
      stats.value = data
    } catch {
      error.value = 'Renndaten konnten nicht neu geladen werden.'
    } finally {
      loading.value = false
    }
  }

  return {
    years,
    year,
    stats,
    races,
    totalRaces,
    liveSessionBlocked,
    drivers,
    teams,
    selectedRaceIndex,
    selectedRace,
    selectedDrivers,
    loading,
    error,
    loadYears,
    loadSeason,
    selectYear,
    openRace,
    clearRace,
    toggleDriver,
    refreshSeason,
    refreshRace,
    refreshRaceSingle,
  }
})
