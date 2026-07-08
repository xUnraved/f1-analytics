/**
 * HTTP-Service-Layer für alle Quarkus-Backend-Endpunkte.
 * Alle Funktionen werfen bei HTTP-Fehler einen Error mit Statuscode.
 */
import type { SeasonStats, QuizData } from '@/types/f1'

/** Basis-URL des Quarkus-Backends (dev: localhost:8081). */
const BASE_URL = 'http://localhost:8081/api'

/** Eine einzelne Quiz-Frage mit Antwortoptionen, korrekter Antwort und Punktwert. */
export interface QuizQuestionDto {
  question: string
  options: string[]
  answer: string
  level: number
  points: number
}

/** Saison-Quiz: 12 Fragen zur gewählten Saison. */
export interface SeasonQuizDto {
  year: number
  questions: QuizQuestionDto[]
}

/**
 * Millionär-Quiz: 15 Fragen mit Punkteleiter (100–1.000.000)
 * und Sicherheitsstufen (SAFE_LEVELS) bei Frage 5 und 10.
 */
export interface MillionaireQuizDto {
  questions: QuizQuestionDto[]
  ladder: number[]
  safeLevels: number[]
}

/**
 * Fahrer-Prognose für das nächste Rennen.
 * winProb/podiumProb: Softmax-Wahrscheinlichkeiten (0–1).
 * form: Formwert der letzten 3 Rennen (0–10).
 * track: historischer Streckenwert (null wenn < 2 Rennen auf dieser Strecke).
 * rating: kombinierter Gesamt-Score (35% WM-Punkte + 30% Form + 20% Siege + 15% Strecke).
 */
export interface DriverForecastDto {
  abbr: string
  name: string
  team: string
  color: string
  winProb: number
  podiumProb: number
  form: number
  track: number | null
  trackRaces: number
  trend: 'up' | 'down' | 'flat'
  rating: number
}

/** Prognose-Payload für eine Saison inkl. nächstem GP und Basis der Streckenauswertung. */
export interface ForecastDto {
  year: number
  completedRaces: number
  nextGp: string | null
  nextRound: number | null
  trackName: string | null
  /** 'circuit' = Streckenname matcht, 'country' = Länder-Fallback. */
  trackBasis: 'circuit' | 'country' | null
  drivers: DriverForecastDto[]
}

/** Gibt alle verfügbaren Saisonjahre zurück (GET /api/seasons). */
export async function fetchSeasons(): Promise<number[]> {
  const res = await fetch(`${BASE_URL}/seasons`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt die aggregierten Saisondaten; liefert loading=true wenn Backend noch cacht. */
export async function fetchSeason(year: number): Promise<SeasonStats> {
  const res = await fetch(`${BASE_URL}/season?year=${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt GPS-Replay-Frames für eine Race-Session (~4 Hz, kartesische Koordinaten). */
export async function fetchReplay(sessionKey: number, dateStart: string): Promise<import('@/types/f1').ReplayData> {
  const res = await fetch(`${BASE_URL}/replay?session_key=${sessionKey}&date_start=${encodeURIComponent(dateStart)}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt Timing-Daten (Runden, Stints, Positionen, Flags, Abstände) für eine Session. */
export async function fetchTiming(sessionKey: number, dateStart: string): Promise<import('@/types/f1').TimingData> {
  const res = await fetch(`${BASE_URL}/replay/timing?session_key=${sessionKey}&date_start=${encodeURIComponent(dateStart)}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Löscht den DB-Cache einer ganzen Saison (DELETE /api/season/cache). */
export async function refreshSeason(year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/season/cache?year=${year}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}

/** Löscht den Race-Cache einer einzelnen Session (DELETE /api/race). */
export async function refreshRace(sessionKey: number, year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/race?session_key=${sessionKey}&year=${year}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}

/** Lädt ein einzelnes Rennen synchron neu und gibt die aktualisierte SeasonStats zurück. */
export async function refreshRaceSingle(sessionKey: number, year: number): Promise<SeasonStats> {
  const res = await fetch(`${BASE_URL}/race/refresh?session_key=${sessionKey}&year=${year}`, { method: 'POST' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Gibt alle Strecken- und Fahrerprofile für die Quiz-Ansicht zurück. */
export async function fetchQuizData(): Promise<QuizData> {
  const res = await fetch(`${BASE_URL}/quiz`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt einen saison-spezifischen Quiz mit 12 Fragen. */
export async function fetchSeasonQuiz(year: number, lang: string = 'de'): Promise<SeasonQuizDto> {
  const res = await fetch(`${BASE_URL}/quiz/season/${year}?lang=${lang}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt den Millionär-Quiz mit 15 Fragen, Leiter und Sicherheitsstufen. */
export async function fetchMillionaireQuiz(lang: string = 'de'): Promise<MillionaireQuizDto> {
  const res = await fetch(`${BASE_URL}/quiz/millionaire?lang=${lang}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Lädt die Softmax-Fahrer-Prognose für das nächste Rennen einer Saison. */
export async function fetchForecast(year: number): Promise<ForecastDto> {
  const res = await fetch(`${BASE_URL}/forecast/${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

/** Löscht den GPS-Replay-Cache einer Session (DELETE /api/replay). */
export async function refreshReplay(sessionKey: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/replay?session_key=${sessionKey}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}
