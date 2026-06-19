<template>
  <div class="chart-section">
    <div class="chart-header">
      <span class="eyebrow">Punkteverlauf · Kumuliert</span>
      <div class="top-btns">
        <button
          v-for="n in tops"
          :key="n"
          type="button"
          class="top-btn"
          :class="{ active: topN === n }"
          @click="topN = n"
        >
          TOP {{ n }}
        </button>
      </div>
    </div>
    <div class="canvas-wrap">
      <Line v-if="hasData" :data="chartData" :options="chartOptions" />
      <div v-else class="no-data">Keine abgeschlossenen Rennen</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
} from 'chart.js'
import { useSeasonStore } from '@/stores/seasonStore'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend)

const store = useSeasonStore()
const tops = [3, 5, 10]
const topN = ref(5)

const completedRaces = computed(() => store.races.filter((r) => r.completed))
const completedCount = computed(() => completedRaces.value.length)
const hasData = computed(() => completedCount.value > 0 && store.drivers.length > 0)

const chartData = computed(() => ({
  labels: completedRaces.value.map((r) => r.gp),
  datasets: store.drivers.slice(0, topN.value).map((d) => ({
    label: d.abbr,
    data: d.cum.slice(0, completedCount.value),
    borderColor: d.color,
    backgroundColor: d.color + '22',
    borderWidth: 2.5,
    pointRadius: 3,
    pointHoverRadius: 6,
    tension: 0.25,
    fill: false,
  })),
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: {
      display: true,
      labels: {
        color: '#aeb8c6',
        font: { family: 'JetBrains Mono', size: 11 },
        boxWidth: 14,
        padding: 16,
      },
    },
    tooltip: {
      backgroundColor: '#141921',
      borderColor: '#28303c',
      borderWidth: 1,
      titleColor: '#eef1f6',
      bodyColor: '#aeb8c6',
      padding: 10,
      callbacks: {
        label: (ctx: { dataset: { label: string }; parsed: { y: number } }) =>
          `  ${ctx.dataset.label}: ${ctx.parsed.y} Pkt`,
      },
    },
  },
  scales: {
    x: {
      ticks: {
        color: '#6c7686',
        font: { family: 'JetBrains Mono', size: 10 },
        maxRotation: 45,
        minRotation: 30,
      },
      grid: { color: '#28303c' },
    },
    y: {
      ticks: { color: '#6c7686', font: { family: 'JetBrains Mono', size: 11 } },
      grid: { color: '#28303c' },
      title: { display: true, text: 'Punkte', color: '#6c7686', font: { size: 11 } },
    },
  },
}
</script>

<style scoped>
.chart-section {
  background: linear-gradient(180deg, var(--surface), color-mix(in srgb, var(--surface) 62%, transparent));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 20px 22px 18px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.top-btns {
  display: flex;
  gap: 6px;
}

.top-btn {
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  padding: 5px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.top-btn:hover {
  color: var(--text-dim);
  border-color: var(--accent);
}

.top-btn.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.canvas-wrap {
  height: 320px;
  position: relative;
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-faint);
  letter-spacing: 0.06em;
}
</style>
