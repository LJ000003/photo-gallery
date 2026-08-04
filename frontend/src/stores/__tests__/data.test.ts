import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

import { useDataStore } from '../data'

function mockFetch(ok: boolean, data: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok,
      status: ok ? 200 : 500,
      headers: new Headers(),
      json: () => Promise.resolve({ code: ok ? 200 : 500, data }),
    }),
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
  localStorage.clear()
  setActivePinia(createPinia())
})

describe('data store — loadAll', () => {
  it('populates tags/categories/albums on success', async () => {
    mockFetch(true, [])
    const store = useDataStore()
    await store.loadAll()
    expect(store.tags).toEqual([])
    expect(store.categories).toEqual([])
    expect(store.albums).toEqual([])
  })

  it('失败后不缓存被拒的 Promise：下次 loadAll 重新请求并成功', async () => {
    const firstFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      headers: new Headers(),
      json: () => Promise.resolve({ code: 500, message: 'boom' }),
    })
    vi.stubGlobal('fetch', firstFetch)
    const store = useDataStore()

    // 第一次失败（任一接口 5xx → api() throw → loadAll reject）
    await expect(store.loadAll()).rejects.toBeTruthy()

    // 第二次调用必须重新发起请求（旧实现直接返回同一个 rejected promise，永久空白）
    const secondFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: () => Promise.resolve({ code: 200, data: [] }),
    })
    vi.stubGlobal('fetch', secondFetch)
    await expect(store.loadAll()).resolves.toBeUndefined()

    expect(firstFetch).toHaveBeenCalledTimes(3) // 首轮 3 接口均尝试
    expect(secondFetch).toHaveBeenCalledTimes(3) // 重试轮重新请求，未复用被拒 Promise
  })

  it('并发调用共享同一进行中的请求', async () => {
    let resolveAll!: (v: unknown) => void
    const gate = new Promise<unknown>((r) => {
      resolveAll = r as (v: unknown) => void
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(() => gate.then(() => ({ ok: true, status: 200, json: () => Promise.resolve({ code: 200, data: [] }) }))),
    )
    const store = useDataStore()
    const p1 = store.loadAll()
    const p2 = store.loadAll()
    resolveAll!(null)
    await Promise.all([p1, p2])
    expect(fetch).toHaveBeenCalledTimes(3) // 并发去重：只发一轮
  })
})
