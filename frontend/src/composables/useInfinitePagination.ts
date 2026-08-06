import { ref } from 'vue'
import { AuthError } from '../api'
import { logError } from '../utils/logger'

export interface PagePayload<T> {
  content: T[]
  totalPages: number
  totalElements: number
}

/**
 * 无限滚动分页骨架（P2-#17 从 photo store 抽出）：page/hasMore/loading/totalCount
 * 状态 + requestId 竞态防护（丢弃过期响应，防止旧响应覆盖新筛选结果）。
 * fetchPage 由调用方提供（URL 构造是业务领域知识，不进通用骨架）；
 * 失败返回 false 供调用方终止循环（防死循环重试），hasMore 不翻转（失败≠到底）。
 */
export function useInfinitePagination<T>(
  fetchPage: (page: number) => Promise<PagePayload<T> | null>,
  onLoaded: (payload: PagePayload<T>) => void,
) {
  const page = ref(0)
  const hasMore = ref(true)
  const loading = ref(false)
  const totalCount = ref(0)
  let requestId = 0

  /** @returns 是否成功加载了一页（失败时调用方应终止循环，避免死循环重试） */
  async function loadMore(): Promise<boolean> {
    if (loading.value || !hasMore.value) return false
    loading.value = true
    const myId = ++requestId
    try {
      const payload = await fetchPage(page.value)
      if (myId !== requestId) return false
      if (!payload) return false
      onLoaded(payload)
      page.value++
      hasMore.value = page.value < payload.totalPages
      totalCount.value = payload.totalElements
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

  /** 清空分页状态（列表已清空的场景：筛选/排序/搜索变更后重载）。totalCount 保持旧值，
   *  由下一次 loadMore 覆盖（与原实现行为一致）。 */
  function reset(): void {
    requestId++
    page.value = 0
    hasMore.value = true
    loading.value = false
  }

  return { page, hasMore, loading, totalCount, loadMore, reset }
}
