<template>
  <section class="conversation-history" aria-label="历史会话">
    <header class="conversation-history__header">
      <div>
        <strong>会话</strong>
        <span>{{ conversations.length }}</span>
      </div>
      <div class="conversation-history__actions">
        <el-tooltip content="刷新会话" placement="bottom">
          <el-button circle text :loading="loading" aria-label="刷新会话" @click="$emit('refresh')">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="新建会话" placement="bottom">
          <el-button circle type="primary" aria-label="新建会话" @click="$emit('create')">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </header>

    <el-input v-model="query" class="conversation-history__search" clearable placeholder="搜索会话">
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <div v-if="loading && conversations.length === 0" class="conversation-history__loading">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="filteredConversations.length" class="conversation-history__list">
      <button
        v-for="conversation in filteredConversations"
        :key="conversation.id"
        type="button"
        :class="['conversation-history__item', { 'is-active': conversation.id === activeId }]"
        @click="$emit('select', conversation.id)"
      >
        <el-icon><ChatDotRound /></el-icon>
        <span class="conversation-history__item-copy">
          <strong>{{ conversation.title }}</strong>
          <small>{{ formatUpdatedAt(conversation.updatedAt) }}</small>
        </span>
      </button>
    </div>

    <div v-else class="conversation-history__empty">
      {{ query ? '没有匹配的会话' : '暂无历史会话' }}
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChatDotRound, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import type { ConversationSummary } from '../../types/conversation'

const props = defineProps<{
  conversations: ConversationSummary[]
  activeId: string
  loading: boolean
}>()

defineEmits<{
  select: [id: string]
  create: []
  refresh: []
}>()

const query = ref('')

const filteredConversations = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  if (!keyword) {
    return props.conversations
  }
  return props.conversations.filter((conversation) => conversation.title.toLocaleLowerCase().includes(keyword))
})

function formatUpdatedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(date)
  }
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date)
}
</script>

<style scoped>
.conversation-history {
  height: 100%;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
  min-width: 0;
}

.conversation-history__header,
.conversation-history__header > div,
.conversation-history__actions {
  display: flex;
  align-items: center;
}

.conversation-history__header {
  justify-content: space-between;
  gap: 12px;
}

.conversation-history__header > div:first-child {
  gap: 8px;
  color: #182230;
}

.conversation-history__header strong {
  font-size: 15px;
}

.conversation-history__header span {
  min-width: 22px;
  height: 20px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: #e9eaeb;
  color: #535862;
  font-size: 11px;
}

.conversation-history__actions {
  gap: 4px;
}

.conversation-history__list {
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 4px;
  overflow: auto;
  scrollbar-width: thin;
}

.conversation-history__item {
  width: 100%;
  min-height: 58px;
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #535862;
  text-align: left;
  cursor: pointer;
}

.conversation-history__item:hover {
  background: #f5f5f5;
}

.conversation-history__item.is-active {
  border-color: #b2ddff;
  background: #eff8ff;
  color: #175cd3;
}

.conversation-history__item-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.conversation-history__item-copy strong {
  overflow: hidden;
  color: #252b37;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-history__item-copy small {
  color: #717680;
  font-size: 11px;
}

.conversation-history__empty {
  align-self: center;
  color: #717680;
  font-size: 13px;
  text-align: center;
}

.conversation-history__loading {
  padding: 4px;
}
</style>
