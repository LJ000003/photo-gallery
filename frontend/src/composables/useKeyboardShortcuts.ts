import { onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUiStore } from '../stores/ui'

/**
 * 全局快捷键（AppShell 挂载）
 *
 * 职责边界：
 * - 本组合式函数只处理「全局」快捷键：模式导航、聚焦搜索、打开上传
 * - 灯箱（查看器）内的 ← → Space E Esc 由 PhotoViewer 自持监听（随组件生命周期）
 * - 回收站的 Delete / R 由 TrashView 自持
 * - 解锁屏（KonamiGate）持有自己的键盘监听，锁定期间此处全部忽略
 */
export function useKeyboardShortcuts(): void {
  const router = useRouter()
  const route = useRoute()
  const ui = useUiStore()

  const ROUTE_KEYS: Record<string, string> = { g: 'gallery', a: 'albums', t: 'timeline', m: 'map' }

  function isTyping(e: KeyboardEvent): boolean {
    const t = e.target as HTMLElement | null
    return !!t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)
  }

  function handleKey(e: KeyboardEvent): void {
    if (!ui.unlocked) return // KonamiGate 持有键盘
    // 灯箱/编辑抽屉打开时全局快捷键让位：
    // 否则 g/a/m/t 会在灯箱背后切路由、/ 会聚焦灯箱背后的搜索框
    if (ui.viewPhoto || ui.editPhoto || ui.batchEditPhotos) return
    if (e.ctrlKey || e.metaKey || e.altKey) {
      if (e.ctrlKey && e.shiftKey && (e.key === 'U' || e.key === 'u')) {
        e.preventDefault()
        ui.uploadOpen = true
      }
      return
    }
    if (isTyping(e)) return

    const key = e.key.toLowerCase()
    const target = ROUTE_KEYS[key]
    if (target && route.name !== target) {
      router.push({ name: target })
      return
    }
    if (e.key === '/') {
      e.preventDefault()
      document.dispatchEvent(new CustomEvent('kb:focusSearch'))
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKey)
  })
  onUnmounted(() => {
    window.removeEventListener('keydown', handleKey)
  })
}
