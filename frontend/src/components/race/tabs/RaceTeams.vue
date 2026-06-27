<template>
  <div class="panel">
    <div class="cmp-card pad pick">
      <div class="search">
        <svg viewBox="0 0 24 24" class="search-ico" aria-hidden="true">
          <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <input
          v-model="query"
          type="text"
          class="search-input"
          placeholder="Team oder Fahrer suchen …"
          @focus="focused = true"
          @blur="focused = false"
          @keydown.enter="addFirst"
          @keydown.esc="query = ''"
        />
        <button v-if="query" type="button" class="search-clear" @click="query = ''" aria-label="Leeren">×</button>

        <div v-if="suggestions.length" class="suggest">
          <button
            v-for="t in suggestions"
            :key="t.team"
            type="button"
            class="sug"
            @mousedown.prevent
            @click="addTeam(t.team)"
          >
            <span class="sug-photo team" :style="{ '--dc': t.color, background: t.color }">{{ teamInitials(t.team) }}</span>
            <span class="sug-text">
              <span class="sug-name"><b>{{ t.team }}</b></span>
              <span class="sug-team">{{ t.points }} PKT · {{ t.drivers.map((d) => d.abbr + ' ' + d.posText).join(' / ') }}</span>
            </span>
            <span class="sug-add">+</span>
          </button>
        </div>
        <div v-else-if="focused && full" class="sug-note">Maximal 4 Teams — entferne eins, um zu tauschen.</div>
        <div v-else-if="focused && query.trim()" class="sug-note">Kein Treffer für „{{ query.trim() }}".</div>
      </div>

      <div class="tiles" :style="tilesCols">
        <div
          v-for="(t, i) in selected"
          :key="t.team"
          class="tile"
          :class="{ dragging: dragIndex === i, over: overIndex === i }"
          :style="{ '--dc': dcolor(t) }"
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
            <span class="tile-name">{{ t.drivers.map((d) => d.abbr + ' ' + d.posText).join(' · ') }}</span>
            <span class="tile-team">{{ t.points }} PKT</span>
          </span>
          <span class="tile-score">
            <span class="tile-score-val">{{ f1Text(t) }}</span>
            <span class="tile-score-lbl">F1ALYTICS Ø</span>
          </span>
          <button type="button" class="tile-x" @click.stop="removeTeam(t.team)" aria-label="Entfernen">×</button>
        </div>
        <div v-if="!selected.length" class="tiles-empty">
          Suche oben nach einem Team und füge bis zu 4 hinzu.
        </div>
      </div>
    </div>

    <div v-if="selected.length" class="cmp-card pad bars-card">
      <div class="ch">
        <span class="eyebrow">F1alytics Ø · Team</span>
        <span class="hint mono">DIESES RENNEN · 1–10</span>
      </div>
      <div class="sbars">
        <div v-for="t in selected" :key="t.team" class="sbar">
          <div class="sbar-head">
            <span class="sbar-ab" :style="{ color: dcolor(t) }">{{ t.team }}</span>
            <span class="sbar-drs">{{ t.drivers.map((d) => d.abbr + ' ' + d.posText).join(' · ') }}</span>
            <span class="sbar-val">{{ f1Text(t) }}</span>
          </div>
          <div class="sbar-track">
            <div class="sbar-fill" :style="{ width: f1Pct(t), background: dcolor(t) }"></div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selected.length" class="cmp-card pad">
      <div class="cmp-grid" :style="gridStyle">
        <div class="row">
          <div class="lbl"></div>
          <div v-for="t in selected" :key="t.team" class="hd" :style="{ borderColor: dcolor(t) }">
            <div class="ab" :style="{ color: dcolor(t) }">{{ short(t.team) }}</div>
          </div>
        </div>
        <div v-for="m in metrics" :key="m.l" class="row" :class="{ hl: m.hl }">
          <div class="lbl">{{ m.l }}</div>
          <div v-for="t in selected" :key="t.team" class="val" :class="{ best: isBest(m, t) }">
            {{ m.f(t) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { RaceResultRow } from '@/types/f1'

const props = defineProps<{ result: RaceResultRow[] }>()

interface DrvAgg {
  abbr: string
  posText: string
  pos: number
  out: boolean
}
interface TeamAgg {
  team: string
  color: string
  points: number
  bestPos: number | null
  f1: number | null
  drivers: DrvAgg[]
}
interface Metric {
  l: string
  v: (t: TeamAgg) => number | null
  f: (t: TeamAgg) => string
  low: boolean
  hl?: boolean
}

function out(r: RaceResultRow): boolean {
  return r.dnf || r.dns || r.dsq
}
function statusText(r: RaceResultRow): string {
  if (r.dnf) return 'DNF'
  if (r.dns) return 'DNS'
  if (r.dsq) return 'DSQ'
  return 'P' + r.pos
}

const teams = computed<TeamAgg[]>(() => {
  const map = new Map<string, { team: string; color: string; points: number; rows: RaceResultRow[] }>()
  for (const r of props.result) {
    let t = map.get(r.team)
    if (!t) {
      t = { team: r.team, color: r.color, points: 0, rows: [] }
      map.set(r.team, t)
    }
    t.points += r.pts
    t.rows.push(r)
  }
  const arr: TeamAgg[] = [...map.values()].map((t) => {
    const finishers = t.rows.filter((r) => !out(r)).map((r) => r.pos)
    const scores = t.rows.map((r) => r.score?.score).filter((v): v is number => v != null)
    const drivers = [...t.rows]
      .sort((a, b) => a.pos - b.pos)
      .map((r) => ({ abbr: r.abbr, posText: statusText(r), pos: r.pos, out: out(r) }))
    return {
      team: t.team,
      color: t.color,
      points: t.points,
      bestPos: finishers.length ? Math.min(...finishers) : null,
      f1: scores.length ? scores.reduce((a, b) => a + b, 0) / scores.length : null,
      drivers,
    }
  })
  arr.sort((a, b) => b.points - a.points || (a.bestPos ?? 99) - (b.bestPos ?? 99))
  return arr
})

const metrics: Metric[] = [
  { l: 'F1ALYTICS Ø', v: (t) => t.f1, f: (t) => (t.f1 == null ? '–' : t.f1.toFixed(1)), low: false, hl: true },
  { l: 'PUNKTE', v: (t) => t.points, f: (t) => String(t.points), low: false },
  { l: 'BESTES ERGEBNIS', v: (t) => t.bestPos, f: (t) => (t.bestPos == null ? '–' : 'P' + t.bestPos), low: true },
  { l: 'FAHRER', v: () => null, f: (t) => t.drivers.map((d) => d.posText).join(' / '), low: false },
]

const query = ref('')
const focused = ref(false)
const picked = ref<string[]>([])

function seed() {
  picked.value = teams.value.slice(0, Math.min(3, teams.value.length)).map((t) => t.team)
}
seed()
watch(() => props.result, seed)

const full = computed(() => picked.value.length >= 4)

const selected = computed<TeamAgg[]>(() =>
  picked.value.map((name) => teams.value.find((t) => t.team === name)).filter((t): t is TeamAgg => !!t),
)

const suggestions = computed<TeamAgg[]>(() => {
  if (full.value) return []
  const avail = teams.value.filter((t) => !picked.value.includes(t.team))
  const q = query.value.trim().toLowerCase()
  if (!q) return focused.value ? avail : []
  return avail.filter(
    (t) => t.team.toLowerCase().includes(q) || t.drivers.some((d) => d.abbr.toLowerCase().includes(q)),
  )
})

function addTeam(name: string) {
  if (picked.value.length < 4 && !picked.value.includes(name)) picked.value.push(name)
  query.value = ''
}
function addFirst() {
  const first = suggestions.value[0]
  if (first) addTeam(first.team)
}
function removeTeam(name: string) {
  picked.value = picked.value.filter((t) => t !== name)
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
  const moved = arr.splice(from, 1)[0]
  if (moved === undefined) return
  arr.splice(i, 0, moved)
  picked.value = arr
}
function resetDrag() {
  dragIndex.value = null
  overIndex.value = null
}

const gridStyle = computed(() => ({
  gridTemplateColumns: `1.4fr repeat(${Math.max(1, selected.value.length)}, 1fr)`,
}))
const tilesCols = computed(() => ({
  gridTemplateColumns: `repeat(${Math.max(1, selected.value.length)}, minmax(0, 1fr))`,
}))

function short(name: string): string {
  return name.length > 14 ? name.slice(0, 13) + '…' : name
}
function teamInitials(name: string): string {
  const words = name.trim().split(/\s+/)
  if (words.length === 1) return words[0].slice(0, 3).toUpperCase()
  return words.slice(0, 3).map((w) => w[0]).join('').toUpperCase()
}
function f1Text(t: TeamAgg): string {
  return t.f1 == null ? '–' : t.f1.toFixed(1)
}
function f1Pct(t: TeamAgg): string {
  if (t.f1 == null) return '0%'
  return Math.max(0, Math.min(100, (t.f1 / 10) * 100)).toFixed(1) + '%'
}

function isBest(m: Metric, t: TeamAgg): boolean {
  if (selected.value.length < 2) return false
  const vals = selected.value.map(m.v).filter((v): v is number => v != null)
  if (vals.length < 2) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  const v = m.v(t)
  return v != null && v === target && Math.min(...vals) !== Math.max(...vals)
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
      let best = PALETTE[0]
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
function dcolor(t: TeamAgg): string {
  return dmap.value[t.team] || t.color
}
</script>

<style scoped>
.panel {
  animation: fade 0.4s ease both;
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

.cmp-card {
  background: linear-gradient(180deg, var(--surface), color-mix(in srgb, var(--surface) 62%, transparent));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.cmp-card.pad {
  padding: 20px;
}
.pick {
  margin-bottom: 14px;
}
.bars-card {
  margin-bottom: 14px;
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
  font-size: 11px;
  font-weight: 700;
  color: #fff;
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
  font-size: 0.74rem;
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

.sbars {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sbar-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 7px;
}
.sbar-ab {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
  white-space: nowrap;
}
.sbar-drs {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sbar-val {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 18px;
  color: var(--text);
}
.sbar-track {
  height: 12px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid var(--line);
  overflow: hidden;
}
.sbar-fill {
  height: 100%;
  border-radius: 6px 0 0 6px;
  transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.cmp-grid {
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
}
.hd .ab {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
}
.lbl {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.08em;
  padding: 13px 0;
}
.val {
  text-align: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 17px;
  padding: 13px 0;
  color: var(--text-dim);
}
.val.best {
  color: var(--accent);
}
.row.hl > .lbl {
  color: var(--accent);
}
.row.hl > .val {
  font-size: 23px;
  color: var(--text);
}
.row.hl > .val.best {
  color: var(--accent);
}
</style>
