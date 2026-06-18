<template>
  <div class="panel">
    <div class="eyebrow head">Konstrukteurs-Wertung · {{ store.year }}</div>
    <div v-for="(t, i) in store.teams" :key="t.team" class="team-row">
      <div class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</div>
      <div class="cbar" :style="{ background: t.color }"></div>
      <div class="nm" :style="{ color: t.color }">{{ t.team }}</div>
      <div class="drs">{{ t.drivers.map((d) => d.abbr).join(' · ') }}</div>
      <div class="bar-wrap"><div class="bar" :style="{ width: pct(t.points) + '%', background: t.color }"></div></div>
      <div class="pts">{{ t.points }}<small>PKT</small></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore.ts'

const store = useSeasonStore()
const max = computed(() => store.teams[0]?.points ?? 1)

function pct(points: number): number {
  return (points / max.value) * 100
}
</script>

<style scoped>
.panel {
  animation: fade 0.45s ease both;
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

.head {
  margin-bottom: 18px;
}

.team-row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px 18px;
  margin-bottom: 10px;
  transition: border-color 0.2s;
}

.team-row:hover {
  border-color: color-mix(in srgb, var(--accent) 40%, var(--line));
}

.team-row .rk {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 18px;
  color: var(--text-faint);
  width: 26px;
}

.team-row .rk.top {
  color: var(--accent);
}

.team-row .cbar {
  width: 4px;
  align-self: stretch;
  border-radius: 2px;
}

.team-row .nm {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 18px;
  flex-shrink: 0;
  width: 150px;
}

.team-row .drs {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.04em;
}

.team-row .bar-wrap {
  flex: 1;
  height: 6px;
  background: var(--surface-2);
  border-radius: 3px;
  overflow: hidden;
  min-width: 60px;
}

.team-row .bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.team-row .pts {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 22px;
  width: 96px;
  text-align: right;
  color: var(--text);
}

.team-row .pts small {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  font-weight: 400;
  margin-left: 4px;
}
</style>
