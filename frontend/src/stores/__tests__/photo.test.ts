import { describe, it, expect, beforeEach, vi } from 'vitest'
import { reactive } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const mockReplace = vi.fn()
// reactive route：route.query watch 需要可变对象才能触发
const routeState = reactive<{ query: Record<string, string> }>({ query: {} })

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mockReplace }),
  useRoute: () => routeState,
}))

import { usePhotoStore } from '../photo'

function mockFetchOk(pageResponse: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      status: 200,
      ok: true,
      json: () =>
        Promise.resolve({
          code: 200,
          data: pageResponse,
        }),
    }),
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
  localStorage.clear()
  setActivePinia(createPinia())
  routeState.query = {}
})

describe('photo store — local mutations', () => {
  it('starts with empty state', () => {
    const photo = usePhotoStore()
    expect(photo.photos).toHaveLength(0)
    expect(photo.page).toBe(0)
    expect(photo.hasMore).toBe(true)
    expect(photo.loading).toBe(false)
    expect(photo.totalCount).toBe(0)
  })

  it('removePhoto decrements totalCount and removes from list', () => {
    const photo = usePhotoStore()
    // Directly set internal state for this unit test
    photo.photos.push({ id: 1, name: 'a', description: '', fileSize: 100 } as never)
    photo.photos.push({ id: 2, name: 'b', description: '', fileSize: 200 } as never)
    ;(photo as never as { totalCount: number }).totalCount = 2

    photo.removePhoto(1)
    expect(photo.photos).toHaveLength(1)
    expect(photo.photos[0].id).toBe(2)
    expect(photo.totalCount).toBe(1)
  })

  it('removePhotos batch removes multiple', () => {
    const photo = usePhotoStore()
    photo.photos.push({ id: 1, name: 'a', description: '', fileSize: 100 } as never)
    photo.photos.push({ id: 2, name: 'b', description: '', fileSize: 200 } as never)
    photo.photos.push({ id: 3, name: 'c', description: '', fileSize: 300 } as never)
    ;(photo as never as { totalCount: number }).totalCount = 3

    photo.removePhotos([1, 3])
    expect(photo.photos).toHaveLength(1)
    expect(photo.photos[0].id).toBe(2)
    expect(photo.totalCount).toBe(1)
  })

  it('removePhoto 不在列表的照片不减计数（时间线/地图删除场景）', () => {
    const photo = usePhotoStore()
    ;(photo as never as { totalCount: number }).totalCount = 5

    photo.removePhoto(999)

    expect(photo.totalCount).toBe(5)
    expect([...photo.deletedIds]).toContain(999)
  })

  it('removePhotos 只减实际在列表中的数量', () => {
    const photo = usePhotoStore()
    photo.photos.push({ id: 1, name: 'a', description: '', fileSize: 100 } as never)
    ;(photo as never as { totalCount: number }).totalCount = 5

    photo.removePhotos([1, 888])

    expect(photo.totalCount).toBe(4)
  })

  it('applyBatchEdit patches matching ids in place keeping order', () => {
    const photo = usePhotoStore()
    photo.photos.push({ id: 1, name: 'a', description: '', fileSize: 100 } as never)
    photo.photos.push({ id: 2, name: 'b', description: '', fileSize: 200 } as never)
    photo.photos.push({ id: 3, name: 'c', description: '', fileSize: 300 } as never)

    photo.applyBatchEdit([
      {
        id: 2,
        name: 'b2',
        description: '',
        fileSize: 200,
        tags: [{ id: 9, name: 't', color: '#fff' }],
      },
      { id: 4, name: 'gone', description: '', fileSize: 50 }, // not in list — ignored
    ] as never)

    expect(photo.photos).toHaveLength(3)
    expect(photo.photos[0].id).toBe(1)
    expect(photo.photos[1].name).toBe('b2')
    expect(photo.photos[1].tags?.[0].id).toBe(9)
    expect(photo.photos[2].id).toBe(3)
  })
})

describe('photo store — sort and search', () => {
  it('setSort toggles order when same field clicked', () => {
    const photo = usePhotoStore()
    expect(photo.sortBy).toBe('time')
    expect(photo.sortOrder).toBe('asc')

    photo.setSort('time')
    expect(photo.sortOrder).toBe('desc')

    photo.setSort('time')
    expect(photo.sortOrder).toBe('asc')
  })

  it('setSort switches to new field with default asc', () => {
    const photo = usePhotoStore()
    photo.setSort('name')
    expect(photo.sortBy).toBe('name')
    expect(photo.sortOrder).toBe('asc')
  })

  it('setSearch updates query and triggers reload', () => {
    const photo = usePhotoStore()
    photo.setSearch('sunset')
    expect(photo.searchQuery).toBe('sunset')
    // After resetAndReload, photos array is cleared
    expect(photo.photos).toHaveLength(0)
  })
})

describe('photo store — loadMore (success)', () => {
  it('loads first page and accumulates data', async () => {
    mockFetchOk({
      content: [
        { id: 1, name: 'p1', description: '', fileSize: 100 },
        { id: 2, name: 'p2', description: '', fileSize: 200 },
      ],
      totalPages: 3,
      totalElements: 5,
    })

    const photo = usePhotoStore()
    await photo.loadMore()

    expect(photo.photos).toHaveLength(2)
    expect(photo.page).toBe(1)
    expect(photo.totalCount).toBe(5)
    expect(photo.hasMore).toBe(true)
  })

  it('sets hasMore false on last page', async () => {
    mockFetchOk({
      content: [{ id: 10, name: 'last', description: '', fileSize: 50 }],
      totalPages: 1,
      totalElements: 1,
    })

    const photo = usePhotoStore()
    await photo.loadMore()

    expect(photo.hasMore).toBe(false)
    expect(photo.totalCount).toBe(1)
  })

  it('loadMore returns false on server error（调用方据此终止全选循环）', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        headers: new Headers(),
        json: () => Promise.resolve({ code: 500, message: 'boom' }),
      }),
    )

    const photo = usePhotoStore()
    const ok = await photo.loadMore()

    expect(ok).toBe(false)
    // hasMore 不翻转：失败≠到底，但调用方不得死循环重试
    expect(photo.hasMore).toBe(true)
    expect(photo.photos).toHaveLength(0)
  })
})

describe('photo store — URL sync', () => {
  it('syncUrlState calls router.replace with query state', () => {
    const photo = usePhotoStore()
    photo.setSearch('sunset')
    // resetAndReload → syncUrlState → router.replace
    // After setSearch, syncUrlState is called with search query
    expect(mockReplace).toHaveBeenCalled()
  })

  it('syncUrlState omits defaults from query', () => {
    const photo = usePhotoStore()
    mockReplace.mockClear()
    photo.syncUrlState()
    // With default values (time/asc, no q, no tags/cats), should be empty query
    expect(mockReplace).toHaveBeenCalledWith({ query: {} })
  })
})

describe('photo store — route.query watch（URL 是筛选唯一事实源）', () => {
  it('外部 URL 变化（后退/前进/手改）→ 筛选 refs 更新并重载列表', async () => {
    mockFetchOk({
      content: [{ id: 1, name: 'p1', description: '', fileSize: 100 }],
      totalPages: 1,
      totalElements: 1,
    })
    const photo = usePhotoStore()

    routeState.query = { sortBy: 'name', sortOrder: 'desc', tags: '4', cats: '2', q: '海边' }
    await flushPromises()

    expect(photo.sortBy).toBe('name')
    expect(photo.sortOrder).toBe('desc')
    expect(photo.selectedTagIds).toEqual([4])
    expect(photo.selectedCategoryIds).toEqual([2])
    expect(photo.searchQuery).toBe('海边')
    // resetAndReload → 列表清空 + 按新筛选加载
    expect(photo.photos).toHaveLength(1)
  })

  it('同值 query 变化不重载（syncUrlState 写回后不死循环）', async () => {
    mockFetchOk({
      content: [{ id: 1, name: 'p1', description: '', fileSize: 100 }],
      totalPages: 1,
      totalElements: 1,
    })
    const photo = usePhotoStore()
    // 先加载第一页
    await photo.loadMore()
    const fetchCount = vi.mocked(fetch).mock.calls.length
    mockReplace.mockClear()

    // 相同值的 URL 变化（等价于 syncUrlState 的 replace 回写）
    routeState.query = {}
    await flushPromises()
    routeState.query = {}
    await flushPromises()

    // 未触发额外加载，也未写回 URL
    expect(vi.mocked(fetch).mock.calls.length).toBe(fetchCount)
    expect(mockReplace).not.toHaveBeenCalled()
  })
})
