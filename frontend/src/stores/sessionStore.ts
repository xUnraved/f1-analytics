import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchSessions } from '@/services/f1Api'

export interface Session {
  sessionKey: number
  sessionName: string
  sessionType: string
  countryName: string
  circuitShortName: string
  dateStart: string
  year: number
}

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<Session[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

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

  return { sessions, loading, error, loadSessions }
})
