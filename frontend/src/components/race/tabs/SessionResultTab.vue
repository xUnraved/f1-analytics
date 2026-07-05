<template>
  <div>
    <div v-if="!sessions.length" class="state">{{ t('race.session.noData') }}</div>

    <template v-else>
      <!-- Untertabs bei mehreren Sessions (Training P1/P2/P3) -->
      <div v-if="sessions.length > 1" class="session-tabs">
        <button
          v-for="s in sessions"
          :key="s.name"
          type="button"
          class="stab clip-tab"
          :class="{ active: activeIndex === sessions.indexOf(s) }"
          @click="activeIndex = sessions.indexOf(s)"
        >
          {{ s.name }}
        </button>
      </div>

      <div v-if="!activeSession.result.length" class="state">{{ t('race.session.noResults') }}</div>

      <div v-else class="table-wrap">
        <table class="result-table">
          <thead>
          <tr>
            <th class="col-pos">{{ t('common.pos') }}</th>
            <th>{{ t('common.driver') }}</th>
            <th class="hide-sm">{{ t('common.team') }}</th>
            <th class="col-time">{{ t('common.bestLap') }}</th>
            <th class="col-gap">{{ t('race.session.gap') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr
            v-for="r in activeSession.result"
            :key="r.pos"
            :class="{ 'row-out': r.dnf || r.dns || r.dsq }"
          >
            <td>
              <span class="pos-badge" :class="posBadgeClass(r.pos)">{{ r.pos }}</span>
            </td>
            <td>
              <div class="driver-cell">
                <span class="team-bar" :style="{ background: r.color }"></span>
                <div>
                  <div class="driver-name">{{ r.name }}</div>
                  <div class="driver-abbr">{{ r.abbr }}</div>
                </div>
              </div>
            </td>
            <td class="cell-dim hide-sm">{{ r.team }}</td>
            <td class="cell-time">{{ r.bestLap }}</td>
            <td class="cell-gap">
              <span v-if="r.dnf" class="badge-out">DNF</span>
              <span v-else-if="r.dns" class="badge-out">DNS</span>
              <span v-else-if="r.dsq" class="badge-out">DSQ</span>
              <span v-else-if="r.gap === 'POLE'" class="badge-pole">POLE</span>
              <span v-else>{{ r.gap }}</span>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PracticeSession } from '@/types/f1'

const { t } = useI18n()

const props = defineProps<{
  sessions: PracticeSession[]
}>()

const activeIndex = ref(0)

watch(() => props.sessions, () => { activeIndex.value = 0 }, { deep: true })

const activeSession = computed<PracticeSession>(
  () => props.sessions[activeIndex.value] ?? { name: '', result: [] }
)

function posBadgeClass(pos: number) {
  if (pos === 1) return 'badge-gold'
  if (pos === 2) return 'badge-silver'
  if (pos === 3) return 'badge-bronze'
  return ''
}
</script>

<style scoped>
.state {
  padding: 60px 0;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-faint);
}

.session-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 18px;
}

.stab {
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  padding: 8px 20px 8px 14px;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}
.stab:hover { color: var(--text-dim); }
.stab.active { background: var(--accent); border-color: var(--accent); color: #fff; }

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.result-table {
  width: 100%;
  border-collapse: collapse;
}

.result-table thead tr {
  background: var(--surface);
  border-bottom: 2px solid var(--accent);
}

.result-table th {
  padding: 10px 14px;
  text-align: left;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-faint);
  white-space: nowrap;
}

.col-pos  { width: 52px; text-align: center; }
.col-time { width: 110px; text-align: right; }
.col-gap  { width: 110px; }

.result-table tbody tr {
  border-bottom: 1px solid var(--line-soft);
  transition: background 0.12s;
}
.result-table tbody tr:hover { background: var(--surface); }
.row-out { opacity: 0.45; }

.result-table td {
  padding: 11px 14px;
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
.badge-gold   { background: #7a5a0a; color: #f5c842; }
.badge-silver { background: #3a3a3a; color: #c8c8c8; }
.badge-bronze { background: #5a3010; color: #cd7f3a; }

.driver-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team-bar {
  width: 3px;
  height: 32px;
  border-radius: 2px;
  flex-shrink: 0;
}

.driver-name {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text);
}

.driver-abbr {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  letter-spacing: 0.08em;
  margin-top: 1px;
}

.cell-dim { color: var(--text-dim); font-size: 0.88rem; }
.cell-time { font-family: var(--font-mono); font-size: 0.88rem; color: var(--text-dim); text-align: right; }
.cell-gap  { font-family: var(--font-mono); font-size: 0.88rem; color: var(--text-faint); }

.badge-out {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  padding: 2px 7px;
  border-radius: 3px;
  background: rgba(255, 60, 60, 0.15);
  color: #ff6b6b;
}

.badge-pole {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  padding: 2px 7px;
  border-radius: 3px;
  background: rgba(61, 127, 255, 0.15);
  color: var(--accent);
}

@media (max-width: 640px) {
  .hide-sm { display: none; }
}
</style>
