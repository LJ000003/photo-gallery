import { createRouter, createWebHistory } from 'vue-router'
import { useUiStore } from '../stores/ui'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'gallery',
      component: () => import('../pages/GalleryPage.vue'),
    },
    {
      path: '/albums',
      name: 'albums',
      component: () => import('../pages/AlbumsPage.vue'),
    },
    {
      path: '/timeline',
      name: 'timeline',
      component: () => import('../pages/TimelinePage.vue'),
    },
    {
      path: '/map',
      name: 'map',
      component: () => import('../pages/MapPage.vue'),
    },
    {
      path: '/share/:token',
      name: 'share',
      component: () => import('../components/ShareViewer.vue'),
    },
  ],
})

router.beforeEach((to) => {
  if (to.path.startsWith('/share/')) return true

  const ui = useUiStore()
  if (!ui.unlocked) {
    document.title = '照片管理器 — 已锁定'
    return false
  }

  document.title = '照片管理器'
  return true
})

export default router
