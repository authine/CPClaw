import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export type CpTheme = 'light' | 'dark'

const theme = ref<CpTheme>(typeof window !== 'undefined' && window.localStorage.getItem('cpclaw-theme') === 'dark' ? 'dark' : 'light')

function syncTheme() {
  theme.value = window.localStorage.getItem('cpclaw-theme') === 'dark' ? 'dark' : 'light'
  document.documentElement.classList.toggle('cpclaw-theme-dark', theme.value === 'dark')
}

export function setTheme(next: CpTheme) {
  theme.value = next
  window.localStorage.setItem('cpclaw-theme', next)
  document.documentElement.classList.toggle('cpclaw-theme-dark', next === 'dark')
  window.dispatchEvent(new Event('cpclaw-theme-change'))
}

export function useTheme() {
  onMounted(() => {
    syncTheme()
    window.addEventListener('storage', syncTheme)
    window.addEventListener('cpclaw-theme-change', syncTheme)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('storage', syncTheme)
    window.removeEventListener('cpclaw-theme-change', syncTheme)
  })
  return { theme, darkMode: computed({ get: () => theme.value === 'dark', set: (enabled: boolean) => setTheme(enabled ? 'dark' : 'light') }) }
}
