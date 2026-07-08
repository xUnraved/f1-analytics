<template>
  <div v-if="race">
    <div class="race-head">
      <div class="race-head-left">
        <span class="eyebrow round">{{ t('race.detail.round') }} {{ String(race.round).padStart(2, '0') }}</span>
        <div class="title-row">
          <h2 class="race-title">{{ race.gp.toUpperCase() }}</h2>
          <img
            v-if="trackImageUrl"
            :src="trackImageUrl"
            :alt="race.gp + ' Streckenkarte'"
            class="track-map"
            @error="trackImgError = true"
          />
        </div>
        <div class="race-sub">
          <span>{{ race.circuit }}</span>
          <span class="country-cell">
            <img v-if="race.countryFlag" :src="race.countryFlag" :alt="race.country" class="flag" />
            {{ race.country }}
          </span>
          <span>{{ race.date }}</span>
        </div>
      </div>
      <div class="head-actions">
        <button type="button" class="icon-btn" :class="{ spinning: refreshingRace }" :disabled="refreshingRace" :title="t('race.detail.refresh')" @click="onRefreshRace">⟳</button>
        <button type="button" class="back-link" @click="emit('back')">{{ t('race.detail.back') }}</button>
      </div>
    </div>

    <div v-if="!race.result.length" class="empty">{{ t('race.detail.noResults') }}</div>

    <template v-else>
      <div class="rtabs">
        <button
          v-for="tabItem in tabs"
          :key="tabItem.key"
          type="button"
          class="rtab clip-tab"
          :class="{ active: tab === tabItem.key }"
          @click="tab = tabItem.key"
        >
          {{ tabItem.label }}
        </button>
      </div>

      <OverviewTab v-if="tab === 'result'" />
      <SessionResultTab
        v-else-if="tab === 'qualifying'"
        :sessions="[{ name: 'Qualifying', result: race.qualifyingResult }]"
      />
      <SessionResultTab
        v-else-if="tab === 'practice'"
        :sessions="race.practiceResults"
      />
      <RaceDrivers v-else-if="tab === 'drivers'" :result="race.result" />
      <RaceTeams v-else-if="tab === 'teams'" :result="race.result" />
      <ReplayTab v-else-if="tab === 'replay'" :session-key="race.sessionKey" :date-start="race.sessionDateStart" />
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * Detailansicht eines GP-Wochenendes mit Tab-Navigation.
 *
 * Tabs (kontextsensitiv):
 *  - result: Rennergebnis (immer sichtbar)
 *  - qualifying: nur wenn qualifyingResult vorhanden
 *  - practice: nur wenn practiceResults vorhanden
 *  - drivers: Fahrer-Vergleich
 *  - teams: Team-Übersicht
 *  - replay: GPS-Replay (nur für abgeschlossene Rennen mit sessionKey)
 *
 * Refresh-Button ruft store.refreshRaceSingle() auf (synchron, kein Polling).
 * trackImgError: verhindert broken-image-Icon bei fehlendem Streckenbild.
 */
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import OverviewTab from './tabs/OverviewTab.vue'
import RaceDrivers from './tabs/RaceDrivers.vue'
import RaceTeams from './tabs/RaceTeams.vue'
import SessionResultTab from './tabs/SessionResultTab.vue'
import ReplayTab from './tabs/ReplayTab.vue'

type RTab = 'result' | 'qualifying' | 'practice' | 'drivers' | 'teams' | 'replay'

const { t } = useI18n()
const emit = defineEmits<{ back: [] }>()
const store = useSeasonStore()
const race = computed(() => store.selectedRace)
const tab = ref<RTab>('result')
const trackImgError = ref(false)
const refreshingRace = ref(false)

async function onRefreshRace() {
  if (!race.value?.sessionKey) return
  refreshingRace.value = true
  try {
    await store.refreshRaceSingle(race.value.sessionKey)
  } finally {
    refreshingRace.value = false
  }
}

const trackImageUrl = computed(() => {
  if (trackImgError.value || !race.value) return ''
  return race.value.circuitImage ?? ''
})

const tabs = computed(() => {
  const tabList: { key: RTab; label: string }[] = [{ key: 'result', label: t('race.detail.tabs.result') }]
  if (race.value?.qualifyingResult?.length) tabList.push({ key: 'qualifying', label: t('race.detail.tabs.qualifying') })
  if (race.value?.practiceResults?.length) tabList.push({ key: 'practice', label: t('race.detail.tabs.practice') })
  tabList.push({ key: 'drivers', label: t('race.detail.tabs.drivers') }, { key: 'teams', label: t('race.detail.tabs.teams') })
  if (race.value?.completed && race.value.sessionKey) tabList.push({ key: 'replay', label: t('race.detail.tabs.replay') })
  return tabList
})

watch(
  () => store.selectedRaceIndex,
  () => {
    tab.value = 'result'
    trackImgError.value = false
  },
)
</script>

<style scoped>
.race-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
  margin-bottom: 26px;
}

.race-head-left {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 20px;
}

.track-map {
  width: 120px;
  height: 120px;
  object-fit: contain;
  opacity: 0.6;
  pointer-events: none;
  flex-shrink: 0;
}

.round {
  color: var(--accent);
}

.race-title {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 0.98;
  letter-spacing: -0.025em;
  margin: 8px 0 6px;
  color: var(--text);
}

.race-sub {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  color: var(--text-dim);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.country-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.flag {
  width: 22px;
  height: 14px;
  object-fit: cover;
  border-radius: 2px;
  flex-shrink: 0;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.icon-btn {
  background: none;
  border: 1px solid var(--line);
  color: var(--text-dim);
  width: 36px;
  height: 36px;
  border-radius: 8px;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.15s, color 0.15s;
}
.icon-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--text); }
.icon-btn:disabled { opacity: 0.4; cursor: default; }
.icon-btn.spinning { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.back-link {
  background: none;
  border: 1px solid var(--line);
  color: var(--text-dim);
  border-radius: 999px;
  padding: 9px 16px;
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  transition: border-color 0.2s, color 0.2s;
}
.back-link:hover {
  border-color: var(--accent);
  color: var(--text);
}

.empty {
  padding: 60px 0;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

.rtabs {
  display: flex;
  gap: 6px;
  margin-bottom: 24px;
}

.rtab {
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.14em;
  padding: 10px 24px 10px 18px;
  cursor: pointer;
  transition:
    color 0.18s,
    background 0.18s;
}

.rtab:hover {
  color: var(--text-dim);
}

.rtab.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
</style>
