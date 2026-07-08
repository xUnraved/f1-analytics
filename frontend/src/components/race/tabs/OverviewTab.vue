<template>
  <div v-if="race" class="panel">
    <div v-if="!race.result.length" class="empty">{{ t('race.overview.noResults') }}</div>

    <div v-else class="table-scroll">
      <table class="results-table">
        <thead>
        <tr>
          <th>{{ t('common.pos') }}</th>
          <th>{{ t('common.driver') }}</th>
          <th>{{ t('common.team') }}</th>
          <th>{{ t('common.laps') }}</th>
          <th>{{ t('common.gap') }}</th>
          <th>{{ t('common.status') }}</th>
          <th class="th-score">{{ t('race.overview.f1alytics') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="r in race.result" :key="r.abbr" :class="rowClass(r)">
          <td><span class="pos-badge" :class="posBadgeClass(r)">{{ out(r) ? '—' : r.pos }}</span></td>
          <td>
            <div class="driver-cell">
              <span class="team-bar" :style="{ background: r.color }"></span>
              <span class="driver-name">{{ r.name }}</span>
            </div>
          </td>
          <td class="cell-team">{{ r.team }}</td>
          <td>{{ r.laps ?? '—' }}</td>
          <td class="cell-gap">{{ gapDisplay(r) }}</td>
          <td><span class="status-chip" :class="statusClass(r)">{{ statusText(r) }}</span></td>
          <td class="cell-score"><ScoreBadge :card="r.score" /></td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Rennergebnis-Tab: zeigt Starterfeld mit Platz, Fahrer, Team, Runden,
 * Abstand und F1alytics ScoreBadge.
 * out(): DNF/DNS/DSQ-Fahrer werden optisch ausgegraut (row-out).
 * gapDisplay(): zeigt „—" für P1 und für ausgeschiedene Fahrer.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSeasonStore } from '@/stores/seasonStore'
import type { RaceResultRow } from '@/types/f1'
import ScoreBadge from '@/components/ui/ScoreBadge.vue'

const { t } = useI18n()
const store = useSeasonStore()
const race = computed(() => store.selectedRace)

function out(r: RaceResultRow): boolean {
  return r.dnf || r.dns || r.dsq
}

function rowClass(r: RaceResultRow): string {
  if (r.pos === 1 && !out(r)) return 'row-winner'
  if (out(r)) return 'row-out'
  return ''
}

function posBadgeClass(r: RaceResultRow): string {
  if (out(r)) return 'pos-out'
  if (r.pos === 1) return 'pos-gold'
  if (r.pos === 2) return 'pos-silver'
  if (r.pos === 3) return 'pos-bronze'
  return ''
}

function statusClass(r: RaceResultRow): string {
  if (r.dnf) return 'chip-dnf'
  if (r.dns) return 'chip-dns'
  if (r.dsq) return 'chip-dsq'
  return 'chip-ok'
}

function statusText(r: RaceResultRow): string {
  if (r.dnf) return 'DNF'
  if (r.dns) return 'DNS'
  if (r.dsq) return 'DSQ'
  return 'FINISH'
}

function gapDisplay(r: RaceResultRow): string {
  if (out(r) || r.pos === 1) return '—'
  return r.gapText
}
</script>

<style scoped>
.panel {
  animation: fade 0.45s ease both;
}

@keyframes fade {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.empty {
  padding: 60px 0;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

.table-scroll {
  overflow-x: auto;
}

.results-table {
  width: 100%;
  border-collapse: collapse;
}

.results-table thead tr {
  border-bottom: 2px solid #e10600;
}

.results-table th {
  padding: 8px 14px;
  text-align: left;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 3px;
  color: #3a3a3a;
}

.th-score {
  text-align: center;
}

.results-table tbody tr {
  border-bottom: 1px solid #161616;
  transition: background 0.1s;
}

.results-table tbody tr:hover {
  background: #161616;
}

.row-winner {
  background: rgba(225, 6, 0, 0.04);
}

.row-out {
  opacity: 0.4;
}

.results-table td {
  padding: 11px 14px;
  font-size: 0.9rem;
  color: #ccc;
}

.cell-score {
  text-align: center;
}

.pos-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-size: 0.85rem;
  font-weight: 900;
  border-radius: 3px;
  background: #1a1a1a;
  color: #fff;
}

.pos-gold {
  background: #b8962a;
  color: #000;
}

.pos-silver {
  background: #8a8a8a;
  color: #000;
}

.pos-bronze {
  background: #8a4a20;
  color: #fff;
}

.pos-out {
  background: #141414;
  color: #2a2a2a;
}

.driver-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team-bar {
  width: 3px;
  height: 18px;
  border-radius: 2px;
  flex-shrink: 0;
}

.driver-name {
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

.cell-team {
  color: #444;
  font-size: 0.78rem;
  letter-spacing: 0.3px;
}

.cell-gap {
  font-variant-numeric: tabular-nums;
  color: #666;
  font-size: 0.82rem;
  font-family: 'Courier New', monospace;
}

.status-chip {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 1px;
  padding: 3px 8px;
  border-radius: 2px;
}

.chip-ok {
  background: #0a1f0e;
  color: #22c55e;
}

.chip-dnf {
  background: #250a08;
  color: #e10600;
}

.chip-dns {
  background: #1a1500;
  color: #a855f7;
}

.chip-dsq {
  background: #180a1a;
  color: #a855f7;
}
</style>
