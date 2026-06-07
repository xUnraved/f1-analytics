const BASE_URL = 'http://localhost:8080/api'

export async function fetchSessions(year: number) {
  const res = await fetch(`${BASE_URL}/sessions?year=${year}`)
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}
