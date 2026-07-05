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
          :placeholder="t('common.search')"
          @focus="focused = true"
          @blur="focused = false"
          @keydown.enter="addFirst"
          @keydown.esc="query = ''"
        />
        <button v-if="query" type="button" class="search-clear" @click="query = ''" :aria-label="t('common.clear')">×</button>

        <div v-if="suggestions.length" class="suggest">
          <button
            v-for="r in suggestions"
            :key="r.abbr"
            type="button"
            class="sug"
            @mousedown.prevent
            @click="addDriver(r.abbr)"
          >
            <span class="sug-photo" :style="`--dc:${r.color}`">
              <img v-if="headshot(r.abbr)" :src="headshot(r.abbr)!" :alt="r.abbr" loading="lazy" />
              <span v-else>{{ r.abbr }}</span>
            </span>
            <span class="sug-text">
              <span class="sug-name"><b>{{ r.abbr }}</b> · {{ r.name }}</span>
              <span class="sug-team">{{ r.team }} · {{ posLabel(r) }}</span>
            </span>
            <span class="sug-add">+</span>
          </button>
        </div>
        <div v-else-if="focused && full" class="sug-note">{{ t('common.maxDrivers') }}</div>
        <div v-else-if="focused && query.trim()" class="sug-note">{{ t('common.noResultFor', { q: query.trim() }) }}</div>
      </div>

      <div class="tiles" :style="tilesCols">
        <div
          v-for="(r, i) in selected"
          :key="r.abbr"
          class="tile"
          :class="{ dragging: dragIndex === i, over: overIndex === i }"
          :style="`--dc:${dcolor(r)}`"
          draggable="true"
          @dragstart="onDragStart(i)"
          @dragover.prevent="overIndex = i"
          @dragleave="overIndex = overIndex === i ? null : overIndex"
          @drop="onDrop(i)"
          @dragend="resetDrag"
        >
          <span class="tile-photo">
            <img v-if="headshot(r.abbr)" :src="headshot(r.abbr)!" :alt="r.abbr" loading="lazy" />
            <span v-else>{{ r.abbr }}</span>
          </span>
          <span class="tile-info">
            <span class="tile-abbr">{{ r.abbr }}</span>
            <span class="tile-name">{{ r.name }}</span>
            <span class="tile-team">{{ r.team }}</span>
          </span>
          <span class="tile-score">
            <span class="tile-score-val">{{ scoreText(r) }}</span>
            <span class="tile-score-lbl">F1ALYTICS</span>
          </span>
          <button type="button" class="tile-x" @click.stop="removeDriver(r.abbr)" :aria-label="t('common.remove')">×</button>
        </div>
        <div v-if="!selected.length" class="tiles-empty">
          {{ t('common.addDriver') }}
        </div>
      </div>
    </div>

    <div v-if="selected.length" class="cmp-card pad bars-card">
      <div class="ch">
        <span class="eyebrow">F1alytics Score</span>
        <span class="hint mono">DIESES RENNEN · 1–10</span>
      </div>
      <div class="sbars">
        <div v-for="r in selected" :key="r.abbr" class="sbar">
          <div class="sbar-head">
            <span class="sbar-ab" :style="{ color: dcolor(r) }">{{ r.abbr }}</span>
            <span class="sbar-team">{{ r.team }}</span>
            <span class="sbar-val">{{ scoreText(r) }}</span>
          </div>
          <div class="sbar-track">
            <div class="sbar-fill" :style="{ width: scorePct(r), background: dcolor(r) }"></div>
          </div>
          <div v-if="r.score" class="sbar-split">
            <span>Q {{ r.score.q.toFixed(1) }}</span>
            <span>R {{ r.score.r.toFixed(1) }}</span>
            <span>Δ {{ r.score.delta.toFixed(1) }}</span>
            <span v-if="r.score.modifiers" class="mod">+{{ r.score.modifiers.toFixed(1) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selected.length" class="cmp-card pad">
      <div class="cmp-grid" :style="gridStyle">
        <div class="row">
          <div class="lbl"></div>
          <div v-for="r in selected" :key="r.abbr" class="hd" :style="{ borderColor: dcolor(r) }">
            <div class="ab" :style="{ color: dcolor(r) }">{{ r.abbr }}</div>
            <div class="tm">{{ r.team }}</div>
          </div>
        </div>
        <div v-for="m in metrics" :key="m.l" class="row" :class="{ hl: m.hl }">
          <div class="lbl">{{ m.l }}</div>
          <div v-for="r in selected" :key="r.abbr" class="val" :class="{ best: isBest(m, r) }">
            {{ m.f(r) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import type { RaceResultRow, ScoreCard } from '@/types/f1'

const props = defineProps<{ result: RaceResultRow[] }>()
const { t } = useI18n()
const store = useSeasonStore()

interface Metric {
  l: string
  v: (r: RaceResultRow) => number | null
  f: (r: RaceResultRow) => string
  low: boolean
  hl?: boolean
}

const metrics: Metric[] = [
  { l: 'F1ALYTICS', v: (r) => sc(r)?.score ?? null, f: (r) => num(sc(r)?.score), low: false, hl: true },
  { l: 'POSITION', v: (r) => (out(r) ? null : r.pos), f: (r) => (out(r) ? statusText(r) : 'P' + r.pos), low: true },
  { l: 'PUNKTE', v: (r) => r.pts, f: (r) => String(r.pts), low: false },
  { l: 'QUALI-WERT (Q)', v: (r) => sc(r)?.q ?? null, f: (r) => num(sc(r)?.q), low: false },
  { l: 'ERGEBNIS-WERT (R)', v: (r) => sc(r)?.r ?? null, f: (r) => num(sc(r)?.r), low: false },
  { l: 'POSITIONEN (Δ)', v: (r) => sc(r)?.delta ?? null, f: (r) => num(sc(r)?.delta), low: false },
  { l: 'RUNDEN', v: (r) => r.laps, f: (r) => (r.laps == null ? '–' : String(r.laps)), low: false },
  { l: 'ABSTAND', v: () => null, f: (r) => (r.pos === 1 && !out(r) ? '—' : r.gapText), low: false },
  { l: 'STATUS', v: () => null, f: (r) => statusText(r), low: false },
]

const query = ref('')
const focused = ref(false)
const picked = ref<string[]>([])

function seed() {
  picked.value = props.result.slice(0, Math.min(3, props.result.length)).map((r) => r.abbr)
}
seed()
watch(() => props.result, seed)

const full = computed(() => picked.value.length >= 3)

const selected = computed<RaceResultRow[]>(() =>
  picked.value.map((a) => props.result.find((r) => r.abbr === a)).filter((r): r is RaceResultRow => !!r),
)

const suggestions = computed<RaceResultRow[]>(() => {
  if (full.value) return []
  const avail = props.result.filter((r) => !picked.value.includes(r.abbr))
  const q = query.value.trim().toLowerCase()
  if (!q) return focused.value ? avail : []
  return avail.filter(
    (r) => r.name.toLowerCase().includes(q) || r.abbr.toLowerCase().includes(q) || r.team.toLowerCase().includes(q),
  )
})

function addDriver(abbr: string) {
  if (picked.value.length < 3 && !picked.value.includes(abbr)) picked.value.push(abbr)
  query.value = ''
}
function addFirst() {
  const first = suggestions.value[0]
  if (first) addDriver(first.abbr)
}
function removeDriver(abbr: string) {
  picked.value = picked.value.filter((a) => a !== abbr)
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

const hsMap = computed<Record<string, string>>(() => {
  const m: Record<string, string> = {}
  for (const d of store.drivers) {
    const h = (d as unknown as { headshot?: string }).headshot
    if (h) m[d.abbr] = h
  }
  return m
})
function headshot(abbr: string): string | null {
  return hsMap.value[abbr] ?? null
}

const gridStyle = computed(() => ({
  gridTemplateColumns: `1.4fr repeat(${Math.max(1, selected.value.length)}, 1fr)`,
}))
const tilesCols = computed(() => ({
  gridTemplateColumns: `repeat(${Math.max(1, selected.value.length)}, minmax(0, 1fr))`,
}))

function sc(r: RaceResultRow): ScoreCard | null {
  return r.score
}
function num(n: number | null | undefined): string {
  return n == null ? '–' : n.toFixed(1)
}
function scoreText(r: RaceResultRow): string {
  return r.score ? r.score.score.toFixed(1) : '–'
}
function scorePct(r: RaceResultRow): string {
  if (!r.score) return '0%'
  const p = Math.max(0, Math.min(100, (r.score.score / 10) * 100))
  return p.toFixed(1) + '%'
}
function out(r: RaceResultRow): boolean {
  return r.dnf || r.dns || r.dsq
}
function statusText(r: RaceResultRow): string {
  if (r.dnf) return 'DNF'
  if (r.dns) return 'DNS'
  if (r.dsq) return 'DSQ'
  return 'FINISH'
}
function posLabel(r: RaceResultRow): string {
  return out(r) ? statusText(r) : 'P' + r.pos
}

function isBest(m: Metric, r: RaceResultRow): boolean {
  if (selected.value.length < 2) return false
  const vals = selected.value.map(m.v).filter((v): v is number => v != null)
  if (vals.length < 2) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  return m.v(r) === target
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
  for (const r of selected.value) {
    let c = r.color || '#999999'
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
    map[r.abbr] = c
  }
  return map
})
function dcolor(r: RaceResultRow): string {
  return dmap.value[r.abbr] || r.color
}
</script>

<style scoped>
.panel {
  --dc: var(--accent);
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
  font-size: 17px;
}
.sbar-team {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  flex: 1;
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
.sbar-split {
  display: flex;
  gap: 14px;
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}
.sbar-split .mod {
  color: var(--accent);
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
  font-size: 20px;
}
.hd .tm {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  margin-top: 3px;
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
  font-size: 19px;
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
  font-size: 25px;
  color: var(--text);
}
.row.hl > .val.best {
  color: var(--accent);
}
</style>
