import { computed, onMounted, onUnmounted, watch, type Ref } from 'vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const SLIDESHOW_INTERVAL = 5000

/**
 * 灯箱控制逻辑（幻灯片 timer + 全屏 + 键盘），自 PhotoViewer 拆分。
 * - 内部使用 ui store（viewPhotos/slideshowPlaying/editPhoto）
 * - fullLoaded 由父组件持有，photo.id 变化时在此复位
 * - onMounted/onUnmounted 的监听注册/清理全部在此完成
 */
export function useViewerControls(
  props: { photo: Photo },
  emit: (evt: 'close') => void,
  rootRef: Ref<HTMLElement | null>,
  fullLoaded: Ref<boolean>,
) {
  const ui = useUiStore()

  /* ---------- 幻灯片 ---------- */
  const currentIndex = computed(() => ui.viewPhotos.findIndex((p) => p.id === props.photo.id))
  const canSlideshow = computed(() => ui.viewPhotos.length >= 2 && currentIndex.value !== -1)

  let slideTimer: ReturnType<typeof setTimeout> | null = null
  function clearSlideTimer(): void {
    if (slideTimer !== null) {
      clearTimeout(slideTimer)
      slideTimer = null
    }
  }
  /** 唯一调度入口：清旧 → 满足条件再挂新（回调内不自调度） */
  function syncSlideTimer(): void {
    clearSlideTimer()
    if (!ui.slideshowPlaying || !canSlideshow.value) return
    slideTimer = setTimeout(() => {
      ui.navigateViewer(1, true)
    }, SLIDESHOW_INTERVAL)
  }

  /* ---------- 全屏 ---------- */
  function isFullscreenActive(): boolean {
    return !!(
      document.fullscreenElement ||
      (document as Document & { webkitFullscreenElement?: Element }).webkitFullscreenElement
    )
  }
  function enterFullscreen(): void {
    const el = rootRef.value
    if (!el || isFullscreenActive()) return
    try {
      if (el.requestFullscreen) {
        el.requestFullscreen().catch(() => {})
      } else {
        const webkit = (el as HTMLElement & { webkitRequestFullscreen?: () => void })
          .webkitRequestFullscreen
        webkit?.call(el)
      }
    } catch {
      /* 静默 */
    }
  }
  function exitFullscreen(): void {
    if (!isFullscreenActive()) return
    try {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {})
      } else {
        const webkit = (document as Document & { webkitExitFullscreen?: () => void })
          .webkitExitFullscreen
        webkit?.call(document)
      }
    } catch {
      /* 静默 */
    }
  }
  function toggleFullscreen(): void {
    if (isFullscreenActive()) exitFullscreen()
    else enterFullscreen()
  }
  /** 用户按 Esc 退出全屏 → 同步暂停幻灯片 */
  function onFullscreenChange(): void {
    if (!isFullscreenActive() && ui.slideshowPlaying) {
      ui.toggleSlideshow()
    }
  }

  /* ---------- 照片切换 / 播放状态 → 重置淡入与倒计时 ---------- */
  watch(
    () => props.photo.id,
    () => {
      fullLoaded.value = false
      syncSlideTimer()
    },
  )
  watch(
    () => ui.slideshowPlaying,
    (playing) => {
      syncSlideTimer()
      if (playing) enterFullscreen()
      else exitFullscreen()
    },
  )
  watch(canSlideshow, () => syncSlideTimer())

  /* ---------- 键盘 ---------- */
  /** 焦点在交互元素上时不响应快捷键（否则按钮 click 与按键双触发，如 Space 播放） */
  function isInteractiveTarget(e: KeyboardEvent): boolean {
    const el = e.target as HTMLElement | null
    if (!el) return false
    return (
      el.tagName === 'BUTTON' ||
      el.tagName === 'INPUT' ||
      el.tagName === 'TEXTAREA' ||
      el.tagName === 'A' ||
      el.isContentEditable
    )
  }

  function onKeydown(e: KeyboardEvent): void {
    if (isInteractiveTarget(e)) return
    switch (e.key) {
      case 'Escape':
        // 分层处理：全屏中先退全屏（浏览器也拦截 Esc），不关闭灯箱
        if (isFullscreenActive()) {
          e.preventDefault()
          exitFullscreen()
        } else {
          emit('close')
        }
        break
      case 'ArrowLeft':
        ui.navigateViewer(-1)
        break
      case 'ArrowRight':
        ui.navigateViewer(1)
        break
      case ' ':
        e.preventDefault()
        ui.toggleSlideshow()
        break
      case 'e':
      case 'E':
        ui.editPhoto = props.photo
        break
      case 'f':
      case 'F':
        if (isFullscreenActive()) exitFullscreen()
        else enterFullscreen()
        break
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onKeydown)
    document.addEventListener('fullscreenchange', onFullscreenChange)
    document.addEventListener('webkitfullscreenchange', onFullscreenChange)
    syncSlideTimer()
  })
  onUnmounted(() => {
    window.removeEventListener('keydown', onKeydown)
    document.removeEventListener('fullscreenchange', onFullscreenChange)
    document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
    clearSlideTimer()
    exitFullscreen()
  })

  function close(): void {
    clearSlideTimer()
    emit('close')
  }

  return {
    currentIndex,
    canSlideshow,
    isFullscreenActive,
    toggleFullscreen,
    close,
  }
}
