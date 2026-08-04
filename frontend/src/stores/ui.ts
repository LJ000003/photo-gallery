import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import type { Photo } from '../types/photo'

/**
 * UI 状态：认证、灯箱查看、编辑抽屉、上传抽屉、筛选面板、帮助弹窗
 * 语义与旧版一致（unlock/relock/查看器导航），旧 sidebarOpen 已随侧栏取消
 */
export const useUiStore = defineStore('ui', () => {
  const viewPhoto: Ref<Photo | null> = ref(null)
  const viewPhotos: Ref<Photo[]> = ref([])
  const slideshowPlaying = ref(false)
  const editPhoto: Ref<Photo | null> = ref(null)
  /** 批量编辑抽屉：非 null 即打开，持有选中的照片列表 */
  const batchEditPhotos: Ref<Photo[] | null> = ref(null)
  /** 上传抽屉 */
  const uploadOpen = ref(false)
  /** 筛选面板 */
  const filterOpen = ref(false)
  /** 相册模式搜索词（与照片模式的 photo.searchQuery 各自独立） */
  const albumSearch = ref('')
  /** 帮助弹窗 */
  const helpOpen = ref(false)
  const showBackTop = ref(false)
  const unlocked = ref(localStorage.getItem('konami_unlocked') === 'true')
  const token: Ref<string | null> = ref(localStorage.getItem('jwt_token'))

  function reLock(): void {
    localStorage.removeItem('konami_unlocked')
    localStorage.removeItem('jwt_token')
    unlocked.value = false
    token.value = null
  }

  function unlock(): void {
    unlocked.value = true
    localStorage.setItem('konami_unlocked', 'true')
  }

  function setToken(t: string): void {
    token.value = t
    localStorage.setItem('jwt_token', t)
  }

  /** 打开查看器：重置幻灯片状态，保证每次打开都是静止起步 */
  function openViewer(p: Photo, list: Photo[] = []): void {
    viewPhoto.value = p
    viewPhotos.value = list
    slideshowPlaying.value = false
  }

  function closeViewer(): void {
    viewPhoto.value = null
    viewPhotos.value = []
  }

  /**
   * 查看器内删除照片：从快照移除并导航到同一位置的下一张（或关闭）。
   * 删除状态统一由 store 负责，组件只触发动作、不做自己的导航逻辑。
   */
  function removeViewerPhoto(id: number): void {
    const idx = viewPhotos.value.findIndex((p) => p.id === id)
    if (idx === -1) return
    viewPhotos.value = viewPhotos.value.filter((p) => p.id !== id)
    if (viewPhoto.value && viewPhoto.value.id === id) {
      const next = viewPhotos.value[Math.min(idx, viewPhotos.value.length - 1)] ?? null
      viewPhoto.value = next
    }
  }

  function toggleSlideshow(): void {
    slideshowPlaying.value = !slideshowPlaying.value
  }

  /**
   * 在查看列表中导航（查看器按钮/方向键/自动轮播共用）
   * wrap=true 时循环（自动轮播）；否则越界静默不动作（手动切换）
   */
  function navigateViewer(direction: -1 | 1, wrap = false): void {
    const current = viewPhoto.value
    if (!current || viewPhotos.value.length < 2) return
    const idx = viewPhotos.value.findIndex((p) => p.id === current.id)
    if (idx === -1) return
    let newIdx = idx + direction
    if (wrap) {
      newIdx = (newIdx + viewPhotos.value.length) % viewPhotos.value.length
    } else if (newIdx < 0 || newIdx >= viewPhotos.value.length) {
      return
    }
    viewPhoto.value = viewPhotos.value[newIdx]
  }

  return {
    viewPhoto,
    viewPhotos,
    slideshowPlaying,
    editPhoto,
    batchEditPhotos,
    uploadOpen,
    filterOpen,
    albumSearch,
    helpOpen,
    showBackTop,
    unlocked,
    token,
    reLock,
    unlock,
    setToken,
    openViewer,
    closeViewer,
    removeViewerPhoto,
    toggleSlideshow,
    navigateViewer,
  }
})
