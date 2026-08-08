import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePhotoStore } from '../photo'
import { useToastStore } from '../toast'
import type { Photo, PhotoProcessingStatus } from '../../types/photo'

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

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

describe('photo store 处理轮询（批量状态端点，2C4G 部署改造）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('处理完成后停止轮询并更新照片状态', async () => {
    // 批量状态端点：/api/photos/status → api.ts 替换为 /api/v1/photos/status
    stubFetch({
      '/api/v1/photos/status?ids=1,2': {
        code: 200,
        data: [mkStatus(1, 'DONE'), mkStatus(2, 'DONE')],
      },
    })
    const store = usePhotoStore()
    store.photos.push(mkPhoto(1, 'PROCESSING'), mkPhoto(2, 'PROCESSING'))

    store.startProcessingPoll()
    await vi.advanceTimersByTimeAsync(3000)

    expect(store.photos[0].processingStatus).toBe('DONE')
    expect(store.photos[1].processingStatus).toBe('DONE')
    // 全部 DONE 后不再有下一轮
    const fetchCount = vi.mocked(fetch).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fetchCount)
  })

  it('每轮单请求批量拉状态（不再逐张拉全量详情）', async () => {
    const fetchMock = vi.fn(async (_url: string) => ({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ code: 200, data: [mkStatus(1, 'DONE'), mkStatus(2, 'DONE')] }),
    }))
    vi.stubGlobal('fetch', fetchMock)
    const store = usePhotoStore()
    store.photos.push(mkPhoto(1, 'PROCESSING'), mkPhoto(2, 'PROCESSING'))

    store.startProcessingPoll()
    await vi.advanceTimersByTimeAsync(3000)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/photos/status?ids=1,2')
  })

  it('20 轮（60s）后切慢速模式：状态保持 PROCESSING、无 toast、15s 低频续跟', async () => {
    // 一直返回 PROCESSING → 永不完成
    stubFetch({
      '/api/v1/photos/status?ids=1': { code: 200, data: [mkStatus(1, 'PROCESSING')] },
    })
    const store = usePhotoStore()
    const toast = useToastStore()
    store.photos.push(mkPhoto(1, 'PROCESSING'))

    store.startProcessingPoll()
    await vi.advanceTimersByTimeAsync(60_000 + 100)

    // 慢机器上处理超时 ≠ 失败：状态保持 PROCESSING、不写入失败信息
    expect(store.photos[0].processingStatus).toBe('PROCESSING')
    expect(store.photos[0].errorMessage).toBeUndefined()
    // 慢速模式无 toast 打扰（旧实现超时 toast + 停轮询，照片永久卡 PROCESSING）
    expect(toast.toasts).toHaveLength(0)
    const fastCount = vi.mocked(fetch).mock.calls.length
    // 慢速模式 15s 低频续跟（旧实现在 60s 后完全停止）
    await vi.advanceTimersByTimeAsync(15_000)
    expect(vi.mocked(fetch).mock.calls.length).toBe(fastCount + 1)
  })
})
