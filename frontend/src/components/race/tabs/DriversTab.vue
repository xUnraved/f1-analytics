<template>
  <div class="panel">
    <div class="card pick">
      <div class="search">
        <svg viewBox="0 0 24 24" class="search-ico" aria-hidden="true">
          <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <input
          v-model="query"
          type="text"
          class="search-input"
          placeholder="Fahrer oder Team suchen …"
          @focus="focused = true"
          @blur="focused = false"
          @keydown.enter="addFirst"
          @keydown.esc="query = ''"
        />
        <button v-if="query" type="button" class="search-clear" @click="query = ''" aria-label="Leeren">×</button>

        <div v-if="suggestions.length" class="suggest">
          <button
            v-for="d in suggestions"
            :key="d.abbr"
            type="button"
            class="sug"
            @mousedown.prevent
            @click="addDriver(d.abbr)"
          >
            <span class="sug-photo" :style="{ '--dc': d.color }">
              <img v-if="d.headshot" :src="d.headshot" :alt="d.abbr" loading="lazy" />
              <span v-else>{{ d.abbr }}</span>
            </span>
            <span class="sug-text">
              <span class="sug-name"><b>{{ d.abbr }}</b> · {{ d.name }}</span>
              <span class="sug-team">{{ d.team }}</span>
            </span>
            <span class="sug-add">+</span>
          </button>
        </div>
        <div v-else-if="focused && full" class="sug-note">Maximal 3 Fahrer — entferne einen, um zu tauschen.</div>
        <div v-else-if="focused && query.trim()" class="sug-note">Kein Treffer für „{{ query.trim() }}".</div>
      </div>

      <div class="tiles" :style="tilesCols">
        <div
          v-for="(d, i) in selected"
          :key="d.abbr"
          class="tile"
          :class="{ dragging: dragIndex === i, over: overIndex === i }"
          :style="{ '--dc': dcolor(d) }"
          draggable="true"
          @dragstart="onDragStart(i)"
          @dragover.prevent="overIndex = i"
          @dragleave="overIndex = overIndex === i ? null : overIndex"
          @drop="onDrop(i)"
          @dragend="resetDrag"
        >
          <span class="tile-photo">
            <img v-if="d.headshot" :src="d.headshot" :alt="d.abbr" loading="lazy" />
            <span v-else>{{ d.abbr }}</span>
          </span>
          <span class="tile-info">
            <span class="tile-abbr">{{ d.abbr }}</span>
            <span class="tile-name">{{ d.name }}</span>
            <span class="tile-team">{{ d.team }}</span>
          </span>
          <span class="tile-score">
            <span class="tile-score-val">{{ d.avgScore != null ? d.avgScore.toFixed(1) : '–' }}</span>
            <span class="tile-score-lbl">F1ALYTICS Ø</span>
          </span>
          <button type="button" class="tile-x" @click.stop="removeDriver(d.abbr)" aria-label="Entfernen">×</button>
        </div>
        <div v-if="!selected.length" class="tiles-empty">
          Suche oben nach einem Fahrer oder Team und füge bis zu 3 hinzu.
        </div>
      </div>
    </div>

    <template v-if="selected.length">
      <div class="dash">
        <section class="card">
          <div class="card-title">Leistungsprofil</div>
          <svg :viewBox="`0 0 ${VW} ${VH}`" width="100%" class="radar">
            <polygon v-for="t in rings" :key="t" :points="ringPoints(t)" class="ring" />
            <line
              v-for="(ax, i) in radarAxes"
              :key="ax.k"
              :x1="CX"
              :y1="CY"
              :x2="axisXY(i, 1)[0]"
              :y2="axisXY(i, 1)[1]"
              class="spoke"
            />
            <polygon
              v-for="d in selected"
              :key="d.abbr"
              :points="radarPoints(d)"
              :style="{ stroke: dcolor(d), fill: dcolor(d) }"
              class="shape"
            />
            <text
              v-for="(ax, i) in radarAxes"
              :key="ax.k + '-l'"
              :x="labelXY(i)[0]"
              :y="labelXY(i)[1]"
              class="axis-label"
              :text-anchor="labelAnchor(i)"
            >{{ ax.l }}</text>
          </svg>
          <div class="legend">
            <span v-for="d in selected" :key="d.abbr"><i :style="{ background: dcolor(d) }"></i>{{ d.abbr }}</span>
          </div>
          <div class="palette">
            <button
              v-for="m in catalog"
              :key="m.k"
              type="button"
              class="pchip"
              :class="{ on: radarKeys.includes(m.k) }"
              @click="toggleAxis(m.k)"
            >
              {{ m.l }}
            </button>
          </div>
        </section>

        <section class="card">
          <div class="card-title">Direktvergleich</div>
          <div class="bars">
            <div v-for="m in barMetrics" :key="m.l" class="bar-metric">
              <div class="bar-label">{{ m.l }}</div>
              <div v-for="d in selected" :key="d.abbr" class="bar-row">
                <span class="bar-abbr">{{ d.abbr }}</span>
                <div class="bar-track">
                  <div class="bar-fill" :style="barStyle(m, d)"></div>
                </div>
                <span class="bar-val" :class="{ best: isBest(m, d) }">{{ m.f(m.g(d)) }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <section class="card">
        <div class="card-title">Form pro Rennen</div>
        <div class="forms">
          <div v-for="d in selected" :key="d.abbr" class="frow">
            <span class="fabbr" :style="{ color: dcolor(d) }">{{ d.abbr }}</span>
            <div class="fcells" :style="{ gridTemplateColumns: `repeat(${raceCount}, 1fr)` }">
              <span
                v-for="i in raceCount"
                :key="i"
                class="fcell"
                :class="{ empty: cell(d, i) == null }"
                :style="cellStyle(d, i)"
                :title="cellTitle(d, i)"
              ></span>
            </div>
          </div>
        </div>
        <div v-if="!raceCount" class="fnone" style="margin-top: 8px">
          Für diese Saison liegen noch keine F1alytics-Wertungen vor.
        </div>
        <div class="fscale">
          <span>schwach</span>
          <i style="background: #e11d48"></i>
          <i style="background: #f97316"></i>
          <i style="background: #eab308"></i>
          <i style="background: #65a30d"></i>
          <i style="background: #16a34a"></i>
          <span>stark</span>
        </div>
      </section>

      <div class="dash">
        <section class="card">
          <div class="card-title">Weitere Werte</div>
          <div class="grid" :style="gridCols">
            <div class="grow">
              <div class="glbl"></div>
              <div v-for="d in selected" :key="d.abbr" class="ghd">{{ d.abbr }}</div>
            </div>
            <div v-for="m in gridMetrics" :key="m.l" class="grow">
              <div class="glbl">{{ m.l }}</div>
              <div v-for="d in selected" :key="d.abbr" class="gval" :class="{ best: isBest(m, d) }">
                {{ m.f(m.g(d)) }}
              </div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-title">Saisonverlauf</div>
          <div class="prog-tabs">
            <button
              v-for="t in trendTabs"
              :key="t.k"
              type="button"
              class="ptab"
              :class="{ active: mode === t.k }"
              @click="mode = t.k"
            >
              {{ t.l }}
            </button>
          </div>
          <svg :viewBox="`0 0 ${W} ${H}`" width="100%" class="trend">
            <line v-for="(gy, i) in trendGrid" :key="i" :x1="pad" :y1="gy" :x2="W - pad" :y2="gy" class="g" />
            <polyline
              v-for="d in selected"
              :key="d.abbr"
              :points="lineFor(d)"
              fill="none"
              :stroke="dcolor(d)"
              stroke-width="2.5"
              stroke-linejoin="round"
            />
          </svg>
          <div class="axis-row">
            <span>{{ axisLo }}</span>
            <span>{{ axisHi }}</span>
          </div>
          <div class="legend">
            <span v-for="d in selected" :key="d.abbr"><i :style="{ background: dcolor(d) }"></i>{{ d.abbr }}</span>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore'
import type { DriverStanding } from '@/types/f1'

interface Metric {
  l: string
  g: (d: DriverStanding) => number | null
  f: (v: number | null) => string
  low: boolean
}
interface CatMetric {
  k: string
  l: string
  g: (d: DriverStanding) => number | null
  invert?: boolean
}

const store = useSeasonStore()

const query = ref('')
const focused = ref(false)

const selected = computed<DriverStanding[]>(() =>
  store.selectedDrivers.map((a) => store.drivers.find((d) => d.abbr === a)).filter((d): d is DriverStanding => !!d),
)
const full = computed(() => store.selectedDrivers.length >= 3)

const tilesCols = computed(() => ({
  gridTemplateColumns: `repeat(${Math.max(1, selected.value.length)}, minmax(0, 1fr))`,
}))

const gridCols = computed(() => ({
  gridTemplateColumns: `minmax(110px, 1.6fr) repeat(${Math.max(1, selected.value.length)}, minmax(0, 1fr))`,
}))

const suggestions = computed<DriverStanding[]>(() => {
  if (full.value) return []
  const avail = store.drivers.filter((d) => !store.selectedDrivers.includes(d.abbr))
  const q = query.value.trim().toLowerCase()
  if (!q) return focused.value ? avail : []
  return avail.filter(
    (d) => d.name.toLowerCase().includes(q) || d.abbr.toLowerCase().includes(q) || d.team.toLowerCase().includes(q),
  )
})

function addDriver(abbr: string) {
  if (store.selectedDrivers.length < 3 && !store.selectedDrivers.includes(abbr)) {
    store.selectedDrivers = [...store.selectedDrivers, abbr]
  }
  query.value = ''
}
function addFirst() {
  const first = suggestions.value[0]
  if (first) addDriver(first.abbr)
}
function removeDriver(abbr: string) {
  store.selectedDrivers = store.selectedDrivers.filter((a) => a !== abbr)
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
  const arr = [...store.selectedDrivers]
  const moved = arr.splice(from, 1)[0]
  if (moved === undefined) return
  arr.splice(i, 0, moved)
  store.selectedDrivers = arr
}
function resetDrag() {
  dragIndex.value = null
  overIndex.value = null
}

const PALETTE = ['#ff8c00', '#3b82f6', '#22d3ee', '#ec4899', '#a855f7', '#facc15', '#34d399', '#f43f5e']
function rgb(h: string): [number, number, number] {
  const x = h.replace('#', '')
  const n = x.length === 3 ? x.split('').map((c) => c + c).join('') : x
  return [parseInt(n.slice(0, 2), 16), parseInt(n.slice(2, 4), 16), parseInt(n.slice(4, 6), 16)]
}
function cdist(a: string, b: string): number {
  const x = rgb(a)
  const y = rgb(b)
  return Math.sqrt((x[0] - y[0]) ** 2 + (x[1] - y[1]) ** 2 + (x[2] - y[2]) ** 2)
}
const colorByAbbr = computed<Record<string, string>>(() => {
  const used: string[] = []
  const out: Record<string, string> = {}
  for (const d of selected.value) {
    let c = d.color || '#888888'
    if (used.some((u) => cdist(u, c) < 115)) {
      let best = c
      let bestScore = -1
      for (const p of PALETTE) {
        const score = used.length ? Math.min(...used.map((u) => cdist(u, p))) : 1000
        if (score > bestScore) {
          bestScore = score
          best = p
        }
      }
      c = best
    }
    used.push(c)
    out[d.abbr] = c
  }
  return out
})
function dcolor(d: DriverStanding): string {
  return colorByAbbr.value[d.abbr] ?? d.color
}

function ppr(d: DriverStanding): number | null {
  const races = d.cum.length
  return races > 0 ? d.points / races : null
}
function top10(d: DriverStanding): number {
  return d.finishes.filter((p) => p <= 10).length
}
function consistency(d: DriverStanding): number | null {
  const xs = d.finishes
  if (xs.length < 2) return null
  const m = xs.reduce((a, b) => a + b, 0) / xs.length
  const v = xs.reduce((a, b) => a + (b - m) * (b - m), 0) / xs.length
  return Math.sqrt(v)
}
function form(d: DriverStanding): number | null {
  const h = d.scoreHistory
  if (!h || h.length === 0) return null
  return h.slice(-3).reduce((a, b) => a + b, 0) / h.slice(-3).length
}
function reliability(d: DriverStanding): number | null {
  const starts = d.finishes.length + d.dnf
  return starts > 0 ? (d.finishes.length / starts) * 100 : null
}

function scoreColor(v: number): string {
  if (v >= 8) return '#16a34a'
  if (v >= 7) return '#65a30d'
  if (v >= 6) return '#eab308'
  if (v >= 5) return '#f97316'
  return '#e11d48'
}

const raceCount = computed(() => store.races.length)
function cell(d: DriverStanding, i: number): number | null {
  const race = store.races[i - 1]
  if (!race) return null
  const row = race.result.find((r) => r.abbr === d.abbr)
  return row && row.score ? row.score.score : null
}
function cellStyle(d: DriverStanding, i: number) {
  const v = cell(d, i)
  return v == null ? {} : { background: scoreColor(v) }
}
function cellTitle(d: DriverStanding, i: number): string {
  const race = store.races[i - 1]
  const label = race ? race.gp : 'Rennen ' + i
  const v = cell(d, i)
  return v == null ? label : label + ' · ' + v.toFixed(1)
}

const barMetrics: Metric[] = [
  { l: 'PUNKTE', g: (d) => d.points, f: (v) => String(v ?? 0), low: false },
  { l: 'F1ALYTICS Ø', g: (d) => d.avgScore, f: (v) => (v == null ? '–' : v.toFixed(1)), low: false },
  { l: 'FORM (Ø LETZTE 3)', g: (d) => form(d), f: (v) => (v == null ? '–' : v.toFixed(1)), low: false },
  { l: 'TOP-SPEED (KM/H)', g: (d) => d.maxTopSpeed, f: (v) => (v == null ? '–' : String(v)), low: false },
  { l: 'PODESTPLÄTZE', g: (d) => d.podiums, f: (v) => String(v ?? 0), low: false },
  { l: 'SIEGE', g: (d) => d.wins, f: (v) => String(v ?? 0), low: false },
]

const gridMetrics: Metric[] = [
  { l: 'F1ALYTICS Ø', g: (d) => d.avgScore, f: (v) => (v == null ? '–' : v.toFixed(1)), low: false },
  { l: 'F1ALYTICS Ø (LETZTE 3)', g: (d) => form(d), f: (v) => (v == null ? '–' : v.toFixed(1)), low: false },
  { l: 'Ø TOP-SPEED (KM/H)', g: (d) => d.avgTopSpeed, f: (v) => (v == null ? '–' : v.toFixed(1)), low: false },
  { l: 'TOP-10', g: (d) => top10(d), f: (v) => String(v ?? 0), low: false },
  { l: 'PUNKTE / RENNEN', g: (d) => ppr(d), f: (v) => (v == null ? '–' : v.toFixed(1)), low: false },
  { l: 'BESTE PLATZIERUNG', g: (d) => d.bestFinish, f: (v) => (v == null ? '–' : 'P' + v), low: true },
  { l: 'Ø PLATZIERUNG', g: (d) => d.avgFinish, f: (v) => (v == null ? '–' : v.toFixed(1)), low: true },
  { l: 'KONSTANZ (σ)', g: (d) => consistency(d), f: (v) => (v == null ? '–' : v.toFixed(1)), low: true },
  { l: 'ZIELANKUNFT %', g: (d) => reliability(d), f: (v) => (v == null ? '–' : v.toFixed(0) + '%'), low: false },
  { l: 'DNF', g: (d) => d.dnf, f: (v) => String(v ?? 0), low: true },
]

function isBest(m: Metric, d: DriverStanding): boolean {
  if (selected.value.length < 2) return false
  const vals = selected.value.map(m.g).filter((v): v is number => v != null)
  if (!vals.length) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  return m.g(d) === target
}

function barStyle(m: Metric, d: DriverStanding) {
  const vals = selected.value.map((s) => m.g(s) ?? 0)
  const max = Math.max(...vals, 1)
  const v = m.g(d) ?? 0
  return {
    width: `${Math.max(4, (v / max) * 100)}%`,
    background: dcolor(d),
    opacity: isBest(m, d) ? 1 : 0.5,
  }
}

const catalog: CatMetric[] = [
  { k: 'f1', l: 'F1ALYTICS', g: (d) => d.avgScore },
  { k: 'finish', l: 'Ø PLATZ', g: (d) => d.avgFinish, invert: true },
  { k: 'points', l: 'PUNKTE', g: (d) => d.points },
  { k: 'konstanz', l: 'KONSTANZ', g: (d) => consistency(d), invert: true },
  { k: 'form', l: 'FORM', g: (d) => form(d) },
  { k: 'ziel', l: 'ZIEL %', g: (d) => reliability(d) },
  { k: 'speed', l: 'TOP-SPEED', g: (d) => d.maxTopSpeed },
  { k: 'podium', l: 'PODESTE', g: (d) => d.podiums },
  { k: 'wins', l: 'SIEGE', g: (d) => d.wins },
  { k: 'top10', l: 'TOP-10', g: (d) => top10(d) },
]
const radarKeys = ref<string[]>(['f1', 'finish', 'points', 'konstanz', 'form', 'ziel'])
const radarAxes = computed<CatMetric[]>(() => catalog.filter((m) => radarKeys.value.includes(m.k)))

function toggleAxis(k: string) {
  if (radarKeys.value.includes(k)) {
    if (radarKeys.value.length <= 3) return
    radarKeys.value = radarKeys.value.filter((x) => x !== k)
  } else {
    if (radarKeys.value.length >= 8) return
    radarKeys.value = [...radarKeys.value, k]
  }
}

const VW = 340
const VH = 262
const CX = 170
const CY = 126
const RR = 86
const rings = [0.25, 0.5, 0.75, 1]

const ranges = computed(() =>
  radarAxes.value.map((ax) => {
    const vs = store.drivers.map(ax.g).filter((v): v is number => v != null)
    return { min: vs.length ? Math.min(...vs) : 0, max: vs.length ? Math.max(...vs) : 1 }
  }),
)

function angle(i: number): number {
  const n = radarAxes.value.length
  return ((i * (360 / n) - 90) * Math.PI) / 180
}
function axisXY(i: number, t: number): [number, number] {
  return [CX + RR * t * Math.cos(angle(i)), CY + RR * t * Math.sin(angle(i))]
}
function ringPoints(t: number): string {
  return radarAxes.value.map((_, i) => axisXY(i, t).join(',')).join(' ')
}
function norm(i: number, d: DriverStanding): number {
  const ax = radarAxes.value[i]!
  const r = ranges.value[i]!
  const v = ax.g(d)
  if (v == null) return 0
  let t = r.max > r.min ? (v - r.min) / (r.max - r.min) : 0.5
  if (ax.invert) t = 1 - t
  return Math.max(0, Math.min(1, t))
}
function radarPoints(d: DriverStanding): string {
  return radarAxes.value.map((_, i) => axisXY(i, Math.max(0.04, norm(i, d))).join(',')).join(' ')
}
function labelXY(i: number): [number, number] {
  return axisXY(i, 1.14)
}
function labelAnchor(i: number): string {
  const x = Math.cos(angle(i))
  if (x > 0.3) return 'start'
  if (x < -0.3) return 'end'
  return 'middle'
}

const trendTabs = [
  { k: 'cum' as const, l: 'PUNKTE KUMULIERT' },
  { k: 'race' as const, l: 'PUNKTE / RENNEN' },
  { k: 'score' as const, l: 'F1ALYTICS / RENNEN' },
]
const mode = ref<'cum' | 'race' | 'score'>('cum')

const W = 460
const H = 300
const pad = 18
const trendGrid = computed(() => [0, 1, 2, 3].map((g) => pad + (g / 3) * (H - 2 * pad)))

function raceSeries(d: DriverStanding): number[] {
  return d.cum.map((v, i) => (i === 0 ? v : v - (d.cum[i - 1] ?? 0)))
}
function series(d: DriverStanding): number[] {
  if (mode.value === 'score') return d.scoreHistory ?? []
  if (mode.value === 'race') return raceSeries(d)
  return d.cum
}
const maxCum = computed(() => Math.max(...selected.value.map((d) => d.cum[d.cum.length - 1] ?? 0), 1))
const maxRace = computed(() => Math.max(...selected.value.flatMap((d) => raceSeries(d)), 1))

const axisLo = computed(() => (mode.value === 'score' ? '1' : '0'))
const axisHi = computed(() =>
  mode.value === 'score' ? '10' : mode.value === 'race' ? String(maxRace.value) : String(maxCum.value),
)

function px(i: number, len: number): number {
  return pad + (i / Math.max(1, len - 1)) * (W - 2 * pad)
}
function frac(v: number): number {
  if (mode.value === 'score') return (v - 1) / 9
  if (mode.value === 'race') return v / maxRace.value
  return v / maxCum.value
}
function lineFor(d: DriverStanding): string {
  const s = series(d)
  return s.map((v, i) => `${px(i, s.length)},${H - pad - frac(v) * (H - 2 * pad)}`).join(' ')
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
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 20px;
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
.sug-photo img,
.tile-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
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
  margin-top: 20px;
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
  font-size: 21px;
  color: var(--dc);
  line-height: 1;
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
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--text-faint);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: all 0.14s;
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
  padding: 8px 2px;
}

.dash {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: stretch;
}
.card-title {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-faint);
  margin-bottom: 16px;
}

.radar {
  display: block;
}
.ring {
  fill: none;
  stroke: var(--line);
  stroke-width: 1;
}
.spoke {
  stroke: var(--line);
  stroke-width: 1;
}
.shape {
  fill-opacity: 0.13;
  stroke-width: 2;
  stroke-linejoin: round;
}
.axis-label {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.02em;
  fill: var(--text-faint);
}

.palette {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(68px, 1fr));
  gap: 6px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}
.pchip {
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.05em;
  padding: 6px 5px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--text-faint);
  cursor: pointer;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: all 0.15s;
}
.pchip:hover {
  color: var(--text-dim);
}
.pchip.on {
  border-color: var(--accent);
  color: var(--text);
  background: color-mix(in srgb, var(--accent) 14%, transparent);
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.bar-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  margin-bottom: 7px;
}
.bar-row {
  display: grid;
  grid-template-columns: 34px 1fr 52px;
  align-items: center;
  gap: 9px;
  margin-bottom: 5px;
}
.bar-abbr {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-dim);
}
.bar-track {
  height: 8px;
  background: var(--surface-2);
  border-radius: 5px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.5s cubic-bezier(0.2, 0.8, 0.2, 1);
}
.bar-val {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
  color: var(--text-faint);
}
.bar-val.best {
  color: var(--text);
}

.forms {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.frow {
  display: grid;
  grid-template-columns: 34px 1fr;
  align-items: center;
  gap: 10px;
}
.fabbr {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
}
.fcells {
  display: grid;
  gap: 3px;
}
.fcell {
  min-width: 0;
  height: 18px;
  border-radius: 3px;
}
.fcell.empty {
  background: color-mix(in srgb, var(--text-faint) 12%, transparent);
}
.fnone {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
}
.fscale {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 14px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
}
.fscale i {
  width: 13px;
  height: 13px;
  border-radius: 3px;
}

.grid {
  display: grid;
  align-items: end;
}
.grow {
  display: contents;
}
.grow > * {
  border-bottom: 1px solid color-mix(in srgb, var(--line) 55%, transparent);
}
.ghd {
  text-align: center;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-dim);
  padding: 4px 0 10px;
  border-bottom: 2px solid var(--line);
}
.glbl {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  letter-spacing: 0.05em;
  padding: 11px 0;
}
.gval {
  text-align: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
  color: var(--text-dim);
  padding: 11px 0;
}
.gval.best {
  color: var(--accent);
}

.prog-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 14px;
}
.ptab {
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  padding: 5px 10px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--text-faint);
  cursor: pointer;
  transition: all 0.15s;
}
.ptab:hover {
  color: var(--text-dim);
}
.ptab.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.trend .g {
  stroke: var(--line);
  stroke-width: 1;
}
.axis-row {
  display: flex;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  margin-top: 4px;
}
.legend {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.legend span {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-dim);
  display: flex;
  align-items: center;
  gap: 7px;
}
.legend i {
  width: 14px;
  height: 3px;
  border-radius: 2px;
}

@media (max-width: 860px) {
  .dash {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .tiles {
    grid-template-columns: 1fr !important;
  }
}
</style>
