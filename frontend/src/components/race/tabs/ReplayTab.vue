<template>
  <div class="replay-wrap">
    <div v-if="state === 'loading'" class="replay-state">
      <LoadingBar :label="t('race.replay.loading')" />
    </div>

    <div v-else-if="state === 'error'" class="replay-state">
      <span>{{ t('race.replay.error') }}</span>
      <button class="retry-btn" :disabled="refreshingReplay" @click="onRefreshReplay">
        {{ refreshingReplay ? '…' : t('race.replay.retry') }}
      </button>
    </div>

    <div v-else-if="state === 'empty'" class="replay-state">
      <span>{{ t('race.replay.noData') }}</span>
      <button class="retry-btn" :disabled="refreshingReplay" @click="onRefreshReplay">
        {{ refreshingReplay ? '…' : t('race.replay.retry') }}
      </button>
    </div>

    <template v-else>
      <div class="canvas-wrap">
        <canvas ref="canvasEl" class="track-canvas" />

        <div class="legend">
          <div v-for="d in data!.drivers" :key="d.num" class="legend-item">
            <span class="legend-dot" :style="{ background: d.color }" />
            <span class="legend-abbr">{{ d.abbr }}</span>
          </div>
        </div>

        <div class="time-badge">{{ formatTime(currentSec) }}</div>
      </div>

      <div class="controls">
        <button class="ctrl-btn" @click="togglePlay">
          {{ playing ? '⏸' : '▶' }}
        </button>
        <button
          class="ctrl-btn refresh"
          :class="{ spinning: refreshingReplay }"
          :disabled="refreshingReplay"
          :title="t('race.replay.refresh')"
          @click="onRefreshReplay"
        >⟳</button>

        <input
          type="range"
          class="scrubber"
          :min="0"
          :max="data!.duration"
          :value="currentSec"
          @input="onScrub"
        />

        <div class="speed-btns">
          <button
            v-for="s in [1, 2, 4, 8]"
            :key="s"
            class="speed-btn"
            :class="{ active: speed === s }"
            @click="speed = s"
          >{{ s }}×</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import LoadingBar from '@/components/ui/LoadingBar.vue'
import { fetchReplay, refreshReplay } from '@/services/f1Api'
import type { ReplayData } from '@/types/f1'

const { t } = useI18n()

const props = defineProps<{
  sessionKey: number
  dateStart: string
  circuitImage?: string | null
}>()

type State = 'loading' | 'ready' | 'error' | 'empty'

const state    = ref<State>('loading')
const data     = ref<ReplayData | null>(null)
const canvasEl = ref<HTMLCanvasElement>()
const playing  = ref(false)
const speed    = ref(1)
const currentSec = ref(0)
const refreshingReplay = ref(false)

// GPS-Koordinaten → Canvas-Mapping
let minX = 0, maxX = 1, minY = 0, maxY = 1

// Animations-State
let rafId = 0
let lastTs = 0

const colorMap = new Map<number, string>()
const abbrMap  = new Map<number, string>()

// Streckenbild + berechnete Ausrichtung
const circuitImg = ref<HTMLImageElement | null>(null)

// Ergebnis der PCA-Ausrichtungsberechnung
interface AlignTransform {
  gpsCx: number; gpsCy: number  // GPS-Schwerpunkt im Canvas
  imgCx: number; imgCy: number  // Bild-Schwerpunkt im Canvas-Raum
  rotation: number              // Winkel (GPS.angle - Bild.angle)
  scale: number                 // GPS.spread / Bild.spread
  valid: boolean
}
let alignTransform: AlignTransform | null = null

// Streckenkontur-Pfade für Overlay
let trackPaths: [number, number][][] = []

// ---- Streckenbild laden ----

watch(() => props.circuitImage, (url) => {
  circuitImg.value = null
  alignTransform = null
  if (!url) return
  const img = new Image()
  img.onload = () => {
    circuitImg.value = img
    // Wenn Daten schon da sind, Ausrichtung sofort berechnen
    const canvas = canvasEl.value
    if (data.value && canvas) {
      alignTransform = computeAlignment(img, data.value, canvas.width, canvas.height)
      drawAtTime(currentSec.value)
    }
  }
  img.src = url
}, { immediate: true })

// ---- Laden ----

async function load() {
  playing.value = false
  cancelAnimationFrame(rafId)
  currentSec.value = 0
  trackPaths = []
  alignTransform = null
  colorMap.clear()
  abbrMap.clear()
  data.value = null
  state.value = 'loading'

  try {
    const d = await fetchReplay(props.sessionKey, props.dateStart)
    if (!d.frames.length) { state.value = 'empty'; return }
    data.value = d
    for (const dr of d.drivers) {
      colorMap.set(dr.num, dr.color)
      abbrMap.set(dr.num, dr.abbr)
    }
    computeBounds(d)
    state.value = 'ready'
    await nextTick()
    resizeCanvas()
  } catch {
    state.value = 'error'
  }
}

async function onRefreshReplay() {
  refreshingReplay.value = true
  try { await refreshReplay(props.sessionKey); await load() }
  finally { refreshingReplay.value = false }
}

// ---- GPS-Koordinaten ----

function computeBounds(d: ReplayData) {
  let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity
  for (const f of d.frames) {
    for (const pos of Object.values(f.p)) {
      if (pos[0] < x0) x0 = pos[0]; if (pos[0] > x1) x1 = pos[0]
      if (pos[1] < y0) y0 = pos[1]; if (pos[1] > y1) y1 = pos[1]
    }
  }
  const pad = 80
  minX = x0 - pad; maxX = x1 + pad
  minY = y0 - pad; maxY = y1 + pad
}

function toCanvas(x: number, y: number, W: number, H: number): [number, number] {
  return [
    ((x - minX) / (maxX - minX)) * W,
    H - ((y - minY) / (maxY - minY)) * H,
  ]
}

// ---- PCA-Hilfsfunktionen ----

interface PcaResult { cx: number; cy: number; angle: number; spread: number }

function pca(pts: [number, number][]): PcaResult {
  if (pts.length < 10) return { cx: 0, cy: 0, angle: 0, spread: 1 }

  let cx = 0, cy = 0
  for (const p of pts) { cx += p[0]; cy += p[1] }
  cx /= pts.length; cy /= pts.length

  let cxx = 0, cxy = 0, cyy = 0
  for (const p of pts) {
    const dx = p[0] - cx, dy = p[1] - cy
    cxx += dx * dx; cxy += dx * dy; cyy += dy * dy
  }
  cxx /= pts.length; cxy /= pts.length; cyy /= pts.length

  const tr   = cxx + cyy
  const disc = Math.sqrt(Math.max(0, ((cxx - cyy) * (cxx - cyy)) * 0.25 + cxy * cxy))
  const l1   = tr * 0.5 + disc

  return {
    cx,
    cy,
    angle:  Math.atan2(l1 - cxx, cxy),
    spread: Math.sqrt(l1),
  }
}

/** Analysiert das Streckenbild bei kleiner Auflösung, findet die farbigen Pixel der Streckenlinie. */
function analyzeImage(img: HTMLImageElement): PcaResult {
  // Kleine Auflösung für Geschwindigkeit; Textur/Text werden weichgezeichnet
  const AW = 160, AH = 100
  const oc  = document.createElement('canvas')
  oc.width  = AW; oc.height = AH
  const ctx = oc.getContext('2d')!
  ctx.drawImage(img, 0, 0, AW, AH)
  const d = ctx.getImageData(0, 0, AW, AH).data

  const pts: [number, number][] = []
  for (let y = 0; y < AH; y++) {
    for (let x = 0; x < AW; x++) {
      const i = (y * AW + x) * 4
      const a = d[i + 3]!
      if (a < 40) continue                                    // transparent → kein Track
      const luma = (d[i]! * 299 + d[i + 1]! * 587 + d[i + 2]! * 114) / 1000
      if (luma > 205) continue                                // zu hell (Hintergrund) → kein Track
      pts.push([x / AW, y / AH])                             // normiert auf [0,1]
    }
  }
  return pca(pts)
}

/** Analysiert die GPS-Positionen der ersten ~6 Minuten, gibt Schwerpunkt + Hauptachse zurück. */
function analyzeGps(d: ReplayData, W: number, H: number): PcaResult {
  const pts: [number, number][] = []
  for (const f of d.frames) {
    if (f.t > 360) break
    for (const pos of Object.values(f.p)) {
      pts.push(toCanvas(pos[0], pos[1], W, H))
    }
  }
  return pca(pts)
}

/**
 * Berechnet die Transformation (Rotation + Skalierung + Translation),
 * die das Streckenbild so ausrichtet, dass die Bildpixel mit den GPS-Punkten übereinstimmen.
 */
function computeAlignment(img: HTMLImageElement, d: ReplayData, W: number, H: number): AlignTransform {
  const gps = analyzeGps(d, W, H)
  const im  = analyzeImage(img)

  if (im.spread < 0.01 || gps.spread < 1) {
    return { gpsCx: W / 2, gpsCy: H / 2, imgCx: W / 2, imgCy: H / 2, rotation: 0, scale: 1, valid: false }
  }

  // Bildkoordinaten (normiert 0–1) in Canvas-Pixel umrechnen
  const imgCx = im.cx * W
  const imgCy = im.cy * H
  // Bildspread ist in normierter Einheit → in Canvas-Pixel umrechnen (geometrisches Mittel der Seiten)
  const imgSpread = im.spread * Math.sqrt(W * H)

  return {
    gpsCx:    gps.cx,
    gpsCy:    gps.cy,
    imgCx,
    imgCy,
    rotation: gps.angle - im.angle,
    scale:    gps.spread / imgSpread,
    valid:    true,
  }
}

// ---- Track-Pfade aufbauen ----

function buildTrackPaths(d: ReplayData, W: number, H: number) {
  trackPaths = []
  for (const driver of d.drivers) {
    const pts: [number, number][] = []
    for (const f of d.frames) {
      if (f.t > 360) break
      const pos = f.p[driver.num]
      if (pos) pts.push(toCanvas(pos[0], pos[1], W, H))
    }
    if (pts.length > 5) trackPaths.push(pts)
  }
}

// ---- Interpoliertes Zeichnen ----

function drawAtTime(sec: number) {
  const canvas = canvasEl.value
  if (!canvas || !data.value) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const W = canvas.width, H = canvas.height

  // Umgebende Frames für lineare Interpolation
  const frames = data.value.frames
  let lo = 0, hi = frames.length - 1
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (frames[mid]!.t <= sec) lo = mid; else hi = mid - 1
  }
  const frameA = frames[lo]!
  const frameB = frames[lo + 1] ?? null
  const frac   = frameB
    ? Math.max(0, Math.min(1, (sec - frameA.t) / (frameB.t - frameA.t)))
    : 0

  // Hintergrund
  ctx.clearRect(0, 0, W, H)
  ctx.fillStyle = '#0a0a0f'
  ctx.fillRect(0, 0, W, H)

  // Streckenbild – ausgerichtet per PCA-Transform
  const img = circuitImg.value
  if (img && img.naturalWidth > 0) {
    const t = alignTransform
    if (t?.valid) {
      ctx.save()
      ctx.translate(t.gpsCx, t.gpsCy)
      ctx.rotate(t.rotation)
      ctx.scale(t.scale, t.scale)
      ctx.translate(-t.imgCx, -t.imgCy)
      ctx.globalAlpha = 0.40
      ctx.drawImage(img, 0, 0, W, H)
      ctx.globalAlpha = 1
      ctx.restore()
    } else {
      // Fallback: gestreckt über den ganzen Canvas
      ctx.globalAlpha = 0.25
      ctx.drawImage(img, 0, 0, W, H)
      ctx.globalAlpha = 1
    }
  }

  // GPS-Streckenkontur als dünnes Overlay
  if (trackPaths.length > 0) {
    ctx.beginPath()
    for (const path of trackPaths) {
      if (path.length < 2) continue
      ctx.moveTo(path[0]![0], path[0]![1])
      for (let i = 1; i < path.length; i++) ctx.lineTo(path[i]![0], path[i]![1])
    }
    ctx.strokeStyle = 'rgba(255,255,255,0.12)'
    ctx.lineWidth   = 1.5
    ctx.lineCap     = 'round'
    ctx.lineJoin    = 'round'
    ctx.stroke()
  }

  // Fahrerpunkte mit interpolierten Positionen
  const R = 11
  for (const [numStr, posA] of Object.entries(frameA.p)) {
    const num = parseInt(numStr)

    let x = posA[0], y = posA[1]
    if (frameB && frac > 0) {
      const posB = frameB.p[numStr]
      if (posB) {
        x = posA[0] + (posB[0] - posA[0]) * frac
        y = posA[1] + (posB[1] - posA[1]) * frac
      }
    }

    const [cx, cy] = toCanvas(x, y, W, H)
    const color = colorMap.get(num) ?? '#888'
    const abbr  = abbrMap.get(num)  ?? `#${num}`

    ctx.shadowColor = color
    ctx.shadowBlur  = 8
    ctx.beginPath()
    ctx.arc(cx, cy, R, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.shadowBlur = 0

    ctx.strokeStyle = 'rgba(255,255,255,0.7)'
    ctx.lineWidth   = 1.5
    ctx.stroke()

    ctx.fillStyle    = '#fff'
    ctx.font         = 'bold 7px monospace'
    ctx.textAlign    = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(abbr, cx, cy)
  }
}

// ---- Animations-Loop ----

function animate(ts: number) {
  if (!playing.value || !data.value) return
  const delta = lastTs ? (ts - lastTs) / 1000 : 0
  lastTs = ts

  currentSec.value = Math.min(
    currentSec.value + delta * speed.value,
    data.value.duration,
  )

  if (currentSec.value >= data.value.duration) {
    playing.value = false
    return
  }

  drawAtTime(currentSec.value)
  rafId = requestAnimationFrame(animate)
}

function togglePlay() {
  if (!data.value) return
  if (currentSec.value >= data.value.duration) currentSec.value = 0
  playing.value = !playing.value
  if (playing.value) {
    lastTs = 0
    rafId = requestAnimationFrame(animate)
  } else {
    cancelAnimationFrame(rafId)
  }
}

function onScrub(e: Event) {
  const val = parseFloat((e.target as HTMLInputElement).value)
  currentSec.value = val
  if (!playing.value) drawAtTime(val)
}

function formatTime(sec: number): string {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = Math.floor(sec % 60)
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${m}:${String(s).padStart(2, '0')}`
}

function resizeCanvas() {
  const canvas = canvasEl.value
  if (!canvas || !data.value) return
  const wrap = canvas.parentElement!
  canvas.width  = wrap.clientWidth
  canvas.height = wrap.clientHeight

  buildTrackPaths(data.value, canvas.width, canvas.height)

  // Ausrichtung mit aktueller Canvas-Größe neu berechnen
  if (circuitImg.value) {
    alignTransform = computeAlignment(circuitImg.value, data.value, canvas.width, canvas.height)
  }

  drawAtTime(currentSec.value)
}

watch(() => props.sessionKey, () => { load() }, { immediate: true })

watch(state, async (s) => {
  if (s === 'ready') {
    await nextTick()
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
  }
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resizeCanvas)
})
</script>

<style scoped>
.replay-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.replay-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 16px;
  min-height: 180px;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  text-align: center;
  padding: 40px 0;
}

.retry-btn {
  background: none;
  border: 1px solid var(--line);
  color: var(--text-dim);
  border-radius: 8px;
  padding: 8px 20px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.retry-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--text); }
.retry-btn:disabled { opacity: 0.4; cursor: default; }

.canvas-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16/9;
  background: #0a0a0f;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
}

.track-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.legend {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: calc(100% - 24px);
  overflow-y: auto;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-abbr {
  font-family: var(--font-mono);
  font-size: 10px;
  color: rgba(255,255,255,0.7);
  letter-spacing: 0.06em;
}

.time-badge {
  position: absolute;
  bottom: 12px;
  left: 12px;
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.1em;
  text-shadow: 0 1px 4px rgba(0,0,0,0.8);
}

.controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ctrl-btn {
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--text);
  width: 40px;
  height: 40px;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.15s;
}
.ctrl-btn:hover:not(:disabled) { border-color: var(--accent); }
.ctrl-btn:disabled { opacity: 0.4; cursor: default; }
.ctrl-btn.spinning { animation: spin 0.9s linear infinite; }

.scrubber {
  flex: 1;
  accent-color: var(--accent);
  height: 4px;
  cursor: pointer;
}

.speed-btns {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.speed-btn {
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--text-faint);
  padding: 6px 10px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}
.speed-btn:hover { color: var(--text); }
.speed-btn.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
</style>
