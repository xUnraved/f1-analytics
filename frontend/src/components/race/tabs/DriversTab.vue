<template>
  <div class="panel">
    <div class="cmp-card pad pick">
      <div class="ch">
        <span class="eyebrow">Fahrer wählen</span>
        <span class="hint mono">BIS ZU 3 · <b>{{ store.selectedDrivers.length }}</b>/3</span>
      </div>
      <div class="chips">
        <button
          v-for="d in store.drivers"
          :key="d.abbr"
          type="button"
          class="chip"
          :class="{ on: store.selectedDrivers.includes(d.abbr) }"
          :style="chipStyle(d)"
          @click="store.toggleDriver(d.abbr)"
        >
          {{ d.abbr }} · {{ d.name }}
        </button>
      </div>
    </div>

    <div class="cmp">
      <div class="cmp-card pad">
        <div class="cmp-grid" :style="gridStyle">
          <div class="row">
            <div class="lbl"></div>
            <div v-for="d in selected" :key="d.abbr" class="hd" :style="{ borderColor: d.color }">
              <div class="ab">{{ d.abbr }}</div>
              <div class="tm">{{ d.team }}</div>
            </div>
          </div>
          <div v-for="m in metrics" :key="m.l" class="row">
            <div class="lbl">{{ m.l }}</div>
            <div v-for="d in selected" :key="d.abbr" class="val" :class="{ best: isBest(m, d) }">
              {{ m.f(m.g(d)) }}
            </div>
          </div>
        </div>
      </div>

      <div class="cmp-card pad">
        <div class="prog-title">Punkte-Verlauf · kumuliert</div>
        <svg v-if="selected.length" :viewBox="`0 0 ${W} ${H}`" width="100%" class="chart">
          <line v-for="(gy, i) in gridLines" :key="i" :x1="pad" :y1="gy" :x2="W - pad" :y2="gy" class="g" />
          <polyline
            v-for="d in selected"
            :key="d.abbr"
            :points="lineFor(d)"
            fill="none"
            :stroke="d.color"
            stroke-width="2.5"
            stroke-linejoin="round"
          />
        </svg>
        <div class="legend">
          <span v-for="d in selected" :key="d.abbr"><i :style="{ background: d.color }"></i>{{ d.abbr }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore'
import type { DriverStanding } from '@/types/f1'

interface Metric {
  l: string
  g: (d: DriverStanding) => number | null
  f: (v: number | null) => string
  low: boolean
}

const store = useSeasonStore()

const W = 440
const H = 240
const pad = 12

const metrics: Metric[] = [
  { l: 'PUNKTE', g: (d) => d.points, f: (v) => String(v), low: false },
  { l: 'SIEGE', g: (d) => d.wins, f: (v) => String(v), low: false },
  { l: 'PODESTPLÄTZE', g: (d) => d.podiums, f: (v) => String(v), low: false },
  { l: 'BESTE PLATZIERUNG', g: (d) => d.bestFinish, f: (v) => (v == null ? '–' : 'P' + v), low: true },
  { l: 'Ø PLATZIERUNG', g: (d) => d.avgFinish, f: (v) => (v == null ? '–' : v.toFixed(1)), low: true },
  { l: 'DNF', g: (d) => d.dnf, f: (v) => String(v), low: false },
]

function ensureSeed() {
  if (store.selectedDrivers.length === 0 && store.drivers.length > 0) {
    store.selectedDrivers = store.drivers.slice(0, 3).map((d) => d.abbr)
  }
}

onMounted(ensureSeed)
watch(() => store.drivers, ensureSeed)

const selected = computed<DriverStanding[]>(() =>
  store.selectedDrivers.map((a) => store.drivers.find((d) => d.abbr === a)).filter((d): d is DriverStanding => !!d),
)

const gridStyle = computed(() => ({
  gridTemplateColumns: `1.2fr repeat(${Math.max(1, selected.value.length)}, 1fr)`,
}))

function chipStyle(d: DriverStanding) {
  return store.selectedDrivers.includes(d.abbr)
    ? { borderColor: d.color, color: d.color, background: `${d.color}1A` }
    : {}
}

function isBest(m: Metric, d: DriverStanding): boolean {
  if (selected.value.length < 2) return false
  const vals = selected.value.map(m.g).filter((v): v is number => v != null)
  if (!vals.length) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  return m.g(d) === target
}

const n = computed(() => store.races.length)
const maxPts = computed(() => Math.max(...selected.value.map((d) => d.cum[d.cum.length - 1] ?? 0), 1))
const gridLines = computed(() => [0, 1, 2, 3].map((g) => pad + (g / 3) * (H - 2 * pad)))

function x(i: number): number {
  return pad + (i / Math.max(1, n.value - 1)) * (W - 2 * pad)
}
function y(v: number): number {
  return H - pad - (v / maxPts.value) * (H - 2 * pad)
}
function lineFor(d: DriverStanding): string {
  return d.cum.map((v, i) => `${x(i)},${y(v)}`).join(' ')
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

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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

.cmp {
  display: grid;
  gap: 14px;
  grid-template-columns: 1.1fr 1fr;
  align-items: start;
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
  color: var(--text);
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
  font-size: 20px;
  padding: 13px 0;
  color: var(--text-dim);
}
.val.best {
  color: var(--accent);
}

.prog-title {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
  text-transform: uppercase;
  margin-bottom: 14px;
}
.chart .g {
  stroke: var(--line);
  stroke-width: 1;
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

@media (max-aspect-ratio: 13/16) {
  .cmp {
    grid-template-columns: 1fr;
  }
}
</style>
