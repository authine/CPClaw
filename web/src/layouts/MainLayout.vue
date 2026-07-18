<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout__aside">
      <div class="layout__brand">CPClaw</div>
      <el-menu router :default-active="$route.path" class="layout__menu">
        <el-menu-item index="/">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="layout__content">
      <el-main class="layout__main">
        <RouterView />
      </el-main>
      <nav class="layout__mobile-nav" aria-label="主导航">
        <RouterLink to="/" :class="{ 'is-active': $route.path === '/' }">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话</span>
        </RouterLink>
        <RouterLink to="/settings" :class="{ 'is-active': $route.path === '/settings' }">
          <el-icon><Setting /></el-icon>
          <span>设置</span>
        </RouterLink>
      </nav>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ChatDotRound, Setting } from '@element-plus/icons-vue'
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout__aside {
  background: #101828;
  color: #fff;
}

.layout__content {
  min-width: 0;
}

.layout__brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 20px;
  font-weight: 700;
}

.layout__menu {
  border-right: none;
}

.layout__main {
  padding: 24px;
}

.layout__mobile-nav {
  display: none;
}

@media (max-width: 760px) {
  .layout__aside {
    display: none;
  }

  .layout__main {
    padding: 0;
  }

  .layout__mobile-nav {
    position: fixed;
    z-index: 20;
    right: 0;
    bottom: 0;
    left: 0;
    height: calc(58px + env(safe-area-inset-bottom));
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding-bottom: env(safe-area-inset-bottom);
    border-top: 1px solid #e9eaeb;
    background: rgba(255, 255, 255, 0.98);
  }

  .layout__mobile-nav a {
    display: grid;
    place-content: center;
    justify-items: center;
    gap: 3px;
    color: #717680;
    font-size: 11px;
    text-decoration: none;
  }

  .layout__mobile-nav a .el-icon {
    font-size: 20px;
  }

  .layout__mobile-nav a.is-active {
    color: #175cd3;
  }
}
</style>
