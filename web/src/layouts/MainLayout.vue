<template>
  <el-container :class="['layout', { 'layout--chat': isWorkbenchRoute }]">
    <el-header v-if="!isWorkbenchRoute" class="layout__header" height="64px">
      <RouterLink class="layout__brand" to="/" aria-label="CPClaw 对话工作台">
        <span class="layout__brand-mark">C</span>
        <span class="layout__brand-text">CPClaw</span>
      </RouterLink>
      <el-menu router mode="horizontal" :default-active="activePath" class="layout__nav" :ellipsis="false">
        <el-menu-item index="/">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/metadata">
          <el-icon><Collection /></el-icon>
          <span>元数据</span>
        </el-menu-item>
        <el-menu-item index="/settings?section=log-analytics">
          <el-icon><DocumentChecked /></el-icon>
          <span>日志分析</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-header>
    <el-main :class="['layout__main', { 'layout__main--chat': isWorkbenchRoute }]">
      <RouterView />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Collection, DocumentChecked, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const activePath = computed(() => route.path)
const isChatRoute = computed(() => route.name === 'chat')
const isWorkbenchRoute = computed(() => isChatRoute.value || route.name === 'settings' || route.name === 'settings-metadata-app')
</script>

<style scoped>
.layout {
  background: var(--cp-bg-page);
  min-height: 100vh;
}

.layout__header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: var(--cp-space-7);
  padding: 0 var(--cp-space-6);
  border-bottom: 1px solid var(--cp-border);
  background: var(--cp-bg-surface);
  backdrop-filter: blur(12px);
}

.layout__brand {
  display: flex;
  align-items: center;
  gap: var(--cp-space-3);
  color: var(--cp-text-primary);
  text-decoration: none;
}

.layout__brand-mark {
  display: grid;
  width: 32px;
  height: 32px;
  border-radius: var(--cp-radius-md);
  background: var(--cp-brand);
  color: #fff;
  font-weight: 700;
  place-items: center;
}

.layout__brand-text {
  font-size: var(--cp-font-body-lg);
  font-weight: 800;
  letter-spacing: 0;
}

.layout__nav {
  flex: 1;
  min-width: 0;
  border-bottom: 0;
  background: transparent;
}

.layout__nav :deep(.el-menu-item) {
  height: var(--cp-header-height);
  border-bottom-width: 2px;
  color: var(--cp-text-secondary);
  font-weight: 600;
}

.layout__nav :deep(.el-menu-item:hover),
.layout__nav :deep(.el-menu-item:focus) {
  background: var(--cp-bg-hover);
  color: var(--cp-text-primary);
}

.layout__nav :deep(.el-menu-item.is-active) {
  border-bottom-color: var(--cp-brand);
  background: var(--cp-brand-soft);
  color: var(--cp-text-primary);
}

.layout__nav :deep(.el-menu-item.is-active .el-icon) {
  color: var(--cp-brand);
}

.layout__main {
  padding: var(--cp-space-6) var(--cp-content-pad);
}

.layout__main--chat {
  min-height: 100vh;
  padding: 0;
  overflow: visible;
}

@media (max-width: 760px) {
  .layout__header {
    align-items: stretch;
    flex-direction: column;
    height: auto !important;
    gap: 8px;
    padding: 12px 16px 0;
  }

  .layout__nav {
    overflow-x: auto;
  }

  .layout__nav :deep(.el-menu-item) {
    height: 46px;
  }

  .layout__main {
    padding: 12px;
  }

  .layout__main--chat {
    padding: 0;
  }
}
</style>
