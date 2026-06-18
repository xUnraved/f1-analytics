<template>
  <div class="panel">
    <div class="cmp-card pad">
      <div class="ch">
        <span class="eyebrow">Team-Vergleich</span>
        <span class="hint mono">ZWEI TEAMS GEGENÜBERSTELLEN</span>
      </div>

      <div class="selects">
        <div class="sel">
          <label>TEAM A</label>
          <FSelect v-model="teamA" :options="teamOptions" width="100%" />
        </div>
        <div class="sel">
          <label>TEAM B</label>
          <FSelect v-model="teamB" :options="teamOptions" width="100%" />
        </div>
      </div>

      <div v-if="a && b" class="cmp-grid" :style="{ gridTemplateColumns: '1.2fr 1fr 1fr' }">
        <div class="row">
          <div class="lbl"></div>
          <div class="hd" :style="{ borderColor: a.color }">
            <div class="nm" :style="{ color: a.color }">{{ a.team }}</div>
            <div class="drs">{{ a.drivers.map((d) => d.abbr).join(' / ') }}</div>
          </div>
          <div class="hd" :style="{ borderColor: b.color }">
            <div class="nm" :style="{ color: b.color }">{{ b.team }}</div>
            <div class="drs">{{ b.drivers.map((d) => d.abbr).join(' / ') }}</div>
          </div>
        </div>
        <div v-for="m in teamMetrics" :key="m.l" class="row">
          <div class="lbl">{{ m.l }}</div>
          <div class="val" :class="{ best: best(m, a, b) === 'a' }">{{ m.g(a) }}</div>
          <div class="val" :class="{ best: best(m, a, b) === 'b' }">{{ m.g(b) }}</div>
        </div>
      </div>

      <div v-if="a && b" class="chart-wrap">
        <svg :viewBox="`0 0 ${W} ${H}`" width="100%" class="chart">
          <line v-for="(gy, i) in gridLines" :key="i" :x1="pad" :y1="gy" :x2="W - pad" :y2="gy" class="g" />
          <polyline :points="line(a)" fill="none" :stroke="a.color" stroke-width="2.5" stroke-linejoin="round" />
          <polyline :points="line(b)" fill="none" :stroke="b.color" stroke-width="2.5" stroke-linejoin="round" />
        </svg>
        <div class="legend">
          <span><i :style="{ background: a.color }"></i>{{ a.team }}</span>
          <span><i :style="{ background: b.color }"></i>{{ b.team }}</span>
        </div>
      </div>
    </div>

    <transition name="duel">
      <div v-if="duel" class="cmp-card pad duel-card">
        <div class="ch">
          <span class="eyebrow">Fahrer-Duell · {{ duel.team }}</span>
          <button type="button" class="close" @click="duelTeam = null">SCHLIESSEN ✕</button>
        </div>
        <div v-if="duel.drivers.length >= 2" class="cmp-grid" :style="{ gridTemplateColumns: '1.2fr 1fr 1fr' }">
          <div class="row">
            <div class="lbl"></div>
            <div v-for="d in duel.drivers.slice(0, 2)" :key="d.abbr" class="hd" :style="{ borderColor: d.color }">
              <div class="ab">{{ d.abbr }}</div>
              <div class="drs">{{ d.name }}</div>
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
        <div v-else class="solo">Nur ein Fahrer in diesem Team erfasst.</div>
      </div>
    </transition>

    <div class="all">
      <div class="ch">
        <span class="eyebrow">Alle Teams</span>
        <span class="hint mono">TEAM ANKLICKEN → FAHRER-DUELL</span>
      </div>
      <div class="cards">
        <button
          v-for="(t, i) in store.teams"
          :key="t.team"
          type="button"
          class="tcard"
          :style="{ '--c': t.color }"
          @click="duelTeam = t.team"
        >
          <div class="tc-top">
            <span class="pos mono">P{{ i + 1 }}</span>
            <span class="pts">{{ t.points }}<small>PKT</small></span>
          </div>
          <div class="tc-name" :style="{ color: t.color }">{{ t.team }}</div>
          <div class="tc-foot">
            <span class="tc-drs mono">{{ t.drivers.map((d) => `${d.abbr} ${d.points}`).join(' · ') }}</span>
            <span class="duell mono">DUELL →</span>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore'
import FSelect from '@/components/ui/FSelect.vue'
import type { TeamStanding, DriverStanding } from '@/types/f1'

interface TMetric {
  l: string
  g: (t: TeamStanding) => number
  low?: boolean
}
interface DMetric {
  l: string
  g: (d: DriverStanding) => number | null
  f: (v: number | null) => string
  low?: boolean
}

const store = useSeasonStore()

const W = 460
const H = 220
const pad = 12

const teamA = ref<string>('')
const teamB = ref<string>('')
const duelTeam = ref<string | null>(null)

const teamOptions = computed(() => store.teams.map((t) => ({ value: t.team, label: t.team })))

const teamMetrics: TMetric[] = [
  { l: 'PUNKTE', g: (t) => t.points },
  { l: 'SIEGE', g: (t) => t.wins },
  { l: 'PODESTPLÄTZE', g: (t) => t.drivers.reduce((s, d) => s + d.podiums, 0) },
  { l: 'POLE POSITIONS', g: (t) => t.drivers.reduce((s, d) => s + d.poles, 0) },
]

const driverMetrics: DMetric[] = [
  { l: 'PUNKTE', g: (d) => d.points, f: (v) => String(v) },
  { l: 'SIEGE', g: (d) => d.wins, f: (v) => String(v) },
  { l: 'PODESTPLÄTZE', g: (d) => d.podiums, f: (v) => String(v) },
  { l: 'POLES', g: (d) => d.poles, f: (v) => String(v) },
  { l: 'Ø PLATZIERUNG', g: (d) => d.avgFinish, f: (v) => (v == null ? '–' : v.toFixed(1)), low: true },
  { l: 'DNF', g: (d) => d.dnf, f: (v) => String(v) },
]

function ensureSeed() {
  const t = store.teams
  if (t.length && !t.some((x) => x.team === teamA.value)) teamA.value = t[0]!.team
  if (t.length > 1 && (!t.some((x) => x.team === teamB.value) || teamB.value === teamA.value))
    teamB.value = (t.find((x) => x.team !== teamA.value) ?? t[0]!).team
}

onMounted(ensureSeed)
watch(() => store.teams, ensureSeed)

const a = computed<TeamStanding | null>(() => store.teams.find((t) => t.team === teamA.value) ?? null)
const b = computed<TeamStanding | null>(() => store.teams.find((t) => t.team === teamB.value) ?? null)
const duel = computed<TeamStanding | null>(() => store.teams.find((t) => t.team === duelTeam.value) ?? null)

function best(m: TMetric, x: TeamStanding, y: TeamStanding): 'a' | 'b' | null {
  const vx = m.g(x)
  const vy = m.g(y)
  if (vx === vy) return null
  const aWins = m.low ? vx < vy : vx > vy
  return aWins ? 'a' : 'b'
}

function duelBest(m: DMetric, ds: DriverStanding[], d: DriverStanding): boolean {
  const vals = ds.map(m.g).filter((v): v is number => v != null)
  if (vals.length < 2) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  return m.g(d) === target && vals[0] !== vals[1]
}

const n = computed(() => store.races.length)
function teamCum(t: TeamStanding): number[] {
  const out = new Array(n.value).fill(0)
  for (const d of t.drivers) d.cum.forEach((v, i) => { if (i < out.length) out[i] += v })
  return out
}
const maxPts = computed(() => {
  const va = a.value ? teamCum(a.value) : [0]
  const vb = b.value ? teamCum(b.value) : [0]
  return Math.max(va[va.length - 1] ?? 0, vb[vb.length - 1] ?? 0, 1)
})
const gridLines = computed(() => [0, 1, 2, 3].map((g) => pad + (g / 3) * (H - 2 * pad)))

function x(i: number): number {
  return pad + (i / Math.max(1, n.value - 1)) * (W - 2 * pad)
}
function y(v: number): number {
  return H - pad - (v / maxPts.value) * (H - 2 * pad)
}
function line(t: TeamStanding): string {
  return teamCum(t).map((v, i) => `${x(i)},${y(v)}`).join(' ')
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
.duel-card {
  margin-top: 14px;
  border-color: color-mix(in srgb, var(--accent) 40%, var(--line));
}

.ch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.hint {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}
.close {
  background: none;
  border: 1px solid var(--line);
  color: var(--text-dim);
  border-radius: 999px;
  padding: 6px 13px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  cursor: pointer;
}
.close:hover {
  border-color: var(--accent);
  color: var(--text);
}

.selects {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 22px;
}
.sel label {
  display: block;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  margin-bottom: 7px;
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
  padding: 14px 4px 12px;
  border-bottom: 2px solid var(--line);
}
.hd .nm {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 18px;
}
.hd .ab {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 20px;
  color: var(--text);
}
.hd .drs {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  margin-top: 3px;
}
.lbl {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  padding: 14px 0;
}
.val {
  text-align: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 21px;
  padding: 14px 0;
  color: var(--text-dim);
}
.val.best {
  color: var(--accent);
}

.chart-wrap {
  margin-top: 18px;
}
.chart .g {
  stroke: var(--line);
  stroke-width: 1;
}
.legend {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 12px;
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
.solo {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-faint);
  padding: 14px 0;
}

.all {
  margin-top: 24px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.tcard {
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--line);
  border-left: 3px solid var(--c);
  border-radius: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    transform 0.2s;
}
.tcard:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--c) 60%, var(--line));
}
.tc-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.tc-top .pos {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
}
.tc-top .pts {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 20px;
  color: var(--text);
}
.tc-top .pts small {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-faint);
  font-weight: 400;
  margin-left: 3px;
}
.tc-name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 19px;
  margin: 8px 0 12px;
}
.tc-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 11px;
  border-top: 1px solid var(--line);
}
.tc-drs {
  font-size: 11px;
  color: var(--text-dim);
}
.duell {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--accent);
}

.duel-enter-active,
.duel-leave-active {
  transition:
    opacity 0.22s,
    transform 0.22s;
}
.duel-enter-from,
.duel-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-aspect-ratio: 13/16) {
  .selects {
    grid-template-columns: 1fr;
  }
}
</style>
