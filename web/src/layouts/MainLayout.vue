<template>
  <el-container :class="['layout', { 'layout--chat': isChatRoute }]">
    <el-header v-if="!isChatRoute" class="layout__header" height="64px">
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
        <el-menu-item index="/audit">
          <el-icon><DocumentChecked /></el-icon>
          <span>审计</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>账号与模型</span>
        </el-menu-item>
      </el-menu>
    </el-header>
    <el-main :class="['layout__main', { 'layout__main--chat': isChatRoute }]">
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
</script>

<style scoped>
.layout {
  background: #f3f6fb;
  min-height: 100vh;
}

.layout__header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 24px;
  border-bottom: 1px solid #243044;
  background: #111827;
  backdrop-filter: blur(12px);
}

.layout__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  text-decoration: none;
}

.layout__brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #2e90fa;
  color: #fff;
  font-weight: 700;
  place-items: center;
}

.layout__brand-text {
  font-size: 18px;
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
  height: 64px;
  border-bottom-width: 3px;
  color: #cbd5e1;
  font-weight: 600;
}

.layout__nav :deep(.el-menu-item:hover),
.layout__nav :deep(.el-menu-item:focus) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.layout__nav :deep(.el-menu-item.is-active) {
  border-bottom-color: #2e90fa;
  background: rgba(46, 144, 250, 0.12);
  color: #fff;
}

.layout__nav :deep(.el-menu-item.is-active .el-icon) {
  color: #60a5fa;
}

.layout__main {
  padding: 18px 20px 20px;
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
