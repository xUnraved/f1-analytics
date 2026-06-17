<template>
  <div class="f1-app">

    <!-- ═══ HEADER ═══ -->
    <header class="f1-header">
      <div class="stripe"></div>
      <div class="header-inner">
        <div class="brand">
          <div class="brand-f1">F1</div>
          <span class="brand-label">ANALYTICS</span>
        </div>
        <nav class="year-nav">
          <button
            v-for="y in years"
            :key="y"
            :class="['year-btn', { active: selectedYear === y }]"
            @click="selectYear(y)"
          >{{ y }}</button>
        </nav>
      </div>
    </header>

    <!-- ═══ CONTENT ═══ -->
    <main class="f1-content">

      <!-- Loading (sessions) -->
      <div v-if="store.loading && !store.selectedSession" class="state-loading">
        <div class="spinner"></div>
        <p>DATEN WERDEN GELADEN</p>
      </div>

      <!-- Error -->
      <div v-else-if="store.error" class="state-error">⚠ {{ store.error }}</div>

      <!-- ─── SESSION LIST ─── -->
      <template v-else-if="!store.selectedSession">
        <div class="section-header">
          <div class="section-line"></div>
          <span>SESSIONS {{ selectedYear }}</span>
          <div class="section-line"></div>
        </div>
        <div class="session-grid">
          <div
            v-for="s in store.sessions"
            :key="s.sessionKey"
            class="session-card"
            @click="store.selectSession(s)"
          >
            <div :class="['type-badge', typeClass(s.sessionType)]">{{ s.sessionType }}</div>
            <div class="card-country">{{ s.countryName }}</div>
            <div class="card-name">{{ s.sessionName }}</div>
            <div class="card-circuit">{{ s.circuitShortName }}</div>
            <div class="card-footer">
              <span class="card-date">{{ formatDate(s.dateStart) }}</span>
              <span class="card-caret">›</span>
            </div>
          </div>
        </div>
      </template>

      <!-- ─── RESULTS VIEW ─── -->
      <template v-else>
        <div class="results-topbar">
          <button class="btn-back" @click="store.selectedSession = null">← ZURÜCK</button>
          <div class="results-meta">
            <h1 class="results-country">{{ store.selectedSession.countryName.toUpperCase() }}</h1>
            <p class="results-session-name">{{ store.selectedSession.sessionName }}</p>
            <p class="results-circuit">{{ store.selectedSession.circuitShortName }}</p>
          </div>
        </div>

        <div v-if="store.loading" class="state-loading">
          <div class="spinner"></div>
          <p>ERGEBNISSE WERDEN GELADEN</p>
        </div>

        <div v-else-if="store.raceResults.length === 0" class="state-empty">
          Keine Ergebnisse verfügbar.
        </div>

        <div v-else class="table-scroll">
          <table class="results-table">
            <thead>
              <tr>
                <th>POS</th>
                <th>FAHRER</th>
                <th>TEAM</th>
                <th>RUNDEN</th>
                <th>ABSTAND</th>
                <th>STATUS</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in sortedResults"
                :key="r.driver_number"
                :class="rowClass(r)"
              >
                <td>
                  <span :class="['pos-badge', posBadgeClass(r)]">{{ r.position ?? '—' }}</span>
                </td>
                <td>
                  <div class="driver-cell">
                    <span class="team-bar" :style="{ background: getTeamColor(r.driver_number) }"></span>
                    <span class="driver-name">{{ store.getDriverName(r.driver_number) }}</span>
                  </div>
                </td>
                <td class="cell-team">{{ getDriver(r.driver_number)?.team_name ?? '—' }}</td>
                <td>{{ r.number_of_laps ?? '—' }}</td>
                <td class="cell-gap">{{ formatGap(r) }}</td>
                <td>
                  <span :class="['status-chip', statusClass(r)]">{{ formatStatus(r) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useSessionStore, type RaceResult } from '@/stores/sessionStore'

const store = useSessionStore()
const selectedYear = ref(2024)
const years = [2023, 2024, 2025]

onMounted(() => store.loadSessions(selectedYear.value))

function selectYear(y: number) {
  selectedYear.value = y
  store.loadSessions(y)
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: 'short', year: 'numeric' })
}

function typeClass(type: string) {
  const t = (type ?? '').toLowerCase()
  if (t === 'race') return 'badge-race'
  if (t.includes('qualifying') || t.includes('shootout')) return 'badge-quali'
  if (t.includes('sprint')) return 'badge-sprint'
  return 'badge-practice'
}

const sortedResults = computed(() =>
  [...store.raceResults].sort((a, b) => {
    if (a.position == null) return 1
    if (b.position == null) return -1
    return a.position - b.position
  })
)

function getDriver(driverNumber: number) {
  return store.drivers.find(d => d.driver_number === driverNumber)
}

const TEAM_COLORS: Record<string, string> = {
  'red bull':    '#3671C6',
  'ferrari':     '#E8002D',
  'mercedes':    '#27F4D2',
  'mclaren':     '#FF8000',
  'aston martin':'#358C75',
  'alpine':      '#FF87BC',
  'williams':    '#64C4FF',
  'sauber':      '#52E252',
  'haas':        '#B6BABD',
}

const RB_EXACT: Record<string, string> = { 'rb': '#6692FF', 'visa cash app rb': '#6692FF' }

function getTeamColor(driverNumber: number) {
  const team = getDriver(driverNumber)?.team_name ?? ''
  const lower = team.toLowerCase().trim()
  if (RB_EXACT[lower]) return RB_EXACT[lower]
  for (const [key, color] of Object.entries(TEAM_COLORS)) {
    if (lower.includes(key)) return color
  }
  return '#555'
}

function rowClass(r: RaceResult) {
  if (r.position === 1) return 'row-winner'
  if (r.dnf || r.dns || r.dsq) return 'row-out'
  return ''
}

function posBadgeClass(r: RaceResult) {
  if (r.dnf || r.dns || r.dsq) return 'pos-out'
  if (r.position === 1) return 'pos-gold'
  if (r.position === 2) return 'pos-silver'
  if (r.position === 3) return 'pos-bronze'
  return ''
}

function statusClass(r: RaceResult) {
  if (r.dnf) return 'chip-dnf'
  if (r.dns) return 'chip-dns'
  if (r.dsq) return 'chip-dsq'
  return 'chip-ok'
}

function formatStatus(r: RaceResult) {
  if (r.dnf) return 'DNF'
  if (r.dns) return 'DNS'
  if (r.dsq) return 'DSQ'
  return 'FINISH'
}

function formatGap(r: RaceResult) {
  if (r.position === 1) return '—'
  if (r.gap_to_leader == null) return '—'
  if (typeof r.gap_to_leader === 'number') return `+${r.gap_to_leader.toFixed(3)}s`
  return String(r.gap_to_leader)
}
</script>

<style scoped>
/* ── Base ── */
.f1-app {
  min-height: 100vh;
  background: #0f0f0f;
}

/* ── Header ── */
.f1-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #111;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.7);
}

.stripe {
  height: 4px;
  background: #e10600;
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  height: 56px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-f1 {
  background: #e10600;
  color: #fff;
  font-weight: 900;
  font-size: 1rem;
  padding: 5px 16px 5px 10px;
  clip-path: polygon(0 0, 82% 0, 100% 50%, 82% 100%, 0 100%);
  letter-spacing: 2px;
}

.brand-label {
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 5px;
  color: #fff;
  text-transform: uppercase;
}

.year-nav {
  display: flex;
  gap: 2px;
}

.year-btn {
  background: transparent;
  border: 1px solid #2a2a2a;
  color: #555;
  font-family: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 2px;
  padding: 6px 18px;
  cursor: pointer;
  transition: all 0.15s;
}

.year-btn:hover {
  border-color: #e10600;
  color: #fff;
}

.year-btn.active {
  background: #e10600;
  border-color: #e10600;
  color: #fff;
}

/* ── Content ── */
.f1-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 2.5rem 2rem;
}

/* ── States ── */
.state-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 5rem;
  color: #3a3a3a;
  font-size: 0.7rem;
  letter-spacing: 5px;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #1e1e1e;
  border-top-color: #e10600;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.state-error {
  text-align: center;
  padding: 3rem;
  color: #e10600;
  letter-spacing: 1px;
  font-size: 0.9rem;
}

.state-empty {
  text-align: center;
  padding: 4rem;
  color: #2a2a2a;
  font-size: 0.75rem;
  letter-spacing: 4px;
  text-transform: uppercase;
}

/* ── Section header ── */
.section-header {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 5px;
  color: #3a3a3a;
  margin-bottom: 1.5rem;
}

.section-line {
  flex: 1;
  height: 1px;
  background: #1a1a1a;
}

/* ── Session grid ── */
.session-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 2px;
  background: #191919;
}

.session-card {
  background: #111;
  padding: 1.25rem 1.25rem 1rem;
  cursor: pointer;
  transition: background 0.15s;
  border-left: 3px solid transparent;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-card:hover {
  background: #161616;
  border-left-color: #e10600;
}

.type-badge {
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 2px;
  padding: 2px 8px;
  display: inline-block;
  margin-bottom: 8px;
  text-transform: uppercase;
  width: fit-content;
}

.badge-race     { background: #e10600; color: #fff; }
.badge-sprint   { background: #7c3aed; color: #fff; }
.badge-quali    { background: #c45000; color: #fff; }
.badge-practice { background: #1a1a1a; color: #444; border: 1px solid #252525; }

.card-country {
  font-size: 1.3rem;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 1px;
  line-height: 1.1;
  color: #fff;
}

.card-name {
  font-size: 0.72rem;
  color: #555;
  letter-spacing: 1px;
  text-transform: uppercase;
  margin-top: 2px;
}

.card-circuit {
  font-size: 0.68rem;
  color: #333;
  margin-top: 5px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid #1a1a1a;
}

.card-date {
  font-size: 0.68rem;
  color: #3a3a3a;
  letter-spacing: 1px;
}

.card-caret {
  color: #222;
  font-size: 1.3rem;
  line-height: 1;
  transition: all 0.15s;
}

.session-card:hover .card-caret {
  color: #e10600;
  transform: translateX(4px);
}

/* ── Results topbar ── */
.results-topbar {
  display: flex;
  align-items: flex-start;
  gap: 2rem;
  margin-bottom: 2.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid #1a1a1a;
}

.btn-back {
  flex-shrink: 0;
  background: transparent;
  border: 1px solid #2a2a2a;
  color: #555;
  font-family: inherit;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 2px;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 8px;
}

.btn-back:hover {
  border-color: #e10600;
  color: #fff;
}

.results-country {
  font-size: 2.8rem;
  font-weight: 900;
  letter-spacing: 2px;
  line-height: 1;
  color: #fff;
}

.results-session-name {
  font-size: 0.8rem;
  color: #555;
  letter-spacing: 3px;
  text-transform: uppercase;
  margin-top: 6px;
}

.results-circuit {
  font-size: 0.68rem;
  color: #2e2e2e;
  margin-top: 5px;
  letter-spacing: 1px;
}

/* ── Results table ── */
.table-scroll {
  overflow-x: auto;
}

.results-table {
  width: 100%;
  border-collapse: collapse;
}

.results-table thead tr {
  border-bottom: 2px solid #e10600;
}

.results-table th {
  padding: 8px 14px;
  text-align: left;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 3px;
  color: #3a3a3a;
}

.results-table tbody tr {
  border-bottom: 1px solid #161616;
  transition: background 0.1s;
}

.results-table tbody tr:hover {
  background: #161616;
}

.row-winner {
  background: rgba(225, 6, 0, 0.04);
}

.row-out {
  opacity: 0.4;
}

.results-table td {
  padding: 11px 14px;
  font-size: 0.9rem;
  color: #ccc;
}

/* Position badge */
.pos-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-size: 0.85rem;
  font-weight: 900;
  border-radius: 3px;
  background: #1a1a1a;
  color: #fff;
}

.pos-gold   { background: #b8962a; color: #000; }
.pos-silver { background: #8a8a8a; color: #000; }
.pos-bronze { background: #8a4a20; color: #fff; }
.pos-out    { background: #141414; color: #2a2a2a; }

/* Driver cell */
.driver-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team-bar {
  width: 3px;
  height: 18px;
  border-radius: 2px;
  flex-shrink: 0;
}

.driver-name {
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

/* Team cell */
.cell-team {
  color: #444;
  font-size: 0.78rem;
  letter-spacing: 0.3px;
}

/* Gap cell */
.cell-gap {
  font-variant-numeric: tabular-nums;
  color: #666;
  font-size: 0.82rem;
  font-family: 'Courier New', monospace;
}

/* Status chip */
.status-chip {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 1px;
  padding: 3px 8px;
  border-radius: 2px;
}

.chip-ok  { background: #0a1f0e; color: #22c55e; }
.chip-dnf { background: #250a08; color: #e10600; }
.chip-dns { background: #1a1500; color: #c9a227; }
.chip-dsq { background: #180a1a; color: #a855f7; }
</style>
