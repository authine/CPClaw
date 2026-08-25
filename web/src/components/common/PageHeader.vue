<template>
  <header class="page-header">
    <div class="page-header__content">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.page-header__content { min-width: 0; }
.page-header__actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: var(--cp-space-2); }
</style>
