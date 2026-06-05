import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    selectedModelId: '',
    thinkingEnabled: false
  }),
  actions: {
    setModel(modelId: string) {
      this.selectedModelId = modelId
    },
    setThinkingEnabled(enabled: boolean) {
      this.thinkingEnabled = enabled
    }
  }
})
