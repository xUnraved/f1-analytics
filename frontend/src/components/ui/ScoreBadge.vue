<template>
  <span v-if="score !== null" class="score-badge" :class="band" :aria-label="aria">
    <span class="val">{{ score.toFixed(1) }}</span><span v-if="card?.dnf" class="star">*</span>

    <span v-if="card" class="breakdown" role="tooltip">
      <span class="bd-head">
        <b>F1alytics</b>
        <span class="bd-total">{{ card.score.toFixed(1) }}<small>/10</small></span>
      </span>
      <span class="bd-row"><span>{{ t('score.qualifying') }}</span><span>{{ card.q.toFixed(1) }}</span></span>
      <span class="bd-row"><span>{{ t('score.result') }}</span><span>{{ card.r.toFixed(1) }}</span></span>
      <span class="bd-row"><span>{{ t('score.positions') }}</span><span>{{ card.delta.toFixed(1) }}</span></span>
      <span class="bd-row muted"><span>{{ t('score.base') }}</span><span>{{ card.base.toFixed(1) }}</span></span>
      <span v-if="card.modifiers" class="bd-row"
      ><span>{{ t('score.modifiers') }}</span><span>+{{ card.modifiers.toFixed(1) }}</span></span
      >
      <span v-if="card.note" class="bd-note">{{ card.note }}</span>
    </span>
  </span>
  <span v-else class="score-badge band-na">—</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ScoreCard } from '@/types/f1'

const { t } = useI18n()

const props = defineProps<{

  card?: ScoreCard | null

  value?: number | null
}>()

const score = computed<number | null>(() => props.card?.score ?? props.value ?? null)

const band = computed(() => {
  const s = score.value
  if (s === null) return 'band-na'
  if (s >= 8.5) return 'band-elite'
  if (s >= 7.0) return 'band-strong'
  if (s >= 5.5) return 'band-par'
  if (s >= 4.0) return 'band-weak'
  return 'band-poor'
})

const aria = computed(() =>
  score.value === null ? t('score.noScore') : `F1alytics Score ${score.value.toFixed(1)} ${t('score.of10')}`,
)
</script>

<style scoped>
.score-badge {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  height: 26px;
  padding: 0 8px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 0.82rem;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.02em;
  border: 1px solid transparent;
  cursor: default;
}

.val {
  font-variant-numeric: tabular-nums;
}

.star {
  margin-left: 1px;
  opacity: 0.7;
}

.band-elite {
  background: rgba(34, 197, 94, 0.16);
  color: #4ade80;
  border-color: rgba(34, 197, 94, 0.4);
}
.band-strong {
  background: rgba(132, 204, 22, 0.13);
  color: #a3e635;
  border-color: rgba(132, 204, 22, 0.32);
}
.band-par {
  background: rgba(96, 165, 250, 0.12);
  color: #93c5fd;
  border-color: rgba(96, 165, 250, 0.3);
}
.band-weak {
  background: rgba(245, 158, 11, 0.13);
  color: #fbbf24;
  border-color: rgba(245, 158, 11, 0.32);
}
.band-poor {
  background: rgba(225, 6, 0, 0.14);
  color: #f87171;
  border-color: rgba(225, 6, 0, 0.38);
}
.band-na {
  background: var(--surface-2);
  color: var(--text-faint);
}

.breakdown {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  z-index: 60;
  min-width: 168px;
  padding: 10px 12px;
  background: var(--surface-2, #1c232e);
  border: 1px solid var(--line, #28303c);
  border-radius: 10px;
  box-shadow: 0 14px 34px -16px rgba(0, 0, 0, 0.9);
  display: flex;
  flex-direction: column;
  gap: 5px;
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.score-badge:hover .breakdown {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

.breakdown::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 6px solid transparent;
  border-top-color: var(--line, #28303c);
}

.bd-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 4px;
  margin-bottom: 2px;
  border-bottom: 1px solid var(--line, #28303c);
}

.bd-head b {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.16em;
  color: var(--text-faint);
}

.bd-total {
  font-family: var(--font-display, sans-serif);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text, #eef1f6);
}

.bd-total small {
  font-size: 0.7rem;
  color: var(--text-faint);
  font-weight: 400;
}

.bd-row {
  display: flex;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-size: 0.74rem;
  color: var(--text-dim, #aeb8c6);
}

.bd-row.muted {
  color: var(--text-faint, #6c7686);
}

.bd-row span:last-child {
  font-variant-numeric: tabular-nums;
}

.bd-note {
  margin-top: 3px;
  font-size: 0.68rem;
  color: var(--text-faint, #6c7686);
  line-height: 1.3;
}
</style>
