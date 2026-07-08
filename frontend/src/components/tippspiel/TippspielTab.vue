<template>
  <div class="panel">
    <div v-if="!auth.initialized" class="loading">
      <LoadingBar :label="t('tippspiel.loading')" />
    </div>

    <section v-else-if="!auth.isLoggedIn" class="auth-shell">
      <div class="auth-hero">
        <div class="hero-emoji" aria-hidden="true">🏁</div>
        <h1 class="hero-title">{{ t('tippspiel.hero.title') }}</h1>
        <p class="hero-sub">{{ t('tippspiel.hero.subtitle') }}</p>
        <ul class="hero-bullets mono">
          <li>{{ t('tippspiel.hero.bullet1', { pts: 10 }) }}</li>
          <li>{{ t('tippspiel.hero.bullet2', { pts: 5 }) }}</li>
          <li>{{ t('tippspiel.hero.bullet3', { pts: 5 }) }}</li>
          <li>{{ t('tippspiel.hero.bullet4', { pts: 3 }) }}</li>
          <li>{{ t('tippspiel.hero.bullet5', { pts: 4 }) }}</li>
        </ul>
      </div>

      <div class="auth-card">
        <div class="auth-tabs">
          <button :class="{ on: mode === 'login' }" @click="mode = 'login'; clearAuthError()">{{ t('tippspiel.login') }}</button>
          <button :class="{ on: mode === 'register' }" @click="mode = 'register'; clearAuthError()">{{ t('tippspiel.register') }}</button>
        </div>

        <form v-if="mode === 'login'" class="auth-form" @submit.prevent="doLogin">
          <label class="field">
            <span class="lbl mono">{{ t('tippspiel.loginForm.userOrEmail') }}</span>
            <input v-model.trim="loginValue" type="text" required autocomplete="username" maxlength="128" />
          </label>
          <label class="field">
            <span class="lbl mono">{{ t('tippspiel.loginForm.password') }}</span>
            <input v-model="loginPassword" type="password" required autocomplete="current-password" maxlength="128" />
          </label>
          <button type="submit" class="btn primary big" :disabled="auth.loading">
            {{ auth.loading ? t('tippspiel.loginForm.submitting') : t('tippspiel.loginForm.submit') }}
          </button>
          <p v-if="auth.error" class="auth-error">{{ auth.error }}</p>
        </form>

        <form v-else class="auth-form" @submit.prevent="doRegister">
          <label class="field">
            <span class="lbl mono">{{ t('tippspiel.registerForm.username') }}</span>
            <input v-model.trim="regUsername" type="text" required minlength="3" maxlength="32" pattern="[A-Za-z0-9_\-]+" autocomplete="username" />
          </label>
          <label class="field">
            <span class="lbl mono">{{ t('tippspiel.registerForm.email') }}</span>
            <input v-model.trim="regEmail" type="email" required maxlength="128" autocomplete="email" />
          </label>
          <label class="field">
            <span class="lbl mono">{{ t('tippspiel.registerForm.password') }}</span>
            <input v-model="regPassword" type="password" required minlength="6" maxlength="128" autocomplete="new-password" />
          </label>
          <button type="submit" class="btn primary big" :disabled="auth.loading">
            {{ auth.loading ? t('tippspiel.registerForm.submitting') : t('tippspiel.registerForm.submit') }}
          </button>
          <p v-if="auth.error" class="auth-error">{{ auth.error }}</p>
        </form>
      </div>
    </section>

    <div v-else-if="dataLoading" class="loading">
      <LoadingBar :label="dataLoadLabel" :pct="dataLoadPct" />
    </div>

    <div v-else style="display: contents">
      <section class="account-bar">
        <div class="account-info">
          <span class="avatar" :style="{ background: (auth.user?.color ?? '#888888') }">{{ initial((auth.user?.username ?? '')) }}</span>
          <div>
            <div class="user-nick">{{ (auth.user?.username ?? '') }}</div>
            <div class="user-meta mono">
              <span class="pts-pill">{{ myTotalPoints }} {{ t('tippspiel.bar.pts') }}</span>
              <span>{{ mySettled }} {{ t('tippspiel.bar.settled') }}</span>
              <span>{{ myOpen }} {{ t('tippspiel.bar.open') }}</span>
            </div>
          </div>
        </div>
        <button class="btn ghost small" @click="auth.logout">{{ t('tippspiel.bar.logout') }}</button>
      </section>

      <section v-if="upcoming.length" class="race-strip">
        <button v-for="r in upcoming" :key="r.round" type="button" class="race-pill" :class="{ on: selectedRound === r.round }" @click="selectedRound = r.round">
          <span class="rp-round mono">R{{ r.round }}</span>
          <span class="rp-name">{{ r.gp }}</span>
          <span class="rp-date mono">{{ shortDate(r.date) }}</span>
        </button>
      </section>

      <section v-if="currentRace" class="race-hero" :style="{ backgroundImage: heroBg(currentRace) }">
        <div class="race-hero-inner">
          <div class="rh-meta mono">
            <span v-if="currentRace.countryFlag" class="flag-wrap"><img :src="currentRace.countryFlag" :alt="currentRace.country" /></span>
            {{ currentRace.country }} · R{{ currentRace.round }} · {{ formatDate(currentRace.date) }}
          </div>
          <h2 class="rh-title">{{ currentRace.gp }}</h2>
          <div class="rh-countdown mono">
            <span v-if="daysUntil(currentRace.date) > 0">
              <b>{{ daysUntil(currentRace.date) }}</b> {{ daysUntil(currentRace.date) === 1 ? t('tippspiel.day') : t('tippspiel.days') }}{{ t('tippspiel.daysUntil') }}
            </span>
            <span v-else class="hot">{{ t('tippspiel.today') }}</span>
          </div>
        </div>
      </section>

      <section v-if="currentRace" class="tips-grid">
        <article class="tip-card winner-card" :class="{ done: !!myTipps.WINNER }">
          <div class="tip-head">
            <div class="tip-cat-row">
              <span class="tip-icon" aria-hidden="true">🏆</span>
              <span class="tip-cat mono">{{ t('tippspiel.tip.winner') }}</span>
            </div>
            <span class="tip-pts mono">+10</span>
          </div>
          <p class="tip-desc">{{ t('tippspiel.tip.winnerQ') }}</p>
          <div class="picker">
            <select v-model="picks.WINNER">
              <option value="">{{ t('tippspiel.tip.choose') }}</option>
              <option v-for="d in driverOptions" :key="d.abbr" :value="d.abbr">{{ d.abbr }} · {{ d.name }}</option>
            </select>
            <button type="button" class="btn primary" :disabled="!picks.WINNER || saving.WINNER" @click="save('WINNER')">
              {{ myTipps.WINNER ? t('tippspiel.tip.change') : t('tippspiel.tip.bet') }}
            </button>
          </div>
          <div v-if="myTipps.WINNER" class="my-tipp">
            <span class="mt-pick">{{ myTipps.WINNER.pick }}</span>
            <span class="mt-status mono">{{ t('tippspiel.tip.submitted') }}</span>
          </div>
        </article>

        <article class="tip-card pole-card" :class="{ done: !!myTipps.POLE }">
          <div class="tip-head">
            <div class="tip-cat-row">
              <span class="tip-icon" aria-hidden="true">⚡</span>
              <span class="tip-cat mono">{{ t('tippspiel.tip.pole') }}</span>
            </div>
            <span class="tip-pts mono">+5</span>
          </div>
          <p class="tip-desc">{{ t('tippspiel.tip.poleQ') }}</p>
          <div class="picker">
            <select v-model="picks.POLE">
              <option value="">{{ t('tippspiel.tip.choose') }}</option>
              <option v-for="d in driverOptions" :key="d.abbr" :value="d.abbr">{{ d.abbr }} · {{ d.name }}</option>
            </select>
            <button type="button" class="btn primary" :disabled="!picks.POLE || saving.POLE" @click="save('POLE')">
              {{ myTipps.POLE ? t('tippspiel.tip.change') : t('tippspiel.tip.bet') }}
            </button>
          </div>
          <div v-if="myTipps.POLE" class="my-tipp">
            <span class="mt-pick">{{ myTipps.POLE.pick }}</span>
            <span class="mt-status mono">{{ t('tippspiel.tip.submitted') }}</span>
          </div>
        </article>

        <article class="tip-card podium-card" :class="{ done: !!myTipps.PODIUM }">
          <div class="tip-head">
            <div class="tip-cat-row">
              <span class="tip-icon" aria-hidden="true">🥇</span>
              <span class="tip-cat mono">{{ t('tippspiel.tip.podium') }}</span>
            </div>
            <span class="tip-pts mono">{{ t('tippspiel.tip.podiumPts') }}</span>
          </div>
          <p class="tip-desc">{{ t('tippspiel.tip.podiumQ') }}</p>
          <div class="podium-picks">
            <div class="podium-slot">
              <span class="ps-pos mono">P1</span>
              <select v-model="podiumPicks[0]">
                <option value="">—</option>
                <option v-for="d in podiumOptions(0)" :key="'p0-' + d.abbr" :value="d.abbr">{{ d.abbr }}</option>
              </select>
            </div>
            <div class="podium-slot">
              <span class="ps-pos mono">P2</span>
              <select v-model="podiumPicks[1]">
                <option value="">—</option>
                <option v-for="d in podiumOptions(1)" :key="'p1-' + d.abbr" :value="d.abbr">{{ d.abbr }}</option>
              </select>
            </div>
            <div class="podium-slot">
              <span class="ps-pos mono">P3</span>
              <select v-model="podiumPicks[2]">
                <option value="">—</option>
                <option v-for="d in podiumOptions(2)" :key="'p2-' + d.abbr" :value="d.abbr">{{ d.abbr }}</option>
              </select>
            </div>
            <button type="button" class="btn primary" :disabled="!podiumComplete || saving.PODIUM" @click="save('PODIUM')">
              {{ myTipps.PODIUM ? t('tippspiel.tip.change') : t('tippspiel.tip.bet') }}
            </button>
          </div>
          <div v-if="myTipps.PODIUM" class="my-tipp">
            <span class="mt-pick">{{ formatPodium(myTipps.PODIUM.pick) }}</span>
            <span class="mt-status mono">{{ t('tippspiel.tip.submitted') }}</span>
          </div>
        </article>

        <article class="tip-card fl-card" :class="{ done: !!myTipps.FASTEST_LAP }">
          <div class="tip-head">
            <div class="tip-cat-row">
              <span class="tip-icon" aria-hidden="true">⏱</span>
              <span class="tip-cat mono">{{ t('tippspiel.tip.fastest') }}</span>
            </div>
            <span class="tip-pts mono">+3</span>
          </div>
          <p class="tip-desc">{{ t('tippspiel.tip.fastestQ') }}</p>
          <div class="picker">
            <select v-model="picks.FASTEST_LAP">
              <option value="">{{ t('tippspiel.tip.choose') }}</option>
              <option v-for="d in driverOptions" :key="d.abbr" :value="d.abbr">{{ d.abbr }} · {{ d.name }}</option>
            </select>
            <button type="button" class="btn primary" :disabled="!picks.FASTEST_LAP || saving.FASTEST_LAP" @click="save('FASTEST_LAP')">
              {{ myTipps.FASTEST_LAP ? t('tippspiel.tip.change') : t('tippspiel.tip.bet') }}
            </button>
          </div>
          <div v-if="myTipps.FASTEST_LAP" class="my-tipp">
            <span class="mt-pick">{{ myTipps.FASTEST_LAP.pick }}</span>
            <span class="mt-status mono">{{ t('tippspiel.tip.submitted') }}</span>
          </div>
        </article>

        <article class="tip-card h2h-card" :class="{ done: !!myTipps.H2H }">
          <div class="tip-head">
            <div class="tip-cat-row">
              <span class="tip-icon" aria-hidden="true">⚔️</span>
              <span class="tip-cat mono">{{ t('tippspiel.tip.h2h') }}</span>
            </div>
            <span class="tip-pts mono">+4</span>
          </div>
          <p class="tip-desc">{{ t('tippspiel.tip.h2hQ') }}</p>
          <div class="podium-picks">
            <div class="podium-slot">
              <span class="ps-pos mono">A</span>
              <select v-model="h2hPicks[0]">
                <option value="">—</option>
                <option v-for="d in h2hOptions(0)" :key="'h0-' + d.abbr" :value="d.abbr">{{ d.abbr }}</option>
              </select>
            </div>
            <span class="h2h-vs mono">{{ t('tippspiel.tip.h2hVs') }}</span>
            <div class="podium-slot">
              <span class="ps-pos mono">B</span>
              <select v-model="h2hPicks[1]">
                <option value="">—</option>
                <option v-for="d in h2hOptions(1)" :key="'h1-' + d.abbr" :value="d.abbr">{{ d.abbr }}</option>
              </select>
            </div>
            <button type="button" class="btn primary" :disabled="!h2hComplete || saving.H2H" @click="save('H2H')">
              {{ myTipps.H2H ? t('tippspiel.tip.change') : t('tippspiel.tip.bet') }}
            </button>
          </div>
          <div v-if="myTipps.H2H" class="my-tipp">
            <span class="mt-pick">{{ formatH2H(myTipps.H2H.pick) }}</span>
            <span class="mt-status mono">{{ t('tippspiel.tip.submitted') }}</span>
          </div>
        </article>
      </section>

      <ForecastPanel />

      <section v-if="currentRace && hasCrowdPicks" class="card community-card">
        <div class="card-title">{{ t('tippspiel.community.title') }}{{ currentRace.round }}</div>
        <div class="crowd-grid">
          <div v-for="cat in categories" :key="cat" class="crowd-col">
            <div class="crowd-label mono">
              <span class="crowd-icon" aria-hidden="true">{{ categoryIcon(cat) }}</span>
              {{ categoryLabel(cat) }}
            </div>
            <div v-if="crowdByCat[cat].length" class="crowd-bars">
              <div v-for="(p, i) in crowdByCat[cat].slice(0, 4)" :key="cat + '-' + i" class="crowd-row" :class="{ leader: i === 0 }">
                <span class="cr-pick">{{ formatCrowdPick(cat, p.pick) }}</span>
                <div class="cr-track">
                  <div class="cr-fill" :style="`width:${crowdPct(cat, p.count)}%`"></div>
                </div>
                <span class="cr-pct mono">{{ crowdPct(cat, p.count) }}%</span>
              </div>
            </div>
            <div v-else class="crowd-empty mono">{{ t('tippspiel.community.noTips') }}</div>
          </div>
        </div>
      </section>

      <section class="card lb-card">
        <div class="card-title">{{ t('tippspiel.leaderboard.title') }} {{ store.year }}</div>
        <div v-if="leaderboard.length" class="lb-list">
          <div v-for="(entry, i) in leaderboard" :key="entry.username" class="lb-row" :class="{ me: entry.username === (auth.user ? auth.user.username : ''), top: i < 3 }">
            <span class="lb-rank">
              <span v-if="i === 0" class="medal" aria-hidden="true">🥇</span>
              <span v-else-if="i === 1" class="medal" aria-hidden="true">🥈</span>
              <span v-else-if="i === 2" class="medal" aria-hidden="true">🥉</span>
              <span v-else class="mono">{{ i + 1 }}</span>
            </span>
            <span class="lb-avatar" :style="{ background: entry.color }">{{ initial(entry.username) }}</span>
            <span class="lb-nick">
              {{ entry.username }}
              <span v-if="entry.username === (auth.user?.username ?? '')" class="me-tag mono">{{ t('tippspiel.leaderboard.you') }}</span>
            </span>
            <span class="lb-stats mono">{{ entry.settledTipps }} {{ t('tippspiel.bar.settled') }} · {{ entry.openTipps }} {{ t('tippspiel.bar.open') }}</span>
            <span class="lb-points">{{ entry.totalPoints }}</span>
          </div>
        </div>
        <div v-else class="empty">{{ t('tippspiel.leaderboard.empty') }}</div>
      </section>

      <GroupsPanel />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Tippspiel-Hauptkomponente (Betting-Tab).
 *
 * Zeigt je nach Anmeldestatus:
 *  - Nicht angemeldet: Hero-Card mit Regelübersicht + Login/Registrierungsformular.
 *  - Angemeldet: Rennen-Strip (upcoming), Race-Hero-Banner, 5 Tipp-Karten
 *    (WINNER/POLE/PODIUM/FASTEST_LAP/H2H), ForecastPanel, Community-Picks,
 *    Gesamt-Leaderboard und GroupsPanel.
 *
 * Tipp-Kategorien und Punkte:
 *  WINNER=10, POLE=5, PODIUM=5 (je Position korrekt), FASTEST_LAP=3, H2H=4.
 *
 * PODIUM-Pick: 3 Slots P1/P2/P3 mit Duplikat-Ausschluss.
 * H2H-Pick: Fahrer A › Fahrer B (wird mit | kodiert, z. B. "VER|HAM").
 *
 * dataLoadStep + dataLoadPct: Fortschrittsanzeige beim Laden (4 API-Calls).
 * reloadAll(): lädt upcoming, myTipps, crowd und leaderboard sequenziell/parallel.
 */
import { ref, computed, onMounted, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import { useAuthStore, authHeaders } from '@/stores/authStore'
import LoadingBar from '@/components/ui/LoadingBar.vue'
import ForecastPanel from '@/components/tippspiel/ForecastPanel.vue'
import GroupsPanel from '@/components/tippspiel/GroupsPanel.vue'

const { t, locale } = useI18n()
const store = useSeasonStore()
const auth = useAuthStore()

const dataLoading = ref(false)
const dataLoadStep = ref(0)
const DATA_LOAD_TOTAL = 4
const dataLoadPct = computed(() => Math.round((dataLoadStep.value / DATA_LOAD_TOTAL) * 100))
const dataLoadLabel = computed(() => {
  if (dataLoadStep.value === 0) return t('tippspiel.loadingSteps.upcoming')
  if (dataLoadStep.value === 1) return t('tippspiel.loadingSteps.tipps')
  return t('tippspiel.loadingSteps.leaderboard')
})

interface Tipp {
  id: number
  username: string
  userColor: string
  year: number
  round: number
  category: string
  pick: string
  points: number | null
  createdAt: string
  settledAt: string | null
}
interface LeaderboardEntry {
  username: string
  color: string
  totalPoints: number
  settledTipps: number
  openTipps: number
}
interface CrowdPickItem {
  category: string
  pick: string
  count: number
}
interface RaceInfo {
  year: number
  round: number
  gp: string
  country: string
  date: string
  countryFlag: string | null
  circuitImage: string | null
  tipsOpen: boolean
}

const categories = ['WINNER', 'POLE', 'PODIUM', 'FASTEST_LAP', 'H2H'] as const
type Cat = (typeof categories)[number]

const mode = ref<'login' | 'register'>('login')
const loginValue = ref('')
const loginPassword = ref('')
const regUsername = ref('')
const regEmail = ref('')
const regPassword = ref('')

const upcoming = ref<RaceInfo[]>([])
const selectedRound = ref<number | null>(null)
const myAllTipps = ref<Tipp[]>([])
const leaderboard = ref<LeaderboardEntry[]>([])
const crowdRaw = ref<CrowdPickItem[]>([])

const picks = reactive<Record<Cat, string>>({ WINNER: '', POLE: '', PODIUM: '', FASTEST_LAP: '', H2H: '' })
const podiumPicks = ref<string[]>(['', '', ''])
const h2hPicks = ref<string[]>(['', ''])
const saving = reactive<Record<Cat, boolean>>({ WINNER: false, POLE: false, PODIUM: false, FASTEST_LAP: false, H2H: false })

const currentRace = computed<RaceInfo | null>(
  () => upcoming.value.find((r) => r.round === selectedRound.value) ?? upcoming.value[0] ?? null,
)

const driverOptions = computed(() => store.drivers.map((d) => ({ abbr: d.abbr, name: d.name })))

function podiumOptions(slot: number) {
  const taken = new Set(podiumPicks.value.filter((_, i) => i !== slot).filter(Boolean))
  return driverOptions.value.filter((d) => !taken.has(d.abbr))
}

const podiumComplete = computed(() => podiumPicks.value.every((p) => p) && new Set(podiumPicks.value).size === 3)

function h2hOptions(slot: number) {
  const other = h2hPicks.value[slot === 0 ? 1 : 0]
  return driverOptions.value.filter((d) => d.abbr !== other)
}
const h2hComplete = computed(() => !!h2hPicks.value[0] && !!h2hPicks.value[1] && h2hPicks.value[0] !== h2hPicks.value[1])

function formatH2H(pick: string): string {
  const [a, b] = pick.split('|')
  return (a ?? '?') + ' › ' + (b ?? '?')
}

const myTipps = computed<Record<Cat, Tipp | null>>(() => {
  const r: Record<Cat, Tipp | null> = { WINNER: null, POLE: null, PODIUM: null, FASTEST_LAP: null, H2H: null }
  if (!currentRace.value) return r
  for (const tipp of myAllTipps.value) {
    if (tipp.round === currentRace.value.round && (tipp.category as Cat) in r) {
      r[tipp.category as Cat] = tipp
    }
  }
  return r
})

const myTotalPoints = computed(() => myAllTipps.value.reduce((s, tipp) => s + (tipp.points ?? 0), 0))
const mySettled = computed(() => myAllTipps.value.filter((tipp) => tipp.points != null).length)
const myOpen = computed(() => myAllTipps.value.filter((tipp) => tipp.points == null).length)

const crowdByCat = computed<Record<Cat, CrowdPickItem[]>>(() => {
  const r: Record<Cat, CrowdPickItem[]> = { WINNER: [], POLE: [], PODIUM: [], FASTEST_LAP: [], H2H: [] }
  for (const p of crowdRaw.value) {
    if ((p.category as Cat) in r) r[p.category as Cat].push(p)
  }
  return r
})

const hasCrowdPicks = computed(() => crowdRaw.value.length > 0)

function crowdPct(cat: Cat, count: number): number {
  const total = crowdByCat.value[cat].reduce((s, p) => s + p.count, 0)
  return total ? Math.round((count / total) * 100) : 0
}

function categoryLabel(cat: Cat): string {
  if (cat === 'WINNER') return t('tippspiel.tip.winner')
  if (cat === 'POLE') return t('tippspiel.tip.pole')
  if (cat === 'PODIUM') return t('tippspiel.tip.podium')
  if (cat === 'H2H') return t('tippspiel.tip.h2h')
  return t('tippspiel.tip.fastest')
}
function categoryIcon(cat: Cat): string {
  return cat === 'WINNER' ? '🏆' : cat === 'POLE' ? '⚡' : cat === 'PODIUM' ? '🥇' : cat === 'H2H' ? '⚔️' : '⏱'
}

function formatPodium(pick: string): string {
  return pick.split('|').map((p, i) => `P${i + 1} ${p}`).join(' · ')
}
function formatCrowdPick(cat: Cat, pick: string): string {
  if (cat === 'PODIUM') return pick.replace(/\|/g, '·')
  if (cat === 'H2H') return pick.replace('|', ' › ')
  return pick
}

function initial(s: string): string {
  return s ? s.trim().charAt(0).toUpperCase() : '?'
}

function formatDate(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso + 'T00:00:00')
  if (isNaN(d.getTime())) return iso
  const lang = locale.value === 'en' ? 'en-US' : 'de-DE'
  return d.toLocaleDateString(lang, { day: '2-digit', month: 'long', year: 'numeric' })
}
function shortDate(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso + 'T00:00:00')
  if (isNaN(d.getTime())) return iso
  const lang = locale.value === 'en' ? 'en-US' : 'de-DE'
  return d.toLocaleDateString(lang, { day: '2-digit', month: 'short' })
}
function daysUntil(iso: string): number {
  if (!iso) return 0
  const d = new Date(iso + 'T00:00:00').getTime()
  const now = new Date().setHours(0, 0, 0, 0)
  return Math.max(0, Math.round((d - now) / (1000 * 60 * 60 * 24)))
}
function heroBg(r: RaceInfo): string {
  if (r.circuitImage) {
    return `linear-gradient(180deg, rgba(13,17,23,0.55) 0%, rgba(13,17,23,0.92) 100%), url(${r.circuitImage})`
  }
  return 'linear-gradient(135deg, color-mix(in srgb, var(--accent) 22%, var(--surface)), var(--surface))'
}

function clearAuthError() {
  auth.clearError()
}

async function doLogin() {
  const ok = await auth.login(loginValue.value, loginPassword.value)
  if (ok) {
    loginPassword.value = ''
    await reloadAll()
  }
}

async function doRegister() {
  const ok = await auth.register(regUsername.value, regEmail.value, regPassword.value)
  if (ok) {
    regPassword.value = ''
    await reloadAll()
  }
}

async function loadUpcoming() {
  try {
    const res = await fetch(`/api/betting/upcoming/${store.year}`)
    upcoming.value = await res.json()
    if (upcoming.value.length && (selectedRound.value === null || !upcoming.value.some((r) => r.round === selectedRound.value))) {
      selectedRound.value = upcoming.value[0]?.round ?? null
    }
  } catch {
    upcoming.value = []
  }
}

async function loadMyTipps() {
  if (!auth.isLoggedIn) {
    myAllTipps.value = []
    return
  }
  try {
    const res = await fetch(`/api/betting/me/season/${store.year}`, { headers: authHeaders() })
    if (res.ok) myAllTipps.value = await res.json()
    else myAllTipps.value = []
  } catch {
    myAllTipps.value = []
  }
}

async function loadCrowd() {
  if (!currentRace.value) {
    crowdRaw.value = []
    return
  }
  try {
    const res = await fetch(`/api/betting/crowd/${store.year}/${currentRace.value.round}`)
    crowdRaw.value = await res.json()
  } catch {
    crowdRaw.value = []
  }
}

async function loadLeaderboard() {
  try {
    const res = await fetch(`/api/betting/leaderboard/${store.year}`)
    leaderboard.value = await res.json()
  } catch {
    leaderboard.value = []
  }
}

async function save(cat: Cat) {
  if (!auth.isLoggedIn || !currentRace.value) return
  let pick = picks[cat]
  if (cat === 'PODIUM') {
    if (!podiumComplete.value) return
    pick = podiumPicks.value.join('|')
  }
  if (cat === 'H2H') {
    if (!h2hComplete.value) return
    pick = h2hPicks.value.join('|')
  }
  if (!pick) return
  saving[cat] = true
  try {
    const res = await fetch('/api/betting/tipp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({
        year: store.year,
        round: currentRace.value.round,
        category: cat,
        pick,
      }),
    })
    if (!res.ok) {
      const txt = await res.text()
      alert(t('errors.saveTipp', { msg: txt }))
      return
    }
    await Promise.all([loadMyTipps(), loadCrowd(), loadLeaderboard()])
  } finally {
    saving[cat] = false
  }
}

async function reloadAll() {
  dataLoading.value = true
  dataLoadStep.value = 0
  await loadUpcoming()
  dataLoadStep.value = 1
  await Promise.all([
    loadMyTipps().then(() => { dataLoadStep.value++ }),
    loadCrowd().then(() => { dataLoadStep.value++ }),
    loadLeaderboard().then(() => { dataLoadStep.value++ }),
  ])
  dataLoading.value = false
}

watch(myTipps, (mt) => {
  picks.WINNER = mt.WINNER?.pick ?? ''
  picks.POLE = mt.POLE?.pick ?? ''
  picks.FASTEST_LAP = mt.FASTEST_LAP?.pick ?? ''
  if (mt.PODIUM) {
    const parts = mt.PODIUM.pick.split('|')
    podiumPicks.value = [parts[0] ?? '', parts[1] ?? '', parts[2] ?? '']
  } else {
    podiumPicks.value = ['', '', '']
  }
  if (mt.H2H) {
    const parts = mt.H2H.pick.split('|')
    h2hPicks.value = [parts[0] ?? '', parts[1] ?? '']
  } else {
    h2hPicks.value = ['', '']
  }
})

watch(() => selectedRound.value, async () => {
  await loadCrowd()
})
watch(() => store.year, async () => {
  await reloadAll()
})
watch(() => auth.isLoggedIn, async (loggedIn) => {
  if (loggedIn) await reloadAll()
  else {
    myAllTipps.value = []
    dataLoading.value = false
  }
})

onMounted(async () => {
  if (!auth.initialized) await auth.fetchMe()
  await reloadAll()
})
</script>

<style scoped>
.panel {
  animation: fade 0.4s ease both;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
@keyframes fade {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent) 45%, transparent); }
  50% { box-shadow: 0 0 18px 4px color-mix(in srgb, var(--accent) 35%, transparent); }
}
@keyframes count-up {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.loading {
  padding: 24px 4px 8px;
}

.auth-shell {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: stretch;
  padding: 0;
}
.auth-hero {
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 18%, var(--surface)), var(--surface));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 36px 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.hero-emoji {
  font-size: 48px;
  margin-bottom: 8px;
}
.hero-title {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 38px;
  margin: 0 0 10px;
  letter-spacing: -0.02em;
}
.hero-sub {
  font-size: 0.95rem;
  color: var(--text-dim);
  margin: 0 0 22px;
  line-height: 1.5;
}
.hero-bullets {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
}
.hero-bullets b {
  color: var(--accent);
  font-family: var(--font-display);
  font-size: 14px;
  display: inline-block;
  min-width: 22px;
}

.auth-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 28px 28px 24px;
  box-shadow: var(--shadow);
}
.auth-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 22px;
  border-bottom: 1px solid var(--line);
}
.auth-tabs button {
  flex: 1;
  background: none;
  border: none;
  padding: 12px 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.15s;
}
.auth-tabs button:hover {
  color: var(--text-dim);
}
.auth-tabs button.on {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lbl {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}
.field input {
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 0 14px;
  height: 44px;
  color: var(--text);
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.15s;
}
.field input:focus {
  border-color: color-mix(in srgb, var(--accent) 55%, var(--line));
}
.auth-error {
  color: #f87171;
  font-family: var(--font-mono);
  font-size: 11px;
  margin: 0;
  padding: 10px 12px;
  background: color-mix(in srgb, #f87171 12%, transparent);
  border: 1px solid color-mix(in srgb, #f87171 40%, transparent);
  border-radius: 8px;
}

.btn {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  padding: 0 16px;
  height: 38px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--text-dim);
  transition: all 0.15s;
}
.btn:hover:not(:disabled) { color: var(--text); }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn.primary { background: var(--accent); border-color: var(--accent); color: #fff; }
.btn.primary:hover:not(:disabled) { filter: brightness(1.1); }
.btn.ghost { background: transparent; }
.btn.small { height: 32px; padding: 0 12px; font-size: 10px; }
.btn.big { height: 48px; font-size: 12px; }

.account-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 18px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--accent) 6%, var(--surface)), var(--surface));
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.account-info { display: flex; align-items: center; gap: 14px; min-width: 0; }
.avatar {
  width: 46px; height: 46px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-display); font-weight: 700; font-size: 19px;
  color: #fff; text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);
  flex-shrink: 0;
}
.user-nick {
  font-family: var(--font-display); font-weight: 700; font-size: 18px; color: var(--text);
}
.user-meta {
  font-size: 10px; letter-spacing: 0.1em; color: var(--text-faint);
  display: flex; gap: 14px; margin-top: 3px;
}
.pts-pill {
  background: var(--accent); color: #fff;
  padding: 2px 9px; border-radius: 4px; letter-spacing: 0.12em;
}

.race-strip {
  display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px;
  scrollbar-width: thin;
}
.race-pill {
  flex-shrink: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
  display: flex; flex-direction: column; gap: 2px;
  min-width: 110px;
  transition: all 0.15s;
}
.race-pill:hover { border-color: color-mix(in srgb, var(--text-faint) 60%, var(--line)); }
.race-pill.on {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, var(--surface));
}
.rp-round {
  font-size: 9px; letter-spacing: 0.14em; color: var(--text-faint);
}
.race-pill.on .rp-round { color: var(--accent); }
.rp-name {
  font-family: var(--font-display); font-weight: 700; font-size: 13px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  color: var(--text);
}
.rp-date { font-size: 10px; color: var(--text-faint); }

.race-hero {
  position: relative;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background-size: cover;
  background-position: center;
  background-color: var(--surface);
  overflow: hidden;
  min-height: 200px;
  display: flex;
  align-items: flex-end;
}
.race-hero-inner {
  padding: 26px 28px 22px;
  color: #fff;
  width: 100%;
}
.rh-meta {
  font-size: 11px; letter-spacing: 0.1em;
  color: rgba(255,255,255,0.78);
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 8px;
}
.flag-wrap {
  display: inline-flex; width: 24px; height: 16px;
  border-radius: 2px; overflow: hidden;
  border: 1px solid rgba(255,255,255,0.2);
}
.flag-wrap img { width: 100%; height: 100%; object-fit: cover; }
.rh-title {
  font-family: var(--font-display); font-weight: 700;
  font-size: 44px; letter-spacing: -0.02em; margin: 0 0 12px;
  text-shadow: 0 2px 12px rgba(0,0,0,0.5);
}
.rh-countdown {
  font-size: 11px; letter-spacing: 0.16em;
  color: rgba(255,255,255,0.85);
}
.rh-countdown b {
  color: var(--accent); font-family: var(--font-display);
  font-size: 22px; margin-right: 4px;
}
.rh-countdown .hot {
  color: #fbbf24;
  animation: pulse-glow 1.6s ease-in-out infinite;
  padding: 4px 10px;
  border-radius: 6px;
  background: color-mix(in srgb, #fbbf24 22%, transparent);
  display: inline-block;
}

.tips-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.tip-card {
  background: linear-gradient(180deg, var(--surface), color-mix(in srgb, var(--surface) 62%, transparent));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 20px;
  display: flex; flex-direction: column; gap: 12px;
  min-width: 0;
  transition: transform 0.18s ease, border-color 0.18s ease;
}
.tip-card:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--accent) 35%, var(--line));
}
.tip-card.done {
  border-color: color-mix(in srgb, #4ade80 38%, var(--line));
  background: linear-gradient(180deg, color-mix(in srgb, #4ade80 4%, var(--surface)), var(--surface));
}
.tip-card.podium-card { grid-column: span 2; }
.tip-head {
  display: flex; justify-content: space-between; align-items: center;
}
.tip-cat-row { display: flex; align-items: center; gap: 8px; }
.tip-icon { font-size: 18px; }
.tip-cat {
  font-size: 11px; letter-spacing: 0.16em; color: var(--accent);
}
.tip-pts {
  font-size: 10px; letter-spacing: 0.1em; color: var(--text-faint);
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  color: var(--accent);
  padding: 3px 9px; border-radius: 4px;
}
.tip-desc { font-size: 0.86rem; color: var(--text-dim); margin: 0; }

.picker { display: flex; gap: 8px; }
.picker select {
  flex: 1; height: 40px; border-radius: 8px;
  border: 1px solid var(--line); background: var(--surface-2);
  color: var(--text); padding: 0 10px; font-size: 0.9rem; outline: none;
}
.picker select:focus {
  border-color: color-mix(in srgb, var(--accent) 55%, var(--line));
}

.podium-picks {
  display: grid; grid-template-columns: repeat(3, 1fr) auto;
  gap: 10px; align-items: end;
}
.podium-slot { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.ps-pos { font-size: 10px; letter-spacing: 0.14em; color: var(--text-faint); }
.podium-slot select {
  height: 40px; border-radius: 8px;
  border: 1px solid var(--line); background: var(--surface-2);
  color: var(--text); padding: 0 10px; font-size: 0.9rem; outline: none;
}

.my-tipp {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; border-radius: 8px;
  background: rgba(0, 0, 0, 0.2); border: 1px solid var(--line);
  animation: count-up 0.3s ease;
}
.mt-pick {
  font-family: var(--font-display); font-weight: 700; font-size: 16px;
  color: var(--text); flex: 1;
}
.mt-status {
  font-size: 10px; letter-spacing: 0.12em;
  color: #4ade80;
}

.card {
  background: linear-gradient(180deg, var(--surface), color-mix(in srgb, var(--surface) 62%, transparent));
  border: 1px solid var(--line); border-radius: var(--radius);
  box-shadow: var(--shadow); padding: 20px;
}
.card-title {
  font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.16em;
  text-transform: uppercase; color: var(--text-faint); margin-bottom: 16px;
}

.crowd-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 22px;
}
.crowd-col { min-width: 0; }
.crowd-label {
  font-size: 10px; letter-spacing: 0.14em; color: var(--accent);
  margin-bottom: 10px; display: flex; align-items: center; gap: 6px;
}
.crowd-icon { font-size: 14px; }
.crowd-row {
  display: grid; grid-template-columns: 80px 1fr 40px;
  align-items: center; gap: 8px;
  margin-bottom: 6px; font-size: 0.85rem;
}
.cr-pick {
  font-family: var(--font-display); font-weight: 700; color: var(--text-dim);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.crowd-row.leader .cr-pick { color: var(--text); }
.cr-track {
  height: 9px; border-radius: 5px;
  background: rgba(0, 0, 0, 0.28); border: 1px solid var(--line);
  overflow: hidden;
}
.cr-fill {
  height: 100%; background: var(--text-faint);
  border-radius: 5px 0 0 5px;
  transition: width 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.crowd-row.leader .cr-fill { background: var(--accent); }
.cr-pct {
  font-size: 11px; color: var(--text-faint); text-align: right;
}
.crowd-row.leader .cr-pct { color: var(--accent); }
.crowd-empty {
  font-size: 11px; color: var(--text-faint); letter-spacing: 0.08em; padding: 8px 0;
}

.lb-list { display: flex; flex-direction: column; gap: 5px; }
.lb-row {
  display: grid; grid-template-columns: 40px 36px 1fr auto auto;
  gap: 12px; align-items: center;
  padding: 10px 14px; border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--line) 50%, transparent);
  transition: all 0.15s;
}
.lb-row.me {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 9%, transparent);
}
.lb-row.top {
  background: color-mix(in srgb, #facc15 4%, transparent);
}
.lb-row.top.me {
  background: color-mix(in srgb, var(--accent) 9%, transparent);
}
.lb-rank {
  font-size: 12px; color: var(--text-faint); text-align: center;
  display: flex; align-items: center; justify-content: center;
}
.medal { font-size: 22px; }
.lb-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-display); font-weight: 700; font-size: 14px;
  color: #fff; text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);
}
.lb-nick {
  font-family: var(--font-display); font-weight: 600; color: var(--text);
  display: flex; align-items: center; gap: 8px;
}
.me-tag {
  font-size: 9px; letter-spacing: 0.14em;
  padding: 2px 6px; border-radius: 4px;
  background: var(--accent); color: #fff;
}
.lb-stats {
  font-size: 10px; color: var(--text-faint); letter-spacing: 0.08em;
}
.lb-points {
  font-family: var(--font-display); font-weight: 700; font-size: 20px;
  color: var(--accent); min-width: 44px; text-align: right;
}

.empty {
  font-family: var(--font-mono); font-size: 11px;
  letter-spacing: 0.06em; color: var(--text-faint); padding: 20px 4px;
}

@media (max-aspect-ratio: 13/16) {
  .auth-shell { grid-template-columns: 1fr; }
  .tips-grid { grid-template-columns: 1fr; }
  .tip-card.podium-card { grid-column: span 1; }
  .crowd-grid { grid-template-columns: 1fr; }
  .rh-title { font-size: 30px; }
  .lb-row { grid-template-columns: 32px 28px 1fr auto; }
  .lb-stats { display: none; }
}

.h2h-vs {
  align-self: center;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}
</style>
