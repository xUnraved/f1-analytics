<template>
  <div ref="stageEl" class="globe-stage">
    <canvas ref="canvasEl" class="globe-canvas"></canvas>
    <div ref="labelsEl" class="labels"></div>
    <div ref="tipEl" class="globe-tip">
      <div ref="tipGpEl" class="gp"></div>
      <div ref="tipSubEl" class="sub"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as THREE from 'three'
import { COAST } from '@/assets/coastline'
import { useSeasonStore } from '@/stores/seasonStore'
import type { Race } from '@/types/f1'

const emit = defineEmits<{ select: [index: number] }>()
const store = useSeasonStore()

const stageEl = ref<HTMLElement | null>(null)
const canvasEl = ref<HTMLCanvasElement | null>(null)
const labelsEl = ref<HTMLElement | null>(null)
const tipEl = ref<HTMLElement | null>(null)
const tipGpEl = ref<HTMLElement | null>(null)
const tipSubEl = ref<HTMLElement | null>(null)

const R = 1.6
const AUTO = 0.0015
const ZMIN = 5.6
const ZMAX = 11.5

let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera
let renderer: THREE.WebGLRenderer | null = null
let globeGroup: THREE.Group
let markerGroup: THREE.Group
let raycaster: THREE.Raycaster
let mouse: THREE.Vector2
let pinTex: THREE.CanvasTexture
let frame = 0

let markers: THREE.Sprite[] = []
let labelDivs: HTMLDivElement[] = []

let drag = false
let lastX = 0
let lastY = 0
let camZ = 7.6
let pinchDist = 0
let reduced = false
const pointers = new Map<number, { x: number; y: number }>()

function latLonToVec3(lat: number, lon: number, r: number): THREE.Vector3 {
  const phi = ((90 - lat) * Math.PI) / 180
  const theta = ((lon + 180) * Math.PI) / 180
  return new THREE.Vector3(
    -(r * Math.sin(phi) * Math.cos(theta)),
    r * Math.cos(phi),
    r * Math.sin(phi) * Math.sin(theta),
  )
}

function makeEarthTexture(): THREE.CanvasTexture {
  const W = 2048
  const H = 1024
  const c = document.createElement('canvas')
  c.width = W
  c.height = H
  const g = c.getContext('2d')!
  const grd = g.createLinearGradient(0, 0, 0, H)
  grd.addColorStop(0, '#0a1622')
  grd.addColorStop(0.5, '#0e2030')
  grd.addColorStop(1, '#0a1622')
  g.fillStyle = grd
  g.fillRect(0, 0, W, H)
  g.strokeStyle = 'rgba(120,150,185,0.07)'
  g.lineWidth = 1
  for (let lo = -180; lo <= 180; lo += 30) {
    const x = ((lo + 180) / 360) * W
    g.beginPath()
    g.moveTo(x, 0)
    g.lineTo(x, H)
    g.stroke()
  }
  for (let la = -60; la <= 60; la += 30) {
    const y = ((90 - la) / 180) * H
    g.beginPath()
    g.moveTo(0, y)
    g.lineTo(W, y)
    g.stroke()
  }
  const proj = (lo: number, la: number): [number, number] => [((lo + 180) / 360) * W, ((90 - la) / 180) * H]
  g.fillStyle = '#1b3445'
  g.strokeStyle = 'rgba(90,150,200,0.22)'
  g.lineWidth = 0.8
  COAST.forEach((poly) => {
    g.beginPath()
    poly.forEach((p, i) => {
      const [x, y] = proj(p[0]!, p[1]!)
      if (i) g.lineTo(x, y)
      else g.moveTo(x, y)
    })
    g.closePath()
    g.fill()
    g.stroke()
  })
  const t = new THREE.CanvasTexture(c)
  t.anisotropy = 4
  return t
}

function makePinTexture(): THREE.CanvasTexture {
  const w = 64
  const h = 84
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const g = c.getContext('2d')!
  g.fillStyle = '#fff'
  g.beginPath()
  g.arc(32, 28, 24, Math.PI, 0, false)
  g.bezierCurveTo(56, 46, 40, 58, 32, 82)
  g.bezierCurveTo(24, 58, 8, 46, 8, 28)
  g.closePath()
  g.fill()
  g.fillStyle = '#0a0e13'
  g.beginPath()
  g.arc(32, 28, 10, 0, Math.PI * 2)
  g.fill()
  return new THREE.CanvasTexture(c)
}

function buildMarkers(races: Race[]) {
  if (!markerGroup || !labelsEl.value) return
  markers.forEach((m) => {
    markerGroup.remove(m)
    ;(m.material as THREE.SpriteMaterial).dispose()
  })
  markers = []
  labelsEl.value.innerHTML = ''
  labelDivs = []

  races.forEach((race, i) => {
    if (!race.lat && !race.lon) return
    const pos = latLonToVec3(race.lat, race.lon, R + 0.01)
    const spr = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: pinTex,
        color: race.completed ? 0xff2a2a : 0x7c8696,
        transparent: true,
        depthTest: false,
        depthWrite: false,
      }),
    )
    spr.center.set(0.5, 0)
    spr.scale.set(0.13, 0.171, 1)
    spr.position.copy(pos)
    spr.renderOrder = 2
    spr.userData = { idx: i, baseW: 0.13, baseH: 0.171 }
    markerGroup.add(spr)
    markers.push(spr)

    const el = document.createElement('div')
    el.className = race.completed ? 'pin-label' : 'pin-label upcoming'
    el.textContent = race.gp
    el.style.display = 'none'
    labelsEl.value!.appendChild(el)
    labelDivs.push(el)
  })
}

function pick(e: PointerEvent): number | null {
  if (!canvasEl.value) return null
  const rect = canvasEl.value.getBoundingClientRect()
  mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(mouse, camera)
  const hits = raycaster.intersectObjects(
    markers.filter((m) => m.visible),
    false,
  )
  return hits.length ? (hits[0]!.object.userData.idx as number) : null
}

function hover(e: PointerEvent) {
  const canvas = canvasEl.value!
  const idx = pick(e)
  markers.forEach((m) => m.scale.set(m.userData.baseW, m.userData.baseH, 1))
  if (idx != null) {
    const m = markers[idx]!
    m.scale.set(m.userData.baseW * 1.4, m.userData.baseH * 1.4, 1)
    canvas.style.cursor = 'pointer'
    const race = store.races[idx]!
    const rect = canvas.getBoundingClientRect()
    tipEl.value!.style.left = `${e.clientX - rect.left}px`
    tipEl.value!.style.top = `${e.clientY - rect.top}px`
    tipGpEl.value!.textContent = `GP ${race.gp}`
    tipSubEl.value!.textContent = `RUNDE ${String(idx + 1).padStart(2, '0')} · ${race.circuit}`
    tipEl.value!.classList.add('show')
  } else {
    canvas.style.cursor = 'grab'
    hideTip()
  }
}

function hideTip() {
  tipEl.value?.classList.remove('show')
}

function setZoom(z: number) {
  camZ = Math.max(ZMIN, Math.min(ZMAX, z))
  if (camera) camera.position.z = camZ
}

function twoDist(): number {
  const p = [...pointers.values()]
  return Math.hypot(p[0]!.x - p[1]!.x, p[0]!.y - p[1]!.y)
}

function onWheel(e: WheelEvent) {
  e.preventDefault()
  setZoom(camZ + e.deltaY * 0.0026)
}

function onPointerDown(e: PointerEvent) {
  try {
    canvasEl.value!.setPointerCapture(e.pointerId)
  } catch {
    /* ignore */
  }
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
  if (pointers.size === 1) {
    drag = true
    lastX = e.clientX
    lastY = e.clientY
  } else if (pointers.size === 2) {
    drag = false
    pinchDist = twoDist()
  }
}

function endPointer(e: PointerEvent) {
  if (!pointers.has(e.pointerId)) return
  pointers.delete(e.pointerId)
  if (pointers.size < 2) pinchDist = 0
  if (pointers.size === 0) {
    drag = false
  } else {
    const p = [...pointers.values()][0]!
    drag = true
    lastX = p.x
    lastY = p.y
  }
}

function onPointerMove(e: PointerEvent) {
  if (pointers.has(e.pointerId)) pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
  if (pointers.size >= 2) {
    const d = twoDist()
    if (pinchDist) setZoom(camZ - (d - pinchDist) * 0.012)
    pinchDist = d
    return
  }
  if (drag) {
    const dx = e.clientX - lastX
    const dy = e.clientY - lastY
    lastX = e.clientX
    lastY = e.clientY
    globeGroup.rotation.y += dx * 0.006
    globeGroup.rotation.x = Math.max(-0.9, Math.min(0.9, globeGroup.rotation.x + dy * 0.006))
    return
  }
  hover(e)
}

function onClick(e: PointerEvent) {
  const idx = pick(e)
  if (idx != null) {
    store.openRace(idx)
    emit('select', idx)
  }
}

function resetPointers() {
  pointers.clear()
  drag = false
  pinchDist = 0
}

function updateOverlays() {
  if (!renderer) return
  markerGroup.updateMatrixWorld()
  const el = renderer.domElement
  const wp = new THREE.Vector3()
  markers.forEach((m, i) => {
    wp.copy(m.position).applyMatrix4(markerGroup.matrixWorld)
    const facing = wp.clone().normalize().dot(camera.position.clone().sub(wp).normalize())
    m.visible = facing > 0.12
    const lab = labelDivs[i]!
    if (facing > 0.3) {
      const ndc = wp.clone().project(camera)
      lab.style.left = `${(ndc.x * 0.5 + 0.5) * el.clientWidth}px`
      lab.style.top = `${(-ndc.y * 0.5 + 0.5) * el.clientHeight}px`
      lab.style.opacity = String(Math.min(1, (facing - 0.3) / 0.25))
      lab.style.display = 'block'
    } else {
      lab.style.display = 'none'
    }
  })
}

function resize() {
  if (!renderer || !stageEl.value) return
  const w = stageEl.value.clientWidth
  const h = stageEl.value.clientHeight
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

function animate() {
  frame = requestAnimationFrame(animate)
  if (!renderer || !scene) return
  if (!reduced && pointers.size === 0) globeGroup.rotation.y += AUTO
  updateOverlays()
  renderer.render(scene, camera)
}

onMounted(() => {
  try {
    reduced = window.matchMedia('(prefers-reduced-motion:reduce)').matches
    const canvas = canvasEl.value!
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(38, 1, 0.1, 100)
    camera.position.set(0, 0, camZ)

    globeGroup = new THREE.Group()
    scene.add(globeGroup)
    globeGroup.add(
      new THREE.Mesh(new THREE.SphereGeometry(R, 64, 64), new THREE.MeshBasicMaterial({ map: makeEarthTexture() })),
    )

    const atmMat = new THREE.ShaderMaterial({
      uniforms: { glow: { value: new THREE.Color(0x3d7fff) }, strength: { value: 0.8 } },
      vertexShader:
        'varying vec3 vN;varying vec3 vP;void main(){vN=normalize(normalMatrix*normal);vec4 mv=modelViewMatrix*vec4(position,1.0);vP=mv.xyz;gl_Position=projectionMatrix*mv;}',
      fragmentShader:
        'varying vec3 vN;varying vec3 vP;uniform vec3 glow;uniform float strength;void main(){vec3 v=normalize(-vP);float r=1.0-max(dot(v,vN),0.0);r=pow(r,3.0);gl_FragColor=vec4(glow,r*strength);}',
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
    scene.add(new THREE.Mesh(new THREE.SphereGeometry(R * 1.1, 48, 48), atmMat))

    markerGroup = new THREE.Group()
    globeGroup.add(markerGroup)
    globeGroup.rotation.x = 0.32
    globeGroup.rotation.y = -1.4

    raycaster = new THREE.Raycaster()
    mouse = new THREE.Vector2()
    pinTex = makePinTexture()

    buildMarkers(store.races)

    canvas.addEventListener('wheel', onWheel, { passive: false })
    canvas.addEventListener('pointerdown', onPointerDown)
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('click', onClick)
    canvas.addEventListener('pointerleave', hideTip)
    window.addEventListener('pointerup', endPointer)
    window.addEventListener('pointercancel', endPointer)
    window.addEventListener('blur', resetPointers)
    window.addEventListener('resize', resize)

    resize()
    animate()
  } catch (err) {
    console.error('GlobeView init failed:', err)
  }
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  window.removeEventListener('pointerup', endPointer)
  window.removeEventListener('pointercancel', endPointer)
  window.removeEventListener('blur', resetPointers)
  window.removeEventListener('resize', resize)
  renderer?.dispose()
})

watch(
  () => store.races,
  (races) => {
    if (markerGroup) buildMarkers(races)
  },
)
</script>

<style scoped>
.globe-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  max-height: min(880px, 82vh);
  overflow: visible;
}

.globe-stage::before {
  content: '';
  position: absolute;
  inset: 6%;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, rgba(61, 127, 255, 0.13), transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.globe-canvas {
  width: 100%;
  height: 100%;
  display: block;
  cursor: grab;
  position: relative;
  z-index: 1;
}

.globe-canvas:active {
  cursor: grabbing;
}

.labels {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 2;
}

:deep(.pin-label) {
  position: absolute;
  transform: translate(-50%, -220%);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  color: #e4eaf2;
  background: rgba(8, 11, 16, 0.6);
  border: 1px solid rgba(120, 140, 170, 0.22);
  padding: 2px 7px;
  border-radius: 6px;
  white-space: nowrap;
  transition: opacity 0.12s;
}

:deep(.pin-label.upcoming) {
  color: #9aa6b6;
  border-color: rgba(120, 140, 170, 0.18);
  opacity: 0.85;
}

.globe-tip {
  position: absolute;
  pointer-events: none;
  z-index: 6;
  background: rgba(8, 11, 16, 0.94);
  border: 1px solid var(--accent);
  border-radius: 8px;
  padding: 8px 12px;
  transform: translate(-50%, -150%);
  opacity: 0;
  transition: opacity 0.12s;
}

.globe-tip.show {
  opacity: 1;
}

.globe-tip .gp {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 14px;
  color: #fff;
}

.globe-tip .sub {
  font-family: var(--font-mono);
  font-size: 10px;
  color: #9aa6b6;
  letter-spacing: 0.08em;
  margin-top: 2px;
}

@media (max-aspect-ratio: 13/16) {
  .globe-stage {
    width: 108%;
    margin: 0 -4%;
    max-height: 70vh;
  }
}
</style>
