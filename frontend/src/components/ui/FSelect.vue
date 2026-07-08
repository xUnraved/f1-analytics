<template>
  <div ref="rootEl" class="fselect" :style="width ? { width } : {}">
    <button type="button" class="fs-btn clip-tab" :class="{ open }" @click="open = !open">
      <span class="fs-val">{{ selectedLabel }}</span>
      <span class="fs-car">▾</span>
    </button>
    <transition name="fs">
      <ul v-if="open" class="fs-panel">
        <li
          v-for="o in options"
          :key="o.value"
          class="fs-opt"
          :class="{ sel: o.value === modelValue }"
          @click="select(o.value)"
        >
          <span class="fs-bar"></span>{{ o.label }}
        </li>
      </ul>
    </transition>
  </div>
</template>

<script setup lang="ts">
/**
 * Benutzerdefiniertes Dropdown-Select-Element (v-model kompatibel).
 * Schließt sich automatisch bei Klick außerhalb (onDoc) oder Escape (onKey).
 * Emittiert 'update:modelValue' für v-model und 'change' für direkte Reaktion.
 * width: optionale CSS-Breite (z. B. "180px").
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

type Val = number | string

const props = defineProps<{
  modelValue: Val
  options: { value: Val; label: string }[]
  width?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [v: Val]; change: [v: Val] }>()

const rootEl = ref<HTMLElement | null>(null)
const open = ref(false)

const selectedLabel = computed(
  () => props.options.find((o) => o.value === props.modelValue)?.label ?? String(props.modelValue),
)

function select(v: Val) {
  open.value = false
  if (v !== props.modelValue) {
    emit('update:modelValue', v)
    emit('change', v)
  }
}

function onDoc(e: MouseEvent) {
  if (rootEl.value && !rootEl.value.contains(e.target as Node)) open.value = false
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('click', onDoc)
  document.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDoc)
  document.removeEventListener('keydown', onKey)
})
</script>

<style scoped>
.fselect {
  position: relative;
  min-width: 108px;
}

.fs-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: linear-gradient(180deg, var(--surface-2), var(--surface));
  border: 1px solid var(--line);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.08em;
  padding: 9px 14px;
  cursor: pointer;
  transition:
    border-color 0.18s,
    box-shadow 0.18s;
}

.fs-btn:hover,
.fs-btn.open {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 40%, transparent), 0 0 18px -6px var(--accent);
}

.fs-car {
  color: var(--text-faint);
  font-size: 11px;
  transition: transform 0.18s;
}

.fs-btn.open .fs-car {
  transform: rotate(180deg);
  color: var(--accent);
}

.fs-panel {
  position: absolute;
  right: 0;
  top: calc(100% + 7px);
  min-width: 100%;
  list-style: none;
  background: rgba(11, 14, 19, 0.97);
  backdrop-filter: blur(10px);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 6px;
  z-index: 80;
  box-shadow: var(--shadow);
}

.fs-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--text-dim);
  padding: 9px 12px;
  border-radius: 7px;
  cursor: pointer;
  transition:
    background 0.14s,
    color 0.14s;
}

.fs-opt .fs-bar {
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: transparent;
}

.fs-opt:hover {
  background: var(--surface-2);
  color: var(--text);
}

.fs-opt.sel {
  color: var(--text);
}

.fs-opt.sel .fs-bar {
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
}

.fs-enter-active,
.fs-leave-active {
  transition:
    opacity 0.16s,
    transform 0.16s;
}

.fs-enter-from,
.fs-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
