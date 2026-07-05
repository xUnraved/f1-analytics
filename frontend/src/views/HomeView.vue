<template>
  <section class="wrap intro">
    <span class="eyebrow">{{ t('home.eyebrow') }}</span>
    <h1 class="intro-title">{{ t('home.title') }} <span>{{ store.year }}</span></h1>
  </section>

  <section class="wrap hero">
    <div class="dash card">
      <div class="dash-head">
        <span class="dash-tab clip-tab">{{ t('home.card.label') }}</span>
        <span class="dash-id mono">SYS//{{ store.year }}</span>
      </div>
      <SeasonWheel />
      <div class="readouts">
        <div class="ro"><span>{{ t('home.card.races') }}</span><b>{{ store.races.length }}</b></div>
        <div class="ro"><span>{{ t('home.card.drivers') }}</span><b>{{ store.drivers.length }}</b></div>
        <div class="ro"><span>{{ t('home.card.teams') }}</span><b>{{ store.teams.length }}</b></div>
      </div>
      <div v-if="store.loading" class="loading-bar">
        <div v-if="store.liveSessionBlocked" class="live-blocked">
          <span class="live-icon">&#9679;</span>
          <div class="live-lines">
            <span class="live-title">{{ t('home.liveBlocked.title') }}</span>
            <span class="live-sub">{{ t('home.liveBlocked.detail') }}</span>
          </div>
        </div>
        <template v-else>
          <div class="progress-head">
            <span class="loading-label">
              <template v-if="store.totalRaces === 0">{{ t('home.loading.sessions') }}</template>
              <template v-else>{{ t('home.loading.races', { loaded: store.races.length, total: store.totalRaces }) }}</template>
            </span>
            <span v-if="store.totalRaces > 0" class="loading-pct">{{ pct }}%</span>
          </div>
          <div class="loading-track">
            <div
              class="loading-fill"
              :class="{ indeterminate: store.totalRaces === 0 }"
              :style="store.totalRaces > 0 ? { width: pct + '%' } : {}"
            ></div>
          </div>
          <span v-if="eta" class="loading-eta">{{ t('home.loading.eta', { time: eta }) }}</span>
          <span v-else-if="store.totalRaces === 0" class="loading-eta">{{ t('home.loading.querying') }}</span>
        </template>
      </div>
      <div v-else class="enter-hint"><span class="arrow">↓</span> {{ t('home.hint') }}</div>
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
          v-for="tabItem in tabs"
          :key="tabItem.key"
          type="button"
          class="stab clip-tab"
          :class="{ active: tab === tabItem.key }"
          @click="setTab(tabItem.key)"
        >
          {{ tabItem.label }}
        </button>
      </div>

      <RaceDetail v-if="tab === 'races' && store.selectedRace" @back="backToGrid" />
      <RaceGrid v-else-if="tab === 'races'" @select="goToRace" />
      <SeasonStandings v-else-if="tab === 'standings'" />
      <DriversTab v-else-if="tab === 'drivers'" />
      <SeasonTeams v-else-if="tab === 'teams'" />
      <QuizTab v-else-if="tab === 'quiz'" />
      <TippspielTab v-else />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import SeasonWheel from '@/components/season/SeasonWheel.vue'
import GlobeView from '@/components/season/GlobeView.vue'
import RaceGrid from '@/components/race/RaceGrid.vue'
import RaceDetail from '@/components/race/RaceDetail.vue'
import DriversTab from '@/components/race/tabs/DriversTab.vue'
import SeasonTeams from '@/components/season/SeasonTeams.vue'
import SeasonStandings from '@/components/season/SeasonStandings.vue'
import TippspielTab from '@/components/tippspiel/TippspielTab.vue'
import QuizTab from '@/components/quiz/QuizTab.vue'

type Tab = 'races' | 'drivers' | 'teams' | 'standings' | 'tippspiel' | 'quiz'

const { t } = useI18n()
const store = useSeasonStore()
const raceSectionEl = ref<HTMLElement | null>(null)
const tab = ref<Tab>('races')

const loadStart = ref<number | null>(null)
const nowMs = ref(Date.now())
let nowTimer: ReturnType<typeof setInterval> | null = null

const pct = computed(() => {
  const total = store.totalRaces
  const loaded = store.races.length
  return total > 0 ? Math.min(100, Math.round((loaded / total) * 100)) : 0
})
const eta = computed(() => {
  if (!loadStart.value || store.races.length === 0 || store.totalRaces === 0) return null
  const elapsed = (nowMs.value - loadStart.value) / 1000
  const rate    = elapsed / store.races.length
  const secs    = Math.round((store.totalRaces - store.races.length) * rate)
  if (secs < 8) return null
  return secs < 60 ? `${secs}s` : `${Math.round(secs / 60)}min`
})

watch(() => store.loading, (val) => {
  if (val && loadStart.value === null) {
    loadStart.value = Date.now()
    nowTimer = setInterval(() => { nowMs.value = Date.now() }, 1000)
  } else if (!val) {
    loadStart.value = null
    if (nowTimer !== null) { clearInterval(nowTimer); nowTimer = null }
  }
}, { immediate: true })

onUnmounted(() => {
  if (nowTimer !== null) clearInterval(nowTimer)
})

const tabs = computed(() => [
  { key: 'races' as Tab, label: t('home.tabs.races') },
  { key: 'standings' as Tab, label: t('home.tabs.standings') },
  { key: 'drivers' as Tab, label: t('home.tabs.drivers') },
  { key: 'teams' as Tab, label: t('home.tabs.teams') },
  { key: 'quiz' as Tab, label: t('home.tabs.quiz') },
  { key: 'tippspiel' as Tab, label: t('home.tabs.tippspiel') },
])

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
  gap: 6px;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.loading-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}

.loading-pct {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 0.04em;
}

.loading-track {
  height: 4px;
  background: var(--line);
  border-radius: 2px;
  overflow: hidden;
}

.loading-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-deep), var(--accent));
  border-radius: 2px;
  transition: width 0.6s cubic-bezier(0.25, 1, 0.5, 1);
  box-shadow: 0 0 8px color-mix(in srgb, var(--accent) 60%, transparent);
}

.loading-fill.indeterminate {
  width: 38% !important;
  animation: scan 1.6s ease-in-out infinite;
  transition: none;
}

@keyframes scan {
  0%   { transform: translateX(-160%); }
  100% { transform: translateX(390%); }
}

.loading-eta {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  opacity: 0.7;
}

.live-blocked {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0 4px;
}

.live-icon {
  color: #e10600;
  font-size: 10px;
  margin-top: 2px;
  flex-shrink: 0;
  animation: pulse-dot 1.2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.2; }
}

.live-lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.live-title {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-dim);
}

.live-sub {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.06em;
  line-height: 1.5;
  color: var(--text-faint);
  opacity: 0.75;
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
