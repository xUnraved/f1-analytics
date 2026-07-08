<template>
  <div class="quiz-wrap ui-stagger" :class="{ wide: mode === 'millionaire' && mStarted && !mFinished }">

    <div v-if="loading" class="quiz-state">
      <LoadingBar :label="t('quiz.loading')" />
    </div>

    <template v-else-if="mode === null">
      <h2 class="quiz-title">{{ t('quiz.title') }}</h2>
      <p class="quiz-sub">{{ t('quiz.subtitle') }}</p>
      <div class="mode-grid">
        <button
          class="mode-card"
          :class="{ disabled: circuits.length < 5 }"
          :disabled="circuits.length < 5"
          @click="startQuiz('circuit')"
        >
          <span class="mode-icon">🏟</span>
          <span class="mode-label">{{ t('quiz.circuitMode') }}</span>
          <span class="mode-desc">{{ t('quiz.circuitDesc') }}</span>
          <span v-if="circuits.length < 5" class="mode-na">{{ t('quiz.notEnoughData', { count: circuits.length }) }}</span>
        </button>
        <button
          class="mode-card"
          :class="{ disabled: drivers.length < 5 }"
          :disabled="drivers.length < 5"
          @click="startQuiz('driver')"
        >
          <span class="mode-icon">🪖</span>
          <span class="mode-label">{{ t('quiz.driverMode') }}</span>
          <span class="mode-desc">{{ t('quiz.driverDesc') }}</span>
          <span v-if="drivers.length < 5" class="mode-na">{{ t('quiz.notEnoughData', { count: drivers.length }) }}</span>
        </button>
        <button class="mode-card" @click="startQuiz('season')">
          <span class="mode-icon">📅</span>
          <span class="mode-label">{{ t('quiz.seasonMode') }}</span>
          <span class="mode-desc">{{ t('quiz.seasonDesc') }}</span>
        </button>
        <button class="mode-card gold" @click="startQuiz('millionaire')">
          <span class="mode-icon">💰</span>
          <span class="mode-label">{{ t('quiz.millionaireMode') }}</span>
          <span class="mode-desc">{{ t('quiz.millionaireDesc') }}</span>
        </button>
      </div>
    </template>

    <template v-else-if="mode === 'season' && !seasonLoaded">
      <div class="quiz-header">
        <button class="back-link" @click="backToModes">{{ t('quiz.back') }}</button>
      </div>
      <div class="question-card season-pick">
        <p class="question-text">{{ t('quiz.season.pick') }}</p>
        <div v-if="seasonsLoading" class="quiz-hint-sub">{{ t('quiz.season.loadingYears') }}</div>
        <div v-else class="season-grid">
          <button
            v-for="y in seasonYears"
            :key="y"
            class="season-btn"
            :disabled="seasonQuizLoading"
            @click="loadSeasonQuiz(y)"
          >
            {{ y }}
          </button>
        </div>
        <p v-if="seasonQuizLoading" class="quiz-hint-sub">{{ t('quiz.season.generating') }}</p>
        <p v-if="seasonError" class="season-error">{{ seasonError }}</p>
      </div>
    </template>

    <template v-else-if="mode === 'millionaire'">
      <div v-if="mLoading" class="quiz-state">
        <LoadingBar :label="t('quiz.m.preparing')" />
      </div>

      <div v-else-if="mError" class="quiz-state">
        <p class="quiz-hint">{{ mError }}</p>
        <div class="result-actions">
          <button class="back-btn" @click="backToModes">{{ t('quiz.back') }}</button>
        </div>
      </div>

      <template v-else-if="!mFinished">
        <div class="m-layout">
          <div class="m-main">
            <div class="quiz-header">
              <span class="quiz-progress">{{ t('quiz.question', { current: mIndex + 1, total: mQuestions.length }) }}</span>
              <div class="m-head-right">
                <span class="quiz-score-live">{{ fmtPts(currentLadderValue) }} {{ t('quiz.m.ptsShort') }}</span>
                <button
                  v-if="mIndex > 0 && !mAnswered"
                  class="quit-btn"
                  @click="quitMillionaire"
                >
                  {{ t('quiz.m.quit', { pts: fmtPts(ladder[mIndex - 1] ?? 0) }) }}
                </button>
              </div>
            </div>

            <div class="jokers">
              <button type="button" class="joker" :class="{ used: !jokerFifty }" :disabled="!jokerFifty || mAnswered" @click="useFifty">
                <span class="joker-ico">✂</span>
                <span class="joker-lbl">{{ t('quiz.m.jokers.fifty') }}</span>
              </button>
              <button type="button" class="joker" :class="{ used: !jokerAudience }" :disabled="!jokerAudience || mAnswered" @click="useAudience">
                <span class="joker-ico">👥</span>
                <span class="joker-lbl">{{ t('quiz.m.jokers.audience') }}</span>
              </button>
              <button type="button" class="joker" :class="{ used: !jokerPhone }" :disabled="!jokerPhone || mAnswered" @click="usePhone">
                <span class="joker-ico">📞</span>
                <span class="joker-lbl">{{ t('quiz.m.jokers.phone') }}</span>
              </button>
            </div>

            <div class="question-card">
              <p class="m-question">{{ mCurrent?.question }}</p>

              <div v-if="audienceVotes" class="aud-panel">
                <div class="aud-title">{{ t('quiz.m.jokers.audienceTitle') }}</div>
                <div v-for="v in audienceVotes" :key="v.opt" class="aud-row">
                  <span class="aud-letter">{{ 'ABCD'[mCurrent?.options.indexOf(v.opt) ?? 0] }}</span>
                  <span class="aud-track"><span class="aud-fill" :style="{ width: v.pct + '%' }"></span></span>
                  <span class="aud-pct">{{ v.pct }}%</span>
                </div>
              </div>

              <div v-if="phoneHint" class="phone-panel">
                <span class="phone-ico">📞</span>{{ phoneHint }}
              </div>

              <div class="options">
                <button
                  v-for="(opt, oi) in mCurrent?.options ?? []"
                  :key="opt"
                  class="option-btn m-option"
                  :class="[mOptionClass(opt), { gone: eliminated.includes(opt) }]"
                  :disabled="mAnswered || eliminated.includes(opt)"
                  @click="mAnswer(opt)"
                >
                  <span class="m-letter">{{ 'ABCD'[oi] }}</span> {{ opt }}
                </button>
              </div>
              <button v-if="mAnswered && mChosen === mCurrent?.answer" class="next-btn" @click="mNext">
                {{ mIndex + 1 < mQuestions.length ? t('quiz.next') : t('quiz.m.toMillion') }}
              </button>
              <button v-if="mAnswered && mChosen !== mCurrent?.answer" class="next-btn" @click="mFinished = true">
                {{ t('quiz.result') }}
              </button>
            </div>
          </div>

          <aside class="m-ladder">
            <div
              v-for="(pts, li) in ladderReversed"
              :key="pts"
              class="ladder-row"
              :class="{
                current: ladder.length - 1 - li === mIndex,
                done: ladder.length - 1 - li < mIndex,
                safe: safeLevels.includes(ladder.length - li),
              }"
            >
              <span class="ladder-lvl">{{ ladder.length - li }}</span>
              <span class="ladder-pts">{{ fmtPts(pts) }}</span>
            </div>
          </aside>
        </div>
      </template>

      <template v-else>
        <div class="results-card" :class="{ gold: mOutcome === 'won' }">
          <div class="m-outcome">
            {{ mOutcome === 'won' ? t('quiz.m.outcomeWon') : mOutcome === 'quit' ? t('quiz.m.outcomeQuit') : t('quiz.m.outcomeLost') }}
          </div>
          <div class="result-score">
            <span class="result-num">{{ fmtPts(mEarned) }}</span>
            <span class="result-denom">{{ t('quiz.m.points') }}</span>
          </div>
          <p v-if="mOutcome === 'lost' && mCurrent" class="result-label">
            {{ t('quiz.m.correctWas', { answer: mCurrent.answer }) }}
          </p>
          <p v-else-if="mOutcome === 'lost'" class="result-label">{{ t('quiz.m.lostHint') }}</p>
          <p v-else-if="mOutcome === 'quit'" class="result-label">{{ t('quiz.m.quitHint') }}</p>
          <p v-else class="result-label">{{ t('quiz.m.wonHint') }}</p>
          <div class="result-actions">
            <button class="restart-btn" @click="startQuiz('millionaire')">{{ t('quiz.m.playAgain') }}</button>
            <button class="back-btn" @click="backToModes">{{ t('quiz.results.chooseQuiz') }}</button>
          </div>
        </div>
      </template>
    </template>

    <template v-else-if="!finished">
      <div class="quiz-header">
        <span class="quiz-progress">{{ t('quiz.question', { current: questionIndex + 1, total: questions.length }) }}</span>
        <span class="quiz-score-live">{{ t('quiz.correct', { count: score }) }}</span>
      </div>

      <div class="question-card">
        <div v-if="currentQ?.image && currentQ.imgKind === 'circuit'" class="circuit-img-wrap">
          <img :src="currentQ.image" alt="Circuit" class="circuit-img" />
        </div>
        <div v-else-if="currentQ?.image" class="driver-img-wrap">
          <img :src="currentQ.image" alt="Driver" class="driver-img" />
        </div>

        <p v-if="currentQ?.subtitle" class="driver-name">{{ currentQ.subtitle }}</p>
        <p class="question-text">{{ currentQ?.prompt }}</p>

        <div class="options">
          <button
            v-for="opt in currentQ?.options ?? []"
            :key="opt"
            class="option-btn"
            :class="optionClass(opt)"
            :disabled="answered"
            @click="answer(opt)"
          >
            {{ opt }}
          </button>
        </div>

        <button v-if="answered" class="next-btn" @click="next">
          {{ questionIndex + 1 < questions.length ? t('quiz.next') : t('quiz.result') }}
        </button>
      </div>
    </template>

    <template v-else>
      <div class="results-card">
        <div class="result-score">
          <span class="result-num">{{ score }}</span>
          <span class="result-denom">/ {{ questions.length }}</span>
        </div>
        <p class="result-label">{{ resultLabel }}</p>
        <div class="result-actions">
          <button class="restart-btn" @click="restartQuiz">{{ t('quiz.results.restart') }}</button>
          <button class="back-btn" @click="backToModes">{{ t('quiz.results.chooseQuiz') }}</button>
        </div>

        <div class="review">
          <div
            v-for="(q, i) in questions"
            :key="i"
            class="review-row"
            :class="{ correct: userAnswers[i] === q.answer, wrong: userAnswers[i] !== q.answer }"
          >
            <span class="review-num">{{ i + 1 }}</span>
            <img v-if="q.image && q.imgKind === 'circuit'" :src="q.image" class="review-img" />
            <img v-else-if="q.image" :src="q.image" class="review-img review-headshot" />
            <div class="review-text">
              <span v-if="!q.image" class="review-q">{{ q.prompt }}</span>
              <span class="review-correct">{{ q.answer }}</span>
              <span v-if="userAnswers[i] !== q.answer" class="review-wrong">
                {{ t('quiz.results.yourAnswer', { answer: userAnswers[i] }) }}
              </span>
            </div>
            <span class="review-icon">{{ userAnswers[i] === q.answer ? '✓' : '✗' }}</span>
          </div>
        </div>
      </div>
    </template>

  </div>
</template>

<script setup lang="ts">
/**
 * Quiz-Hauptkomponente mit vier Spielmodi:
 *  - circuit: 12 Fragen – Streckenbilder → Name oder Land erraten.
 *  - driver:  12 Fragen – Fahrerfotos → Name oder Nationalität.
 *  - season:  12 Backend-generierte Fragen zu einer Saison.
 *  - millionaire: 15 Fragen mit Punkteleiter (100–1.000.000), Sicherheitsnetz
 *    bei Stufe 5/10 und 3 Jokern (50:50, Publikum, Telefonjoker).
 *
 * Joker-Logik:
 *  - useFifty(): entfernt 2 falsche Optionen zufällig aus remainingOptions().
 *  - useAudience(): simuliert Publikumsabstimmung (80 % Wahrscheinlichkeit korrekt).
 *  - usePhone(): wählt mit 90 % Wahrscheinlichkeit die korrekte Antwort.
 *
 * safeFallback(): gibt den höchsten SAFE_LEVEL-Betrag zurück, der noch erreicht
 * wurde, wenn ein Spieler falsch antwortet.
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  fetchQuizData,
  fetchSeasons,
  fetchSeasonQuiz,
  fetchMillionaireQuiz,
  type QuizQuestionDto,
} from '@/services/f1Api'
import { countryLabel } from '@/i18n/countries'
import LoadingBar from '@/components/ui/LoadingBar.vue'

const { t, locale } = useI18n()

type Mode = 'circuit' | 'driver' | 'season' | 'millionaire'

interface CircuitItem {
  name: string
  imageUrl: string
  country: string | null
}
interface DriverItem {
  abbr: string
  name: string
  headshotUrl: string
  countryName: string
}
interface LocalQ {
  image?: string
  imgKind?: 'circuit' | 'driver'
  subtitle?: string
  prompt: string
  options: string[]
  answer: string
}

const loading = ref(true)
const circuits = ref<CircuitItem[]>([])
const drivers = ref<DriverItem[]>([])

const mode = ref<Mode | null>(null)
const questions = ref<LocalQ[]>([])
const questionIndex = ref(0)
const answered = ref(false)
const chosen = ref<string | null>(null)
const userAnswers = ref<string[]>([])
const score = ref(0)
const finished = ref(false)

const seasonYears = ref<number[]>([])
const seasonsLoading = ref(false)
const seasonLoaded = ref(false)
const seasonQuizLoading = ref(false)
const seasonError = ref<string | null>(null)

const mLoading = ref(false)
const mError = ref<string | null>(null)
const mStarted = ref(false)
const mQuestions = ref<QuizQuestionDto[]>([])
const ladder = ref<number[]>([])
const safeLevels = ref<number[]>([])
const mIndex = ref(0)
const mAnswered = ref(false)
const mChosen = ref<string | null>(null)
const mFinished = ref(false)
const mOutcome = ref<'won' | 'lost' | 'quit'>('lost')
const mEarned = ref(0)

const jokerFifty = ref(true)
const jokerAudience = ref(true)
const jokerPhone = ref(true)
const eliminated = ref<string[]>([])
const audienceVotes = ref<{ opt: string; pct: number }[] | null>(null)
const phoneHint = ref<string | null>(null)

const apiLang = computed(() => (locale.value === 'en' ? 'en' : 'de'))
const currentQ = computed(() => questions.value[questionIndex.value] ?? null)
const mCurrent = computed(() => mQuestions.value[mIndex.value] ?? null)
const ladderReversed = computed(() => [...ladder.value].reverse())
const currentLadderValue = computed(() => ladder.value[mIndex.value] ?? 0)

onMounted(async () => {
  try {
    const data = (await fetchQuizData()) as unknown as { circuits: CircuitItem[]; drivers: DriverItem[] }
    circuits.value = data.circuits ?? []
    drivers.value = (data.drivers ?? []).filter((d) => d.headshotUrl && d.name && d.countryName)
  } catch {
    circuits.value = []
    drivers.value = []
  } finally {
    loading.value = false
  }
})

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j]!, a[i]!]
  }
  return a
}

function pick<T>(arr: T[], n: number): T[] {
  return shuffle(arr).slice(0, n)
}

function buildOptions(correct: string, pool: string[]): string[] {
  const wrong = shuffle([...new Set(pool.filter((x) => x && x !== correct))]).slice(0, 3)
  return shuffle([correct, ...wrong])
}

function resetRun() {
  questions.value = []
  questionIndex.value = 0
  answered.value = false
  chosen.value = null
  userAnswers.value = []
  score.value = 0
  finished.value = false
}

function backToModes() {
  mode.value = null
  seasonLoaded.value = false
  seasonError.value = null
  mStarted.value = false
  mFinished.value = false
  mError.value = null
  resetRun()
}

function startQuiz(m: Mode) {
  mode.value = m
  resetRun()
  seasonLoaded.value = false
  seasonError.value = null

  if (m === 'circuit') {
    buildCircuitQuiz()
  } else if (m === 'driver') {
    buildDriverQuiz()
  } else if (m === 'season') {
    loadSeasonYears()
  } else {
    startMillionaire()
  }
}

function buildCircuitQuiz() {
  const loc = locale.value
  const names = circuits.value.map((c) => c.name)
  const countries = [
    ...new Set(
      circuits.value
        .map((c) => countryLabel(c.country, loc))
        .filter((c) => !!c),
    ),
  ]
  const items = pick(circuits.value, Math.min(12, circuits.value.length))

  questions.value = items.map((c) => {
    const localCountry = countryLabel(c.country, loc)
    const askCountry = !!localCountry && countries.length >= 4 && Math.random() < 0.5
    if (askCountry) {
      return {
        image: c.imageUrl,
        imgKind: 'circuit' as const,
        prompt: t('quiz.circuitCountry'),
        options: buildOptions(localCountry, countries),
        answer: localCountry,
      }
    }
    return {
      image: c.imageUrl,
      imgKind: 'circuit' as const,
      prompt: t('quiz.circuitName'),
      options: buildOptions(c.name, names),
      answer: c.name,
    }
  })
}

function buildDriverQuiz() {
  const loc = locale.value
  const names = drivers.value.map((d) => d.name)
  const countries = [...new Set(drivers.value.map((d) => countryLabel(d.countryName, loc)))]
  const items = pick(drivers.value, Math.min(12, drivers.value.length))

  questions.value = items.map((d) => {
    const localCountry = countryLabel(d.countryName, loc)
    const askCountry = countries.length >= 4 && Math.random() < 0.4
    if (askCountry) {
      return {
        image: d.headshotUrl,
        imgKind: 'driver' as const,
        subtitle: d.name,
        prompt: t('quiz.driverCountry'),
        options: buildOptions(localCountry, countries),
        answer: localCountry,
      }
    }
    return {
      image: d.headshotUrl,
      imgKind: 'driver' as const,
      prompt: t('quiz.driverName'),
      options: buildOptions(d.name, names),
      answer: d.name,
    }
  })
}

async function loadSeasonYears() {
  if (seasonYears.value.length) return
  seasonsLoading.value = true
  try {
    seasonYears.value = (await fetchSeasons()).sort((a, b) => b - a)
  } catch {
    seasonYears.value = []
  } finally {
    seasonsLoading.value = false
  }
}

async function loadSeasonQuiz(year: number) {
  seasonQuizLoading.value = true
  seasonError.value = null
  try {
    const data = await fetchSeasonQuiz(year, apiLang.value)
    questions.value = data.questions.map((q) => ({
      prompt: q.question,
      options: q.options,
      answer: q.answer,
    }))
    if (!questions.value.length) {
      seasonError.value = t('quiz.season.errorNone', { year })
      return
    }
    seasonLoaded.value = true
  } catch {
    seasonError.value = t('quiz.season.errorData', { year })
  } finally {
    seasonQuizLoading.value = false
  }
}

async function startMillionaire() {
  mLoading.value = true
  mError.value = null
  mStarted.value = true
  mFinished.value = false
  mIndex.value = 0
  mAnswered.value = false
  mChosen.value = null
  mEarned.value = 0
  jokerFifty.value = true
  jokerAudience.value = true
  jokerPhone.value = true
  resetJokerState()
  try {
    const data = await fetchMillionaireQuiz(apiLang.value)
    mQuestions.value = data.questions ?? []
    ladder.value = data.ladder ?? []
    safeLevels.value = data.safeLevels ?? []
    if (!mQuestions.value.length) mError.value = t('quiz.m.noQuestions')
  } catch {
    mError.value = t('quiz.m.loadError')
  } finally {
    mLoading.value = false
  }
}

function answer(opt: string) {
  if (answered.value || !currentQ.value) return
  answered.value = true
  chosen.value = opt
  userAnswers.value.push(opt)
  if (opt === currentQ.value.answer) score.value++
}

function next() {
  if (questionIndex.value + 1 >= questions.value.length) {
    finished.value = true
    return
  }
  questionIndex.value++
  answered.value = false
  chosen.value = null
}

function restartQuiz() {
  if (mode.value === 'circuit') startQuiz('circuit')
  else if (mode.value === 'driver') startQuiz('driver')
  else if (mode.value === 'season') startQuiz('season')
}

function optionClass(opt: string) {
  if (!answered.value || !currentQ.value) return ''
  if (opt === currentQ.value.answer) return 'correct'
  if (opt === chosen.value) return 'wrong'
  return 'faded'
}

function mAnswer(opt: string) {
  if (mAnswered.value || !mCurrent.value) return
  mAnswered.value = true
  mChosen.value = opt
  if (opt !== mCurrent.value.answer) {
    mOutcome.value = 'lost'
    mEarned.value = safeFallback(mIndex.value)
  }
}

function mNext() {
  if (mIndex.value + 1 >= mQuestions.value.length) {
    mOutcome.value = 'won'
    mEarned.value = ladder.value[ladder.value.length - 1] ?? 0
    mFinished.value = true
    return
  }
  mIndex.value++
  mAnswered.value = false
  mChosen.value = null
  resetJokerState()
}

function quitMillionaire() {
  mOutcome.value = 'quit'
  mEarned.value = mIndex.value > 0 ? (ladder.value[mIndex.value - 1] ?? 0) : 0
  mFinished.value = true
}

function safeFallback(completedLevels: number): number {
  let best = 0
  for (const lvl of safeLevels.value) {
    if (completedLevels >= lvl) {
      const pts = ladder.value[lvl - 1] ?? 0
      if (pts > best) best = pts
    }
  }
  return best
}

function resetJokerState() {
  eliminated.value = []
  audienceVotes.value = null
  phoneHint.value = null
}

function remainingOptions(): string[] {
  return (mCurrent.value?.options ?? []).filter((o) => !eliminated.value.includes(o))
}

function useFifty() {
  if (!jokerFifty.value || mAnswered.value || !mCurrent.value) return
  jokerFifty.value = false
  const wrong = remainingOptions().filter((o) => o !== mCurrent.value!.answer)
  eliminated.value = [...eliminated.value, ...shuffle(wrong).slice(0, 2)]
  if (audienceVotes.value) {
    audienceVotes.value = audienceVotes.value.filter((v) => !eliminated.value.includes(v.opt))
  }
}

function useAudience() {
  if (!jokerAudience.value || mAnswered.value || !mCurrent.value) return
  jokerAudience.value = false
  const opts = remainingOptions()
  const correct = mCurrent.value.answer
  const wrongs = opts.filter((o) => o !== correct)
  const pct: Record<string, number> = {}
  const audienceRight = wrongs.length === 0 || Math.random() < 0.8

  if (audienceRight) {
    const top = 52 + Math.floor(Math.random() * 19)
    pct[correct] = top
    let left = 100 - top
    const rest = shuffle(wrongs)
    rest.forEach((o, i) => {
      if (i === rest.length - 1) {
        pct[o] = left
      } else {
        const v = Math.floor(Math.random() * (left / (rest.length - i)) * 1.5)
        pct[o] = Math.min(v, left)
        left -= pct[o]!
      }
    })
  } else {
    const top = shuffle(wrongs)[0]!
    const others = wrongs.filter((o) => o !== top)
    if (others.length === 0) {
      const w = 51 + Math.floor(Math.random() * 5)
      pct[top] = w
      pct[correct] = 100 - w
    } else {
      const w = 32 + Math.floor(Math.random() * 7)
      const gap = 2 + Math.floor(Math.random() * 4)
      const c = w - gap
      pct[top] = w
      pct[correct] = c
      const rest = 100 - w - c
      const cap = c - 1
      const lo = Math.max(0, rest - cap)
      const hi = Math.min(cap, rest)
      const a = lo + Math.floor(Math.random() * (hi - lo + 1))
      const [o1, o2] = shuffle(others)
      if (o1 != null) pct[o1] = a
      if (o2 != null) pct[o2] = rest - a
    }
  }
  audienceVotes.value = opts.map((o) => ({ opt: o, pct: pct[o] ?? 0 }))
}

function usePhone() {
  if (!jokerPhone.value || mAnswered.value || !mCurrent.value) return
  jokerPhone.value = false
  const opts = remainingOptions()
  const correct = mCurrent.value.answer
  const wrongs = opts.filter((o) => o !== correct)
  const pick = wrongs.length === 0 || Math.random() < 0.9 ? correct : shuffle(wrongs)[0]!
  const letter = 'ABCD'[mCurrent.value.options.indexOf(pick)] ?? '?'
  const variant = 1 + Math.floor(Math.random() * 3)
  phoneHint.value = t('quiz.m.jokers.phone' + variant, { letter, answer: pick })
}

function mOptionClass(opt: string) {
  if (!mAnswered.value || !mCurrent.value) return ''
  if (opt === mCurrent.value.answer) return 'correct'
  if (opt === mChosen.value) return 'wrong'
  return 'faded'
}

function fmtPts(n: number): string {
  return n.toLocaleString(locale.value === 'en' ? 'en-US' : 'de-DE')
}

const resultLabel = computed(() => {
  if (!questions.value.length) return ''
  const pct = score.value / questions.value.length
  if (pct === 1) return t('quiz.results.perfect')
  if (pct >= 0.8) return t('quiz.results.great')
  if (pct >= 0.6) return t('quiz.results.good')
  if (pct >= 0.4) return t('quiz.results.ok')
  return t('quiz.results.bad')
})
</script>

<style scoped>
.quiz-wrap {
  max-width: 640px;
  margin: 0 auto;
  padding: 8px 0 40px;
}
.quiz-wrap.wide {
  max-width: 920px;
}

.quiz-state {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 8px 0 40px;
}
.quiz-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: var(--ls-1);
  color: var(--text-dim);
  text-align: center;
  margin: 0 0 12px;
}
.quiz-hint-sub {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  opacity: 0.7;
  margin: 0;
  text-align: center;
}

.quiz-title {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 32px;
  letter-spacing: -0.02em;
  color: var(--text);
  margin: 0 0 6px;
}
.quiz-sub {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  margin: 0 0 32px;
}
.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.mode-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 24px 20px;
  cursor: pointer;
  transition: border-color 0.18s, background 0.18s, transform 0.18s;
  text-align: left;
}
.mode-card:hover:not(.disabled) {
  border-color: var(--accent);
  transform: translateY(-2px);
}
.mode-card.gold {
  border-color: color-mix(in srgb, var(--gold) 35%, transparent);
  background: linear-gradient(160deg, color-mix(in srgb, var(--gold) 8%, transparent), var(--surface) 55%);
}
.mode-card.gold:hover {
  border-color: var(--gold);
}
.mode-card.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.mode-icon {
  font-size: 22px;
}
.mode-label {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--text);
}
.mode-card.gold .mode-label {
  color: var(--gold);
}
.mode-desc {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  line-height: 1.5;
}
.mode-na {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--danger);
  letter-spacing: 0.08em;
}

.quiz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 12px;
}
.quiz-progress {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}
.quiz-score-live {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
}
.back-link {
  background: transparent;
  border: none;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  cursor: pointer;
  padding: 4px 0;
}
.back-link:hover {
  color: var(--text);
}

.question-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.circuit-img-wrap {
  width: 100%;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--bg);
  aspect-ratio: 16 / 9;
}
.circuit-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.driver-img-wrap {
  display: flex;
  justify-content: center;
}
.driver-img {
  height: 160px;
  object-fit: contain;
}
.driver-name {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--text);
  margin: 0;
  text-align: center;
}
.question-text {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  margin: 0;
  text-align: center;
}
.options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.option-btn {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 14px 12px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: var(--ls-1);
  color: var(--text-dim);
  cursor: pointer;
  transition: border-color var(--dur-fast) ease, background var(--dur-fast) ease, color var(--dur-fast) ease;
  text-align: center;
}
.option-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--text);
}
.option-btn.correct {
  border-color: var(--ok);
  background: color-mix(in srgb, var(--ok) 12%, transparent);
  color: var(--ok);
}
.option-btn.wrong {
  border-color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
  color: var(--danger);
}
.option-btn.faded {
  opacity: 0.35;
}
.option-btn:disabled {
  cursor: default;
}
.next-btn {
  align-self: flex-end;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-sm);
  padding: 12px 24px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #fff;
  cursor: pointer;
  transition: opacity var(--dur-fast) ease;
}
.next-btn:hover {
  opacity: 0.85;
}

.season-pick {
  align-items: center;
}
.season-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}
.season-btn {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 16px 26px;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  cursor: pointer;
  transition: border-color var(--dur-fast) ease, transform var(--dur-fast) ease;
}
.season-btn:hover:not(:disabled) {
  border-color: var(--accent);
  transform: translateY(-2px);
}
.season-btn:disabled {
  opacity: 0.5;
  cursor: wait;
}
.season-error {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--danger);
  letter-spacing: 0.08em;
  margin: 0;
  text-align: center;
}

.m-layout {
  display: grid;
  grid-template-columns: 1fr 210px;
  gap: 18px;
  align-items: start;
}
.m-main {
  min-width: 0;
}
.m-head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.quit-btn {
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--gold) 40%, transparent);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: var(--ls-1);
  color: var(--gold);
  cursor: pointer;
  transition: background var(--dur-fast) ease;
}
.quit-btn:hover {
  background: color-mix(in srgb, var(--gold) 10%, transparent);
}
.m-question {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
  margin: 0;
  text-align: center;
  line-height: 1.45;
}
.m-option {
  text-align: left;
}
.m-letter {
  color: var(--gold);
  font-weight: 700;
  margin-right: 6px;
}
.m-ladder {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ladder-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 10px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
}
.ladder-row.safe {
  color: var(--text-dim);
  font-weight: 700;
}
.ladder-row.done {
  color: var(--ok);
  opacity: 0.75;
}
.ladder-row.current {
  background: color-mix(in srgb, var(--gold) 14%, transparent);
  color: var(--gold);
  font-weight: 700;
}
.ladder-lvl {
  opacity: 0.7;
}
.ladder-pts {
  letter-spacing: 0.06em;
}

.results-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.results-card.gold {
  border-color: color-mix(in srgb, var(--gold) 50%, transparent);
  background: linear-gradient(160deg, color-mix(in srgb, var(--gold) 10%, transparent), var(--surface) 60%);
}
.m-outcome {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--text-dim);
}
.results-card.gold .m-outcome {
  color: var(--gold);
}
.result-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.result-num {
  font-family: var(--font-display);
  font-size: 60px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1;
}
.results-card.gold .result-num {
  color: var(--gold);
}
.result-denom {
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--text-faint);
}
.result-label {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--text-dim);
  text-align: center;
  margin: 0;
}
.result-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}
.restart-btn {
  background: var(--accent);
  border: none;
  border-radius: var(--radius-sm);
  padding: 12px 24px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #fff;
  cursor: pointer;
  transition: opacity var(--dur-fast) ease;
}
.restart-btn:hover {
  opacity: 0.85;
}
.back-btn {
  background: transparent;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 12px 24px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  cursor: pointer;
  transition: border-color var(--dur-fast) ease, color var(--dur-fast) ease;
}
.back-btn:hover {
  border-color: var(--accent);
  color: var(--text);
}

.review {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
  border-top: 1px solid var(--line);
  padding-top: 16px;
}
.review-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.12);
}
.review-row.correct {
  border-left: 3px solid var(--ok);
}
.review-row.wrong {
  border-left: 3px solid var(--danger);
}
.review-num {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  min-width: 16px;
}
.review-img {
  width: 48px;
  height: 30px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}
.review-headshot {
  object-fit: contain;
  background: transparent;
  height: 36px;
  width: 28px;
}
.review-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.review-q {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-faint);
  letter-spacing: 0.04em;
}
.review-correct {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-dim);
  letter-spacing: 0.06em;
}
.review-wrong {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--danger);
  letter-spacing: 0.06em;
}
.review-icon {
  font-size: 14px;
  color: var(--text-faint);
}
.review-row.correct .review-icon {
  color: var(--ok);
}
.review-row.wrong .review-icon {
  color: var(--danger);
}

@media (max-width: 760px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }
  .m-layout {
    grid-template-columns: 1fr;
  }
  .m-ladder {
    order: -1;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .ladder-row {
    display: none;
  }
  .ladder-row.current,
  .ladder-row.safe {
    display: flex;
    gap: 8px;
  }
}

.jokers {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.joker {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--surface);
  border: 1px solid color-mix(in srgb, var(--gold) 35%, transparent);
  border-radius: 999px;
  padding: 9px 10px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: var(--ls-1);
  color: var(--gold);
  cursor: pointer;
  transition: background var(--dur-fast) ease, transform var(--dur-fast) ease, opacity var(--dur-fast) ease;
}
.joker:hover:not(:disabled) {
  background: color-mix(in srgb, var(--gold) 10%, transparent);
  transform: translateY(-1px);
}
.joker:disabled {
  cursor: default;
}
.joker.used {
  opacity: 0.32;
}
.joker.used .joker-lbl {
  text-decoration: line-through;
}
.joker-ico {
  font-size: 13px;
}
.aud-panel {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.aud-title {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: var(--ls-2);
  color: var(--text-faint);
  text-transform: uppercase;
  margin-bottom: 2px;
}
.aud-row {
  display: grid;
  grid-template-columns: 18px 1fr 40px;
  align-items: center;
  gap: 9px;
}
.aud-letter {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--gold);
}
.aud-track {
  height: 9px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}
.aud-fill {
  display: block;
  height: 100%;
  border-radius: 99px;
  background: linear-gradient(90deg, var(--gold), #f59e0b);
  transition: width 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.aud-pct {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--text);
  text-align: right;
}
.phone-panel {
  background: var(--bg);
  border: 1px dashed color-mix(in srgb, var(--gold) 40%, transparent);
  border-radius: 10px;
  padding: 11px 14px;
  font-size: 13px;
  color: var(--text-dim);
  line-height: 1.5;
}
.phone-ico {
  margin-right: 7px;
}
.option-btn.gone {
  opacity: 0.12;
  pointer-events: none;
}
</style>
