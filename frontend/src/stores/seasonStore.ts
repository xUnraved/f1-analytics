import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchSeasons, fetchSeason } from '@/services/f1Api'
import type { SeasonStats, Race, DriverStanding, TeamStanding } from '@/types/f1'

export const useSeasonStore = defineStore('season', () => {
  const cache = new Map<number, SeasonStats>()
  const years = ref<number[]>([])
  const year = ref(new Date().getFullYear())
  const stats = ref<SeasonStats | null>(null)
  const selectedRaceIndex = ref<number | null>(null)
  const selectedDrivers = ref<string[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const races = computed<Race[]>(() => stats.value?.races ?? [])
  const drivers = computed<DriverStanding[]>(() => stats.value?.drivers ?? [])
  const teams = computed<TeamStanding[]>(() => stats.value?.teams ?? [])
  const selectedRace = computed<Race | null>(() =>
    selectedRaceIndex.value === null ? null : (races.value[selectedRaceIndex.value] ?? null),
  )

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

  async function loadSeason(y: number) {
    const cached = cache.get(y)
    if (cached) {
      stats.value = cached
      return
    }
    loading.value = true
    error.value = null
    try {
      const data = await fetchSeason(y)
      cache.set(y, data)
      stats.value = data
    } catch {
      error.value = 'Saisondaten konnten nicht geladen werden.'
    } finally {
      loading.value = false
    }
  }

  async function selectYear(y: number) {
    year.value = y
    selectedRaceIndex.value = null
    selectedDrivers.value = []
    await loadSeason(y)
  }

  function openRace(index: number) {
    selectedRaceIndex.value = index
    selectedDrivers.value = []
  }

  function clearRace() {
    selectedRaceIndex.value = null
  }

  function toggleDriver(abbr: string) {
    const i = selectedDrivers.value.indexOf(abbr)
    if (i >= 0) selectedDrivers.value.splice(i, 1)
    else if (selectedDrivers.value.length < 3) selectedDrivers.value.push(abbr)
  }

  return {
    years,
    year,
    stats,
    races,
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
  }
})
