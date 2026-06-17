import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchSessions, fetchRaceResults, fetchDrivers } from '@/services/f1Api'

export interface Session {
  sessionKey: number
  sessionName: string
  sessionType: string
  countryName: string
  circuitShortName: string
  dateStart: string
  year: number
}

export interface RaceResult {
  session_key: number
  driver_number: number
  position: number
  number_of_laps: number
  duration: number
  gap_to_leader: string | number | null
  dnf: boolean
  dns: boolean
  dsq: boolean
  meeting_key: number
}

export interface Driver {
  session_key: number
  driver_number: number
  broadcast_name: string
  first_name: string
  full_name: string
  last_name: string
  team_name: string
  meeting_key: number
}

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<Session[]>([])
  const raceResults = ref<RaceResult[]>([])
  const drivers = ref<Driver[]>([])
  const selectedSession = ref<Session | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Computed helper to get driver name by driver number
  const getDriverName = (driverNumber: number) => {
    const driver = drivers.value.find(d => d.driver_number === driverNumber)
    return driver ? driver.broadcast_name : `#${driverNumber}`
  }

  async function loadSessions(year: number) {
    loading.value = true
    error.value = null
    try {
      sessions.value = await fetchSessions(year)
    } catch (e) {
      error.value = 'Daten konnten nicht geladen werden.'
    } finally {
      loading.value = false
    }
  }

  async function loadRaceResults(sessionKey: number) {
    loading.value = true
    error.value = null
    try {
      raceResults.value = await fetchRaceResults(sessionKey)
    } catch (e) {
      error.value = 'Rennresultate konnten nicht geladen werden.'
    } finally {
      loading.value = false
    }
  }

  async function loadDrivers(sessionKey: number) {
    try {
      drivers.value = await fetchDrivers(sessionKey)
    } catch (e) {
      // Fehler bei Fahrern ist nicht kritisch
      drivers.value = []
    }
  }

  function selectSession(session: Session) {
    selectedSession.value = session
    loadRaceResults(session.sessionKey)
    loadDrivers(session.sessionKey)
  }

  return { 
    sessions, 
    raceResults, 
    drivers,
    selectedSession, 
    loading, 
    error, 
    loadSessions, 
    selectSession,
    getDriverName
  }
})
