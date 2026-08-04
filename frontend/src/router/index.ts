import { createRouter, createWebHistory } from 'vue-router'
import { useUiStore } from '../stores/ui'
import AppShell from '../layouts/AppShell.vue'
import i18n from '../i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/share/:token',
      name: 'share',
      component: () => import('../components/share/ShareViewer.vue'),
    },
    {
      // AppShell 保持 eager：解锁屏（KonamiGate）必须随首屏即时渲染
      path: '/',
      component: AppShell,
      children: [
        {
          path: '',
          name: 'gallery',
          component: () => import('../components/gallery/PhotosView.vue'),
          meta: { titleKey: 'nav.photos' },
        },
        {
          path: 'albums',
          name: 'albums',
          component: () => import('../components/albums/AlbumsView.vue'),
          meta: { titleKey: 'nav.albums' },
        },
        {
          path: 'timeline',
          name: 'timeline',
          component: () => import('../components/timeline/TimelineView.vue'),
          meta: { titleKey: 'nav.timeline' },
        },
        {
          path: 'map',
          name: 'map',
          component: () => import('../components/map/MapView.vue'),
          meta: { titleKey: 'nav.map' },
        },
        {
          path: 'stats',
          name: 'stats',
          component: () => import('../components/stats/StatsView.vue'),
          meta: { titleKey: 'stats.title' },
        },
        {
          path: 'trash',
          name: 'trash',
          component: () => import('../components/trash/TrashView.vue'),
          meta: { titleKey: 'nav.trash' },
        },
      ],
    },
    {
      // 404 兜底：未知路径重定向首页（此前白屏）。仅覆盖客户端内导航；
      // 后端静态资源无 SPA fallback，硬导航未知路径仍返回后端 404（现状）
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

router.beforeEach((to) => {
  const ui = useUiStore()
  const t = i18n.global.t
  if (to.path.startsWith('/share/')) {
    document.title = t('share.viewer')
    return true
  }
  if (!ui.unlocked) {
    document.title = `${t('app.name')} · ${t('auth.locked')}`
    return true
  }
  const key = to.meta.titleKey as string | undefined
  document.title = key ? `${t(key)} · ${t('app.name')}` : t('app.name')
  return true
})

export default router
