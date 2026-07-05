<template>
  <div class="standings-wrap">
    <div v-if="store.loading" class="state-loading">
      <LoadingBar
        :label="store.totalRaces > 0 ? t('home.loading.races', { loaded: store.races.length, total: store.totalRaces }) : t('standings.loading')"
        :pct="store.totalRaces > 0 ? Math.round(store.races.length / store.totalRaces * 100) : undefined"
        :sub="store.totalRaces > 0 ? `${store.races.length} / ${store.totalRaces}` : undefined"
      />
    </div>

    <template v-else-if="store.drivers.length">
      <PointsChart />

      <div class="dual">
        <div class="block">
          <div class="block-head">
            <span class="eyebrow">{{ t('standings.drivers') }}</span>
            <span class="mono season-label">{{ t('standings.season') }} {{ store.year }}</span>
          </div>
          <DriverChampionship />
        </div>

        <div class="block">
          <div class="block-head">
            <span class="eyebrow">{{ t('standings.constructors') }}</span>
            <span class="mono season-label">{{ t('standings.season') }} {{ store.year }}</span>
          </div>
          <ConstructorChampionship />
        </div>
      </div>
    </template>

    <div v-else class="state-msg">{{ t('standings.noData') }}</div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import LoadingBar from '@/components/ui/LoadingBar.vue'
import PointsChart from './PointsChart.vue'
import DriverChampionship from './DriverChampionship.vue'
import ConstructorChampionship from './ConstructorChampionship.vue'

const { t } = useI18n()
const store = useSeasonStore()
</script>

<style scoped>
.standings-wrap {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28px;
  align-items: start;
}

.block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.season-label {
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--accent);
}

.state-loading {
  padding: 24px 0 12px;
}

@media (max-width: 900px) {
  .dual {
    grid-template-columns: 1fr;
  }
}
</style>
