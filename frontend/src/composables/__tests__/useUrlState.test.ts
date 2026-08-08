import { describe, it, expect, vi } from 'vitest'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { parseQuery, useUrlState } from '../useUrlState'

const mockReplace = vi.fn()

function mkRouter() {
  return { replace: mockReplace } as unknown as Router
}

describe('parseQuery — query → 默认化状态（store 的 route.query watch 复用）', () => {
  it('空 query 取默认值', () => {
    expect(parseQuery({})).toEqual({
      sortBy: 'time',
      sortOrder: 'asc',
      selectedTagIds: [],
      selectedCategoryIds: [],
      searchQuery: '',
    })
  })

  it('非默认值还原 + tags 逗号去空', () => {
    expect(
      parseQuery({ sortBy: 'size', sortOrder: 'desc', tags: '3,', cats: '5,6', q: '海边' }),
    ).toEqual({
      sortBy: 'size',
      sortOrder: 'desc',
      selectedTagIds: [3],
      selectedCategoryIds: [5, 6],
      searchQuery: '海边',
    })
  })
})

describe('useUrlState — 初始值从 URL 读取', () => {
  it('空 query 时取默认值（time/asc/空列表/空搜索）', () => {
    const s = useUrlState(mkRouter(), { query: {} } as RouteLocationNormalizedLoaded)
    expect(s.sortBy.value).toBe('time')
    expect(s.sortOrder.value).toBe('asc')
    expect(s.selectedTagIds.value).toEqual([])
    expect(s.selectedCategoryIds.value).toEqual([])
    expect(s.searchQuery.value).toBe('')
  })

  it('非默认值从 query 还原（含 tags 逗号分隔去空）', () => {
    const s = useUrlState(mkRouter(), {
      query: { sortBy: 'size', sortOrder: 'desc', tags: '3,', cats: '5,6', q: '海边' },
    } as unknown as RouteLocationNormalizedLoaded)
    expect(s.sortBy.value).toBe('size')
    expect(s.sortOrder.value).toBe('desc')
    expect(s.selectedTagIds.value).toEqual([3])
    expect(s.selectedCategoryIds.value).toEqual([5, 6])
    expect(s.searchQuery.value).toBe('海边')
  })
})

describe('useUrlState — syncUrlState 写回 URL', () => {
  it('默认值不写 query（保持 URL 干净）', () => {
    const router = mkRouter()
    const s = useUrlState(router, { query: {} } as RouteLocationNormalizedLoaded)
    s.syncUrlState()
    expect(router.replace).toHaveBeenCalledWith({ query: {} })
  })

  it('非默认值写入 query（tags/cats 逗号拼接）', () => {
    const router = mkRouter()
    const s = useUrlState(router, { query: {} } as RouteLocationNormalizedLoaded)
    s.sortBy.value = 'name'
    s.searchQuery.value = '海'
    s.selectedTagIds.value = [1, 2]
    s.selectedCategoryIds.value = [5]
    s.syncUrlState()
    expect(router.replace).toHaveBeenCalledWith({
      query: { sortBy: 'name', q: '海', tags: '1,2', cats: '5' },
    })
  })

  it('清空后不再写该字段（q/tags 从 query 消失）', () => {
    const router = mkRouter()
    const s = useUrlState(router, {
      query: { sortBy: 'name', tags: '1,2', q: '海' },
    } as unknown as RouteLocationNormalizedLoaded)
    mockReplace.mockClear()
    s.searchQuery.value = ''
    s.selectedTagIds.value = []
    s.syncUrlState()
    expect(router.replace).toHaveBeenCalledWith({ query: { sortBy: 'name' } })
  })
})
