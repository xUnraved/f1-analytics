<template>
  <div class="table-wrap">
    <table class="standings">
      <thead>
      <tr>
        <th class="col-pos">POS</th>
        <th class="col-driver">FAHRER</th>
        <th class="col-team">TEAM</th>
        <th class="col-num">PTS</th>
        <th class="col-num">SIEGE</th>
        <th class="col-num">PODESTE</th>
        <th class="col-num hide-sm">DNF</th>
        <th class="col-score">F1ALYTICS Ø</th>
        <th class="col-gap hide-sm">LETZTES</th>
      </tr>
      </thead>
      <tbody>
      <tr
        v-for="(d, i) in store.drivers"
        :key="d.abbr"
        class="row"
        :class="rowClass(i)"
      >
        <td>
          <span class="pos-badge" :class="posBadgeClass(i)">{{ i + 1 }}</span>
        </td>
        <td>
          <div class="driver-cell">
            <span class="team-stripe" :style="{ background: d.color }"></span>
            <div>
              <div class="driver-name">{{ d.name }}</div>
              <div class="driver-abbr">{{ d.abbr }} · #{{ d.num }}</div>
            </div>
          </div>
        </td>
        <td class="cell-team">{{ d.team }}</td>
        <td class="cell-pts">{{ d.points }}</td>
        <td class="cell-num">{{ d.wins }}</td>
        <td class="cell-num">{{ d.podiums }}</td>
        <td class="cell-num hide-sm">{{ d.dnf }}</td>
        <td class="cell-score"><ScoreBadge :value="d.avgScore" /></td>
        <td class="cell-gap hide-sm">
          {{ d.finishes.length ? 'P' + d.finishes[d.finishes.length - 1] : '—' }}
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { useSeasonStore } from '@/stores/seasonStore'
import ScoreBadge from '@/components/ui/ScoreBadge.vue'

const store = useSeasonStore()

function rowClass(i: number): string {
  if (i === 0) return 'row-champion'
  if (i < 3) return 'row-podium'
  return ''
}

function posBadgeClass(i: number): string {
  if (i === 0) return 'badge-gold'
  if (i === 1) return 'badge-silver'
  if (i === 2) return 'badge-bronze'
  return ''
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
.col-num { width: 70px; text-align: center; }
.col-score { width: 96px; text-align: center; }
.col-gap { width: 80px; text-align: center; }

.standings tbody .row {
  border-bottom: 1px solid var(--line-soft);
  transition: background 0.12s;
}

.standings tbody .row:hover {
  background: var(--surface);
}

.row-champion {
  background: rgba(255, 30, 30, 0.04);
}

.row-podium {
  background: rgba(255, 255, 255, 0.015);
}

.standings td {
  padding: 12px 14px;
  color: var(--text-dim);
  font-size: 0.88rem;
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

.badge-gold  { background: #7a5a0a; color: #f5c842; }
.badge-silver { background: #3a3a3a; color: #c0c0c0; }
.badge-bronze { background: #4a2a10; color: #cd7f32; }

.driver-cell {
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

.driver-name {
  font-weight: 700;
  color: var(--text);
  letter-spacing: 0.02em;
  font-size: 0.93rem;
}

.driver-abbr {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  margin-top: 2px;
  letter-spacing: 0.06em;
}

.cell-team {
  font-size: 0.8rem;
  color: var(--text-faint);
  letter-spacing: 0.02em;
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

.cell-score {
  text-align: center;
}

.cell-gap {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  text-align: center;
  color: var(--text-faint);
}

@media (max-width: 640px) {
  .hide-sm { display: none; }
}
</style>
