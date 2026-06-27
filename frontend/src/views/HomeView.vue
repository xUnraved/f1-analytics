<template>
  <section class="wrap intro">
    <span class="eyebrow">Saison-Analyse</span>
    <h1 class="intro-title">SAISON <span>{{ store.year }}</span></h1>
  </section>

  <section class="wrap hero">
    <div class="dash card">
      <div class="dash-head">
        <span class="dash-tab clip-tab">SAISON-WAHL</span>
        <span class="dash-id mono">SYS//{{ store.year }}</span>
      </div>
      <SeasonWheel />
      <div class="readouts">
        <div class="ro"><span>RENNEN</span><b>{{ store.races.length }}</b></div>
        <div class="ro"><span>FAHRER</span><b>{{ store.drivers.length }}</b></div>
        <div class="ro"><span>TEAMS</span><b>{{ store.teams.length }}</b></div>
      </div>
      <div v-if="store.loading" class="loading-bar">
        <div class="loading-track"><div class="loading-fill"></div></div>
        <span class="loading-label">Daten werden von OpenF1 geladen …</span>
      </div>
      <div v-else class="enter-hint"><span class="arrow">↓</span> RENNEN ERKUNDEN</div>
    </div>

    <div class="cockpit">
      <svg class="radar" viewBox="0 0 200 200" preserveAspectRatio="xMidYMid meet">
        <circle v-for="r in rings" :key="'r' + r" cx="100" cy="100" :r="r" class="ring" />
        <line v-for="(s, i) in spokes" :key="'s' + i" x1="100" y1="100" :x2="s.x" :y2="s.y" class="spoke" />
        <circle cx="100" cy="100" r="96" class="ring-glow" />
      </svg>
      <div class="sweep"></div>
      <div class="globe-layer">
        <GlobeView @select="goToRace" />
      </div>
      <div class="hud-corners"></div>
    </div>
  </section>

  <section ref="raceSectionEl" class="race-section">
    <div class="wrap">
      <div class="stabs">
        <button
          v-for="t in tabs"
          :key="t.key"
          type="button"
          class="stab clip-tab"
          :class="{ active: tab === t.key }"
          @click="setTab(t.key)"
        >
          {{ t.label }}
        </button>
      </div>

      <RaceDetail v-if="tab === 'races' && store.selectedRace" @back="backToGrid" />
      <RaceGrid v-else-if="tab === 'races'" @select="goToRace" />
      <SeasonStandings v-else-if="tab === 'standings'" />
      <DriversTab v-else-if="tab === 'drivers'" />
      <SeasonTeams v-else />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore'
import SeasonWheel from '@/components/season/SeasonWheel.vue'
import GlobeView from '@/components/season/GlobeView.vue'
import RaceGrid from '@/components/race/RaceGrid.vue'
import RaceDetail from '@/components/race/RaceDetail.vue'
import DriversTab from '@/components/race/tabs/DriversTab.vue'
import SeasonTeams from '@/components/season/SeasonTeams.vue'
import SeasonStandings from '@/components/season/SeasonStandings.vue'

type Tab = 'races' | 'drivers' | 'teams' | 'standings'

const store = useSeasonStore()
const raceSectionEl = ref<HTMLElement | null>(null)
const tab = ref<Tab>('races')

const tabs: { key: Tab; label: string }[] = [
  { key: 'races', label: 'RENNEN' },
  { key: 'standings', label: 'WERTUNG' },
  { key: 'drivers', label: 'ANALYSE' },
  { key: 'teams', label: 'TEAMS' },
]

const rings = [30, 48, 66, 84, 96]
const spokes = Array.from({ length: 12 }, (_, i) => {
  const a = (i * 30 * Math.PI) / 180
  return { x: 100 + 96 * Math.cos(a), y: 100 + 96 * Math.sin(a) }
})

onMounted(async () => {
  await store.loadYears()
  await store.loadSeason(store.year)
})

function setTab(t: Tab) {
  if (t === 'races') store.clearRace()
  tab.value = t
}

async function goToRace() {
  tab.value = 'races'
  await nextTick()
  raceSectionEl.value?.scrollIntoView({ behavior: 'smooth' })
}

async function backToGrid() {
  store.clearRace()
  await nextTick()
  raceSectionEl.value?.scrollIntoView({ behavior: 'smooth' })
}

watch(
  () => store.selectedRaceIndex,
  (i) => {
    if (i !== null) tab.value = 'races'
  },
)
</script>

<style scoped>
.intro {
  padding: 34px 0 4px;
}

.intro-title {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: clamp(40px, 6vw, 74px);
  line-height: 0.95;
  letter-spacing: -0.03em;
  margin-top: 8px;
  color: var(--text);
}

.intro-title span {
  color: var(--accent);
}

.hero {
  display: grid;
  grid-template-columns: minmax(230px, 27%) 1fr;
  gap: 44px;
  align-items: center;
  padding: 18px 0 54px;
}

.dash {
  display: flex;
  flex-direction: column;
  padding: 16px 18px 20px;
  position: relative;
  overflow: hidden;
}

.dash::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--accent);
}

.dash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.dash-tab {
  background: var(--accent);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  padding: 5px 16px 5px 11px;
}

.dash-id {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}

.readouts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 14px;
}

.ro {
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 10px 8px;
  text-align: center;
  background: rgba(0, 0, 0, 0.22);
}

.ro span {
  display: block;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
}

.ro b {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 24px;
  color: var(--text);
}

.loading-bar {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.loading-track {
  height: 2px;
  background: var(--line);
  border-radius: 1px;
  overflow: hidden;
}

.loading-fill {
  height: 100%;
  width: 40%;
  background: var(--accent);
  border-radius: 1px;
  animation: scan 1.6s ease-in-out infinite;
}

@keyframes scan {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
}

.loading-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}

.enter-hint {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 9px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
}

.enter-hint .arrow {
  color: var(--accent);
  animation: bob 1.8s ease-in-out infinite;
}

@keyframes bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(4px);
  }
}

.cockpit {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.radar {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 96%;
  aspect-ratio: 1 / 1;
  pointer-events: none;
  z-index: 0;
}

.radar .ring {
  fill: none;
  stroke: rgba(120, 150, 200, 0.1);
  stroke-width: 0.4;
}

.radar .spoke {
  stroke: rgba(120, 150, 200, 0.08);
  stroke-width: 0.4;
}

.radar .ring-glow {
  fill: none;
  stroke: rgba(61, 127, 255, 0.4);
  stroke-width: 0.7;
  filter: drop-shadow(0 0 6px rgba(61, 127, 255, 0.5));
}

.sweep {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 96%;
  aspect-ratio: 1 / 1;
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
  background: conic-gradient(from 0deg, transparent 0 80%, rgba(61, 127, 255, 0.12) 92%, transparent 100%);
  animation: radar-spin 9s linear infinite;
}

@keyframes radar-spin {
  to {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

.globe-layer {
  position: relative;
  z-index: 1;
  width: 100%;
}

.hud-corners {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 5;
}

.hud-corners::before,
.hud-corners::after {
  content: '';
  position: absolute;
  width: 26px;
  height: 26px;
  border: 2px solid rgba(170, 184, 198, 0.32);
}

.hud-corners::before {
  left: 1%;
  top: 4%;
  border-right: none;
  border-bottom: none;
}

.hud-corners::after {
  right: 1%;
  bottom: 4%;
  border-left: none;
  border-top: none;
}

.race-section {
  border-top: 1px solid var(--line);
  padding: 40px 0 70px;
}

.stabs {
  display: flex;
  gap: 6px;
  margin-bottom: 26px;
}

.stab {
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.14em;
  padding: 11px 26px 11px 20px;
  cursor: pointer;
  transition:
    color 0.18s,
    background 0.18s;
}

.stab:hover {
  color: var(--text-dim);
}

.stab.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

@media (max-aspect-ratio: 13/16) {
  .intro {
    padding: 20px 0 2px;
  }
  .hero {
    grid-template-columns: 1fr;
    align-items: start;
    gap: 16px;
    padding: 10px 0 24px;
  }
  .race-section {
    padding: 24px 0 54px;
  }
}
</style>
