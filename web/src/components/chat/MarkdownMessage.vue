<template>
  <div class="markdown-message" v-html="html" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const props = defineProps<{
  content?: string
}>()

const html = computed(() => {
  const parsed = marked.parse(stripExecutionTrace(props.content ?? ''), {
    async: false,
    breaks: true,
    gfm: true
  }) as string

  return DOMPurify.sanitize(parsed, {
    USE_PROFILES: { html: true }
  })
})

function stripExecutionTrace(content: string) {
  const normalized = content.replace(/\r\n/g, '\n')
  const trimmed = normalized.trimStart()
  if (!trimmed.startsWith('### 执行过程')) {
    return content
  }

  const lines = trimmed.split('\n')
  let index = 1
  while (index < lines.length) {
    if (lines[index].trim() === '') {
      index += 1
      break
    }
    index += 1
  }

  return lines.slice(index).join('\n').trimStart()
}
</script>

<style scoped>
.markdown-message {
  color: #111827;
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.markdown-message :deep(*) {
  margin-top: 0;
}

.markdown-message :deep(*:last-child) {
  margin-bottom: 0;
}

.markdown-message :deep(p),
.markdown-message :deep(ul),
.markdown-message :deep(ol),
.markdown-message :deep(pre),
.markdown-message :deep(blockquote),
.markdown-message :deep(table) {
  margin-bottom: 10px;
}

.markdown-message :deep(h1),
.markdown-message :deep(h2),
.markdown-message :deep(h3),
.markdown-message :deep(h4) {
  margin-bottom: 8px;
  color: #101828;
  font-weight: 700;
  line-height: 1.35;
}

.markdown-message :deep(h1) {
  font-size: 20px;
}

.markdown-message :deep(h2) {
  font-size: 18px;
}

.markdown-message :deep(h3) {
  font-size: 16px;
}

.markdown-message :deep(h4) {
  font-size: 16px;
}

.markdown-message :deep(ul),
.markdown-message :deep(ol) {
  padding-left: 22px;
}

.markdown-message :deep(li + li) {
  margin-top: 6px;
}

.markdown-message :deep(code) {
  border-radius: 4px;
  background: #eef2f6;
  color: #344054;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.92em;
  padding: 1px 4px;
}

.markdown-message :deep(pre) {
  overflow: auto;
  border-radius: 8px;
  background: #101828;
  color: #f9fafb;
  padding: 12px;
}

.markdown-message :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.markdown-message :deep(blockquote) {
  border-left: 3px solid #d0d5dd;
  color: #475467;
  padding-left: 12px;
}

.markdown-message :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-message :deep(th),
.markdown-message :deep(td) {
  border: 1px solid #d0d5dd;
  padding: 6px 8px;
  text-align: left;
}

.markdown-message :deep(th) {
  background: #f2f4f7;
  font-weight: 600;
}

.markdown-message :deep(a) {
  color: #175cd3;
  text-decoration: none;
}

.markdown-message :deep(a:hover) {
  text-decoration: underline;
}
</style>
