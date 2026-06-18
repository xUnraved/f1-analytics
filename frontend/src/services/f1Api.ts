import type { SeasonStats } from '@/types/f1'

const BASE_URL = 'http://localhost:8081/api'

export async function fetchSeasons(): Promise<number[]> {
  const res = await fetch(`${BASE_URL}/seasons`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchSeason(year: number): Promise<SeasonStats> {
  const res = await fetch(`${BASE_URL}/season?year=${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchSessions(year: number) {
  const res = await fetch(`${BASE_URL}/sessions?year=${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchRaceResults(sessionKey: number) {
  const res = await fetch(`${BASE_URL}/sessions/${sessionKey}/results`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchDrivers(sessionKey: number) {
  const res = await fetch(`${BASE_URL}/sessions/${sessionKey}/drivers`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}
