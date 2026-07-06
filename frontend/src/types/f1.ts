export interface Driver {
  abbr: string
  name: string
  team: string
  num: number
}

/** F1alytics Score – Komponenten-Karte eines Fahrers in einem Rennen */
export interface ScoreCard {
  score: number // Endwert 1–10
  q: number // Qualifying-Teil 0–10
  r: number // Ergebnis-Teil 0–10
  delta: number // Positionsveränderung-Teil 0–10
  base: number // gewichtete Basis vor Modifikatoren
  modifiers: number // Summe der Modifikatoren
  dnf: boolean
  note: string
}

export interface RaceResultRow {
  abbr: string
  name: string
  team: string
  color: string
  pos: number
  pts: number
  gapText: string
  dnf: boolean
  dns: boolean
  dsq: boolean
  laps: number | null
  score: ScoreCard | null
}

export interface SessionResultRow {
  abbr: string
  name: string
  team: string
  color: string
  pos: number
  bestLap: string
  gap: string
  dnf: boolean
  dns: boolean
  dsq: boolean
}

export interface PracticeSession {
  name: string
  result: SessionResultRow[]
}

export interface Race {
  gp: string
  country: string
  circuit: string
  lat: number
  lon: number
  date: string
  round: number
  completed: boolean
  result: RaceResultRow[]
  fastestLap: RaceResultRow | null
  circuitImage: string | null
  countryFlag: string | null
  qualifyingResult: SessionResultRow[]
  practiceResults: PracticeSession[]
  sessionKey: number
  sessionDateStart: string
}

export interface ReplayDriver {
  num: number
  abbr: string
  name: string
  team: string
  color: string
}

export interface ReplayFrame {
  t: number
  p: Record<string, [number, number]>
}

export interface ReplayData {
  drivers: ReplayDriver[]
  frames: ReplayFrame[]
  duration: number
}

export interface LapInfo {
  driverNumber: number
  lapNumber: number
  t: number
  duration: number | null
  sector1: number | null
  sector2: number | null
  sector3: number | null
}

export interface StintInfo {
  driverNumber: number
  compound: string
  lapStart: number
  lapEnd: number
}

export interface PositionPoint {
  driverNumber: number
  position: number
  t: number
}

export interface FlagEvent {
  t: number
  flag: string
  scope: string | null
}

export interface IntervalPoint {
  driverNumber: number
  gapSec: number | null
  t: number
}

export interface TimingData {
  laps: LapInfo[]
  stints: StintInfo[]
  positions: PositionPoint[]
  flags: FlagEvent[]
  intervals: IntervalPoint[]
  gridPosition: Record<string, number>
}

export interface DriverStanding extends Driver {
  color: string
  points: number
  wins: number
  podiums: number
  dnf: number
  finishes: number[]
  cum: number[]
  avgFinish: number | null
  bestFinish: number | null
  avgScore: number | null
  scoreHistory: number[]
  headshot: string | null
  maxTopSpeed: number | null
  avgTopSpeed: number | null
}

export interface TeamStanding {
  team: string
  color: string
  points: number
  wins: number
  drivers: DriverStanding[]
}

export interface QuizCircuit {
  name: string
  imageUrl: string
}

export interface QuizDriver {
  abbr: string
  name: string
  headshotUrl: string
  countryCode: string
  countryName: string
}

export interface QuizData {
  circuits: QuizCircuit[]
  drivers: QuizDriver[]
}

export interface SeasonStats {
  races: Race[]
  drivers: DriverStanding[]
  teams: TeamStanding[]
  loading: boolean
  totalRaces: number
  liveSessionBlocked: boolean
}
