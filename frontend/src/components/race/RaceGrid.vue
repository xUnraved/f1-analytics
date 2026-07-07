<template>
  <div class="grid-head">
    <div class="eyebrow">{{ t('race.grid.title') }} · {{ store.year }}</div>
    <div class="mono count">{{ store.races.length }} {{ t('race.grid.countSuffix') }}</div>
  </div>

  <div v-if="store.error" class="state">{{ store.error }}</div>
  <div v-else-if="store.loading && store.races.length === 0" class="state-loading">
    <LoadingBar
      :label="t('home.loading.sessions')"
    />
  </div>

  <div v-else class="tiles">
    <button
      v-for="(race, i) in store.races"
      :key="race.round"
      type="button"
      class="tile"
      :class="{ done: race.result.length > 0 }"
      @click="select(i)"
    >
      <div class="tile-top mono">
        <span class="rnd">{{ t('race.grid.round') }} {{ String(race.round).padStart(2, '0') }}</span>
        <span class="date">{{ race.date }}</span>
      </div>
      <div class="gp" :title="race.gp">{{ race.gp }}</div>
      <div class="ct">
        <span class="circuit" :title="race.circuit">{{ race.circuit }}</span>
        <span class="dot">·</span>
        <span class="country-chip" :title="race.country">
          <img v-if="race.countryFlag" :src="race.countryFlag" :alt="race.country" class="flag" />
          <span class="country-name">{{ race.country }}</span>
        </span>
      </div>
      <div v-if="race.result.length" class="win">
        <i :style="{ background: race.result[0]!.color }"></i>
        <span class="win-name">{{ t('race.grid.winner') }}{{ race.result[0]!.name }}</span>
      </div>
      <div v-else class="win pending">
        <i></i>
        <span class="win-name">
          {{ daysUntil(race.date) > 0 ? t('race.grid.upcoming', { days: daysUntil(race.date) }) : daysUntil(race.date) === 0 ? t('race.grid.today') : t('race.grid.pending') }}
        </span>
      </div>
    </button>
  </div>

  <div v-if="store.loading && store.races.length > 0" class="loading-more">
    <LoadingBar
      :label="store.totalRaces > 0 ? t('home.loading.races', { loaded: store.races.length, total: store.totalRaces }) : t('race.grid.moreLoading')"
      :pct="store.totalRaces > 0 ? Math.round(store.races.length / store.totalRaces * 100) : undefined"
    />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import LoadingBar from '@/components/ui/LoadingBar.vue'

const { t } = useI18n()
const emit = defineEmits<{ select: [index: number] }>()
const store = useSeasonStore()

function select(i: number) {
  store.openRace(i)
  emit('select', i)
}

function daysUntil(iso: string): number {
  if (!iso) return -1
  const d = new Date(iso + 'T00:00:00').getTime()
  if (isNaN(d)) return -1
  const now = new Date().setHours(0, 0, 0, 0)
  return Math.round((d - now) / 86400000)
}
</script>

<style scoped>
.grid-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 20px;
}

.count {
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.1em;
}

.state-loading {
  padding: 8px 0 24px;
}

.loading-more {
  margin-top: 16px;
}

.state {
  padding: 60px 0;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px;
}

.tile {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  text-align: left;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.024), transparent 34%),
    var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 15px 16px 14px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    transform 0.2s,
    box-shadow 0.2s;
  position: relative;
  overflow: hidden;
  min-height: 132px;
}

.tile::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--accent);
  opacity: 0;
  transition: opacity 0.2s;
}

.tile:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  transform: translateY(-2px);
  box-shadow: 0 14px 26px -18px rgba(0, 0, 0, 0.9);
}

.tile:hover::before {
  opacity: 1;
}

.tile-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
}

.tile .rnd {
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
  white-space: nowrap;
}

.tile .date {
  font-size: 10px;
  color: var(--text-faint);
  white-space: nowrap;
}

.tile .gp {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 19px;
  margin-top: 8px;
  letter-spacing: -0.01em;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tile .ct {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 4px;
  min-width: 0;
}

.circuit {
  flex: 0 1 auto;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dot {
  flex: none;
  color: var(--text-faint);
}

.country-chip {
  flex: 0 1 auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.country-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

.flag {
  width: 20px;
  height: 13px;
  object-fit: cover;
  border-radius: 2px;
  flex-shrink: 0;
}

.tile .win {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-dim);
  min-width: 0;
}

.tile .win i {
  width: 3px;
  height: 13px;
  border-radius: 1px;
  flex-shrink: 0;
}

.win-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tile .win.pending {
  color: var(--text-faint);
}

.tile .win.pending i {
  background: var(--line);
}

.tile .ct + .win {
  margin-top: auto;
}

@media (max-aspect-ratio: 13/16) {
  .tiles {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
  .country-name {
    max-width: 84px;
  }
}
</style>
