<template>
  <div class="wheel-wrap">
    <div ref="wheelEl" class="wheel" @scroll="onScroll">
      <button
        v-for="(y, i) in store.years"
        :key="y"
        type="button"
        class="wheel-item"
        @click="scrollToIndex(i, true)"
      >
        <span class="num">{{ y }}</span>
      </button>
    </div>
    <div class="wheel-band">
      <span class="chev left">‹</span>
      <span class="chev right">›</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Saison-Auswahlrad mit 3D-Rotations-Effekt.
 *
 * Rendert die verfügbaren Jahre als scroll-snappende Liste mit perspektivischer
 * rotateX-Transformation, so dass nicht-fokussierte Items wie auf einem Rad
 * nach hinten gekippt erscheinen. Nach 130 ms Scroll-Inaktivität wird das
 * nächste Item eingerastet und selectYear() aufgerufen.
 * suppressSettle verhindert, dass programmatisches Scrollen (centerOnYear)
 * denselben Settle-Timer auslöst.
 */
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useSeasonStore } from '@/stores/seasonStore'

const store = useSeasonStore()
const wheelEl = ref<HTMLElement | null>(null)

let itemH = 64
let halfH = 168
let settleTimer: ReturnType<typeof setTimeout> | undefined
let suppressSettle = false

function items(): HTMLElement[] {
  return Array.from(wheelEl.value?.children ?? []) as HTMLElement[]
}

function measure() {
  const wheel = wheelEl.value
  const first = items()[0]
  if (!wheel || !first) return
  itemH = first.offsetHeight || 64
  halfH = wheel.clientHeight / 2
  const pad = Math.max(0, halfH - itemH / 2)
  wheel.style.paddingTop = `${pad}px`
  wheel.style.paddingBottom = `${pad}px`
}

function updateTransforms() {
  const wheel = wheelEl.value
  if (!wheel) return
  const st = wheel.scrollTop
  items().forEach((el, i) => {
    const d = i * itemH - st
    const norm = d / halfH
    const ang = Math.max(-1, Math.min(1, norm)) * 58
    el.style.transform = `rotateX(${-ang}deg) scale(${Math.max(0.45, 1 - Math.abs(norm) * 0.3)})`
    el.style.opacity = String(Math.max(0.12, 1 - Math.abs(norm) * 0.85))
  })
}

function centerOnYear(smooth = false) {
  const wheel = wheelEl.value
  if (!wheel) return
  const idx = store.years.indexOf(store.year)
  if (idx < 0) return
  suppressSettle = true
  wheel.scrollTo({ top: idx * itemH, behavior: smooth ? 'smooth' : 'auto' })
  window.setTimeout(
    () => {
      suppressSettle = false
      updateTransforms()
    },
    smooth ? 360 : 0,
  )
}

function onScroll() {
  requestAnimationFrame(updateTransforms)
  if (suppressSettle) return
  clearTimeout(settleTimer)
  settleTimer = setTimeout(() => {
    const wheel = wheelEl.value
    if (!wheel) return
    const i = Math.max(0, Math.min(store.years.length - 1, Math.round(wheel.scrollTop / itemH)))
    const y = store.years[i]
    if (y !== undefined && y !== store.year) store.selectYear(y)
  }, 130)
}

function scrollToIndex(i: number, smooth: boolean) {
  wheelEl.value?.scrollTo({ top: i * itemH, behavior: smooth ? 'smooth' : 'auto' })
}

function onResize() {
  measure()
  centerOnYear(false)
}

onMounted(async () => {
  await nextTick()
  measure()
  centerOnYear(false)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  clearTimeout(settleTimer)
})

watch(
  () => store.years,
  async () => {
    await nextTick()
    measure()
    centerOnYear(false)
  },
)

watch(
  () => store.year,
  async () => {
    await nextTick()
    centerOnYear(true)
  },
)
</script>

<style scoped>
.wheel-wrap {
  position: relative;
  height: 300px;
  -webkit-mask-image: linear-gradient(180deg, transparent, #000 24%, #000 76%, transparent);
  mask-image: linear-gradient(180deg, transparent, #000 24%, #000 76%, transparent);
}

.wheel {
  height: 100%;
  overflow-y: scroll;
  scroll-snap-type: y mandatory;
  perspective: 760px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.wheel::-webkit-scrollbar {
  display: none;
}

.wheel-item {
  height: 64px;
  width: 100%;
  scroll-snap-align: center;
  display: flex;
  align-items: center;
  cursor: pointer;
  background: none;
  border: none;
  padding: 0 0 0 26px;
  text-align: left;
  transform-origin: center center;
  backface-visibility: hidden;
  will-change: transform, opacity;
}

.wheel-item .num {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 50px;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--text);
}

.wheel-band {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 64px;
  transform: translateY(-50%);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 9%, transparent), transparent 60%);
  pointer-events: none;
}

.wheel-band::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  background: var(--accent);
  box-shadow: 0 0 12px var(--accent);
}

.chev {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 22px;
  line-height: 1;
  color: color-mix(in srgb, var(--accent) 75%, var(--text-faint));
}

.chev.left {
  left: 9px;
  opacity: 0;
}

.chev.right {
  right: 10px;
}

@media (max-aspect-ratio: 13/16) {
  .wheel-wrap {
    height: 190px;
  }
  .wheel-item .num {
    font-size: 38px;
  }
}
</style>
