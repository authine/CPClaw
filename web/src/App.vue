<template>
  <RouterView />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

const THEME_STORAGE_KEY = 'cpclaw-theme'
const DARK_THEME_CLASS = 'cpclaw-theme-dark'

function syncThemeClass() {
  document.documentElement.classList.toggle(DARK_THEME_CLASS, window.localStorage.getItem(THEME_STORAGE_KEY) === 'dark')
}

onMounted(() => {
  syncThemeClass()
  window.addEventListener('storage', syncThemeClass)
  window.addEventListener('cpclaw-theme-change', syncThemeClass)
})

onBeforeUnmount(() => {
  window.removeEventListener('storage', syncThemeClass)
  window.removeEventListener('cpclaw-theme-change', syncThemeClass)
})
</script>
