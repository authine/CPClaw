<template>
  <div class="markdown-message">
    <slot>{{ content }}</slot>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  content?: string
}>()
</script>

<style scoped>
.markdown-message {
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
