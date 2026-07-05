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
      @click="select(i)"
    >
      <span class="date">{{ race.date }}</span>
      <div class="rnd">{{ t('race.grid.round') }} {{ String(race.round).padStart(2, '0') }}</div>
      <div class="gp">{{ race.gp }}</div>
      <div class="ct">
        {{ race.circuit }} ·
        <img v-if="race.countryFlag" :src="race.countryFlag" :alt="race.country" class="flag" />
        {{ race.country }}
      </div>
      <div v-if="race.result.length" class="win">
        <i :style="{ background: race.result[0]!.color }"></i>
        {{ t('race.grid.winner') }}{{ race.result[0]!.name }}
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
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    transform 0.2s;
  position: relative;
  overflow: hidden;
}

.tile:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  transform: translateY(-2px);
}

.tile .date {
  position: absolute;
  right: 14px;
  top: 15px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
}

.tile .rnd {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
}

.tile .gp {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 19px;
  margin-top: 8px;
  letter-spacing: -0.01em;
  color: var(--text);
}

.tile .ct {
  display: flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 3px;
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
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-dim);
}

.tile .win i {
  width: 3px;
  height: 13px;
  border-radius: 1px;
}

@media (max-aspect-ratio: 13/16) {
  .tiles {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
</style>s
