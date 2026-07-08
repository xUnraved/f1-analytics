/** Kompakte Fahrer-Identifikation (Kürzel, Name, Team, Startnummer). */
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

/** Ergebniszeile eines Fahrers in der Renntabelle inkl. F1alytics Score. */
export interface RaceResultRow {
  abbr: string
  name: string
  team: string
  /** Teamfarbe als Hex-String ohne „#"-Präfix (z. B. „E8002D" für Ferrari). */
  color: string
  pos: number
  /** WM-Punkte, nur für Race-Sessions > 0. */
  pts: number
  gapText: string
  dnf: boolean
  dns: boolean
  dsq: boolean
  laps: number | null
  score: ScoreCard | null
}

/** Ergebniszeile für Qualifying- und Trainings-Sessions (bestLap statt pts). */
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

/** Eine Trainingssession (FP1/FP2/FP3/Sprint Shootout) mit Ergebnistabelle. */
export interface PracticeSession {
  name: string
  result: SessionResultRow[]
}

/**
 * Vollständige Daten eines GP-Wochenendes: Rennergebnis, Qualifying,
 * Trainings sowie Metadaten (Koordinaten für den 3D-Globus, Runden-Nr.).
 */
export interface Race {
  gp: string
  country: string
  circuit: string
  /** Breitengrad der Strecke – wird für den 3D-Globus verwendet. */
  lat: number
  /** Längengrad der Strecke – wird für den 3D-Globus verwendet. */
  lon: number
  date: string
  round: number
  /** false wenn das Rennen noch aussteht (Ergebnistabellen sind leer). */
  completed: boolean
  result: RaceResultRow[]
  fastestLap: RaceResultRow | null
  circuitImage: string | null
  countryFlag: string | null
  qualifyingResult: SessionResultRow[]
  practiceResults: PracticeSession[]
  /** OpenF1 session_key der Race-Session (Primärschlüssel im Backend). */
  sessionKey: number
  sessionDateStart: string
}

/** Fahrer-Metadaten für die GPS-Replay-Ansicht (Farbe für Fahrzeug-Marker). */
export interface ReplayDriver {
  num: number
  abbr: string
  name: string
  team: string
  color: string
}

/**
 * Ein Zeitrahmen der GPS-Replay-Animation.
 * t: Millisekunden seit Session-Start.
 * p: Map von Fahrernummer (als String) auf [x, y] kartesische Koordinaten.
 */
export interface ReplayFrame {
  t: number
  p: Record<string, [number, number]>
}

/** Vollständige GPS-Replay-Payload: Fahrer, Frames (~4 Hz) und Gesamtdauer. */
export interface ReplayData {
  drivers: ReplayDriver[]
  frames: ReplayFrame[]
  duration: number
}

/** Rundendaten eines Fahrers: Sektorzeiten in Sekunden, t = ms seit Session-Start. */
export interface LapInfo {
  driverNumber: number
  lapNumber: number
  t: number
  duration: number | null
  sector1: number | null
  sector2: number | null
  sector3: number | null
}

/** Reifenstint: Compound (SOFT/MEDIUM/HARD/INTERMEDIATE/WET), Rundenbereich. */
export interface StintInfo {
  driverNumber: number
  compound: string
  lapStart: number
  lapEnd: number
}

/** Fahrposition (~1 Hz) für das Positions-Diagramm im Timing-Panel. */
export interface PositionPoint {
  driverNumber: number
  position: number
  t: number
}

/** Race-Control-Flagge mit Zeitstempel und optionalem Geltungsbereich (Sektor). */
export interface FlagEvent {
  t: number
  flag: string
  scope: string | null
}

/** Zeitabstand zum Vordermann in Sekunden; null = Führender oder überrundet. */
export interface IntervalPoint {
  driverNumber: number
  gapSec: number | null
  t: number
}

/**
 * Vollständige Timing-Payload für eine Race-Session.
 * gridPosition: Map Fahrernummer → Startplatz (aus OpenF1 /grid).
 */
export interface TimingData {
  laps: LapInfo[]
  stints: StintInfo[]
  positions: PositionPoint[]
  flags: FlagEvent[]
  intervals: IntervalPoint[]
  gridPosition: Record<string, number>
}

/**
 * Fahrer-WM-Stand inkl. Statistiken für Charts und Ranglisten.
 * cum: kumulierte Punkte nach jedem Rennen (für den Punkteverlauf-Chart).
 * finishes: Platzierer-Liste aller Rennen (für Finish-Verteilung).
 * scoreHistory: F1alytics-Score je Rennen in chronologischer Reihenfolge.
 */
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

/** Konstrukteurs-WM-Stand mit den beiden Fahrern des Teams. */
export interface TeamStanding {
  team: string
  color: string
  points: number
  wins: number
  drivers: DriverStanding[]
}

/** Streckendaten für den Circuit-Quiz (Name + Streckenbild-URL). */
export interface QuizCircuit {
  name: string
  imageUrl: string
}

/**
 * Fahrerprofil für den Driver-Quiz.
 * birthYear: aus der statischen BIRTH_YEARS-Map im Backend (nicht von OpenF1).
 */
export interface QuizDriver {
  abbr: string
  name: string
  headshotUrl: string
  countryCode: string
  countryName: string
  birthYear?: number
}

/** Payload des /quiz-Endpunkts mit allen Strecken- und Fahrerprofilen. */
export interface QuizData {
  circuits: QuizCircuit[]
  drivers: QuizDriver[]
}

/**
 * Aggregierte Saisondaten – Top-Level-Payload von GET /api/season.
 * loading: true wenn der Backend-Cache noch aufgebaut wird (Polling nötig).
 * liveSessionBlocked: true wenn OpenF1 HTTP 401 zurückgibt (Live-Qualifying/-Rennen läuft).
 */
export interface SeasonStats {
  races: Race[]
  drivers: DriverStanding[]
  teams: TeamStanding[]
  loading: boolean
  totalRaces: number
  liveSessionBlocked: boolean
}
