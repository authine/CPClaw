import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const ChatView = () => import('../views/ChatView.vue')
const SettingsView = () => import('../views/SettingsView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', name: 'chat', component: ChatView },
        { path: 'metadata', name: 'metadata', redirect: '/settings?section=metadata-browser' },
        // Keep legacy audit links inside the unified system-settings experience.
        // The former standalone page exposed raw run IDs and duplicated the log-analysis capability.
        { path: 'audit', name: 'audit', redirect: '/settings?section=log-analytics' },
        { path: 'settings', name: 'settings', component: SettingsView }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

export default router
