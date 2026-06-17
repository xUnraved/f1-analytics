<template>
  <main>
    <SeasonView />
  </main>
</template>

<script setup lang="ts">
import SeasonView from '@/components/SeasonView.vue'
</script>

<style>
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  background: #0f0f0f;
  color: #fff;
  font-family: 'Titillium Web', 'Arial Narrow', Arial, sans-serif;
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
}

#app {
  min-height: 100vh;
}

::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: #111; }
::-webkit-scrollbar-thumb { background: #2a2a2a; border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: #e10600; }
</style>
