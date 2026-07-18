import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const ChatView = () => import('../views/ChatView.vue')
const SettingsView = () => import('../views/SettingsView.vue')
const MetadataView = () => import('../views/MetadataView.vue')
const AuditView = () => import('../views/AuditView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', name: 'chat', component: ChatView },
        { path: 'metadata', name: 'metadata', component: MetadataView },
        { path: 'audit', name: 'audit', component: AuditView },
        { path: 'settings', name: 'settings', component: SettingsView }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

export default router
