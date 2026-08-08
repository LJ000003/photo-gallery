import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useProcessingPolling } from '../useProcessingPolling'
import type { Photo, PhotoProcessingStatus } from '../../types/photo'

function mkPhoto(id: number, status: string): Photo {
  return { id, name: `p${id}`, processingStatus: status } as Photo
}

function mkStatus(id: number, status: string): PhotoProcessingStatus {
  return { id, processingStatus: status }
}

function stubFetch(responses: Record<string, { code: number; data?: PhotoProcessingStatus[] }>) {
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

describe('useProcessingPolling — 批量状态轮询（2C4G 部署改造）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('每轮单请求批量拉状态，全部 DONE 后停止', async () => {
    // 批量状态端点：/api/photos/status → api.ts 替换为 /api/v1/photos/status
    stubFetch({
      '/api/v1/photos/status?ids=1,2': {
        code: 200,
        data: [mkStatus(1, 'DONE'), mkStatus(2, 'DONE')],
      },
    })
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'), mkPhoto(2, 'PROCESSING'))

    polling.start()
    await vi.advanceTimersByTimeAsync(3000)

    expect(photos.value[0].processingStatus).toBe('DONE')
    expect(photos.value[1].processingStatus).toBe('DONE')
    // 全部 DONE 后不再有下一轮
    const fetchCount = vi.mocked(fetch).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fetchCount)
  })

  it('批量单请求（不再逐张拉全量详情）', async () => {
    const fetchMock = vi.fn(async (_url: string) => ({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ code: 200, data: [mkStatus(1, 'DONE'), mkStatus(2, 'DONE')] }),
    }))
    vi.stubGlobal('fetch', fetchMock)
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'), mkPhoto(2, 'PROCESSING'))

    polling.start()
    await vi.advanceTimersByTimeAsync(3000)

    // 两张照片一轮只发 1 个请求（旧实现每张一个全量详情请求）
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/photos/status?ids=1,2')
  })

  it('状态项与现有 photo 合并 patch（保留其余字段）', async () => {
    stubFetch({
      '/api/v1/photos/status?ids=1': { code: 200, data: [mkStatus(1, 'DONE')] },
    })
    const { photos, polling } = mkPolling()
    const withExtra = { ...mkPhoto(1, 'PROCESSING'), description: '保留我', fileSize: 123 } as Photo
    photos.value.push(withExtra)

    polling.start()
    await vi.advanceTimersByTimeAsync(3000)

    // 批量状态项只含 id/status → 合并后原有字段不得丢失（旧实现直接替换整对象）
    expect(photos.value[0].processingStatus).toBe('DONE')
    expect(photos.value[0].description).toBe('保留我')
    expect(photos.value[0].fileSize).toBe(123)
    expect(photos.value[0].name).toBe('p1')
  })

  it('20 轮（60s）后切慢速模式：状态保持 PROCESSING、无 toast、15s 低频续跟直到 DONE', async () => {
    // 一直返回 PROCESSING → 永不完成，先验证慢速模式续跟
    let done = false
    const fetchMock = vi.fn(async (_url: string) => ({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ code: 200, data: [mkStatus(1, done ? 'DONE' : 'PROCESSING')] }),
    }))
    vi.stubGlobal('fetch', fetchMock)
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'))

    polling.start()
    // 20 轮 × 3s = 60s
    await vi.advanceTimersByTimeAsync(60_000 + 100)

    // 核心断言：状态保持 PROCESSING——慢机器上处理超时 ≠ 处理失败，不得误报；
    // 且无 toast 打扰（旧实现超时即停轮询 + 提示「刷新查看」，照片永久卡 PROCESSING）
    expect(photos.value[0].processingStatus).toBe('PROCESSING')
    expect(photos.value[0].errorMessage).toBeUndefined()
    const fastCount = vi.mocked(fetch).mock.calls.length
    expect(fastCount).toBe(20)

    // 慢速模式：15s 低频续跟（不再每 3s 一个请求）
    await vi.advanceTimersByTimeAsync(15_000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fastCount + 1)

    // 处理完成后自动停止
    done = true
    await vi.advanceTimersByTimeAsync(15_000)
    expect(photos.value[0].processingStatus).toBe('DONE')
    const finalCount = vi.mocked(fetch).mock.calls.length
    await vi.advanceTimersByTimeAsync(30_000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(finalCount)
  })

  it('stop 后不再轮询', async () => {
    stubFetch({
      '/api/v1/photos/status?ids=1': { code: 200, data: [mkStatus(1, 'PROCESSING')] },
    })
    const { photos, polling } = mkPolling()
    photos.value.push(mkPhoto(1, 'PROCESSING'))

    polling.start()
    polling.stop()
    const fetchCount = vi.mocked(fetch).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fetchCount)
  })
})
