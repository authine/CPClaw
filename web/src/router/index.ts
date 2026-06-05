import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import ChatView from '../views/ChatView.vue'
import SettingsView from '../views/SettingsView.vue'
import MetadataView from '../views/MetadataView.vue'
import AuditView from '../views/AuditView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', name: 'chat', component: ChatView },
        { path: 'settings', name: 'settings', component: SettingsView },
        { path: 'metadata', name: 'metadata', component: MetadataView },
        { path: 'audit', name: 'audit', component: AuditView }
      ]
    }
  ]
})

export default router
