import type { SeasonStats, QuizData } from '@/types/f1'

const BASE_URL = 'http://localhost:8081/api'

/** Gibt alle verfügbaren Saisons zurück (z.B. [2023, 2024, 2025, 2026]) */
export async function fetchSeasons(): Promise<number[]> {
  const res = await fetch(`${BASE_URL}/seasons`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt die aggregierten Saisondaten (Rennen, Fahrer, Teams).
 *  Gibt loading:true zurück, wenn der Backend-Thread noch läuft. */
export async function fetchSeason(year: number): Promise<SeasonStats> {
  const res = await fetch(`${BASE_URL}/season?year=${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt die Replay-Positionsdaten für eine Race-Session (on demand). */
export async function fetchReplay(sessionKey: number, dateStart: string): Promise<import('@/types/f1').ReplayData> {
  const res = await fetch(`${BASE_URL}/replay?session_key=${sessionKey}&date_start=${encodeURIComponent(dateStart)}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Löscht den Saison-Cache (DB + RAM) → Backend lädt alles neu von OpenF1. */
export async function refreshSeason(year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/season/cache?year=${year}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}

/** Löscht nur das Rennergebnis einer Session → Saison wird neu geladen. */
export async function refreshRace(sessionKey: number, year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/race?session_key=${sessionKey}&year=${year}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}

/**
 * Löscht nur dieses eine Rennen aus der DB und holt es synchron von der OpenF1-API.
 * Gibt die vollständig aktualisierten SeasonStats zurück — kein Polling nötig.
 */
export async function refreshRaceSingle(sessionKey: number, year: number): Promise<SeasonStats> {
  const res = await fetch(`${BASE_URL}/race/refresh?session_key=${sessionKey}&year=${year}`, { method: 'POST' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Liefert Quizdaten (Strecken + Fahrer). */
export async function fetchQuizData(): Promise<QuizData> {
  const res = await fetch(`${BASE_URL}/quiz`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Löscht die Replay-Positionsdaten einer Session → nächster Abruf holt von OpenF1. */
export async function refreshReplay(sessionKey: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/replay?session_key=${sessionKey}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}
