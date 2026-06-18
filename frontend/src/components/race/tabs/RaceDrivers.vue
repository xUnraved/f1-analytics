<template>
  <div class="panel">
    <div class="cmp-card pad pick">
      <div class="ch">
        <span class="eyebrow">Fahrer wählen</span>
        <span class="hint mono">BIS ZU 3 · <b>{{ picked.length }}</b>/3</span>
      </div>
      <div class="chips">
        <button
          v-for="r in result"
          :key="r.abbr"
          type="button"
          class="chip"
          :class="{ on: picked.includes(r.abbr) }"
          :style="chipStyle(r)"
          @click="toggle(r.abbr)"
        >
          {{ r.abbr }} · {{ r.name }}
        </button>
      </div>
    </div>

    <div class="cmp-card pad">
      <div class="cmp-grid" :style="gridStyle">
        <div class="row">
          <div class="lbl"></div>
          <div v-for="r in selected" :key="r.abbr" class="hd" :style="{ borderColor: r.color }">
            <div class="ab">{{ r.abbr }}</div>
            <div class="tm">{{ r.team }}</div>
          </div>
        </div>
        <div v-for="m in metrics" :key="m.l" class="row">
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
import type { RaceResultRow } from '@/types/f1'

const props = defineProps<{ result: RaceResultRow[] }>()

interface Metric {
  l: string
  v: (r: RaceResultRow) => number | null
  f: (r: RaceResultRow) => string
  low: boolean
}

const metrics: Metric[] = [
  { l: 'POSITION', v: (r) => (out(r) ? null : r.pos), f: (r) => (out(r) ? statusText(r) : 'P' + r.pos), low: true },
  { l: 'PUNKTE', v: (r) => r.pts, f: (r) => String(r.pts), low: false },
  { l: 'RUNDEN', v: (r) => r.laps, f: (r) => (r.laps == null ? '–' : String(r.laps)), low: false },
  { l: 'ABSTAND', v: () => null, f: (r) => (r.pos === 1 && !out(r) ? '—' : r.gapText), low: false },
  { l: 'STATUS', v: () => null, f: (r) => statusText(r), low: false },
]

const picked = ref<string[]>([])

function seed() {
  picked.value = props.result.slice(0, Math.min(3, props.result.length)).map((r) => r.abbr)
}
seed()
watch(() => props.result, seed)

function toggle(abbr: string) {
  const i = picked.value.indexOf(abbr)
  if (i >= 0) picked.value.splice(i, 1)
  else if (picked.value.length < 3) picked.value.push(abbr)
}

const selected = computed<RaceResultRow[]>(() =>
  picked.value.map((a) => props.result.find((r) => r.abbr === a)).filter((r): r is RaceResultRow => !!r),
)

const gridStyle = computed(() => ({
  gridTemplateColumns: `1.2fr repeat(${Math.max(1, selected.value.length)}, 1fr)`,
}))

function out(r: RaceResultRow): boolean {
  return r.dnf || r.dns || r.dsq
}

function statusText(r: RaceResultRow): string {
  if (r.dnf) return 'DNF'
  if (r.dns) return 'DNS'
  if (r.dsq) return 'DSQ'
  return 'FINISH'
}

function chipStyle(r: RaceResultRow) {
  return picked.value.includes(r.abbr) ? { borderColor: r.color, color: r.color, background: `${r.color}1A` } : {}
}

function isBest(m: Metric, r: RaceResultRow): boolean {
  if (selected.value.length < 2) return false
  const vals = selected.value.map(m.v).filter((v): v is number => v != null)
  if (vals.length < 2) return false
  const target = m.low ? Math.min(...vals) : Math.max(...vals)
  return m.v(r) === target
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
  font-size: 19px;
  padding: 13px 0;
  color: var(--text-dim);
}
.val.best {
  color: var(--accent);
}
</style>
