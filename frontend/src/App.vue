<template>
  <div class="ambient" aria-hidden="true">
    <div class="rail left">
      <div class="rail-line"></div>
      <div class="rail-text">{{ t('app.tagline') }}</div>
    </div>
    <div class="rail right">
      <div class="rail-line"></div>
      <div class="rail-text">{{ t('app.datafeed') }}</div>
    </div>
    <div class="scan"></div>
  </div>

  <TheHeader />
  <router-view />

  <footer class="app-footer">
    <div class="wrap">
      <span>{{ t('app.footer.tech') }}</span>
      <span>{{ t('app.footer.data') }}</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TheHeader from '@/components/layout/TheHeader.vue'

const { t } = useI18n()
</script>

<style scoped>
.ambient {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}

.rail {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 66px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rail.left {
  left: 0;
}

.rail.right {
  right: 0;
}

.rail-line {
  position: absolute;
  top: 10%;
  bottom: 10%;
  width: 1px;
  background: linear-gradient(180deg, transparent, var(--line), transparent);
}

.rail.left .rail-line {
  left: 32px;
}

.rail.right .rail-line {
  right: 32px;
}

.rail-line::before,
.rail-line::after {
  content: '';
  position: absolute;
  left: -3px;
  width: 7px;
  height: 1px;
  background: var(--text-faint);
  opacity: 0.5;
}

.rail-line::before {
  top: 18%;
}

.rail-line::after {
  bottom: 18%;
}

.rail-text {
  writing-mode: vertical-rl;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.5em;
  color: var(--text-faint);
  opacity: 0.42;
}

.rail.left .rail-text {
  transform: rotate(180deg);
}

.scan {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 220px;
  background: linear-gradient(180deg, rgba(225, 6, 0, 0.05), transparent);
}

.app-footer {
  border-top: 1px solid var(--line);
  margin-top: 40px;
  padding: 26px 0;
  position: relative;
}

.app-footer .wrap {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.app-footer span {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
}

@media (max-width: 1180px) {
  .rail {
    display: none;
  }
}
</style>
