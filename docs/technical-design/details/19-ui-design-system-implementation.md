# CPClaw UI 设计系统技术落地

## 1. 令牌分层

`web/src/style.css` 是唯一全局入口：L1 基础尺度，L2 亮/暗语义主题，L3 通用组件契约与 Element Plus 映射。`App.vue` 只负责同步 `html.cpclaw-theme-dark`，页面不得各自维护第二套主题状态。

## 2. 迁移策略

先统一全局令牌、页面壳层、页头、操作组、卡片和表格，再迁移 Settings/Metadata/Audit，最后清理 Chat/InsightReport 的历史局部样式。迁移期间旧的 settings/chat 变量只能作为别名，不能扩展。

## 3. 质量门禁

前端构建必须通过 `npm run build`。主题切换检查 teleported 的弹窗、下拉、消息无白块；静态检查重点页面不得新增裸颜色值、字号和重复 darkMode 状态。所有改动需保持 API 与业务逻辑不变。
