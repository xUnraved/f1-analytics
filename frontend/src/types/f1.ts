export interface Driver {
  abbr: string
  name: string
  team: string
  num: number
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
}

export interface TeamStanding {
  team: string
  color: string
  points: number
  wins: number
  drivers: DriverStanding[]
}

export interface SeasonStats {
  races: Race[]
  drivers: DriverStanding[]
  teams: TeamStanding[]
  loading: boolean
}
