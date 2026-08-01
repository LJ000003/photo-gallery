import { onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePhotoStore } from '../stores/photo'
import { useUiStore } from '../stores/ui'

type ShortcutScope = 'global' | 'gallery' | 'trash' | 'view-modal' | 'edit-modal'

interface ShortcutDef {
  /** 按键组合，如 "ArrowLeft", "Ctrl+Enter", "/", "g" */
  key: string
  handler: (e: KeyboardEvent) => void
  scope: ShortcutScope | ShortcutScope[]
  /** 即使在 input/textarea 中也可触发（默认 false） */
  allowInInput?: boolean
}

/**
 * 检测当前焦点是否在输入元素中
 */
function isInputFocused(): boolean {
  const el = document.activeElement
  if (!el) return false
  const tag = el.tagName.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return true
  return (el as HTMLElement).isContentEditable === true
}

/**
 * 判断按键是否匹配快捷键定义
 */
function matchesKey(e: KeyboardEvent, defKey: string): boolean {
  const parts = defKey.split('+')
  const mainKey = parts[parts.length - 1]
  const hasCtrl = parts.includes('Ctrl')
  const hasShift = parts.includes('Shift')
  const hasAlt = parts.includes('Alt')

  // 修饰键检查：Ctrl 对 Windows/Linux 用 ctrlKey，对 Mac 也用 metaKey (Cmd)
  const ctrlPressed = e.ctrlKey || e.metaKey
  if (hasCtrl !== ctrlPressed) return false
  if (hasShift !== e.shiftKey) return false
  if (hasAlt !== e.altKey) return false

  // 单字母快捷键：确保没有任何修饰键被按下
  if (parts.length === 1 && mainKey.length === 1) {
    if (e.ctrlKey || e.metaKey || e.altKey || e.shiftKey) return false
  }

  // 主键匹配
  return e.key === mainKey || e.key.toLowerCase() === mainKey.toLowerCase()
}

/**
 * 在照片列表中导航（查看器左右箭头用）
 */
function navigateViewer(direction: -1 | 1): void {
  const photoStore = usePhotoStore()
  const ui = useUiStore()
  const current = ui.viewPhoto
  if (!current || photoStore.photos.length === 0) return

  const idx = photoStore.photos.findIndex((p) => p.id === current.id)
  if (idx === -1) return

  const newIdx = idx + direction
  if (newIdx < 0 || newIdx >= photoStore.photos.length) return

  ui.viewPhoto = photoStore.photos[newIdx]
}

export function useKeyboardShortcuts(): void {
  const router = useRouter()
  const route = useRoute()
  const photo = usePhotoStore()
  const ui = useUiStore()

  /**
   * 根据当前 store 状态和路由判断快捷键上下文
   * 优先级：edit-modal > view-modal > 路由页面
   */
  function getCurrentScope(): ShortcutScope {
    if (ui.editPhoto) return 'edit-modal'
    if (ui.viewPhoto) return 'view-modal'
    const name = route.name as string | undefined
    if (name === 'trash') return 'trash'
    if (name === 'gallery' || name === undefined) return 'gallery'
    return 'global'
  }

  function scopeMatch(defScope: ShortcutScope | ShortcutScope[], current: ShortcutScope): boolean {
    if (Array.isArray(defScope)) return defScope.includes(current)
    return defScope === current || defScope === 'global'
  }

  const shortcuts: ShortcutDef[] = [
    // ===== 全局导航 =====
    {
      key: 'g',
      scope: 'global',
      handler: (e) => {
        e.preventDefault()
        router.push('/')
      },
    },
    {
      key: 'a',
      scope: 'global',
      handler: (e) => {
        e.preventDefault()
        router.push('/albums')
      },
    },
    {
      key: 't',
      scope: 'global',
      handler: (e) => {
        e.preventDefault()
        router.push('/timeline')
      },
    },
    {
      key: 'm',
      scope: 'global',
      handler: (e) => {
        e.preventDefault()
        router.push('/map')
      },
    },
    // Escape 关闭所有弹窗
    {
      key: 'Escape',
      scope: 'global',
      handler: (e) => {
        e.preventDefault()
        ui.viewPhoto = null
        ui.editPhoto = null
      },
    },

    // ===== 图库页面 =====
    {
      key: '/',
      scope: 'gallery',
      handler: (e) => {
        e.preventDefault()
        const input = document.querySelector<HTMLInputElement>('.search-input')
        input?.focus()
        input?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      },
    },
    {
      key: 'Ctrl+Shift+U',
      scope: 'gallery',
      handler: (e) => {
        e.preventDefault()
        const el = document.getElementById('fileInput')
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
        el?.focus()
      },
    },
    {
      key: 'Ctrl+a',
      scope: 'gallery',
      handler: (e) => {
        e.preventDefault()
        document.dispatchEvent(new CustomEvent('kb:selectAll'))
      },
    },
    {
      key: 'Delete',
      scope: 'gallery',
      handler: (e) => {
        e.preventDefault()
        document.dispatchEvent(new CustomEvent('kb:deleteSelected'))
      },
    },
    {
      key: 'Enter',
      scope: 'gallery',
      handler: (e) => {
        e.preventDefault()
        document.dispatchEvent(new CustomEvent('kb:viewSelected'))
      },
    },

    // ===== 照片查看器 =====
    {
      key: 'ArrowLeft',
      scope: 'view-modal',
      handler: (e) => {
        e.preventDefault()
        navigateViewer(-1)
      },
    },
    {
      key: 'ArrowRight',
      scope: 'view-modal',
      handler: (e) => {
        e.preventDefault()
        navigateViewer(1)
      },
    },
    {
      key: 'e',
      scope: 'view-modal',
      handler: (e) => {
        e.preventDefault()
        const p = ui.viewPhoto
        if (p) {
          ui.viewPhoto = null
          // 下一帧再打开编辑，避免两个 modal 同时渲染
          requestAnimationFrame(() => {
            ui.editPhoto = p
          })
        }
      },
    },

    // ===== 编辑弹窗 =====
    {
      key: 'Ctrl+Enter',
      scope: 'edit-modal',
      allowInInput: true,
      handler: (e) => {
        e.preventDefault()
        const form = document.querySelector<HTMLFormElement>('#editModal form')
        form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
      },
    },

    // ===== 回收站页面 =====
    {
      key: 'Delete',
      scope: 'trash',
      handler: (e) => {
        e.preventDefault()
        document.dispatchEvent(new CustomEvent('kb:trashDelete'))
      },
    },
    {
      key: 'Shift+r',
      scope: 'trash',
      handler: (e) => {
        e.preventDefault()
        document.dispatchEvent(new CustomEvent('kb:trashRestore'))
      },
    },
  ]

  function onKeydown(e: KeyboardEvent): void {
    // 未解锁时 KonamiGate 接管所有键盘事件
    if (!ui.unlocked) return

    const scope = getCurrentScope()

    for (const def of shortcuts) {
      if (!scopeMatch(def.scope, scope)) continue
      // 输入框焦点保护（allowInInput 除外）
      if (!def.allowInInput && isInputFocused()) continue
      if (!matchesKey(e, def.key)) continue

      def.handler(e)
      e.stopPropagation()
      return
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', onKeydown)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', onKeydown)
  })
}
