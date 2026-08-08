import { ref, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import type { SortField, SortOrder } from '../types/view'

export interface UrlState {
  sortBy: SortField
  sortOrder: SortOrder
  selectedTagIds: number[]
  selectedCategoryIds: number[]
  searchQuery: string
}

/** query → 默认化状态（与 useUrlState 的 refs 同构）。纯函数，供 store 的 route.query watch 复用。 */
export function parseQuery(query: RouteLocationNormalizedLoaded['query']): UrlState {
  return {
    sortBy: (query.sortBy as SortField) || 'time',
    sortOrder: (query.sortOrder as SortOrder) || 'asc',
    selectedTagIds: query.tags ? String(query.tags).split(',').filter(Boolean).map(Number) : [],
    selectedCategoryIds: query.cats
      ? String(query.cats).split(',').filter(Boolean).map(Number)
      : [],
    searchQuery: (query.q as string) || '',
  }
}

/**
 * URL query ↔ 列表视图状态双向同步（从 photo store 抽出）。
 * 行为与原实现完全一致：默认值不写 query（sortBy=time / sortOrder=asc 不出现）、
 * tags/cats 逗号拼接并 filter(Boolean) 去空、searchQuery 空值不写。
 */
export function useUrlState(router: Router, route: RouteLocationNormalizedLoaded) {
  const initial = parseQuery(route.query)
  const sortBy: Ref<SortField> = ref(initial.sortBy)
  const sortOrder: Ref<SortOrder> = ref(initial.sortOrder)
  const selectedTagIds: Ref<number[]> = ref(initial.selectedTagIds)
  const selectedCategoryIds: Ref<number[]> = ref(initial.selectedCategoryIds)
  const searchQuery = ref(initial.searchQuery)

  function syncUrlState(): void {
    const query: Record<string, string> = {}
    if (searchQuery.value) query.q = searchQuery.value
    if (sortBy.value !== 'time') query.sortBy = sortBy.value
    if (sortOrder.value !== 'asc') query.sortOrder = sortOrder.value
    if (selectedTagIds.value.length) query.tags = selectedTagIds.value.join(',')
    if (selectedCategoryIds.value.length) query.cats = selectedCategoryIds.value.join(',')
    router.replace({ query })
  }

  return { sortBy, sortOrder, selectedTagIds, selectedCategoryIds, searchQuery, syncUrlState }
}
