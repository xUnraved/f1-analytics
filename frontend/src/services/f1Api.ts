import type { SeasonStats, QuizData } from '@/types/f1'

const BASE_URL = 'http://localhost:8081/api'

export interface QuizQuestionDto {
  question: string
  options: string[]
  answer: string
  level: number
  points: number
}

export interface SeasonQuizDto {
  year: number
  questions: QuizQuestionDto[]
}

export interface MillionaireQuizDto {
  questions: QuizQuestionDto[]
  ladder: number[]
  safeLevels: number[]
}

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

export interface ForecastDto {
  year: number
  completedRaces: number
  nextGp: string | null
  nextRound: number | null
  trackName: string | null
  trackBasis: 'circuit' | 'country' | null
  drivers: DriverForecastDto[]
}

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

export async function fetchReplay(sessionKey: number, dateStart: string): Promise<import('@/types/f1').ReplayData> {
  const res = await fetch(`${BASE_URL}/replay?session_key=${sessionKey}&date_start=${encodeURIComponent(dateStart)}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchTiming(sessionKey: number, dateStart: string): Promise<import('@/types/f1').TimingData> {
  const res = await fetch(`${BASE_URL}/replay/timing?session_key=${sessionKey}&date_start=${encodeURIComponent(dateStart)}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function refreshSeason(year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/season/cache?year=${year}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}

export async function refreshRace(sessionKey: number, year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/race?session_key=${sessionKey}&year=${year}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}

export async function refreshRaceSingle(sessionKey: number, year: number): Promise<SeasonStats> {
  const res = await fetch(`${BASE_URL}/race/refresh?session_key=${sessionKey}&year=${year}`, { method: 'POST' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchQuizData(): Promise<QuizData> {
  const res = await fetch(`${BASE_URL}/quiz`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchSeasonQuiz(year: number, lang: string = 'de'): Promise<SeasonQuizDto> {
  const res = await fetch(`${BASE_URL}/quiz/season/${year}?lang=${lang}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchMillionaireQuiz(lang: string = 'de'): Promise<MillionaireQuizDto> {
  const res = await fetch(`${BASE_URL}/quiz/millionaire?lang=${lang}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function fetchForecast(year: number): Promise<ForecastDto> {
  const res = await fetch(`${BASE_URL}/forecast/${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

export async function refreshReplay(sessionKey: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/replay?session_key=${sessionKey}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
}
