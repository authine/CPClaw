<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout__aside">
      <div class="layout__brand">CPClaw</div>
      <el-menu router :default-active="$route.path" class="layout__menu">
        <el-menu-item index="/">对话操作</el-menu-item>
        <el-menu-item index="/settings">系统设置</el-menu-item>
        <el-menu-item index="/metadata">元数据</el-menu-item>
        <el-menu-item index="/audit">审计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="layout__main">
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout__aside {
  background: #101828;
  color: #fff;
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
</style>
