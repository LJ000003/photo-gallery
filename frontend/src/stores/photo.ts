import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api, AuthError } from '../api'
import type { Photo } from '../types/photo'
import type { ApiResponse, PageResponse } from '../types/api'
import type { SortField, SortOrder } from '../types/view'

export const usePhotoStore = defineStore('photo', () => {
  const router = useRouter()
  const route = useRoute()

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
  const selectedPhotoIds: Ref<Set<number>> = ref(new Set())
  const searchQuery = ref((route.query.q as string) || '')

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

  async function loadMore(): Promise<void> {
    if (loading.value || !hasMore.value) return
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
      if (myId !== requestId) return
      const json: ApiResponse<PageResponse<Photo>> = await res.json()
      const { content, totalPages, totalElements } = json.data
      if (content && content.length) photos.value.push(...content)
      page.value++
      hasMore.value = page.value < totalPages
      totalCount.value = totalElements
    } catch (err) {
      if (!(err instanceof AuthError)) {
        console.error('加载照片失败:', err)
      }
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
    photos.value = photos.value.filter((p) => p.id !== id)
    totalCount.value--
  }

  function removePhotos(ids: number[]): void {
    const set = new Set(ids)
    photos.value = photos.value.filter((p) => !set.has(p.id))
    totalCount.value -= ids.length
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
    selectedPhotoIds,
    searchQuery,
    loadMore,
    resetAndReload,
    setSort,
    setSearch,
    removePhoto,
    removePhotos,
    syncUrlState,
  }
})
