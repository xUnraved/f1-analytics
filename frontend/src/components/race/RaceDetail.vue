<template>
  <div v-if="race">
    <div class="race-head">
      <div class="race-head-left">
        <span class="eyebrow round">Runde {{ String(race.round).padStart(2, '0') }}</span>
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
          <span>{{ race.country }}</span>
          <span>{{ race.date }}</span>
        </div>
      </div>
      <button type="button" class="back-link" @click="emit('back')">← Alle Rennen</button>
    </div>

    <div v-if="!race.result.length" class="empty">Für dieses Rennen liegen noch keine Ergebnisse vor.</div>

    <template v-else>
      <div class="rtabs">
        <button
          v-for="t in tabs"
          :key="t.key"
          type="button"
          class="rtab clip-tab"
          :class="{ active: tab === t.key }"
          @click="tab = t.key"
        >
          {{ t.label }}
        </button>
      </div>

      <OverviewTab v-if="tab === 'result'" />
      <RaceDrivers v-else-if="tab === 'drivers'" :result="race.result" />
      <RaceTeams v-else :result="race.result" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore'
import OverviewTab from './tabs/OverviewTab.vue'
import RaceDrivers from './tabs/RaceDrivers.vue'
import RaceTeams from './tabs/RaceTeams.vue'

type RTab = 'result' | 'drivers' | 'teams'

const emit = defineEmits<{ back: [] }>()
const store = useSeasonStore()
const race = computed(() => store.selectedRace)
const tab = ref<RTab>('result')
const trackImgError = ref(false)

const trackImageUrl = computed(() => {
  if (trackImgError.value || !race.value) return ''
  return race.value.circuitImage ?? ''
})

const tabs: { key: RTab; label: string }[] = [
  { key: 'result', label: 'ERGEBNIS' },
  { key: 'drivers', label: 'FAHRER' },
  { key: 'teams', label: 'TEAMS' },
]

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
  transition:
    border-color 0.2s,
    color 0.2s;
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
