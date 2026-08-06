import { ref, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import type { SortField, SortOrder } from '../types/view'

/**
 * URL query ↔ 列表视图状态双向同步（P2-#17 从 photo store 抽出）。
 * 行为与原实现完全一致：默认值不写 query（sortBy=time / sortOrder=asc 不出现）、
 * tags/cats 逗号拼接并 filter(Boolean) 去空、searchQuery 空值不写。
 */
export function useUrlState(router: Router, route: RouteLocationNormalizedLoaded) {
  const sortBy: Ref<SortField> = ref((route.query.sortBy as SortField) || 'time')
  const sortOrder: Ref<SortOrder> = ref((route.query.sortOrder as SortOrder) || 'asc')
  const selectedTagIds: Ref<number[]> = ref(
    route.query.tags ? String(route.query.tags).split(',').filter(Boolean).map(Number) : [],
  )
  const selectedCategoryIds: Ref<number[]> = ref(
    route.query.cats ? String(route.query.cats).split(',').filter(Boolean).map(Number) : [],
  )
  const searchQuery = ref((route.query.q as string) || '')

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
