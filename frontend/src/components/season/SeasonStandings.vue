 <template>
  <div class="standings-wrap">
    <!-- Loading -->
    <div v-if="store.loading" class="state-msg">Daten werden geladen …</div>

    <template v-else-if="store.drivers.length">
      <!-- Punkteverlauf Chart -->
      <PointsChart />

      <!-- Zwei-Spalten Wertungen -->
      <div class="dual">
        <!-- Fahrerwertung -->
        <div class="block">
          <div class="block-head">
            <span class="eyebrow">Fahrerwertung</span>
            <span class="mono season-label">SAISON {{ store.year }}</span>
          </div>
          <DriverChampionship />
        </div>

        <!-- Konstrukteurswertung -->
        <div class="block">
          <div class="block-head">
            <span class="eyebrow">Konstrukteurswertung</span>
            <span class="mono season-label">SAISON {{ store.year }}</span>
          </div>
          <ConstructorChampionship />
        </div>
      </div>
    </template>

    <div v-else class="state-msg">Keine Daten für diese Saison verfügbar.</div>
  </div>
</template>

<script setup lang="ts">
import { useSeasonStore } from '@/stores/seasonStore'
import PointsChart from './PointsChart.vue'
import DriverChampionship from './DriverChampionship.vue'
import ConstructorChampionship from './ConstructorChampionship.vue'

const store = useSeasonStore()
</script>

<style scoped>
.standings-wrap {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28px;
  align-items: start;
}

.block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.season-label {
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--accent);
}

.state-msg {
  padding: 60px 0;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

@media (max-width: 900px) {
  .dual {
    grid-template-columns: 1fr;
  }
}
</style>
