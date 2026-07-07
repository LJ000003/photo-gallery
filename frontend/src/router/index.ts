import { createRouter, createWebHistory } from 'vue-router'
import { useUiStore } from '../stores/ui'
import MainLayout from '../layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/share/:token',
      name: 'share',
      component: () => import('../components/ShareViewer.vue'),
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'gallery',
          component: () => import('../pages/GalleryPage.vue'),
        },
        {
          path: 'albums',
          name: 'albums',
          component: () => import('../pages/AlbumsPage.vue'),
        },
        {
          path: 'timeline',
          name: 'timeline',
          component: () => import('../pages/TimelinePage.vue'),
        },
        {
          path: 'map',
          name: 'map',
          component: () => import('../pages/MapPage.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.path.startsWith('/share/')) return true

  const ui = useUiStore()
  document.title = ui.unlocked ? '照片管理器' : '照片管理器 — 已锁定'
  return true
})

export default router
