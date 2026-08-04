import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api, AuthError } from '../api'
import i18n from '../i18n'
import { useToastStore } from './toast'
import { logError } from '../utils/logger'
import type { Photo } from '../types/photo'
import type { ApiResponse, PageResponse } from '../types/api'
import type { SortField, SortOrder } from '../types/view'

export const usePhotoStore = defineStore('photo', () => {
  const router = useRouter()
  const route = useRoute()
  const toast = useToastStore()

  const photos: Ref<Photo[]> = ref([])
  const page = ref(0)
  const hasMore = ref(true)
  const loading = ref(false)
  const totalCount = ref(0)
  const sortBy: Ref<SortField> = ref((route.query.sortBy as SortField) || 'time')
  const sortOrder: Ref<SortOrder> = ref((route.query.sortOrder as SortOrder) || 'asc')
  const selectedTagIds: Ref<number[]> = ref(
    route.query.tags ? String(route.query.tags).split(',').filter(Boolean).map(Number) : [],
  )
  const selectedCategoryIds: Ref<number[]> = ref(
    route.query.cats ? String(route.query.cats).split(',').filter(Boolean).map(Number) : [],
  )
  const searchQuery = ref((route.query.q as string) || '')
  /** 已删除照片 id（时间线/地图等持有本地列表的视图据此清理） */
  const deletedIds: Ref<Set<number>> = ref(new Set())

  let requestId = 0

  function syncUrlState(): void {
    const query: Record<string, string> = {}
    if (searchQuery.value) query.q = searchQuery.value
    if (sortBy.value !== 'time') query.sortBy = sortBy.value
    if (sortOrder.value !== 'asc') query.sortOrder = sortOrder.value
    if (selectedTagIds.value.length) query.tags = selectedTagIds.value.join(',')
    if (selectedCategoryIds.value.length) query.cats = selectedCategoryIds.value.join(',')
    router.replace({ query })
  }

  /** @returns 是否成功加载了一页（失败时调用方应终止循环，避免死循环重试） */
  async function loadMore(): Promise<boolean> {
    if (loading.value || !hasMore.value) return false
    loading.value = true
    const myId = ++requestId
    try {
      const fieldMap: Record<SortField, string> = {
        time: 'createdAt',
        name: 'name',
        size: 'fileSize',
      }
      const order =
        sortBy.value === 'time' ? (sortOrder.value === 'asc' ? 'desc' : 'asc') : sortOrder.value
      const sortStr = `${fieldMap[sortBy.value]},${order}`
      let url = `/api/photos?page=${page.value}&size=20&sort=${sortStr}`
      selectedTagIds.value.forEach((id) => {
        url += `&tagIds=${id}`
      })
      selectedCategoryIds.value.forEach((id) => {
        url += `&categoryIds=${id}`
      })
      if (searchQuery.value) url += `&q=${encodeURIComponent(searchQuery.value)}`
      const res = await api(url)
      if (myId !== requestId) return false
      const json: ApiResponse<PageResponse<Photo>> = await res.json()
      if (!json.data) return false
      const { content, totalPages, totalElements } = json.data
      if (content && content.length) photos.value.push(...content)
      page.value++
      hasMore.value = page.value < totalPages
      totalCount.value = totalElements
      return true
    } catch (err) {
      if (!(err instanceof AuthError)) {
        logError(err, '加载照片失败')
      }
      return false
    } finally {
      if (myId === requestId) loading.value = false
    }
  }

  function resetAndReload(): void {
    requestId++
    photos.value = []
    page.value = 0
    hasMore.value = true
    loading.value = false
    syncUrlState()
    loadMore()
  }

  function setSort(key: SortField): void {
    if (sortBy.value === key) {
      sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
    } else {
      sortBy.value = key
      sortOrder.value = 'asc'
    }
    resetAndReload()
  }

  function setSearch(q: string): void {
    searchQuery.value = q
    resetAndReload()
  }

  function removePhoto(id: number): void {
    // 仅当照片确实在当前列表才减计数：时间线/地图用残缺对象删除时，
    // 照片可能不在过滤后的列表里，无条件 -- 会导致首页计数错乱
    const existed = photos.value.some((p) => p.id === id)
    photos.value = photos.value.filter((p) => p.id !== id)
    if (existed) totalCount.value--
    deletedIds.value.add(id)
  }

  function removePhotos(ids: number[]): void {
    const set = new Set(ids)
    const removed = photos.value.filter((p) => set.has(p.id)).length
    photos.value = photos.value.filter((p) => !set.has(p.id))
    totalCount.value -= removed
    for (const id of ids) deletedIds.value.add(id)
  }

  /**
   * 批量编辑后按 id 原地替换（与 startProcessingPoll 的 patch 模式一致，保持滚动位置）。
   * 列表顺序与总数不变；响应中缺失的照片（已被删除）静默跳过。
   */
  function applyBatchEdit(updated: Photo[]): void {
    const map = new Map(updated.map((p) => [p.id, p]))
    photos.value.forEach((p, i) => {
      const u = map.get(p.id)
      if (u) photos.value[i] = u
    })
  }

  let processingTimer: ReturnType<typeof setTimeout> | null = null
  const MAX_POLL_ATTEMPTS = 20

  function startProcessingPoll(): void {
    stopProcessingPoll()
    let attempts = 0
    function poll(): void {
      const processingPhotos = photos.value.filter((p) => p.processingStatus === 'PROCESSING')
      if (processingPhotos.length === 0) {
        stopProcessingPoll()
        return
      }
      if (attempts >= MAX_POLL_ATTEMPTS) {
        // 超时（20×3s=60s）不再静默停止：本地标 FAILED（FAILED 态出现重试按钮）+ 提示
        const timeoutMsg = i18n.global.t('gallery.processingTimeoutMessage')
        photos.value.forEach((p, i) => {
          if (p.processingStatus === 'PROCESSING') {
            photos.value[i] = { ...p, processingStatus: 'FAILED', errorMessage: timeoutMsg }
          }
        })
        toast.info(i18n.global.t('gallery.processingTimeout'))
        stopProcessingPoll()
        return
      }
      attempts++
      processingTimer = setTimeout(async () => {
        try {
          // 本轮全部处理中照片并行拉取（旧实现逐张串行，50 张 = 50 个串行请求）
          await Promise.all(
            processingPhotos.map(async (p) => {
              const res = await api(`/api/photos/${p.id}`)
              const json: ApiResponse<Photo> = await res.json()
              if (json.code === 200 && json.data) {
                const idx = photos.value.findIndex((x) => x.id === p.id)
                if (idx !== -1) photos.value[idx] = json.data
              }
            }),
          )
        } catch (err) {
          console.warn(
            `[photo store] 轮询照片处理状态失败: ${processingPhotos.map((p) => p.id).join(',')}`,
            err,
          )
        }
        poll()
      }, 3000)
    }
    poll()
  }

  function stopProcessingPoll(): void {
    if (processingTimer) {
      clearTimeout(processingTimer)
      processingTimer = null
    }
  }

  syncUrlState()

  return {
    photos,
    page,
    hasMore,
    loading,
    totalCount,
    sortBy,
    sortOrder,
    selectedTagIds,
    selectedCategoryIds,
    searchQuery,
    deletedIds,
    loadMore,
    resetAndReload,
    setSort,
    setSearch,
    removePhoto,
    removePhotos,
    applyBatchEdit,
    startProcessingPoll,
    stopProcessingPoll,
    syncUrlState,
  }
})
