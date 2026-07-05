<template>
  <div class="quiz-wrap">

    <!-- Loading -->
    <div v-if="loading" class="quiz-state">
      <LoadingBar :label="t('quiz.loading')" />
    </div>

    <!-- No data -->
    <div v-else-if="noData" class="quiz-state">
      <p class="quiz-hint">{{ t('quiz.noData') }}</p>
      <p class="quiz-hint-sub">{{ t('quiz.noDataHint') }}</p>
    </div>

    <!-- Mode selection -->
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
          <span class="mode-icon">&#9654;</span>
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
          <span class="mode-icon">&#9654;</span>
          <span class="mode-label">{{ t('quiz.driverMode') }}</span>
          <span class="mode-desc">{{ t('quiz.driverDesc') }}</span>
          <span v-if="drivers.length < 5" class="mode-na">{{ t('quiz.notEnoughData', { count: drivers.length }) }}</span>
        </button>
      </div>
    </template>

    <!-- Active quiz -->
    <template v-else-if="!finished">
      <div class="quiz-header">
        <span class="quiz-progress">{{ t('quiz.question', { current: questionIndex + 1, total: questions.length }) }}</span>
        <span class="quiz-score-live">{{ t('quiz.correct', { count: score }) }}</span>
      </div>

      <div class="question-card">
        <!-- Circuit question -->
        <template v-if="mode === 'circuit'">
          <div class="circuit-img-wrap">
            <img :src="currentQ.imageUrl" :alt="currentQ.name" class="circuit-img" />
          </div>
          <p class="question-text">{{ t('quiz.circuitQuestion') }}</p>
        </template>

        <!-- Driver question -->
        <template v-else>
          <div class="driver-img-wrap">
            <img :src="currentQ.headshotUrl" :alt="currentQ.name" class="driver-img" />
          </div>
          <p class="driver-name">{{ currentQ.name }}</p>
          <p class="question-text">{{ t('quiz.driverQuestion') }}</p>
        </template>

        <!-- Options -->
        <div class="options">
          <button
            v-for="opt in currentOptions"
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

    <!-- Results -->
    <template v-else>
      <div class="results-card">
        <div class="result-score">
          <span class="result-num">{{ score }}</span>
          <span class="result-denom">/ {{ questions.length }}</span>
        </div>
        <p class="result-label">{{ resultLabel }}</p>
        <div class="result-actions">
          <button class="restart-btn" @click="restartQuiz">{{ t('quiz.results.restart') }}</button>
          <button class="back-btn" @click="mode = null">{{ t('quiz.results.chooseQuiz') }}</button>
        </div>

        <!-- Answer review -->
        <div class="review">
          <div
            v-for="(q, i) in questions"
            :key="i"
            class="review-row"
            :class="{ correct: userAnswers[i] === correctAnswer(q), wrong: userAnswers[i] !== correctAnswer(q) }"
          >
            <span class="review-num">{{ i + 1 }}</span>
            <template v-if="mode === 'circuit'">
              <img :src="q.imageUrl" class="review-img" />
            </template>
            <template v-else>
              <img :src="q.headshotUrl" class="review-img review-headshot" />
            </template>
            <div class="review-text">
              <span class="review-correct">{{ correctAnswer(q) }}</span>
              <span v-if="userAnswers[i] !== correctAnswer(q)" class="review-wrong">
                {{ t('quiz.results.yourAnswer') }}{{ userAnswers[i] }}
              </span>
            </div>
            <span class="review-icon">{{ userAnswers[i] === correctAnswer(q) ? '✓' : '✗' }}</span>
          </div>
        </div>
      </div>
    </template>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchQuizData } from '@/services/f1Api'
import type { QuizCircuit, QuizDriver } from '@/types/f1'
import LoadingBar from '@/components/ui/LoadingBar.vue'

const { t } = useI18n()

type Mode = 'circuit' | 'driver'
type Question = QuizCircuit | QuizDriver

const loading = ref(true)
const circuits = ref<QuizCircuit[]>([])
const drivers = ref<QuizDriver[]>([])

const mode = ref<Mode | null>(null)
const questions = ref<Question[]>([])
const questionIndex = ref(0)
const currentOptions = ref<string[]>([])
const answered = ref(false)
const chosen = ref<string | null>(null)
const userAnswers = ref<string[]>([])
const score = ref(0)
const finished = ref(false)

const noData = computed(() => circuits.value.length === 0 && drivers.value.length === 0)

onMounted(async () => {
  try {
    const data = await fetchQuizData()
    circuits.value = data.circuits
    drivers.value = data.drivers
  } catch {
    // quiz data unavailable — buttons stay disabled
  } finally {
    loading.value = false
  }
})

const currentQ = computed(() => questions.value[questionIndex.value] as any)

function correctAnswer(q: Question): string {
  if (mode.value === 'circuit') return (q as QuizCircuit).name
  return (q as QuizDriver).countryName
}

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
  const wrong = shuffle(pool.filter(x => x !== correct)).slice(0, 3)
  return shuffle([correct, ...wrong])
}

function startQuiz(m: Mode) {
  mode.value = m
  finished.value = false
  score.value = 0
  userAnswers.value = []
  questionIndex.value = 0
  answered.value = false
  chosen.value = null

  if (m === 'circuit') {
    questions.value = pick(circuits.value, Math.min(10, circuits.value.length))
    setCircuitOptions()
  } else {
    questions.value = pick(drivers.value, Math.min(10, drivers.value.length))
    setDriverOptions()
  }
}

function setCircuitOptions() {
  const q = questions.value[questionIndex.value] as QuizCircuit
  const allNames = circuits.value.map(c => c.name)
  currentOptions.value = buildOptions(q.name, allNames)
}

function setDriverOptions() {
  const q = questions.value[questionIndex.value] as QuizDriver
  const allCountries = [...new Set(drivers.value.map(d => d.countryName))]
  currentOptions.value = buildOptions(q.countryName, allCountries)
}

function answer(opt: string) {
  if (answered.value) return
  answered.value = true
  chosen.value = opt
  userAnswers.value.push(opt)
  if (opt === correctAnswer(questions.value[questionIndex.value]!)) score.value++
}

function next() {
  if (questionIndex.value + 1 >= questions.value.length) {
    finished.value = true
    return
  }
  questionIndex.value++
  answered.value = false
  chosen.value = null
  if (mode.value === 'circuit') setCircuitOptions()
  else setDriverOptions()
}

function restartQuiz() {
  if (mode.value) startQuiz(mode.value)
}

function optionClass(opt: string) {
  if (!answered.value) return ''
  const correct = correctAnswer(questions.value[questionIndex.value]!)
  if (opt === correct) return 'correct'
  if (opt === chosen.value) return 'wrong'
  return 'faded'
}

const resultLabel = computed(() => {
  const pct = score.value / questions.value.length
  if (pct === 1)   return t('quiz.results.perfect')
  if (pct >= 0.8)  return t('quiz.results.great')
  if (pct >= 0.6)  return t('quiz.results.good')
  if (pct >= 0.4)  return t('quiz.results.ok')
  return t('quiz.results.bad')
})
</script>

<style scoped>
.quiz-wrap {
  max-width: 640px;
  margin: 0 auto;
  padding: 8px 0 40px;
}

/* ── States ── */
.quiz-state {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 8px 0 40px;
}
.quiz-hint-sub {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  opacity: 0.6;
  margin: 0;
  text-align: center;
  max-width: 340px;
}

/* ── Mode selection ── */
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
  grid-template-columns: 1fr 1fr;
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
  transition: border-color 0.18s, background 0.18s;
  text-align: left;
}
.mode-card:hover:not(.disabled) {
  border-color: var(--accent);
  background: rgba(61,127,255,0.06);
}
.mode-card.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.mode-icon {
  font-size: 20px;
  color: var(--accent);
}
.mode-label {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--text);
}
.mode-desc {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
}
.mode-na {
  font-family: var(--font-mono);
  font-size: 9px;
  color: #e10600;
  letter-spacing: 0.08em;
}

/* ── Active quiz ── */
.quiz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
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
  border-radius: 8px;
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
  letter-spacing: 0.1em;
  color: var(--text-dim);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
  text-align: center;
}
.option-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--text);
}
.option-btn.correct {
  border-color: #22c55e;
  background: rgba(34,197,94,0.12);
  color: #22c55e;
}
.option-btn.wrong {
  border-color: #e10600;
  background: rgba(225,6,0,0.1);
  color: #e10600;
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
  border-radius: 8px;
  padding: 12px 24px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #fff;
  cursor: pointer;
  transition: opacity 0.15s;
}
.next-btn:hover { opacity: 0.85; }

/* ── Results ── */
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
.result-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.result-num {
  font-family: var(--font-display);
  font-size: 72px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1;
}
.result-denom {
  font-family: var(--font-display);
  font-size: 28px;
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
  border-radius: 8px;
  padding: 12px 24px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #fff;
  cursor: pointer;
  transition: opacity 0.15s;
}
.restart-btn:hover { opacity: 0.85; }
.back-btn {
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px 24px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.back-btn:hover {
  border-color: var(--accent);
  color: var(--text);
}

/* ── Review ── */
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
  border-radius: 8px;
  background: rgba(0,0,0,0.12);
}
.review-row.correct { border-left: 3px solid #22c55e; }
.review-row.wrong   { border-left: 3px solid #e10600; }
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
  color: #e10600;
  letter-spacing: 0.06em;
}
.review-icon {
  font-size: 14px;
  color: var(--text-faint);
}
.review-row.correct .review-icon { color: #22c55e; }
.review-row.wrong   .review-icon { color: #e10600; }
</style>
