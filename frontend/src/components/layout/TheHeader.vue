<template>
  <header class="header">
    <div class="wrap head-row">
      <div class="brand">
        <div class="brand-mark"></div>
        <div class="brand-text">
          <div class="brand-title">F1 <span>ANALYTICS</span></div>
          <div class="brand-sub">{{ t('header.subtitle') }}</div>
        </div>
      </div>

      <div class="head-right">
        <button
          v-if="nextRace && cd"
          type="button"
          class="next-race mono"
          :class="{ raceday: cd.live }"
          :title="t('header.jumpTo')"
          @click="goToNextRace"
        >
          <span class="nr-label">{{ cd.live ? t('header.raceDay') : t('header.next') }}</span>
          <span class="nr-gp">{{ nextRace.gp }}</span>
          <span v-if="!cd.live" class="nr-cd">
            <template v-if="cd.d > 0">{{ cd.d }}{{ t('header.unitDays') }}</template>{{ pad(cd.h) }}:{{ pad(cd.m) }}:{{ pad(cd.s) }}
          </span>
          <span v-else class="nr-live-flag">🏁</span>
        </button>

        <div class="live"><span class="dot"></span>{{ t('header.live') }}</div>
        <FSelect :model-value="store.year" :options="yearOptions" width="116px" @change="onYear" />
        <button class="refresh-btn" :class="{ spinning: refreshing }" :disabled="refreshing" :title="t('header.refreshSeason')" @click="onRefreshSeason">
          <span class="refresh-icon">⟳</span>
          <span class="refresh-label">{{ t('header.refreshSeason') }}</span>
        </button>
        <button class="locale-btn" :title="locale === 'de' ? 'Switch to English' : 'Zu Deutsch wechseln'" @click="toggleLocale">
          {{ locale === 'de' ? 'EN' : 'DE' }}
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import FSelect from '@/components/ui/FSelect.vue'
import type { Locale } from '@/i18n'

const { t, locale } = useI18n()
const store = useSeasonStore()
const refreshing = ref(false)

const yearOptions = computed(() => store.years.map((y) => ({ value: y, label: String(y) })))

const nowTs = ref(Date.now())
let timer: number | undefined

onMounted(() => {
  timer = window.setInterval(() => {
    nowTs.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer)
})

const nextRace = computed(() => store.races.find((r) => !r.completed) ?? null)

const raceTs = computed<number | null>(() => {
  const r = nextRace.value
  if (!r) return null
  const withStart = r as unknown as { sessionDateStart?: string }
  if (withStart.sessionDateStart) {
    const ts = Date.parse(withStart.sessionDateStart)
    if (!isNaN(ts)) return ts
  }
  if (r.date) {
    const ts = Date.parse(r.date + 'T14:00:00')
    if (!isNaN(ts)) return ts
  }
  return null
})

const cd = computed(() => {
  if (raceTs.value == null) return null
  let diff = raceTs.value - nowTs.value
  if (diff <= 0) return { live: true, d: 0, h: 0, m: 0, s: 0 }
  const d = Math.floor(diff / 86_400_000)
  diff -= d * 86_400_000
  const h = Math.floor(diff / 3_600_000)
  diff -= h * 3_600_000
  const m = Math.floor(diff / 60_000)
  diff -= m * 60_000
  const s = Math.floor(diff / 1_000)
  return { live: false, d, h, m, s }
})

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function goToNextRace() {
  const i = store.races.findIndex((r) => !r.completed)
  if (i < 0) return
  store.openRace(i)
  window.setTimeout(() => {
    document.querySelector('.stabs')?.scrollIntoView({ behavior: 'smooth' })
  }, 60)
}

function onYear(v: number | string) {
  store.selectYear(Number(v))
}

async function onRefreshSeason() {
  refreshing.value = true
  try { await store.refreshSeason() } finally { refreshing.value = false }
}

function toggleLocale() {
  const next: Locale = locale.value === 'de' ? 'en' : 'de'
  locale.value = next
  localStorage.setItem('f1alytics.locale', next)
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(14px);
  background: color-mix(in srgb, var(--bg) 80%, transparent);
  border-bottom: 1px solid var(--line);
}

.header::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent 38%);
  opacity: 0.7;
}

.head-row {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  position: relative;
  overflow: hidden;
  clip-path: polygon(0 0, 100% 0, 78% 100%, 0 100%);
}

.brand-mark::after {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(118deg, rgba(255, 255, 255, 0.32) 0 2px, transparent 2px 6px);
}

.brand-title {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 19px;
  letter-spacing: 0.04em;
  color: var(--text);
}

.brand-title span {
  color: var(--accent);
}

.brand-sub {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.34em;
  color: var(--text-faint);
  margin-top: 2px;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.next-race {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--line));
  border-radius: 10px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 10%, transparent), color-mix(in srgb, var(--surface) 70%, transparent));
  max-width: 480px;
  min-width: 0;
  cursor: pointer;
  transition: border-color var(--dur-fast, 0.15s) ease, box-shadow var(--dur-fast, 0.15s) ease, transform var(--dur-fast, 0.15s) ease;
}

.next-race:hover {
  border-color: var(--accent);
  box-shadow: 0 0 18px -6px color-mix(in srgb, var(--accent) 60%, transparent);
  transform: translateY(-1px);
}

.next-race.raceday {
  border-color: var(--accent);
  animation: raceday-pulse 1.8s infinite;
}

@keyframes raceday-pulse {
  0% { box-shadow: 0 0 0 0 rgba(255, 30, 30, 0.4); }
  70% { box-shadow: 0 0 0 9px rgba(255, 30, 30, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 30, 30, 0); }
}

.nr-label {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: var(--ls-3, 0.22em);
  color: var(--accent);
  white-space: nowrap;
}

.nr-gp {
  font-size: 11px;
  letter-spacing: 0.05em;
  color: var(--text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nr-cd {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--text);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.nr-live-flag {
  font-size: 15px;
}

.refresh-btn,
.locale-btn {
  background: none;
  border: 1px solid var(--line);
  color: var(--text-dim);
  height: 34px;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 10px;
}
.refresh-btn:hover:not(:disabled),
.locale-btn:hover { border-color: var(--accent); color: var(--text); }
.refresh-btn:disabled { opacity: 0.4; cursor: default; }

.refresh-icon {
  font-size: 16px;
  line-height: 1;
  display: inline-block;
}
.refresh-btn.spinning .refresh-icon { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.refresh-label {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.14em;
  white-space: nowrap;
}

.locale-btn {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.live {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--text-dim);
}

.live .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.8s infinite;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(255, 30, 30, 0.55); }
  70% { box-shadow: 0 0 0 7px rgba(255, 30, 30, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 30, 30, 0); }
}

@media (max-width: 1180px) {
  .nr-gp {
    display: none;
  }
  .next-race {
    max-width: 190px;
  }
}

@media (max-aspect-ratio: 13/16) {
  .brand-sub,
  .live,
  .next-race { display: none; }
}
</style>
