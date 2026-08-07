import { defineStore } from 'pinia'
import { ref, watch, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api } from '../api'
import { parseQuery, useUrlState } from '../composables/useUrlState'
import { useInfinitePagination } from '../composables/useInfinitePagination'
import { useProcessingPolling } from '../composables/useProcessingPolling'
import type { Photo } from '../types/photo'
import type { ApiResponse, PageResponse } from '../types/api'
import type { SortField } from '../types/view'

/**
 * 照片列表 store（状态层分层）：
 * 仅保留列表状态与编排——URL 双向同步（useUrlState）、分页/竞态（useInfinitePagination）、
 * 处理轮询（useProcessingPolling）均为独立 composables，store 不手写 URL 拼接与定时器。
 */
export const usePhotoStore = defineStore('photo', () => {
  const router = useRouter()
  const route = useRoute()

  const photos: Ref<Photo[]> = ref([])
  /** 已删除照片 id（时间线/地图等持有本地列表的视图据此清理） */
  const deletedIds: Ref<Set<number>> = ref(new Set())

  // 过滤/排序/搜索状态与 URL 同步
  const { sortBy, sortOrder, selectedTagIds, selectedCategoryIds, searchQuery, syncUrlState } =
    useUrlState(router, route)

  // 分页骨架：fetchPage 持有 URL 构造（业务领域知识留在 store），onLoaded 负责 push
  const pagination = useInfinitePagination<Photo>(
    async (page) => {
      const fieldMap: Record<SortField, string> = {
        time: 'createdAt',
        name: 'name',
        size: 'fileSize',
      }
      const order =
        sortBy.value === 'time' ? (sortOrder.value === 'asc' ? 'desc' : 'asc') : sortOrder.value
      const sortStr = `${fieldMap[sortBy.value]},${order}`
      let url = `/api/photos?page=${page}&size=20&sort=${sortStr}`
      selectedTagIds.value.forEach((id) => {
        url += `&tagIds=${id}`
      })
      selectedCategoryIds.value.forEach((id) => {
        url += `&categoryIds=${id}`
      })
      if (searchQuery.value) url += `&q=${encodeURIComponent(searchQuery.value)}`
      const res = await api(url)
      const json: ApiResponse<PageResponse<Photo>> = await res.json()
      return json.data ?? null
    },
    (payload) => {
      if (payload.content && payload.content.length) {
        // 重载的新对象合并媒体版本号（transform 后 bump 的版本在对象外持久）
        payload.content.forEach((p) => {
          const v = mediaVersions.get(p.id)
          if (v) p.version = v
        })
        photos.value.push(...payload.content)
      }
    },
  )
  const { page, hasMore, loading, totalCount, error: loadError, loadMore } = pagination

  function resetAndReload(): void {
    photos.value = []
    pagination.reset()
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

  /**
   * 图片变换（旋转/镜像/裁剪）成功后递增媒体版本号：
   * 缩略图/WebP 响应带 7 天 Cache-Control + Workbox CacheFirst，
   * URL 不加版本参数会命中旧图缓存——version 拼进 URL（?v=）强制回源。
   * 版本存 store 级 Map（重载列表后按 id 合并回新对象），不依赖对象存活。
   */
  const mediaVersions = new Map<number, number>()
  function bumpMediaVersion(id: number): void {
    const v = Date.now()
    mediaVersions.set(id, v)
    const p = photos.value.find((x) => x.id === id)
    if (p) p.version = v
  }

  const polling = useProcessingPolling({
    getPhotos: () => photos.value,
    patch: (updated) => {
      const idx = photos.value.findIndex((x) => x.id === updated.id)
      if (idx !== -1) photos.value[idx] = updated
    },
  })

  syncUrlState()

  // URL 是筛选状态的唯一事实源：浏览器后退/前进、ModeTabs 带 query 导航等外部 URL 变化
  // 同步回 store 并重载。防死循环：syncUrlState 写回的值与当前 refs 一致 → 相等判断跳过
  // （且 vue-router 对同值 replace 判定为 duplicated navigation，不触发路由更新）。
  watch(
    () => route.query,
    (query) => {
      const next = parseQuery(query)
      const arraysEqual = (a: number[], b: number[]) =>
        a.length === b.length && a.every((v, i) => v === b[i])
      if (
        next.sortBy === sortBy.value &&
        next.sortOrder === sortOrder.value &&
        next.searchQuery === searchQuery.value &&
        arraysEqual(next.selectedTagIds, selectedTagIds.value) &&
        arraysEqual(next.selectedCategoryIds, selectedCategoryIds.value)
      ) {
        return
      }
      sortBy.value = next.sortBy
      sortOrder.value = next.sortOrder
      selectedTagIds.value = next.selectedTagIds
      selectedCategoryIds.value = next.selectedCategoryIds
      searchQuery.value = next.searchQuery
      resetAndReload()
    },
  )

  return {
    photos,
    page,
    hasMore,
    loading,
    totalCount,
    loadError,
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
    bumpMediaVersion,
    startProcessingPoll: polling.start,
    stopProcessingPoll: polling.stop,
    syncUrlState,
  }
})
