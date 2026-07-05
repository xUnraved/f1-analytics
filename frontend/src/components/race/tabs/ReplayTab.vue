<template>
  <div class="replay-wrap">
    <div v-if="state === 'loading'" class="replay-state">
      <LoadingBar :label="t('race.replay.loading')" />
    </div>

    <div v-else-if="state === 'error'" class="replay-state">
      <span>{{ t('race.replay.error') }}</span>
      <button class="retry-btn" :disabled="retrying" @click="retryLoad">
        {{ retrying ? '…' : t('race.replay.retry') }}
      </button>
    </div>

    <div v-else-if="state === 'empty'" class="replay-state">
      <span>{{ t('race.replay.noData') }}</span>
      <button class="retry-btn" :disabled="retrying" @click="retryLoad">
        {{ retrying ? '…' : t('race.replay.retry') }}
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
}>()

type State = 'loading' | 'ready' | 'error' | 'empty'

const state          = ref<State>('loading')
const data           = ref<ReplayData | null>(null)
const canvasEl       = ref<HTMLCanvasElement>()
const playing        = ref(false)
const speed          = ref(1)
const currentSec     = ref(0)
const refreshingReplay = ref(false)
const retrying       = ref(false)

// GPS → Canvas: PCA-Rotation + einheitlicher Maßstab
let _meanX = 0, _meanY = 0
let _cosA = 1, _sinA = 0
let _minRX = 0, _maxRX = 1, _minRY = 0, _maxRY = 1
let _scale = 1, _offX = 0, _offY = 0

// Animations-State
let rafId  = 0
let lastTs = 0

const colorMap = new Map<number, string>()
const abbrMap  = new Map<number, string>()

// Vorberechnete Streckenpfade aus GPS-Daten
let trackPaths: [number, number][][] = []

// ---- Laden ----

async function load() {
  playing.value = false
  cancelAnimationFrame(rafId)
  currentSec.value = 0
  trackPaths = []
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

async function retryLoad() {
  retrying.value = true
  try { await load() }
  finally { retrying.value = false }
}

async function onRefreshReplay() {
  refreshingReplay.value = true
  try { await refreshReplay(props.sessionKey); await load() }
  finally { refreshingReplay.value = false }
}

// ---- GPS-Koordinaten ----

function computeBounds(d: ReplayData) {
  const BOUND_SECS = 360

  // Nur Fahrer mit echter GPS-Bewegung verwenden: Fahrer mit Varianz < 100m (Nullposition/kein Signal) ausschließen
  const pts: [number, number][] = []
  for (const driver of d.drivers) {
    const dpts: [number, number][] = []
    for (const f of d.frames) {
      if (f.t > BOUND_SECS) break
      const pos = f.p[String(driver.num)]
      if (pos) dpts.push([pos[0], pos[1]])
    }
    if (dpts.length < 2) continue
    const mx = dpts.reduce((s, p) => s + p[0], 0) / dpts.length
    const my = dpts.reduce((s, p) => s + p[1], 0) / dpts.length
    const varXY = dpts.reduce((s, p) => s + (p[0] - mx) ** 2 + (p[1] - my) ** 2, 0) / dpts.length
    // Mindest-Varianz: 100m Standardabweichung ≈ echter Fahrer auf der Strecke
    if (varXY >= 100 * 100) {
      for (const pt of dpts) pts.push(pt)
    }
  }
  const n = pts.length
  if (n < 10) return  // kein gültiges GPS → Bounds unverändert lassen

  _meanX = pts.reduce((s, p) => s + p[0], 0) / n
  _meanY = pts.reduce((s, p) => s + p[1], 0) / n

  // PCA — Hauptachse der Strecke ausrichten (auch für diagonale Strecken wie Barcelona, Montreal)
  let cxx = 0, cyy = 0, cxy = 0
  for (const p of pts) {
    const dx = p[0] - _meanX, dy = p[1] - _meanY
    cxx += dx * dx; cyy += dy * dy; cxy += dx * dy
  }
  const angle = Math.atan2(2 * cxy, cxx - cyy) / 2
  _cosA = Math.cos(angle); _sinA = Math.sin(angle)

  // Min/Max der rotierten Koordinaten mit ±2800m Clamp gegen extreme Ausreißer
  let rxMin = Infinity, rxMax = -Infinity, ryMin = Infinity, ryMax = -Infinity
  for (const p of pts) {
    const dx = p[0] - _meanX, dy = p[1] - _meanY
    const rx = dx * _cosA + dy * _sinA
    const ry = -dx * _sinA + dy * _cosA
    if (rx < rxMin) rxMin = rx; if (rx > rxMax) rxMax = rx
    if (ry < ryMin) ryMin = ry; if (ry > ryMax) ryMax = ry
  }

  const padX = Math.max((rxMax - rxMin) * 0.12, 200)
  const padY = Math.max((ryMax - ryMin) * 0.12, 200)
  const CLAMP = 2800
  _minRX = Math.max(rxMin - padX, -CLAMP); _maxRX = Math.min(rxMax + padX, CLAMP)
  _minRY = Math.max(ryMin - padY, -CLAMP); _maxRY = Math.min(ryMax + padY, CLAMP)

  console.log(`[Bounds] drivers=${d.drivers.length} validPts=${n} angle=${(angle*180/Math.PI).toFixed(1)}° rx=[${rxMin.toFixed(0)},${rxMax.toFixed(0)}] ry=[${ryMin.toFixed(0)},${ryMax.toFixed(0)}]`)
}

function updateScale(W: number, H: number) {
  const scaleX = W / (_maxRX - _minRX)
  const scaleY = H / (_maxRY - _minRY)
  _scale = Math.min(scaleX, scaleY) * 0.92
  _offX = (W - (_maxRX - _minRX) * _scale) / 2
  _offY = (H - (_maxRY - _minRY) * _scale) / 2
}

function toCanvas(x: number, y: number, _W: number, H: number): [number, number] {
  const dx = x - _meanX, dy = y - _meanY
  const rx = dx * _cosA + dy * _sinA
  const ry = -dx * _sinA + dy * _cosA
  return [
    _offX + (rx - _minRX) * _scale,
    H - _offY - (ry - _minRY) * _scale,
  ]
}

// ---- Streckenpfade aus GPS-Daten aufbauen ----

function buildTrackPaths(d: ReplayData, W: number, H: number) {
  trackPaths = []
  // 1000m Threshold: überbrückt Datenlücken von bis zu ~12s bei Vollgas,
  // erlaubt Boxeneinfahrten — filtert nur echte Positionssprünge (Bergung etc.)
  const MAX_STEP_SQ = 1000 * 1000

  for (const driver of d.drivers) {
    let segment: [number, number][] = []
    let prevGps: [number, number] | null = null

    for (const f of d.frames) {
      const pos = f.p[String(driver.num)]
      if (!pos) continue  // Frame überspringen, prevGps beibehalten

      if (prevGps) {
        const dx = pos[0] - prevGps[0]
        const dy = pos[1] - prevGps[1]
        if (dx * dx + dy * dy > MAX_STEP_SQ) {
          if (segment.length > 2) trackPaths.push(segment)
          segment = []
        }
      }

      segment.push(toCanvas(pos[0], pos[1], W, H))
      prevGps = [pos[0], pos[1]]
    }

    if (segment.length > 2) trackPaths.push(segment)
  }
}

// ---- Zeichnen ----

function drawAtTime(sec: number) {
  const canvas = canvasEl.value
  if (!canvas || !data.value) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const W = canvas.width, H = canvas.height

  // Frames für Interpolation finden
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
  ctx.fillStyle = '#111118'
  ctx.fillRect(0, 0, W, H)

  // Strecke mit Catmull-Rom Splines (weiche Kurven aus 1Hz-Daten)
  if (trackPaths.length > 0) {
    ctx.lineCap  = 'round'
    ctx.lineJoin = 'round'

    // Catmull-Rom: bezierCurveTo mit Kontrollpunkten aus Nachbarpunkten
    const splinePaths = () => {
      for (const path of trackPaths) {
        if (path.length < 2) continue
        ctx.moveTo(path[0]![0], path[0]![1])
        for (let i = 0; i < path.length - 1; i++) {
          const p0 = path[Math.max(0, i - 1)]!
          const p1 = path[i]!
          const p2 = path[i + 1]!
          const p3 = path[Math.min(path.length - 1, i + 2)]!
          const cp1x = p1[0] + (p2[0] - p0[0]) / 6
          const cp1y = p1[1] + (p2[1] - p0[1]) / 6
          const cp2x = p2[0] - (p3[0] - p1[0]) / 6
          const cp2y = p2[1] - (p3[1] - p1[1]) / 6
          ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2[0], p2[1])
        }
      }
    }

    // Äußere dunkle Kante (Randstein-Effekt)
    ctx.beginPath(); splinePaths()
    ctx.strokeStyle = 'rgba(40, 44, 65, 1.0)'
    ctx.lineWidth   = 30
    ctx.stroke()

    // Fahrbahn (hellgrau wie echte Asphalt-Streckenbilder)
    ctx.beginPath(); splinePaths()
    ctx.strokeStyle = 'rgba(148, 155, 185, 0.88)'
    ctx.lineWidth   = 20
    ctx.stroke()

    // Schmale innere Helligkeit (Lichteffekt)
    ctx.beginPath(); splinePaths()
    ctx.strokeStyle = 'rgba(210, 220, 255, 0.18)'
    ctx.lineWidth   = 4
    ctx.stroke()
  }

  // Fahrerpunkte mit Interpolation
  const R = 9
  const carData: { cx: number; cy: number; color: string; abbr: string }[] = []

  for (const [numStr, posA] of Object.entries(frameA.p)) {
    const num = parseInt(numStr)
    let x = posA[0], y = posA[1]
    if (frameB && frac > 0) {
      const posB = frameB.p[numStr]
      if (posB) {
        const dx = posB[0] - posA[0]
        const dy = posB[1] - posA[1]
        // Nur interpolieren wenn Abstand < 300m (verhindert Linien bei ausgeschiedenen Autos)
        if (dx * dx + dy * dy < 300 * 300) {
          x = posA[0] + dx * frac
          y = posA[1] + dy * frac
        }
      }
    }
    const [cx, cy] = toCanvas(x, y, W, H)
    carData.push({ cx, cy, color: colorMap.get(num) ?? '#888', abbr: abbrMap.get(num) ?? `${num}` })
  }

  // Schatten-Pass (erst alle Schatten, dann Punkte — verhindert Überlappungsfehler)
  for (const { cx, cy, color } of carData) {
    ctx.shadowColor = color
    ctx.shadowBlur  = 12
    ctx.beginPath()
    ctx.arc(cx, cy, R, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
  }
  ctx.shadowBlur = 0

  // Punkte + Labels
  for (const { cx, cy, color, abbr } of carData) {
    ctx.beginPath()
    ctx.arc(cx, cy, R, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.strokeStyle = 'rgba(255,255,255,0.9)'
    ctx.lineWidth   = 1.5
    ctx.stroke()

    // Kürzel im Punkt
    ctx.fillStyle    = '#fff'
    ctx.font         = `bold ${R < 8 ? 6 : 7}px monospace`
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
  updateScale(canvas.width, canvas.height)
  buildTrackPaths(data.value, canvas.width, canvas.height)
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
