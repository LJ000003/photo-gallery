import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import { api } from '../api'
import type { Photo } from '../types/photo'
import type { ApiResponse, PageResponse } from '../types/api'
import type { ViewMode, SortField, SortOrder, UrlParams } from '../types/view'

function readUrlParams(): UrlParams {
  const p = new URLSearchParams(window.location.search)
  return {
    view: (p.get('view') || 'grid') as ViewMode,
    q: p.get('q') || '',
    sortBy: (p.get('sortBy') || 'time') as SortField,
    sortOrder: (p.get('sortOrder') || 'asc') as SortOrder,
    tags: (p.get('tags') ?? '').split(',').filter(Boolean).map(Number),
    cats: (p.get('cats') ?? '').split(',').filter(Boolean).map(Number)
  }
}

interface SyncState {
  view: string
  q: string
  sortBy: string
  sortOrder: string
  tags: number[]
  cats: number[]
}

function syncUrl(state: SyncState): void {
  const p = new URLSearchParams()
  if (state.view) p.set('view', state.view)
  if (state.q) p.set('q', state.q)
  if (state.sortBy && state.sortBy !== 'time') p.set('sortBy', state.sortBy)
  if (state.sortOrder && state.sortOrder !== 'asc') p.set('sortOrder', state.sortOrder)
  if (state.tags && state.tags.length) p.set('tags', state.tags.join(','))
  if (state.cats && state.cats.length) p.set('cats', state.cats.join(','))
  const qs = p.toString()
  const url = window.location.pathname + (qs ? '?' + qs : '')
  history.replaceState(null, '', url)
}

const init = readUrlParams()

export const usePhotoStore = defineStore('photo', () => {
  const photos: Ref<Photo[]> = ref([])
  const page = ref(0)
  const hasMore = ref(true)
  const loading = ref(false)
  const totalCount = ref(0)
  const sortBy: Ref<SortField> = ref(init.sortBy)
  const sortOrder: Ref<SortOrder> = ref(init.sortOrder)
  const selectedTagIds: Ref<number[]> = ref(init.tags)
  const selectedCategoryIds: Ref<number[]> = ref(init.cats)
  const selectedPhotoIds: Ref<Set<number>> = ref(new Set())
  const searchQuery = ref(init.q)
  const viewMode: Ref<ViewMode> = ref(init.view)

  let requestId = 0

  async function loadMore(): Promise<void> {
    if (loading.value || !hasMore.value) return
    loading.value = true
    const myId = ++requestId
    try {
      const fieldMap: Record<SortField, string> = { time: 'createdAt', name: 'name', size: 'fileSize' }
      const order = sortBy.value === 'time'
        ? (sortOrder.value === 'asc' ? 'desc' : 'asc')
        : sortOrder.value
      const sortStr = `${fieldMap[sortBy.value]},${order}`
      let url = `/api/photos?page=${page.value}&size=20&sort=${sortStr}`
      selectedTagIds.value.forEach(id => { url += `&tagIds=${id}` })
      selectedCategoryIds.value.forEach(id => { url += `&categoryIds=${id}` })
      if (searchQuery.value) url += `&q=${encodeURIComponent(searchQuery.value)}`
      const res = await api(url)
      if (myId !== requestId) return
      const json: ApiResponse<PageResponse<Photo>> = await res.json()
      const { content, totalPages, totalElements } = json.data
      if (content && content.length) photos.value.push(...content)
      page.value++
      hasMore.value = page.value < totalPages
      totalCount.value = totalElements
    } catch (err) {
      if (err instanceof Error && err.message !== '登录已过期，请重新解锁') {
        console.error('加载照片失败:', err)
      }
    } finally {
      if (myId === requestId) loading.value = false
    }
  }

  function syncUrlState(): void {
    syncUrl({
      view: viewMode.value,
      q: searchQuery.value,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
      tags: selectedTagIds.value,
      cats: selectedCategoryIds.value
    })
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
    photos.value = photos.value.filter(p => p.id !== id)
    totalCount.value--
  }

  function removePhotos(ids: number[]): void {
    const set = new Set(ids)
    photos.value = photos.value.filter(p => !set.has(p.id))
    totalCount.value -= ids.length
  }

  syncUrlState()

  return {
    photos, page, hasMore, loading, totalCount, sortBy, sortOrder,
    selectedTagIds, selectedCategoryIds, selectedPhotoIds,
    searchQuery, viewMode, loadMore, resetAndReload, setSort, setSearch,
    removePhoto, removePhotos, syncUrlState
  }
})
