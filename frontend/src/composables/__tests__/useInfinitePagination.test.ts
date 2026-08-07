import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useInfinitePagination, type PagePayload } from '../useInfinitePagination'

vi.mock('../../api', () => ({
  api: vi.fn(),
  AuthError: class AuthError extends Error {},
}))

import { AuthError } from '../../api'

function payload(content: number[], totalPages = 1): PagePayload<number> {
  return { content, totalPages, totalElements: content.length }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useInfinitePagination — 成功路径', () => {
  it('累积数据并推进 page/hasMore/totalCount', async () => {
    const fetchPage = vi
      .fn()
      .mockResolvedValueOnce(payload([1, 2], 3))
      .mockResolvedValueOnce(payload([3], 3))
    const onLoaded = vi.fn()
    const p = useInfinitePagination<number>(fetchPage, onLoaded)

    expect(await p.loadMore()).toBe(true)
    expect(p.page.value).toBe(1)
    expect(p.hasMore.value).toBe(true)
    expect(p.totalCount.value).toBe(2)
    expect(onLoaded).toHaveBeenCalledWith(payload([1, 2], 3))

    expect(await p.loadMore()).toBe(true)
    expect(p.page.value).toBe(2)
  })

  it('最后一页 hasMore=false', async () => {
    const p = useInfinitePagination<number>(vi.fn().mockResolvedValue(payload([1])), vi.fn())
    await p.loadMore()
    expect(p.hasMore.value).toBe(false)
  })

  it('fetchPage 返回 null（响应无 data）时返回 false 且不推进', async () => {
    const p = useInfinitePagination<number>(vi.fn().mockResolvedValue(null), vi.fn())
    expect(await p.loadMore()).toBe(false)
    expect(p.page.value).toBe(0)
    expect(p.hasMore.value).toBe(true)
  })
})

describe('useInfinitePagination — 竞态防护（requestId）', () => {
  it('过期响应被丢弃，不触发 onLoaded 也不推进状态', async () => {
    const resolvers: Array<(v: PagePayload<number> | null) => void> = []
    const fetchPage = vi.fn(() => new Promise<PagePayload<number> | null>((r) => resolvers.push(r)))
    const onLoaded = vi.fn()
    const p = useInfinitePagination<number>(fetchPage, onLoaded)

    const first = p.loadMore() // 请求 1（page 0）
    p.reset() // requestId++，请求 1 过期
    const second = p.loadMore() // 请求 2（reset 后 page 回 0）

    resolvers[0](payload([0], 5)) // 过期响应先到
    resolvers[1](payload([1])) // 当前响应后到

    expect(await first).toBe(false)
    expect(await second).toBe(true)
    // 只有当前请求触发 onLoaded，且 page 按当前响应推进
    expect(onLoaded).toHaveBeenCalledTimes(1)
    expect(onLoaded.mock.calls[0][0].content).toEqual([1])
    expect(p.page.value).toBe(1)
    expect(p.totalCount.value).toBe(1)
  })
})

describe('useInfinitePagination — 失败路径', () => {
  it('业务错误返回 false 且 hasMore 不翻转（失败≠到底）', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error('boom'))
    const p = useInfinitePagination<number>(fetchPage, vi.fn())
    expect(await p.loadMore()).toBe(false)
    expect(p.hasMore.value).toBe(true)
    expect(p.page.value).toBe(0)
  })

  it('AuthError（401/403 登出场景）不记录日志，静默返回 false', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new AuthError('401'))
    const p = useInfinitePagination<number>(fetchPage, vi.fn())
    expect(await p.loadMore()).toBe(false)
  })
})
