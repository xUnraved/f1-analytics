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
      <div v-if="currentFlagInfo" class="flag-banner" :class="currentFlagInfo.cls">
        <span class="flag-icon">{{ currentFlagInfo.icon }}</span>
        <span class="flag-label">{{ currentFlagInfo.label }}</span>
      </div>

      <div class="replay-body">
        <div class="timing-sidebar">
          <div class="timing-row timing-header">
            <span class="col-pos"></span>
            <span class="col-driver">{{ t('race.replay.timing.driver') }}</span>
            <span class="col-tyre">{{ t('race.replay.timing.tyre') }}</span>
            <span class="col-lap">{{ t('race.replay.timing.lastLap') }}</span>
            <span class="col-sectors">{{ t('race.replay.timing.sectors') }}</span>
            <span class="col-gap">{{ t('race.replay.timing.gap') }}</span>
            <span class="col-pts">{{ t('race.replay.timing.points') }}</span>
          </div>

          <div class="timing-rows">
            <div v-for="row in timingRows" :key="row.num" class="timing-row">
              <span class="col-pos">
                <span class="pos-num">{{ row.position || '–' }}</span>
                <span
                  v-if="row.posDelta"
                  class="pos-delta"
                  :class="row.posDelta > 0 ? 'pos-up' : 'pos-down'"
                >{{ row.posDelta > 0 ? '▲' : '▼' }}{{ Math.abs(row.posDelta) }}</span>
              </span>

              <span class="col-driver">
                <span class="driver-dot" :style="{ background: row.color }" />
                {{ row.abbr }}
              </span>

              <span class="col-tyre">
                <template v-if="row.tyres.length">
                  <span
                    v-for="(c, i) in row.tyres"
                    :key="i"
                    class="tyre-badge"
                    :class="{ 'tyre-current': i === row.tyres.length - 1 }"
                    :title="c"
                    :style="{ background: tyreColor(c), color: tyreTextColor(c) }"
                  >{{ c[0] }}</span>
                  <span v-if="row.tyreAge != null" class="tyre-age" :title="`${row.tyreAge} Runden auf diesem Satz`">{{ row.tyreAge }}</span>
                </template>
                <span v-else class="tyre-badge tyre-none">—</span>
              </span>

              <span class="col-lap">{{ row.lastLapText }}</span>

              <span class="col-sectors">
                <span
                  v-for="(s, i) in row.sectors"
                  :key="i"
                  class="sector-chip"
                  :class="s.cls"
                  :title="`S${i + 1}: ${s.text}`"
                >{{ s.text }}</span>
              </span>

              <span class="col-gap" :class="{ 'gap-leader': row.position === 1 }">{{ row.gapText }}</span>
              <span class="col-pts">{{ row.points }}</span>
            </div>
          </div>
        </div>

        <div class="replay-main">
          <div class="canvas-wrap">
            <canvas ref="canvasEl" class="track-canvas" />

            <div class="legend">
              <div v-for="d in data!.drivers" :key="d.num" class="legend-item">
                <span class="legend-dot" :style="{ background: d.color }" />
                <span class="legend-abbr">{{ d.abbr }}</span>
              </div>
            </div>

            <div class="time-badge">
              {{ formatTime(currentSec) }}
              <span class="time-remaining">–{{ formatTime(Math.max(0, data!.duration - currentSec)) }}</span>
            </div>
          </div>

          <div class="controls">
            <button class="ctrl-btn" @click="togglePlay">
              {{ playing ? '⏸' : '▶' }}
            </button>

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

          <div class="controls-secondary">
            <button
              class="ctrl-btn refresh"
              :class="{ spinning: refreshingReplay }"
              :disabled="refreshingReplay"
              :title="t('race.replay.refresh')"
              @click="onRefreshReplay"
            >⟳</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import LoadingBar from '@/components/ui/LoadingBar.vue'
import { fetchReplay, fetchTiming, refreshReplay } from '@/services/f1Api'
import type { ReplayData, TimingData, LapInfo, PositionPoint, StintInfo, IntervalPoint } from '@/types/f1'

const { t } = useI18n()

const props = defineProps<{
  sessionKey: number
  dateStart: string
}>()

type State = 'loading' | 'ready' | 'error' | 'empty'

const state          = ref<State>('loading')
const data           = ref<ReplayData | null>(null)
const timing         = ref<TimingData | null>(null)
const canvasEl       = ref<HTMLCanvasElement>()
const playing        = ref(false)
const speed          = ref(1)
const currentSec     = ref(0)
// Für die Live-Timing-Sidebar: eigener, gedrosselter Zeitstand (siehe animate()) —
// die Sidebar-Texte müssen nicht mit 60fps neu gerendert werden wie das Canvas.
const displaySec     = ref(0)
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

// Pro Fahrer deduplizierte, echte GPS-Zeitreihe (ohne die vom Backend beim
// Zusammenbauen der Frames "eingefrorenen" Fortschreibungen bei fehlenden Updates).
// Nötig, um bei Datenlücken über die TATSÄCHLICH vergangene Zeit zu interpolieren
// statt über eine pauschal angenommene 1 Sekunde — sonst wirkt es auf der Geraden
// (hohe Geschwindigkeit, größere reale Lücken-Distanz) wie Einfrieren-dann-Sprung.
let driverSeries = new Map<number, { ts: number[]; xs: number[]; ys: number[] }>()

// Vorberechnete Streckenpfade aus GPS-Daten
let trackPaths: [number, number][][] = []
// Einmal gerenderte Streckengrafik (Hintergrund + Asphalt) — verhindert, dass die
// komplette Spline (zehntausende Punkte) bei jedem Animationsframe neu gezeichnet wird
let trackLayer: HTMLCanvasElement | null = null

// ---- Laden ----

async function load() {
  playing.value = false
  cancelAnimationFrame(rafId)
  currentSec.value = 0
  displaySec.value = 0
  trackPaths = []
  trackLayer = null
  driverSeries = new Map()
  colorMap.clear()
  abbrMap.clear()
  data.value = null
  timing.value = null
  state.value = 'loading'

  // Beide Abrufe parallel starten — Timing-Daten (Runden/Reifen/Positionen/Flaggen)
  // sind ein paar Sekunden schnell (keine Pro-Fahrer-Schleife wie beim Replay) und
  // sollen nicht auf den viel langsameren GPS-Abruf warten müssen. Ein Fehler beim
  // Timing-Abruf darf das Replay selbst nicht blockieren (Sidebar bleibt dann leer).
  const timingPromise = fetchTiming(props.sessionKey, props.dateStart).catch(() => null)

  try {
    const d = await fetchReplay(props.sessionKey, props.dateStart)
    if (!d.frames.length) { state.value = 'empty'; return }
    data.value = d
    for (const dr of d.drivers) {
      colorMap.set(dr.num, dr.color)
      abbrMap.set(dr.num, dr.abbr)
    }
    buildDriverSeries(d)
    computeBounds(d)
    state.value = 'ready'
    await nextTick()
    resizeCanvas()
  } catch {
    state.value = 'error'
    return
  }
  timing.value = await timingPromise
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
  // Über die GESAMTE Session scannen (nicht nur den Sessionanfang) — sonst fehlen
  // Streckenteile in den Bounds, die erst später abgefahren werden (z.B. bei
  // verzögertem Rennstart), obwohl sie später mitgezeichnet werden.
  // Nur Fahrer mit echter GPS-Bewegung verwenden: Fahrer mit Varianz < 100m (Nullposition/kein Signal) ausschließen
  const pts: [number, number][] = []
  for (const driver of d.drivers) {
    const dpts: [number, number][] = []
    for (const f of d.frames) {
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

  const rxs: number[] = Array.from({ length: n }, () => 0)
  const rys: number[] = Array.from({ length: n }, () => 0)
  for (let i = 0; i < n; i++) {
    const p = pts[i]!
    const dx = p[0] - _meanX, dy = p[1] - _meanY
    rxs[i] = dx * _cosA + dy * _sinA
    rys[i] = -dx * _sinA + dy * _cosA
  }

  // Perzentil-basiertes Trimmen statt festem Meter-Clamp: verwirft nur die äußersten
  // 0.2% der Punkte (einzelne GPS-Ausreißer/Glitches), erfasst aber die volle
  // Streckenausdehnung unabhängig von der Streckengröße (Monaco bis Spa/Silverstone).
  rxs.sort((a, b) => a - b)
  rys.sort((a, b) => a - b)
  const trim = Math.floor(n * 0.002)
  const rxMin = rxs[trim]!, rxMax = rxs[n - 1 - trim]!
  const ryMin = rys[trim]!, ryMax = rys[n - 1 - trim]!

  const padX = Math.max((rxMax - rxMin) * 0.12, 200)
  const padY = Math.max((ryMax - ryMin) * 0.12, 200)
  _minRX = rxMin - padX; _maxRX = rxMax + padX
  _minRY = ryMin - padY; _maxRY = ryMax + padY

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

// ---- Echte Update-Zeitpunkte pro Fahrer rekonstruieren ----

function buildDriverSeries(d: ReplayData) {
  const series = new Map<number, { ts: number[]; xs: number[]; ys: number[] }>()
  for (const driver of d.drivers) {
    const numStr = String(driver.num)
    const ts: number[] = [], xs: number[] = [], ys: number[] = []
    let lastX = NaN, lastY = NaN
    for (const f of d.frames) {
      const pos = f.p[numStr]
      if (!pos) continue
      // Identischer Wert wie zuvor → vom Backend fortgeschriebene Sekunde ohne
      // echtes neues Sample, nicht als eigener Zeitpunkt aufnehmen
      if (pos[0] !== lastX || pos[1] !== lastY) {
        ts.push(f.t); xs.push(pos[0]); ys.push(pos[1])
        lastX = pos[0]; lastY = pos[1]
      }
    }
    // Kein Ausreißer-Filter hier (normales GPS-Rauschen einzelner Punkte würde sonst
    // zu viele echte Punkte verwerfen → verbleibende Punkte werden mit Geraden quer
    // über die Strecke statt entlang ihr verbunden). Unrealistische Sprünge werden
    // stattdessen erst beim Rendern in getDriverPos abgefangen (einfrieren statt
    // durch sie hindurch zu interpolieren) — betrifft dann nur den einen betroffenen
    // Fahrer/Zeitabschnitt, nicht die ganze Datenreihe.
    series.set(driver.num, { ts, xs, ys })
  }
  driverSeries = series
}

// Größter Index i mit ts[i] <= sec, oder -1 wenn sec vor dem ersten Sample liegt
function findSeriesIndex(ts: number[], sec: number): number {
  if (ts.length === 0 || sec < ts[0]!) return -1
  let lo = 0, hi = ts.length - 1
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (ts[mid]! <= sec) lo = mid; else hi = mid - 1
  }
  return lo
}

const MAX_SPEED_MPS = 130 // realistische Höchstgeschwindigkeit + Marge; größere Sprünge = Vorfall/Versetzung, nicht interpolieren

// Kubische Hermite-Interpolation mit zeit-skalierten Tangenten (nicht die uniforme
// Catmull-Rom-Formel!). Die Tangente an P1/P2 wird aus dem ECHTEN Zeitabstand zu den
// Nachbarpunkten geschätzt (m = Δposition/Δzeit) — das ergibt an jedem echten GPS-Punkt
// eine passende "Geschwindigkeit" statt eines scharfen Knicks (wie bei linearer
// Interpolation) und überschwingt nicht wie die uniforme Catmull-Rom-Formel bei
// unregelmäßigen Abständen.
function hermiteInterp(
  t0: number, p0: number, t1: number, p1: number,
  t2: number, p2: number, t3: number, p3: number,
  t: number,
): number {
  const h = t2 - t1
  const s = h > 0 ? (t - t1) / h : 0
  const m1 = (t2 - t0) > 0 ? (p2 - p0) / (t2 - t0) : (h > 0 ? (p2 - p1) / h : 0)
  const m2 = (t3 - t1) > 0 ? (p3 - p1) / (t3 - t1) : (h > 0 ? (p2 - p1) / h : 0)

  const s2 = s * s, s3 = s2 * s
  const h00 = 2 * s3 - 3 * s2 + 1
  const h10 = s3 - 2 * s2 + s
  const h01 = -2 * s3 + 3 * s2
  const h11 = s3 - s2

  return h00 * p1 + h10 * h * m1 + h01 * p2 + h11 * h * m2
}

// Interpolierte Fahrerposition zum Zeitpunkt sec — von drawAtTime UND der
// Diagnose verwendet, damit die Diagnose exakt das misst, was gezeichnet wird.
function getDriverPos(driverNum: number, sec: number): [number, number] | null {
  const series = driverSeries.get(driverNum)
  if (!series) return null
  const idx = findSeriesIndex(series.ts, sec)
  if (idx < 0) return null

  const tA = series.ts[idx]!, xA = series.xs[idx]!, yA = series.ys[idx]!
  if (idx + 1 >= series.ts.length) return [xA, yA]

  const tB = series.ts[idx + 1]!, xB = series.xs[idx + 1]!, yB = series.ys[idx + 1]!
  const dtReal = tB - tA
  const dx = xB - xA, dy = yB - yA
  const dist = Math.sqrt(dx * dx + dy * dy)
  if (dtReal <= 0 || dist / dtReal > MAX_SPEED_MPS) return [xA, yA]

  const frac = Math.max(0, Math.min(1, (sec - tA) / dtReal))
  if (frac <= 0) return [xA, yA]

  const idxPrev  = Math.max(0, idx - 1)
  const idxNext2 = Math.min(series.ts.length - 1, idx + 2)
  const tPrev = series.ts[idxPrev]!, tNext2 = series.ts[idxNext2]!

  return [
    hermiteInterp(tPrev, series.xs[idxPrev]!, tA, xA, tB, xB, tNext2, series.xs[idxNext2]!, sec),
    hermiteInterp(tPrev, series.ys[idxPrev]!, tA, yA, tB, yB, tNext2, series.ys[idxNext2]!, sec),
  ]
}

// ---- Streckenpfade aus GPS-Daten aufbauen ----

function buildTrackPaths(d: ReplayData, W: number, H: number) {
  trackPaths = []
  // 1000m Threshold: überbrückt Datenlücken von bis zu ~12s bei Vollgas,
  // erlaubt Boxeneinfahrten — filtert nur echte Positionssprünge (Bergung etc.)
  const MAX_STEP_SQ = 1000 * 1000

  // Die Streckenkontur ändert sich nicht von Runde zu Runde — das Backend liefert
  // GPS-Punkte aber mit ~4Hz (siehe getDriverPos/driverSeries für die tatsächliche
  // Animation, die diese volle Auflösung braucht). Für den EINMALIG gezeichneten
  // Streckenhintergrund reicht ~1 Punkt/Sekunde: ohne dieses Downsampling zeichnet
  // renderTrackLayer bei einem 2h-Rennen ~80 Runden × volle Auflösung übereinander
  // (hunderttausende bezierCurveTo-Aufrufe), was so lange dauert/so viel Speicher
  // braucht, dass die Strecke im Hintergrund gar nicht mehr erscheint.
  const MIN_DT_SEC = 1

  for (const driver of d.drivers) {
    let segment: [number, number][] = []
    let prevGps: [number, number] | null = null
    let lastT = -Infinity

    for (const f of d.frames) {
      const pos = f.p[String(driver.num)]
      if (!pos) continue  // Frame überspringen, prevGps beibehalten
      if (f.t - lastT < MIN_DT_SEC) continue
      lastT = f.t

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

// Vorgerenderte Glow-Punkt-Sprites, ein Canvas pro Fahrfarbe — vermeidet, dass
// ctx.shadowBlur (teuer, oft Software-Rendering) bei jedem Animationsframe neu läuft.
const dotSpriteCache = new Map<string, HTMLCanvasElement>()

function getDotSprite(color: string, R: number): HTMLCanvasElement {
  const key = `${color}:${R}`
  let sprite = dotSpriteCache.get(key)
  if (sprite) return sprite

  const PAD = 16
  const size = (R + PAD) * 2
  sprite = document.createElement('canvas')
  sprite.width = size
  sprite.height = size
  const sctx = sprite.getContext('2d')!
  const c = size / 2

  sctx.shadowColor = color
  sctx.shadowBlur  = 12
  sctx.beginPath()
  sctx.arc(c, c, R, 0, Math.PI * 2)
  sctx.fillStyle = color
  sctx.fill()
  sctx.shadowBlur = 0

  sctx.strokeStyle = 'rgba(255,255,255,0.9)'
  sctx.lineWidth   = 1.5
  sctx.stroke()

  dotSpriteCache.set(key, sprite)
  return sprite
}

// Rendert Hintergrund + Streckenlinie EINMALIG auf eine Offscreen-Canvas.
// Wird nur bei resizeCanvas()/buildTrackPaths() aufgerufen, nicht pro Animationsframe —
// die Spline aus zehntausenden GPS-Punkten wäre bei 60fps sonst extrem teuer und
// blockiert den Haupt-Thread (führt zum Einfrieren des Browsers während der Wiedergabe).
function renderTrackLayer(W: number, H: number) {
  const layer = document.createElement('canvas')
  layer.width = W
  layer.height = H
  const ctx = layer.getContext('2d')!

  ctx.fillStyle = '#111118'
  ctx.fillRect(0, 0, W, H)

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

  trackLayer = layer
}

function drawAtTime(sec: number) {
  const canvas = canvasEl.value
  if (!canvas || !data.value) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  // CSS-Pixel-Maße (nicht canvas.width/height — das ist die an devicePixelRatio
  // angepasste physische Backing-Store-Größe, siehe resizeCanvas). updateScale/
  // buildTrackPaths/renderTrackLayer rechnen alle in CSS-Pixeln, der ctx-Transform
  // skaliert das beim Zeichnen automatisch auf die physische Auflösung hoch.
  const W = canvas.clientWidth, H = canvas.clientHeight

  // Hintergrund + Strecke (vorgerendert, siehe renderTrackLayer)
  ctx.clearRect(0, 0, W, H)
  if (trackLayer) {
    ctx.drawImage(trackLayer, 0, 0)
  } else {
    ctx.fillStyle = '#111118'
    ctx.fillRect(0, 0, W, H)
  }

  // Fahrerpunkte: Interpolation über die ECHTE Zeit zwischen zwei tatsächlichen
  // GPS-Updates (nicht über eine pauschale 1-Sekunde-Annahme, siehe driverSeries)
  const R = 9
  const carData: { cx: number; cy: number; color: string; abbr: string }[] = []

  for (const driver of data.value.drivers) {
    const pos = getDriverPos(driver.num, sec)
    if (!pos) continue // Fahrer hat zu diesem Zeitpunkt noch keine Daten

    const [cx, cy] = toCanvas(pos[0], pos[1], W, H)
    carData.push({ cx, cy, color: colorMap.get(driver.num) ?? '#888', abbr: abbrMap.get(driver.num) ?? `${driver.num}` })
  }

  // Punkte (als vorgerenderte Sprites geblittet, siehe getDotSprite — shadowBlur
  // live pro Frame/Fahrer ist eine der teuersten Canvas-Operationen und erzwingt in
  // vielen Browsern Software-Rendering, was bei 60fps × 20 Fahrern spürbar ruckelt)
  for (const { cx, cy, color } of carData) {
    const sprite = getDotSprite(color, R)
    ctx.drawImage(sprite, cx - sprite.width / 2, cy - sprite.height / 2)
  }

  // Kürzel-Labels (separat, da Text nicht Teil des gecachten Sprites ist)
  ctx.fillStyle    = '#fff'
  ctx.font         = `bold ${R < 8 ? 6 : 7}px monospace`
  ctx.textAlign    = 'center'
  ctx.textBaseline = 'middle'
  for (const { cx, cy, abbr } of carData) {
    ctx.fillText(abbr, cx, cy)
  }
}

// ---- Live-Timing (Sidebar) ----

// Standard-Punktesystem (Positionen 1-10) — für "wie viele Punkte bekäme der
// Fahrer aktuell", nicht die echte End-Wertung (die steht bereits in race.result).
const POINTS_TABLE = [25, 18, 15, 12, 10, 8, 6, 4, 2, 1]
function pointsForPosition(pos: number): number {
  return pos >= 1 && pos <= POINTS_TABLE.length ? POINTS_TABLE[pos - 1]! : 0
}

const TYRE_COLORS: Record<string, string> = {
  SOFT: '#e34948',
  MEDIUM: '#eda100',
  HARD: '#e8e8e8',
  INTERMEDIATE: '#1baf7a',
  WET: '#3987e5',
}
function tyreColor(compound: string): string {
  return TYRE_COLORS[compound.toUpperCase()] ?? '#888'
}
function tyreTextColor(compound: string): string {
  return compound.toUpperCase() === 'HARD' ? '#111' : '#fff'
}

function formatLapTime(sec: number): string {
  const m = Math.floor(sec / 60)
  const s = sec - m * 60
  return `${m}:${s.toFixed(3).padStart(6, '0')}`
}

// Letzter Eintrag mit t <= sec (Elemente müssen aufsteigend nach t sortiert sein) —
// generischer Nachfolger von findSeriesIndex für die Timing-Zeitreihen.
function lastAtOrBefore<T extends { t: number }>(arr: T[], sec: number): T | null {
  if (!arr.length || sec < arr[0]!.t) return null
  let lo = 0, hi = arr.length - 1
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (arr[mid]!.t <= sec) lo = mid; else hi = mid - 1
  }
  return arr[lo]!
}

interface SectorCell { text: string; cls: string }
interface TimingRow {
  num: number
  abbr: string
  color: string
  position: number
  posDelta: number | null
  tyres: string[] // bisherige Stints in Reihenfolge, aktueller Reifen zuletzt
  tyreAge: number | null // Runden auf dem aktuellen Reifensatz
  lastLapText: string
  sectors: SectorCell[]
  gapText: string
  points: number
}

function sectorCell(value: number | null | undefined, personalBest: number | undefined, sessionBest: number): SectorCell {
  if (value == null) return { text: '—', cls: 'sector-none' }
  let cls = 'sector-normal'
  if (sessionBest < Infinity && value <= sessionBest + 0.0005) cls = 'sector-best'
  else if (personalBest !== undefined && value <= personalBest + 0.0005) cls = 'sector-personal'
  return { text: value.toFixed(1), cls }
}

// Baut die Sidebar-Zeilen für den aktuellen (gedrosselten) Zeitpunkt. Ein Durchlauf über
// alle bisher gestarteten/abgeschlossenen Runden reicht, um Session- und Fahrer-Bestzeiten
// pro Sektor sowie die aktuelle Rundennummer (für Reifen-Stint-Zuordnung) zu ermitteln —
// pro Fahrerzeile wird dann nur noch nachgeschlagen, nicht neu gescannt.
function buildTimingRows(sec: number): TimingRow[] {
  if (!data.value) return []
  const td = timing.value
  if (!td) {
    return data.value.drivers.map(d => ({
      num: d.num, abbr: d.abbr, color: d.color, position: 0, posDelta: null,
      tyres: [], tyreAge: null, lastLapText: '—',
      sectors: [sectorCell(null, undefined, Infinity), sectorCell(null, undefined, Infinity), sectorCell(null, undefined, Infinity)],
      gapText: '—', points: 0,
    }))
  }

  let bestS1 = Infinity, bestS2 = Infinity, bestS3 = Infinity
  const pbS1 = new Map<number, number>()
  const pbS2 = new Map<number, number>()
  const pbS3 = new Map<number, number>()
  const lapsByDriver = new Map<number, LapInfo[]>()
  const lastCompletedByDriver = new Map<number, LapInfo>()

  for (const lap of td.laps) {
    if (lap.t > sec) break // global nach t sortiert (siehe ReplayTimingService)
    let arr = lapsByDriver.get(lap.driverNumber)
    if (!arr) { arr = []; lapsByDriver.set(lap.driverNumber, arr) }
    arr.push(lap)

    if (lap.duration != null && lap.t + lap.duration <= sec) {
      lastCompletedByDriver.set(lap.driverNumber, lap)
      if (lap.sector1 != null) {
        bestS1 = Math.min(bestS1, lap.sector1)
        pbS1.set(lap.driverNumber, Math.min(pbS1.get(lap.driverNumber) ?? Infinity, lap.sector1))
      }
      if (lap.sector2 != null) {
        bestS2 = Math.min(bestS2, lap.sector2)
        pbS2.set(lap.driverNumber, Math.min(pbS2.get(lap.driverNumber) ?? Infinity, lap.sector2))
      }
      if (lap.sector3 != null) {
        bestS3 = Math.min(bestS3, lap.sector3)
        pbS3.set(lap.driverNumber, Math.min(pbS3.get(lap.driverNumber) ?? Infinity, lap.sector3))
      }
    }
  }

  const posByDriver = new Map<number, PositionPoint[]>()
  for (const p of td.positions) {
    if (p.t > sec) break
    let arr = posByDriver.get(p.driverNumber)
    if (!arr) { arr = []; posByDriver.set(p.driverNumber, arr) }
    arr.push(p)
  }

  const stintsByDriver = new Map<number, StintInfo[]>()
  for (const s of td.stints) {
    let arr = stintsByDriver.get(s.driverNumber)
    if (!arr) { arr = []; stintsByDriver.set(s.driverNumber, arr) }
    arr.push(s)
  }

  const intervalsByDriver = new Map<number, IntervalPoint[]>()
  for (const iv of td.intervals) {
    if (iv.t > sec) break
    let arr = intervalsByDriver.get(iv.driverNumber)
    if (!arr) { arr = []; intervalsByDriver.set(iv.driverNumber, arr) }
    arr.push(iv)
  }

  const rows: TimingRow[] = []
  for (const driver of data.value.drivers) {
    const posArr = posByDriver.get(driver.num)
    const gridPos = td.gridPosition[String(driver.num)] ?? null
    const curPos = posArr?.length ? posArr[posArr.length - 1]!.position : (gridPos ?? 0)
    const posDelta = gridPos != null && curPos > 0 ? gridPos - curPos : null

    const driverLaps = lapsByDriver.get(driver.num)
    const curLapNumber = driverLaps?.length ? driverLaps[driverLaps.length - 1]!.lapNumber : 0
    const lastLap = lastCompletedByDriver.get(driver.num) ?? null

    // Alle Stints, die bis zur aktuellen Runde bereits begonnen haben (nicht nur der
    // aktuelle) — zeigt die komplette bisherige Reifenwahl inkl. Boxenstopps.
    const activeStints = stintsByDriver.get(driver.num)
      ?.filter(s => curLapNumber >= s.lapStart)
      .sort((a, b) => a.lapStart - b.lapStart) ?? []
    const tyres = activeStints.map(s => s.compound)
    const currentStint = activeStints[activeStints.length - 1]
    const tyreAge = currentStint ? curLapNumber - currentStint.lapStart + 1 : null

    // Abstand zum Vordermann aus /intervals — der Führende (curPos===1) hat keinen
    // Vordermann, "null" bei anderen Positionen bedeutet meist "überrundet" (OpenF1
    // liefert dafür Text wie "+1 LAP" statt einer Zahl, siehe ReplayTimingService.parseGap).
    const lastInterval = intervalsByDriver.get(driver.num)?.at(-1) ?? null
    let gapText = '—'
    if (curPos === 1) gapText = 'LEADER'
    else if (lastInterval?.gapSec != null) gapText = `+${lastInterval.gapSec.toFixed(3)}`
    else if (lastInterval) gapText = '+1L'

    rows.push({
      num: driver.num,
      abbr: driver.abbr,
      color: driver.color,
      position: curPos || 0,
      posDelta,
      tyres,
      tyreAge,
      lastLapText: lastLap?.duration != null ? formatLapTime(lastLap.duration) : '—',
      sectors: [
        sectorCell(lastLap?.sector1, pbS1.get(driver.num), bestS1),
        sectorCell(lastLap?.sector2, pbS2.get(driver.num), bestS2),
        sectorCell(lastLap?.sector3, pbS3.get(driver.num), bestS3),
      ],
      gapText,
      points: curPos > 0 ? pointsForPosition(curPos) : 0,
    })
  }

  rows.sort((a, b) => (a.position || 999) - (b.position || 999))
  return rows
}

const timingRows = computed(() => buildTimingRows(displaySec.value))

const FLAG_META: Record<string, { cls: string; icon: string; key: string }> = {
  GREEN:          { cls: 'flag-green',  icon: '🟢', key: 'green' },
  CLEAR:          { cls: 'flag-green',  icon: '🟢', key: 'green' },
  YELLOW:         { cls: 'flag-yellow', icon: '🟡', key: 'yellow' },
  'DOUBLE YELLOW':{ cls: 'flag-double', icon: '🟡🟡', key: 'doubleYellow' },
  RED:            { cls: 'flag-red',    icon: '🔴', key: 'red' },
  CHEQUERED:      { cls: 'flag-chequered', icon: '🏁', key: 'chequered' },
  BLUE:           { cls: 'flag-blue',   icon: '🔵', key: 'blue' },
}

// Blaue Flaggen sind fast immer "scope: Driver" (ein einzelner Hinterherfahrer wird
// gebeten, einen Führenden vorbeizulassen) — sie sagen nichts über den GLOBALEN
// Streckenzustand aus und kommen sehr häufig vor. Für das Banner zählt nur der
// streckenweite Zustand (scope Track/Sector), sonst würde das Banner ständig "Blaue
// Flagge" zeigen, nur weil das der zuletzt ausgegebene Eintrag war.
const trackFlags = computed(() => {
  const td = timing.value
  if (!td) return []
  return td.flags.filter(f => f.scope !== 'Driver')
})

const currentFlagInfo = computed(() => {
  const flags = trackFlags.value
  if (!flags.length) return null
  const f = lastAtOrBefore(flags, displaySec.value)
  if (!f) return null
  const meta = FLAG_META[f.flag.toUpperCase()]
  return {
    cls: meta?.cls ?? 'flag-yellow',
    icon: meta?.icon ?? '🏳️',
    label: meta ? t(`race.replay.flag.${meta.key}`) : f.flag,
  }
})

// ---- Animations-Loop ----

// Sidebar-Refresh-Intervall in REALER Zeit (nicht Playback-Zeit) — bei 8x Speed soll
// die Tabelle trotzdem nicht öfter als ~6x/Sekunde neu berechnet werden, sonst kostet
// das eigene (kleinere) Re-Render der Sidebar unnötig Zeit auf dem Haupt-Thread neben
// dem 60fps-Canvas.
const DISPLAY_UPDATE_MS = 150
let lastDisplayTs = 0

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
    displaySec.value = currentSec.value
    return
  }

  drawAtTime(currentSec.value)
  if (ts - lastDisplayTs >= DISPLAY_UPDATE_MS) {
    displaySec.value = currentSec.value
    lastDisplayTs = ts
  }
  rafId = requestAnimationFrame(animate)
}

function togglePlay() {
  if (!data.value) return
  if (currentSec.value >= data.value.duration) currentSec.value = 0
  playing.value = !playing.value
  if (playing.value) {
    lastTs = 0
    lastDisplayTs = 0
    rafId = requestAnimationFrame(animate)
  } else {
    cancelAnimationFrame(rafId)
  }
}

function onScrub(e: Event) {
  const val = parseFloat((e.target as HTMLInputElement).value)
  currentSec.value = val
  displaySec.value = val
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
  const cssW = wrap.clientWidth, cssH = wrap.clientHeight

  // Canvas-Backing-Store an die echte Bildschirm-Pixeldichte anpassen (devicePixelRatio).
  // Ohne das hat das Canvas bei skalierten/HiDPI-Displays (125/150/200% unter Windows,
  // sehr üblich) eine niedrigere interne Auflösung als der Bildschirm — bewegte Objekte
  // "rasten" dann bei jedem Frame auf das nächste interne Pixel ein, was trotz
  // mathematisch glatter Positionsdaten wie Ruckeln aussieht.
  const dpr = window.devicePixelRatio || 1
  canvas.width  = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)
  const ctx = canvas.getContext('2d')
  if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  updateScale(cssW, cssH)
  buildTrackPaths(data.value, cssW, cssH)
  renderTrackLayer(cssW, cssH)
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

.replay-body {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
}

.replay-main {
  flex: 2 1 480px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

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

.time-remaining {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 400;
  color: rgba(255,255,255,0.65);
}

.controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.controls-secondary {
  display: flex;
  justify-content: flex-end;
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

/* ---- Flaggen-Banner ---- */

.flag-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  width: fit-content;
}
.flag-icon { font-size: 14px; }

/* Feste Statusfarben (nicht Teil des UI-Akzents) — an das reale Flaggenprotokoll
   angelehnt, damit Grün/Gelb/Rot immer gleich aussehen, unabhängig vom Theme. */
.flag-banner.flag-green     { background: rgba(12, 163, 12, 0.16);  color: #0ca30c; }
.flag-banner.flag-yellow    { background: rgba(250, 178, 25, 0.16); color: #fab219; }
.flag-banner.flag-double    { background: rgba(236, 131, 90, 0.18); color: #ec835a; }
.flag-banner.flag-red       { background: rgba(208, 59, 59, 0.18);  color: #e66767; }
.flag-banner.flag-chequered { background: rgba(255, 255, 255, 0.08); color: var(--text); }
.flag-banner.flag-blue      { background: rgba(57, 135, 229, 0.18); color: #3987e5; }

/* ---- Live-Timing-Sidebar ---- */

.timing-sidebar {
  flex: 1 1 480px;
  min-width: 400px;
  max-width: 480px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}

.timing-rows {
  max-height: 520px;
  overflow-y: auto;
}

.timing-row {
  display: grid;
  grid-template-columns: 26px 1fr 74px 40px 76px 44px 16px;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-dim);
  border-bottom: 1px solid var(--line);
}
.timing-row:last-child { border-bottom: none; }

.timing-header {
  color: var(--text-faint);
  letter-spacing: 0.02em;
  text-transform: uppercase;
  font-size: 8px;
  background: rgba(255, 255, 255, 0.02);
}
.timing-header span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: clip;
}

.col-pos {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.1;
}
.pos-num { color: var(--text); font-weight: 700; }
.pos-delta { font-size: 9px; }
.pos-up   { color: #0ca30c; }
.pos-down { color: #e66767; }

.col-driver {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--text);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.driver-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.col-tyre {
  display: flex;
  align-items: center;
  gap: 2px;
  overflow: hidden;
}

.tyre-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 13px;
  height: 13px;
  border-radius: 4px;
  font-weight: 700;
  font-size: 7.5px;
  opacity: 0.55;
}
.tyre-badge.tyre-current {
  opacity: 1;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.5);
}
.tyre-badge.tyre-none {
  background: transparent;
  color: var(--text-faint);
  opacity: 1;
}

.tyre-age {
  margin-left: 2px;
  font-size: 9px;
  color: var(--text-faint);
  flex-shrink: 0;
}

.col-gap { text-align: right; }
.col-gap.gap-leader { color: var(--text-faint); }

.col-sectors {
  display: flex;
  gap: 3px;
  overflow: hidden;
}

.sector-chip {
  flex: 1;
  min-width: 0;
  text-align: center;
  border-radius: 4px;
  padding: 2px 1px;
  font-size: 10px;
  font-weight: 600;
  color: #111;
  background: var(--text-faint);
  overflow: hidden;
  white-space: nowrap;
}
.sector-chip.sector-none    { background: transparent; color: var(--text-faint); }
.sector-chip.sector-normal  { background: #fab219; color: #111; }
.sector-chip.sector-personal{ background: #0ca30c; color: #fff; }
.sector-chip.sector-best    { background: #9085e9; color: #111; }

.col-lap, .col-gap, .col-pts { color: var(--text-dim); }
.col-pts { text-align: right; font-weight: 700; }
</style>
