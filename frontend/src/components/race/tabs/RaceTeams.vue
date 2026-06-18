<template>
  <div class="panel">
    <div class="ch">
      <span class="eyebrow">Team-Wertung · dieses Rennen</span>
      <span class="hint mono">{{ teams.length }} TEAMS</span>
    </div>
    <div v-for="(t, i) in teams" :key="t.team" class="team-row">
      <div class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</div>
      <div class="cbar" :style="{ background: t.color }"></div>
      <div class="nm" :style="{ color: t.color }">{{ t.team }}</div>
      <div class="drs">
        <span v-for="d in t.drivers" :key="d.abbr" class="drv">
          {{ d.abbr }} <em :class="posClass(d)">{{ d.posText }}</em>
        </span>
      </div>
      <div class="bar-wrap"><div class="bar" :style="{ width: pct(t.points) + '%', background: t.color }"></div></div>
      <div class="pts">{{ t.points }}<small>PKT</small></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RaceResultRow } from '@/types/f1'

const props = defineProps<{ result: RaceResultRow[] }>()

interface TeamRow {
  team: string
  color: string
  points: number
  drivers: { abbr: string; posText: string; out: boolean; pos: number }[]
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

const teams = computed<TeamRow[]>(() => {
  const map = new Map<string, TeamRow>()
  for (const r of props.result) {
    let t = map.get(r.team)
    if (!t) {
      t = { team: r.team, color: r.color, points: 0, drivers: [] }
      map.set(r.team, t)
    }
    t.points += r.pts
    t.drivers.push({ abbr: r.abbr, posText: statusText(r), out: out(r), pos: r.pos })
  }
  const arr = [...map.values()]
  arr.forEach((t) => t.drivers.sort((a, b) => a.pos - b.pos))
  arr.sort((a, b) => b.points - a.points)
  return arr
})

const max = computed(() => teams.value[0]?.points ?? 1)

function pct(points: number): number {
  return (points / Math.max(1, max.value)) * 100
}

function posClass(d: { out: boolean; pos: number }): string {
  if (d.out) return 'out'
  if (d.pos === 1) return 'p1'
  if (d.pos <= 3) return 'p3'
  return ''
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

.team-row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 18px;
  margin-bottom: 10px;
  transition: border-color 0.2s;
}
.team-row:hover {
  border-color: color-mix(in srgb, var(--accent) 40%, var(--line));
}

.rk {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 18px;
  color: var(--text-faint);
  width: 24px;
}
.rk.top {
  color: var(--accent);
}

.cbar {
  width: 4px;
  align-self: stretch;
  border-radius: 2px;
}

.nm {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 17px;
  width: 140px;
  flex-shrink: 0;
}

.drs {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  flex: 1;
}
.drv {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.04em;
}
.drv em {
  font-style: normal;
  color: var(--text-dim);
}
.drv em.p1 {
  color: var(--accent);
}
.drv em.p3 {
  color: #c9a227;
}
.drv em.out {
  color: #8a4a4a;
}

.bar-wrap {
  width: 160px;
  height: 6px;
  background: var(--surface-2);
  border-radius: 3px;
  overflow: hidden;
  flex-shrink: 0;
}
.bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.pts {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 20px;
  width: 84px;
  text-align: right;
  color: var(--text);
}
.pts small {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  font-weight: 400;
  margin-left: 4px;
}

@media (max-aspect-ratio: 13/16) {
  .bar-wrap {
    display: none;
  }
  .nm {
    width: auto;
  }
}
</style>
