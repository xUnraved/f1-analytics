<template>
  <div class="panel">
    <section class="card pick">
      <div class="search">
        <svg viewBox="0 0 24 24" class="search-ico" aria-hidden="true">
          <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <input
          v-model="query"
          type="text"
          class="search-input"
          :placeholder="t('common.searchTeam')"
          @focus="focused = true"
          @blur="focused = false"
          @keydown.enter="addFirst"
          @keydown.esc="query = ''"
        />
        <button v-if="query" type="button" class="search-clear" @click="query = ''" :aria-label="t('common.clear')">×</button>

        <div v-if="suggestions.length" class="suggest">
          <button
            v-for="t in suggestions"
            :key="t.team"
            type="button"
            class="sug"
            @mousedown.prevent
            @click="addTeam(t.team)"
          >
            <span class="sug-photo team" :style="`--dc:${t.color};background:${t.color}`">{{ teamInitials(t.team) }}</span>
            <span class="sug-text">
              <span class="sug-name"><b>{{ t.team }}</b></span>
              <span class="sug-team">P{{ rank(t) }} · {{ t.points }} PKT · {{ t.drivers.map((d) => d.abbr).join(' / ') }}</span>
            </span>
            <span class="sug-add">+</span>
          </button>
        </div>
        <div v-else-if="focused && full" class="sug-note">{{ t('common.maxTeams') }}</div>
        <div v-else-if="focused && query.trim()" class="sug-note">{{ t('common.noResultFor', { q: query.trim() }) }}</div>
      </div>

      <div class="tiles" :style="tilesCols">
        <div
          v-for="(t, i) in selected"
          :key="t.team"
          class="tile"
          :class="{ dragging: dragIndex === i, over: overIndex === i }"
          :style="`--dc:${dcolor(t)}`"
          draggable="true"
          @dragstart="onDragStart(i)"
          @dragover.prevent="overIndex = i"
          @dragleave="overIndex = overIndex === i ? null : overIndex"
          @drop="onDrop(i)"
          @dragend="resetDrag"
        >
          <span class="tile-photo team" :style="{ background: dcolor(t) }">{{ teamInitials(t.team) }}</span>
          <span class="tile-info">
            <span class="tile-abbr">{{ short(t.team) }}</span>
            <span class="tile-name">{{ t.drivers.map((d) => d.abbr).join(' · ') }}</span>
            <span class="tile-team">P{{ rank(t) }} · {{ t.points }} PKT</span>
          </span>
          <span class="tile-score">
            <span class="tile-score-val">{{ f1Text(t) }}</span>
            <span class="tile-score-lbl">F1ALYTICS Ø</span>
          </span>
          <button type="button" class="tile-x" @click.stop="removeTeam(t.team)" :aria-label="t('common.remove')">×</button>
        </div>
        <div v-if="!selected.length" class="tiles-empty">
          {{ t('common.addTeam') }}
        </div>
      </div>
    </section>

    <template v-if="selected.length">
      <div class="dash">
        <section class="card">
          <div class="card-title">{{ t('teams.profile') }}</div>
          <svg :viewBox="`0 0 ${RW} ${RH}`" width="100%" class="radar">
            <polygon v-for="(ring, ri) in rings" :key="'ring' + ri" :points="ring" class="r-ring" />
            <line v-for="(ax, ai) in axes" :key="'ax' + ai" :x1="CX" :y1="CY" :x2="axPt(ai, RR)[0]" :y2="axPt(ai, RR)[1]" class="r-ax" />
            <polygon
              v-for="t in selected"
              :key="'p' + t.team"
              :points="poly(t)"
              :style="{ stroke: dcolor(t), fill: dcolor(t) + '24' }"
              class="r-poly"
            />
            <text
              v-for="(ax, ai) in axes"
              :key="'l' + ai"
              :x="axPt(ai, RR * 1.16)[0]"
              :y="axPt(ai, RR * 1.16)[1]"
              class="r-lbl"
              :text-anchor="anchor(ai)"
            >
              {{ ax.l }}
            </text>
          </svg>
        </section>

        <section class="card">
          <div class="card-title">{{ t('teams.comparison') }}</div>
          <div class="bars">
            <div v-for="m in barMetrics" :key="m.l" class="bgroup">
              <div class="blabel mono">{{ m.l }}</div>
              <div v-for="t in selected" :key="t.team" class="brow">
                <span class="bteam" :style="{ color: dcolor(t) }">{{ short(t.team) }}</span>
                <div class="btrack">
                  <div class="bfill" :style="{ width: barPct(m, t), background: dcolor(t) }"></div>
                </div>
                <span class="bval">{{ m.f(m.g(t)) }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="dash">
        <section class="card">
          <div class="card-title">{{ t('teams.moreStats') }}</div>
          <div class="grid" :style="gridCols">
            <div class="row">
              <div class="lbl"></div>
              <div v-for="t in selected" :key="t.team" class="hd" :style="{ borderColor: dcolor(t) }">
                <span :style="{ color: dcolor(t) }">{{ short(t.team) }}</span>
              </div>
            </div>
            <div v-for="m in tableMetrics" :key="m.l" class="row">
              <div class="lbl">{{ m.l }}</div>
              <div v-for="t in selected" :key="t.team" class="val" :class="{ best: isBest(m, t) }">
                {{ m.f(m.g(t)) }}
              </div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-title">{{ t('teams.pointsProgress') }}</div>
          <svg :viewBox="`0 0 ${W} ${H}`" width="100%" class="chart">
            <line v-for="(gy, i) in gridLines" :key="i" :x1="pad" :y1="gy" :x2="W - pad" :y2="gy" class="g" />
            <polyline
              v-for="t in selected"
              :key="'c' + t.team"
              :points="line(t)"
              fill="none"
              :stroke="dcolor(t)"
              stroke-width="2.5"
              stroke-linejoin="round"
            />
          </svg>
          <div class="legend">
            <span v-for="t in selected" :key="'lg' + t.team"><i :style="{ background: dcolor(t) }"></i>{{ short(t.team) }}</span>
          </div>
        </section>
      </div>

      <section class="card">
        <div class="ch">
          <span class="card-title" style="margin: 0">{{ t('teams.teamDrivers') }}</span>
          <span class="hint mono">{{ t('teams.hint') }}</span>
        </div>
        <div class="dpick">
          <button
            v-for="t in selected"
            :key="'d' + t.team"
            type="button"
            class="chip"
            :class="{ on: duelTeam === t.team }"
            :style="duelTeam === t.team ? { borderColor: dcolor(t), color: dcolor(t) } : {}"
            @click="duelTeam = t.team"
          >
            {{ t.team }}
          </button>
        </div>
        <div v-if="duel && duel.drivers.length >= 2" class="grid duel-grid" :style="duelCols">
          <div class="row">
            <div class="lbl"></div>
            <div v-for="d in duel.drivers.slice(0, 2)" :key="d.abbr" class="hd" :style="{ borderColor: d.color }">
              <span class="ab">{{ d.abbr }}</span>
              <span class="sub">{{ d.name }}</span>
            </div>
          </div>
          <div v-for="m in driverMetrics" :key="m.l" class="row">
            <div class="lbl">{{ m.l }}</div>
            <div
              v-for="d in duel.drivers.slice(0, 2)"
              :key="d.abbr"
              class="val"
              :class="{ best: duelBest(m, duel.drivers.slice(0, 2), d) }"
            >
              {{ m.f(m.g(d)) }}
            </div>
          </div>
        </div>
        <div v-else class="solo">{{ t('teams.onlyOneDriver') }}</div>
      </section>
    </template>

    <div v-else class="empty">{{ t('teams.selectTeam') }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import type { TeamStanding, DriverStanding } from '@/types/f1'

const { t } = useI18n()

interface TMetric {
  l: string
  g: (t: TeamStanding) => number | null
  f: (v: number | null) => string
  low?: boolean
}
interface Axis {
  l: string
  g: (t: TeamStanding) => number | null
  invert?: boolean
}
interface DMetric {
  l: string
  g: (d: DriverStanding) => number | null
  f: (v: number | null) => string
  low?: boolean
}

const store = useSeasonStore()

const RW = 340
const RH = 262
const CX = 170
const CY = 126
const RR = 86

const W = 460
const H = 220
const pad = 14

const query = ref('')
const focused = ref(false)
const picked = ref<string[]>([])
const duelTeam = ref<string | null>(null)

function seed() {
  if (!store.teams.length) {
    picked.value = []
    return
  }
  const valid = picked.value.filter((name) => store.teams.some((t) => t.team === name))
  picked.value = valid.length ? valid : store.teams.slice(0, Math.min(2, store.teams.length)).map((t) => t.team)
  if (!duelTeam.value || !picked.value.includes(duelTeam.value)) {
    duelTeam.value = picked.value[0] ?? null
  }
}
seed()
watch(() => store.teams, seed)

const full = computed(() => picked.value.length >= 3)

const selected = computed<TeamStanding[]>(() =>
  picked.value.map((name) => store.teams.find((t) => t.team === name)).filter((t): t is TeamStanding => !!t),
)
const duel = computed<TeamStanding | null>(() => store.teams.find((t) => t.team === duelTeam.value) ?? null)

const suggestions = computed<TeamStanding[]>(() => {
  if (full.value) return []
  const avail = store.teams.filter((t) => !picked.value.includes(t.team))
  const q = query.value.trim().toLowerCase()
  if (!q) return focused.value ? avail : []
  return avail.filter(
    (t) =>
      t.team.toLowerCase().includes(q) ||
      t.drivers.some((d) => d.abbr.toLowerCase().includes(q) || d.name.toLowerCase().includes(q)),
  )
})

function addTeam(name: string) {
  if (picked.value.length < 3 && !picked.value.includes(name)) picked.value.push(name)
  query.value = ''
  if (!duelTeam.value) duelTeam.value = name
}
function addFirst() {
  const first = suggestions.value[0]
  if (first) addTeam(first.team)
}
function removeTeam(name: string) {
  picked.value = picked.value.filter((t) => t !== name)
  if (duelTeam.value === name) duelTeam.value = picked.value[0] ?? null
}

const dragIndex = ref<number | null>(null)
const overIndex = ref<number | null>(null)
function onDragStart(i: number) {
  dragIndex.value = i
}
function onDrop(i: number) {
  const from = dragIndex.value
  resetDrag()
  if (from === null || from === i) return
  const arr = [...picked.value]
  const moved = arr[from]
  if (moved === undefined) return
  arr.splice(from, 1)
  arr.splice(i, 0, moved)
  picked.value = arr
}
function resetDrag() {
  dragIndex.value = null
  overIndex.value = null
}

const tilesCols = computed(() => ({
  gridTemplateColumns: `repeat(${Math.max(1, selected.value.length)}, minmax(0, 1fr))`,
}))
const gridCols = computed(() => ({
  gridTemplateColumns: `minmax(120px, 1.6fr) repeat(${Math.max(1, selected.value.length)}, minmax(0, 1fr))`,
}))
const duelCols = computed(() => ({ gridTemplateColumns: 'minmax(120px, 1.4fr) 1fr 1fr' }))

function teamPodiums(t: TeamStanding): number {
  return t.drivers.reduce((s, d) => s + d.podiums, 0)
}
function teamDnf(t: TeamStanding): number {
  return t.drivers.reduce((s, d) => s + d.dnf, 0)
}
function teamAvgFinish(t: TeamStanding): number | null {
  const vs = t.drivers.map((d) => d.avgFinish).filter((v): v is number => v != null)
  return vs.length ? vs.reduce((a, b) => a + b, 0) / vs.length : null
}
function teamBestFinish(t: TeamStanding): number | null {
  const vs = t.drivers.map((d) => d.bestFinish).filter((v): v is number => v != null)
  return vs.length ? Math.min(...vs) : null
}
function teamF1(t: TeamStanding): number | null {
  const vs = t.drivers.map((d) => d.avgScore).filter((v): v is number => v != null)
  return vs.length ? vs.reduce((a, b) => a + b, 0) / vs.length : null
}

function rank(t: TeamStanding): number {
  return store.teams.findIndex((x) => x.team === t.team) + 1
}
function short(name: string): string {
  return name.length > 14 ? name.slice(0, 13) + '…' : name
}
function teamInitials(name: string): string {
  const words = name.trim().split(/\s+/)
  if (words.length === 1) return (words[0] ?? name).slice(0, 3).toUpperCase()
  return words.slice(0, 3).map((w) => w[0] ?? '').join('').toUpperCase()
}
function f1Text(t: TeamStanding): string {
  const v = teamF1(t)
  return v == null ? '–' : v.toFixed(1)
}
function num1(v: number | null): string {
  return v == null ? '–' : v.toFixed(1)
}
function int(v: number | null): string {
  return v == null ? '–' : String(Math.round(v))
}

const barMetrics = computed<TMetric[]>(() => [
  { l: t('drivers.metrics.points'), g: (tm) => tm.points, f: int },
  { l: t('common.wins'), g: (tm) => tm.wins, f: int },
  { l: t('teams.metrics.podiumCount'), g: teamPodiums, f: int },
  { l: 'F1ALYTICS Ø', g: teamF1, f: num1 },
])

const tableMetrics = computed<TMetric[]>(() => [
  { l: t('drivers.metrics.points'), g: (tm) => tm.points, f: int },
  { l: t('common.wins'), g: (tm) => tm.wins, f: int },
  { l: t('teams.metrics.podiumCount'), g: teamPodiums, f: int },
  { l: t('teams.metrics.avgRank'), g: teamAvgFinish, f: num1, low: true },
  { l: t('teams.metrics.bestRank'), g: teamBestFinish, f: (v) => (v == null ? '–' : 'P' + Math.round(v)), low: true },
  { l: t('common.dnf'), g: teamDnf, f: int, low: true },
  { l: 'F1ALYTICS Ø', g: teamF1, f: num1 },
])

const driverMetrics = computed<DMetric[]>(() => [
  { l: t('drivers.metrics.points'), g: (d) => d.points, f: (v) => String(v ?? 0) },
  { l: t('common.wins'), g: (d) => d.wins, f: (v) => String(v ?? 0) },
  { l: t('teams.metrics.podiumCount'), g: (d) => d.podiums, f: (v) => String(v ?? 0) },
  { l: t('teams.metrics.avgRank'), g: (d) => d.avgFinish, f: num1, low: true },
  { l: t('common.dnf'), g: (d) => d.dnf, f: (v) => String(v ?? 0), low: true },
  { l: 'F1ALYTICS Ø', g: (d) => d.avgScore, f: num1 },
])

function isBest(m: TMetric, t: TeamStanding): boolean {
  if (selected.value.length < 2) return false
  const vals = selected.value.map(m.g).filter((v): v is number => v != null)
  if (vals.length < 2) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  const v = m.g(t)
  return v != null && v === target && Math.min(...vals) !== Math.max(...vals)
}

function barPct(m: TMetric, t: TeamStanding): string {
  const vals = selected.value.map(m.g).filter((v): v is number => v != null)
  const max = vals.length ? Math.max(...vals, 0.0001) : 1
  const v = m.g(t)
  if (v == null || max <= 0) return '0%'
  return Math.max(4, Math.min(100, (v / max) * 100)).toFixed(1) + '%'
}

function duelBest(m: DMetric, ds: DriverStanding[], d: DriverStanding): boolean {
  const vals = ds.map(m.g).filter((v): v is number => v != null)
  if (vals.length < 2) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  return m.g(d) === target && vals[0] !== vals[1]
}

const axes: Axis[] = [
  { l: 'F1ALYTICS', g: teamF1 },
  { l: 'PUNKTE', g: (t) => t.points },
  { l: 'SIEGE', g: (t) => t.wins },
  { l: 'PODIEN', g: teamPodiums },
  { l: 'Ø PLATZ', g: teamAvgFinish, invert: true },
]

function norm(ax: Axis, t: TeamStanding): number {
  const vals = store.teams.map(ax.g).filter((v): v is number => v != null)
  if (!vals.length) return 0
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const v = ax.g(t)
  if (v == null) return 0
  if (max === min) return 0.6
  let f = (v - min) / (max - min)
  if (ax.invert) f = 1 - f
  return f
}

function axPt(i: number, r: number): [number, number] {
  const a = ((-90 + i * (360 / axes.length)) * Math.PI) / 180
  return [CX + r * Math.cos(a), CY + r * Math.sin(a)]
}

function poly(t: TeamStanding): string {
  return axes
    .map((ax, i) => {
      const r = RR * (0.12 + 0.88 * norm(ax, t))
      const [x, y] = axPt(i, r)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

const rings = computed<string[]>(() =>
  [0.25, 0.5, 0.75, 1].map((f) =>
    axes
      .map((_, i) => {
        const [x, y] = axPt(i, RR * f)
        return `${x.toFixed(1)},${y.toFixed(1)}`
      })
      .join(' '),
  ),
)

function anchor(i: number): string {
  const x = axPt(i, RR * 1.16)[0]
  if (x < CX - 6) return 'end'
  if (x > CX + 6) return 'start'
  return 'middle'
}

const n = computed(() => store.races.length)
function teamCum(t: TeamStanding): number[] {
  const arr: number[] = Array.from({ length: n.value }, () => 0)
  for (const d of t.drivers) {
    d.cum.forEach((v, i) => {
      if (i < arr.length) arr[i] = (arr[i] ?? 0) + v
    })
  }
  return arr
}
const maxPts = computed(() => {
  let m = 1
  for (const t of selected.value) {
    const c = teamCum(t)
    m = Math.max(m, c[c.length - 1] ?? 0)
  }
  return m
})
const gridLines = computed(() => [0, 1, 2, 3].map((g) => pad + (g / 3) * (H - 2 * pad)))

function cx(i: number): number {
  return pad + (i / Math.max(1, n.value - 1)) * (W - 2 * pad)
}
function cy(v: number): number {
  return H - pad - (v / maxPts.value) * (H - 2 * pad)
}
function line(t: TeamStanding): string {
  return teamCum(t).map((v, i) => `${cx(i).toFixed(1)},${cy(v).toFixed(1)}`).join(' ')
}

const PALETTE = ['#ff8c00', '#3b82f6', '#22d3ee', '#ec4899', '#a855f7', '#facc15', '#34d399', '#f43f5e']
function hexToRgb(h: string): [number, number, number] {
  const x = h.replace('#', '')
  const v = x.length === 3 ? x.split('').map((c) => c + c).join('') : x
  return [parseInt(v.slice(0, 2), 16) || 0, parseInt(v.slice(2, 4), 16) || 0, parseInt(v.slice(4, 6), 16) || 0]
}
function dist(a: [number, number, number], b: [number, number, number]): number {
  return Math.sqrt((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2)
}
const dmap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  const used: [number, number, number][] = []
  for (const t of selected.value) {
    let c = t.color || '#999999'
    if (used.some((u) => dist(u, hexToRgb(c)) < 115)) {
      let best: string = PALETTE[0] ?? '#888888'
      let bestMin = -1
      for (const p of PALETTE) {
        const prgb = hexToRgb(p)
        const minD = used.length ? Math.min(...used.map((u) => dist(u, prgb))) : 999
        if (minD > bestMin) {
          bestMin = minD
          best = p
        }
      }
      c = best
    }
    used.push(hexToRgb(c))
    map[t.team] = c
  }
  return map
})
function dcolor(t: TeamStanding): string {
  return dmap.value[t.team] || t.color
}
</script>

<style scoped>
.panel {
  animation: fade 0.4s ease both;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
@keyframes fade {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card {
  background: linear-gradient(180deg, var(--surface), color-mix(in srgb, var(--surface) 62%, transparent));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 20px;
}
.card-title {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-faint);
  margin-bottom: 16px;
}

.ch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.hint {
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}
.hint b {
  color: var(--accent);
}

.search {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 560px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 0 12px;
  height: 46px;
}
.search:focus-within {
  border-color: color-mix(in srgb, var(--text-faint) 70%, transparent);
}
.search-ico {
  width: 17px;
  height: 17px;
  color: var(--text-faint);
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  min-width: 0;
  background: none;
  border: none;
  outline: none;
  color: var(--text);
  font-size: 0.92rem;
}
.search-input::placeholder {
  color: var(--text-faint);
}
.search-clear {
  background: none;
  border: none;
  color: var(--text-faint);
  font-size: 18px;
  cursor: pointer;
  line-height: 1;
  padding: 0 2px;
}
.search-clear:hover {
  color: var(--text);
}

.suggest {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 40;
  background: var(--surface-2, #1c232e);
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow: 0 18px 40px -18px rgba(0, 0, 0, 0.85);
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 320px;
  overflow-y: auto;
}
.sug {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 8px 10px;
  border: none;
  background: none;
  border-radius: 7px;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s;
}
.sug:hover {
  background: color-mix(in srgb, var(--text-faint) 14%, transparent);
}
.sug-photo,
.tile-photo {
  position: relative;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: color-mix(in srgb, var(--dc, #888) 22%, var(--surface));
  border: 1.5px solid var(--dc, #888);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--text-dim);
}
.sug-photo.team,
.tile-photo.team {
  color: #fff;
  font-size: 11px;
  letter-spacing: 0.02em;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);
}
.sug-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.sug-name {
  font-size: 0.86rem;
  color: var(--text);
}
.sug-team {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  margin-top: 1px;
}
.sug-add {
  font-size: 16px;
  color: var(--text-faint);
  padding-right: 4px;
}
.sug-note {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 40;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px 14px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
}

.tiles {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}
.tile {
  position: relative;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px 42px 13px 15px;
  min-width: 0;
  min-height: 66px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--dc) 8%, var(--surface)), var(--surface));
  cursor: grab;
  transition:
    box-shadow 0.15s ease,
    opacity 0.15s ease;
}
.tile::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 3px;
  background: var(--dc);
}
.tile:active {
  cursor: grabbing;
}
.tile.dragging {
  opacity: 0.4;
}
.tile.over {
  box-shadow: 0 0 0 2px var(--dc) inset;
}
.tile-photo {
  width: 42px;
  height: 42px;
}
.tile-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.15;
}
.tile-abbr {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
  color: var(--text);
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tile-name {
  font-size: 0.78rem;
  color: var(--text-dim);
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tile-team {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-faint);
  margin-top: 2px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.tile-score {
  margin-left: auto;
  text-align: right;
  padding-left: 10px;
  flex-shrink: 0;
}
.tile-score-val {
  display: block;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 20px;
  color: var(--text);
}
.tile-score-lbl {
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
}
.tile-x {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--text-faint);
  font-size: 15px;
  cursor: pointer;
  opacity: 0;
  transition:
    opacity 0.15s,
    background 0.15s,
    color 0.15s;
}
.tile:hover .tile-x {
  opacity: 1;
}
.tile-x:hover {
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  color: var(--accent);
}
.tiles-empty {
  grid-column: 1 / -1;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-faint);
  padding: 10px 2px;
}

.dash {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: stretch;
}
.dash > .card {
  min-width: 0;
}

.radar {
  display: block;
}
.r-ring {
  fill: none;
  stroke: color-mix(in srgb, var(--line) 70%, transparent);
  stroke-width: 0.8;
}
.r-ax {
  stroke: color-mix(in srgb, var(--line) 80%, transparent);
  stroke-width: 0.8;
}
.r-poly {
  stroke-width: 2;
  stroke-linejoin: round;
}
.r-lbl {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.06em;
  fill: var(--text-faint);
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.blabel {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  margin-bottom: 7px;
}
.brow {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.bteam {
  font-family: var(--font-mono);
  font-size: 10px;
  width: 70px;
  flex-shrink: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.btrack {
  flex: 1;
  height: 10px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid var(--line);
  border-radius: 5px;
  overflow: hidden;
}
.bfill {
  height: 100%;
  border-radius: 5px 0 0 5px;
  transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.bval {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-dim);
  width: 44px;
  text-align: right;
}

.grid {
  display: grid;
  align-items: end;
}
.row {
  display: contents;
}
.row > * {
  border-bottom: 1px solid color-mix(in srgb, var(--line) 55%, transparent);
}
.hd {
  text-align: center;
  padding: 6px 4px 12px;
  border-bottom: 2px solid var(--line);
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 15px;
}
.hd .ab {
  font-size: 20px;
  color: var(--text);
}
.hd .sub {
  display: block;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 400;
  color: var(--text-faint);
  margin-top: 3px;
}
.lbl {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.08em;
  padding: 12px 0;
}
.val {
  text-align: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 18px;
  padding: 12px 0;
  color: var(--text-dim);
}
.val.best {
  color: var(--accent);
}

.chart {
  display: block;
}
.chart .g {
  stroke: color-mix(in srgb, var(--line) 60%, transparent);
  stroke-width: 0.8;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}
.legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend i {
  width: 12px;
  height: 3px;
  border-radius: 2px;
}

.dpick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.chip {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 8px 13px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--text-faint);
  cursor: pointer;
  transition: all 0.18s;
  letter-spacing: 0.04em;
}
.chip:hover {
  color: var(--text-dim);
}
.chip.on {
  color: var(--text);
}
.duel-grid {
  margin-top: 4px;
}
.solo {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-faint);
  padding: 8px 0;
}

.empty {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  padding: 30px 4px;
}

@media (max-aspect-ratio: 13/16) {
  .dash {
    grid-template-columns: 1fr;
  }
}
</style>
