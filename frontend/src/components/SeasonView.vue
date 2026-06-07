<template>
  <div>
    <h1>F1 Sessions</h1>

    <select v-model="selectedYear" @change="load">
      <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
    </select>

    <p v-if="store.loading">Laden...</p>
    <p v-else-if="store.error">{{ store.error }}</p>

    <ul v-else>
      <li v-for="s in store.sessions" :key="s.sessionKey">
        {{ s.countryName }} – {{ s.sessionName }} ({{ s.sessionType }})
        <span>{{ s.dateStart.slice(0, 10) }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'

const store = useSessionStore()
const selectedYear = ref(2024)
const years = [2023, 2024, 2025]

function load() {
  store.loadSessions(selectedYear.value)
}

onMounted(load)
</script>
