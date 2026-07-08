<template>
  <div class="table-wrap">
    <table class="standings">
      <thead>
        <tr>
          <th class="col-pos">{{ t('common.pos') }}</th>
          <th>{{ t('common.team') }}</th>
          <th class="col-num">{{ t('common.pts') }}</th>
          <th class="col-num">{{ t('common.wins') }}</th>
          <th class="col-num hide-sm">{{ t('common.driver') }}</th>
          <th class="col-bar hide-sm">{{ t('standings.share') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(t, i) in store.teams"
          :key="t.team"
          class="row"
          :class="i === 0 ? 'row-champion' : ''"
        >
          <td>
            <span class="pos-badge" :class="i === 0 ? 'badge-gold' : ''">{{ i + 1 }}</span>
          </td>
          <td>
            <div class="team-cell">
              <span class="team-stripe" :style="{ background: t.color }"></span>
              <div>
                <div class="team-name">{{ t.team }}</div>
                <div class="team-drivers">{{ t.drivers.map((d) => d.abbr).join(' · ') }}</div>
              </div>
            </div>
          </td>
          <td class="cell-pts">{{ t.points }}</td>
          <td class="cell-num">{{ t.wins }}</td>
          <td class="cell-num hide-sm">{{ t.drivers.length }}</td>
          <td class="cell-bar hide-sm">
            <div class="bar-bg">
              <div
                class="bar-fill"
                :style="{ width: pct(t.points) + '%', background: t.color }"
              ></div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
/**
 * Konstrukteurs-WM-Rangliste.
 * Zeigt Teams mit Punkten, Siegen und einem Balken (Anteil an den Punkten
 * des Führenden). pct() normiert relativ zum Führenden auf 100 %.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'

const { t } = useI18n()
const store = useSeasonStore()
const maxPts = computed(() => store.teams[0]?.points ?? 1)
function pct(pts: number) {
  return Math.round((pts / maxPts.value) * 100)
}
</script>

<style scoped>
.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.standings {
  width: 100%;
  border-collapse: collapse;
}

.standings thead tr {
  background: var(--surface);
  border-bottom: 2px solid var(--accent);
}

.standings th {
  padding: 10px 14px;
  text-align: left;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--text-faint);
  white-space: nowrap;
}

.col-pos { width: 52px; text-align: center; }
.col-num { width: 72px; text-align: center; }
.col-bar { width: 140px; }

.standings tbody .row {
  border-bottom: 1px solid var(--line-soft);
  transition: background 0.12s;
}

.standings tbody .row:hover { background: var(--surface); }
.row-champion { background: rgba(255, 30, 30, 0.04); }

.standings td {
  padding: 13px 14px;
  vertical-align: middle;
}

.pos-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  border-radius: 4px;
  background: var(--surface-2);
  color: var(--text-dim);
}
.badge-gold { background: #7a5a0a; color: #f5c842; }

.team-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.team-stripe {
  width: 3px;
  height: 36px;
  border-radius: 2px;
  flex-shrink: 0;
}

.team-name {
  font-weight: 700;
  color: var(--text);
  font-size: 0.93rem;
}

.team-drivers {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  margin-top: 2px;
  letter-spacing: 0.06em;
}

.cell-pts {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--text);
  text-align: center;
}

.cell-num {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  text-align: center;
  color: var(--text-dim);
}

.bar-bg {
  height: 6px;
  background: var(--surface-2);
  border-radius: 3px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s ease;
}

@media (max-width: 640px) {
  .hide-sm { display: none; }
}
</style>
