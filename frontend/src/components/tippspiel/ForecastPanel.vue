<template>
  <section class="card fc-card">
    <div class="fc-head">
      <div>
        <div class="fc-title">
          <span class="fc-brand">F1ALYTICS</span> FORECAST
        </div>
        <div class="fc-sub mono">
          <template v-if="forecast?.nextGp">{{ t('forecast.subNext', { gp: forecast.nextGp, round: forecast.nextRound }) }}</template>
          <template v-else>{{ t('forecast.subSeason', { year: store.year }) }}</template>
          · {{ t('forecast.basedOn', { n: forecast?.completedRaces ?? 0 }) }}
        </div>
      </div>
      <span class="fc-badge mono">{{ t('forecast.badge') }}</span>
    </div>

    <div v-if="loading" class="fc-state mono">{{ t('forecast.calculating') }}</div>
    <div v-else-if="error" class="fc-state mono err">{{ error }}</div>
    <div v-else-if="!rows.length" class="fc-state mono">{{ t('forecast.noData') }}</div>

    <div v-else class="fc-table">
      <div class="fc-row fc-row-head mono">
        <span class="c-rank">#</span>
        <span class="c-driver">{{ t('forecast.colDriver') }}</span>
        <span class="c-win">{{ t('forecast.colWin') }}</span>
        <span class="c-pod">{{ t('forecast.colPodium') }}</span>
        <span class="c-form">{{ t('forecast.colForm') }}</span>
        <span class="c-track">{{ t('forecast.colTrack') }}</span>
        <span class="c-trend">{{ t('forecast.colTrend') }}</span>
      </div>
      <div v-for="(d, i) in rows" :key="d.abbr" class="fc-row" :class="{ leader: i === 0 }">
        <span class="c-rank mono">{{ i + 1 }}</span>
        <span class="c-driver">
          <span class="dot" :style="{ background: d.color || '#888' }"></span>
          <span class="abbr mono">{{ d.abbr }}</span>
          <span class="dname">{{ d.name }}</span>
          <span class="dteam mono">{{ d.team }}</span>
        </span>
        <span class="c-win">
          <span class="bar-track">
            <span class="bar-fill" :style="{ width: pct(d.winProb) + '%', background: d.color || 'var(--accent)' }"></span>
          </span>
          <span class="bar-val mono">{{ pct(d.winProb) }}%</span>
        </span>
        <span class="c-pod mono">{{ pct(d.podiumProb) }}%</span>
        <span class="c-form">
          <span class="form-chip mono" :class="formClass(d.form)">{{ d.form.toFixed(1) }}</span>
        </span>
        <span class="c-track">
          <span class="form-chip mono" :class="d.track != null ? formClass(d.track) : 'mid'">
            {{ d.track != null ? d.track.toFixed(1) : '–' }}
          </span>
          <span v-if="d.track != null" class="track-n mono">({{ d.trackRaces }})</span>
        </span>
        <span class="c-trend" :class="'tr-' + d.trend">
          {{ d.trend === 'up' ? '▲' : d.trend === 'down' ? '▼' : '▬' }}
        </span>
      </div>
    </div>

    <p class="fc-foot mono">
      {{ t('forecast.foot') }}
      <template v-if="forecast?.trackBasis === 'country'"> {{ t('forecast.footCountry') }}</template>
    </p>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import { fetchForecast, type ForecastDto } from '@/services/f1Api'

const { t } = useI18n()
const store = useSeasonStore()

const forecast = ref<ForecastDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const rows = computed(() => (forecast.value?.drivers ?? []).slice(0, 10))

function pct(p: number): number {
  return Math.round(p * 100)
}

function formClass(f: number): string {
  if (f >= 7.5) return 'hot'
  if (f >= 5) return 'mid'
  return 'cold'
}

async function load() {
  loading.value = true
  error.value = null
  try {
    forecast.value = await fetchForecast(store.year)
  } catch {
    error.value = t('forecast.loadError')
    forecast.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => store.year, load)
</script>

<style scoped>
.fc-card {
  padding: 20px 22px 16px;
}
.fc-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.fc-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--text);
}
.fc-brand {
  color: var(--accent);
}
.fc-sub {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  margin-top: 4px;
}
.fc-badge {
  font-size: 9px;
  letter-spacing: 0.2em;
  color: var(--text-faint);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 10px;
  white-space: nowrap;
}
.fc-state {
  padding: 18px 4px;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}
.fc-state.err {
  color: #e10600;
}

.fc-table {
  display: flex;
  flex-direction: column;
}
.fc-row {
  display: grid;
  grid-template-columns: 28px minmax(170px, 1.3fr) minmax(150px, 1.5fr) 62px 58px 80px 42px;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  border-bottom: 1px solid var(--line-soft);
}
.fc-row:last-child {
  border-bottom: none;
}
.fc-row.leader {
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 10%, transparent), transparent 70%);
  border-radius: 8px;
}
.fc-row-head {
  font-size: 9px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-faint);
  border-bottom: 1px solid var(--line);
  padding-bottom: 6px;
}
.c-rank {
  font-size: 11px;
  color: var(--text-faint);
  text-align: center;
}
.c-driver {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}
.abbr {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--text);
}
.dname {
  font-size: 12px;
  color: var(--text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dteam {
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.c-win {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}
.bar-fill {
  display: block;
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}
.bar-val {
  font-size: 11px;
  font-weight: 700;
  color: var(--text);
  min-width: 36px;
  text-align: right;
}
.c-pod {
  font-size: 11px;
  color: var(--text-dim);
  text-align: right;
}
.form-chip {
  display: inline-block;
  min-width: 34px;
  text-align: center;
  font-size: 10px;
  font-weight: 700;
  padding: 3px 6px;
  border-radius: 6px;
  border: 1px solid var(--line);
}
.form-chip.hot {
  color: #22c55e;
  border-color: rgba(34, 197, 94, 0.4);
  background: rgba(34, 197, 94, 0.08);
}
.form-chip.mid {
  color: var(--text-dim);
}
.form-chip.cold {
  color: #f59e0b;
  border-color: rgba(245, 158, 11, 0.35);
  background: rgba(245, 158, 11, 0.06);
}
.c-track {
  display: flex;
  align-items: center;
  gap: 4px;
}
.track-n {
  font-size: 9px;
  color: var(--text-faint);
}
.c-trend {
  text-align: center;
  font-size: 12px;
}
.tr-up {
  color: #22c55e;
}
.tr-down {
  color: #e10600;
}
.tr-flat {
  color: var(--text-faint);
}
.fc-foot {
  margin: 14px 0 0;
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  opacity: 0.7;
}

@media (max-width: 760px) {
  .fc-row {
    grid-template-columns: 24px minmax(120px, 1.4fr) minmax(120px, 1.4fr) 54px;
  }
  .c-form,
  .c-trend {
    display: none;
  }
  .dteam {
    display: none;
  }
}
</style>
