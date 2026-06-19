import type { SeasonStats } from '@/types/f1'

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
