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
    <div class="kerb left"></div>
    <div class="kerb right"></div>
    <div class="checker tl"></div>
    <div class="checker tr"></div>
    <div class="speedline s1"></div>
    <div class="speedline s2"></div>
    <div class="speedline s3"></div>
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
/**
 * Root-Komponente der Anwendung.
 *
 * Enthält:
 *  - .ambient: rein dekoratives Hintergrund-Layer (Schienen, Kerb-Streifen,
 *    Schachbrett-Ecken, Speedlines) – pointer-events: none, aria-hidden.
 *  - TheHeader: globale Navigation.
 *  - <router-view>: Haupt-Seiteninhalt (derzeit nur HomeView).
 *  - app-footer: Techstack- und Datenquellenhinweis.
 */
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

.kerb {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 7px;
  background: repeating-linear-gradient(
    -45deg,
    var(--accent) 0 16px,
    rgba(255, 255, 255, 0.85) 16px 32px
  );
  opacity: 0.14;
  -webkit-mask-image: linear-gradient(180deg, transparent, #000 14%, #000 86%, transparent);
  mask-image: linear-gradient(180deg, transparent, #000 14%, #000 86%, transparent);
}

.kerb.left {
  left: 0;
}

.kerb.right {
  right: 0;
}

.checker {
  position: absolute;
  top: 0;
  width: 260px;
  height: 130px;
  background: repeating-conic-gradient(rgba(255, 255, 255, 0.55) 0% 25%, transparent 0% 50%);
  background-size: 20px 20px;
  opacity: 0.055;
}

.checker.tl {
  left: 0;
  -webkit-mask-image: radial-gradient(120% 120% at 0% 0%, #000 30%, transparent 72%);
  mask-image: radial-gradient(120% 120% at 0% 0%, #000 30%, transparent 72%);
}

.checker.tr {
  right: 0;
  -webkit-mask-image: radial-gradient(120% 120% at 100% 0%, #000 30%, transparent 72%);
  mask-image: radial-gradient(120% 120% at 100% 0%, #000 30%, transparent 72%);
}

.speedline {
  position: absolute;
  height: 1px;
  width: 220px;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--accent) 75%, #fff), transparent);
  opacity: 0;
  animation: sweep 9s linear infinite;
}

.speedline.s1 {
  top: 24%;
  animation-delay: 0s;
}

.speedline.s2 {
  top: 55%;
  width: 150px;
  animation-duration: 12s;
  animation-delay: 4s;
}

.speedline.s3 {
  top: 78%;
  width: 300px;
  animation-duration: 15s;
  animation-delay: 8s;
}

@keyframes sweep {
  0% {
    transform: translateX(-320px);
    opacity: 0;
  }
  6% {
    opacity: 0.35;
  }
  18% {
    opacity: 0.35;
  }
  26% {
    transform: translateX(100vw);
    opacity: 0;
  }
  100% {
    transform: translateX(100vw);
    opacity: 0;
  }
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
  .checker {
    width: 160px;
    height: 90px;
  }
}
</style>
