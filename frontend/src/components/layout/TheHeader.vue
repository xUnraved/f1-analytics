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
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import FSelect from '@/components/ui/FSelect.vue'
import type { Locale } from '@/i18n'

const { t, locale } = useI18n()
const store = useSeasonStore()
const refreshing = ref(false)

const yearOptions = computed(() => store.years.map((y) => ({ value: y, label: String(y) })))

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

@media (max-aspect-ratio: 13/16) {
  .brand-sub,
  .live { display: none; }
}
</style>
