import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const mockReplace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mockReplace }),
  useRoute: () => ({ query: {} }),
}))

import { usePhotoStore } from '../photo'

function mockFetchOk(pageResponse: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    status: 200,
    ok: true,
    json: () => Promise.resolve({
      code: 200,
      data: pageResponse,
    }),
  }))
}

beforeEach(() => {
  vi.restoreAllMocks()
  localStorage.clear()
  setActivePinia(createPinia())
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
