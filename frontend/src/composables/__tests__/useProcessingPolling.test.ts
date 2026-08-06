import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useProcessingPolling } from '../useProcessingPolling'
import { useToastStore } from '../../stores/toast'
import type { Photo } from '../../types/photo'

function mkPhoto(id: number, status: string): Photo {
  return { id, name: `p${id}`, processingStatus: status } as Photo
}

function stubFetch(responses: Record<string, { code: number; data?: Photo }>) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const r = responses[String(url)]
      return {
        ok: true,
        status: 200,
        headers: new Headers(),
        json: async () => r ?? { code: 500 },
      } as Response
    }),
  )
}

function mkPolling() {
  const photos = ref<Photo[]>([])
  const polling = useProcessingPolling({
    getPhotos: () => photos.value,
    patch: (updated) => {
      const idx = photos.value.findIndex((x) => x.id === updated.id)
      if (idx !== -1) photos.value[idx] = updated
    },
  })
  return { photos, polling }
}

describe('useProcessingPolling — 处理轮询（P2-#17 从 photo store 抽出，行为与原实现一致）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('处理完成后停止轮询并更新照片状态', async () => {
    // api.ts 会把 /api/photos/{id} 替换为 /api/v1/photos/{id}
    stubFetch({
      '/api/v1/photos/1': { code: 200, data: mkPhoto(1, 'DONE') },
      '/api/v1/photos/2': { code: 200, data: mkPhoto(2, 'DONE') },
    })
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'), mkPhoto(2, 'PROCESSING'))

    polling.start()
    // 一轮 3s 后并行拉取
    await vi.advanceTimersByTimeAsync(3000)

    expect(photos.value[0].processingStatus).toBe('DONE')
    expect(photos.value[1].processingStatus).toBe('DONE')
    // 全部 DONE 后不再有下一轮
    const fetchCount = vi.mocked(fetch).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fetchCount)
  })

  it('一轮内并行拉取全部 PROCESSING 照片', async () => {
    const fetchMock = vi.fn(async (_url: string) => ({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ code: 200, data: mkPhoto(1, 'DONE') }),
    }))
    vi.stubGlobal('fetch', fetchMock)
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'), mkPhoto(2, 'PROCESSING'))

    polling.start()
    await vi.advanceTimersByTimeAsync(3000)

    // 两张照片并行 → 一轮 fetch 2 次（非串行 2×3s）
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/v1/photos/1', '/api/v1/photos/2'])
  })

  it('20 轮（60s）超时后本地标 FAILED 并提示', async () => {
    // 一直返回 PROCESSING → 永不完成
    stubFetch({
      '/api/v1/photos/1': { code: 200, data: mkPhoto(1, 'PROCESSING') },
    })
    const { photos, polling } = mkPolling()
    const toast = useToastStore()
    photos.value.push(mkPhoto(1, 'PROCESSING'))

    polling.start()
    // 20 轮 × 3s = 60s
    await vi.advanceTimersByTimeAsync(60_000 + 100)

    expect(photos.value[0].processingStatus).toBe('FAILED')
    // happy-dom navigator.language 默认 en-US → i18n 取英文文案
    expect(photos.value[0].errorMessage).toContain('timed out')
    expect(toast.toasts.some((t) => t.message.includes('timed out'))).toBe(true)
  })

  it('stop 后不再轮询', async () => {
    stubFetch({ '/api/v1/photos/1': { code: 200, data: mkPhoto(1, 'PROCESSING') } })
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'))

    polling.start()
    polling.stop()
    const fetchCount = vi.mocked(fetch).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fetchCount)
  })
})
