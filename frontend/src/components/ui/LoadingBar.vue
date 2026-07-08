<template>
  <div class="lb-wrap">
    <div class="lb-head">
      <span class="lb-label">{{ label }}</span>
      <span v-if="pct !== undefined" class="lb-pct">{{ Math.round(pct) }}%</span>
    </div>
    <div class="lb-track">
      <div
        class="lb-fill"
        :class="{ indeterminate: pct === undefined }"
        :style="pct !== undefined ? { width: Math.round(pct) + '%' } : {}"
      ></div>
    </div>
    <span v-if="sub" class="lb-sub">{{ sub }}</span>
  </div>
</template>

<script setup lang="ts">
/**
 * Wiederverwendbarer Lade-Fortschrittsbalken.
 * pct: 0–100 → bestimmt Balkenbreite; undefined → indeterminate-Animation (Scan-Loop).
 * sub: optionaler Untertext unter dem Balken (z. B. "4 / 24 Rennen").
 */
defineProps<{
  label: string
  pct?: number
  sub?: string
}>()
</script>

<style scoped>
.lb-wrap {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 40px 0 28px;
  max-width: 360px;
  margin: 0 auto;
}
.lb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.lb-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}
.lb-pct {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 0.04em;
}
.lb-track {
  height: 3px;
  background: var(--line);
  border-radius: 2px;
  overflow: hidden;
}
.lb-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-deep, var(--accent)), var(--accent));
  border-radius: 2px;
  transition: width 0.5s cubic-bezier(0.25, 1, 0.5, 1);
  box-shadow: 0 0 8px color-mix(in srgb, var(--accent) 55%, transparent);
}
.lb-fill.indeterminate {
  width: 38% !important;
  animation: lb-scan 1.6s ease-in-out infinite;
  transition: none;
}
@keyframes lb-scan {
  0%   { transform: translateX(-160%); }
  100% { transform: translateX(390%); }
}
.lb-sub {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  opacity: 0.65;
}
</style>
