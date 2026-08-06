import { api } from '../api'
import i18n from '../i18n'
import { useToastStore } from '../stores/toast'
import type { Photo, PhotoProcessingStatus } from '../types/photo'
import type { ApiResponse } from '../types/api'

export interface ProcessingPollingOptions {
  /** 当前列表快照（只读过滤 PROCESSING 用） */
  getPhotos: () => Photo[]
  /** 按 id 整体替换照片（轮询命中共用；批量状态项需先在组合式内合并成完整对象再传入） */
  patch: (photo: Photo) => void
}

/**
 * 处理中照片状态轮询：
 * - 每 3s 一个批量请求（GET /api/photos/status?ids=...）拉全部 PROCESSING 照片，
 *   替代旧实现逐张 GET /photos/{id} 拉全量详情——50 张在途时旧实现每 3s 发 50 个
 *   全量请求（O(N²/3s)），慢服务器扛不住；
 * - 状态项只含 id/status/errorMessage，与现有 photo 对象合并后 patch（保留其余字段）；
 * - 20 轮（60s）超时：只停止轮询 + toast，不再本地标 FAILED——2 核机器批量上传
 *   处理必然超过 60s，旧逻辑会把仍在处理的照片误报为「处理失败」（慢=失败假象）。
 */
export function useProcessingPolling({ getPhotos, patch }: ProcessingPollingOptions) {
  const toast = useToastStore()
  let processingTimer: ReturnType<typeof setTimeout> | null = null
  const MAX_POLL_ATTEMPTS = 20

  function stop(): void {
    if (processingTimer) {
      clearTimeout(processingTimer)
      processingTimer = null
    }
  }

  function start(): void {
    stop()
    let attempts = 0
    function poll(): void {
      const processingPhotos = getPhotos().filter((p) => p.processingStatus === 'PROCESSING')
      if (processingPhotos.length === 0) {
        stop()
        return
      }
      if (attempts >= MAX_POLL_ATTEMPTS) {
        // 超时（20×3s=60s）只停止轮询：状态保持 PROCESSING，照片可能仍在处理链中
        // （慢机器/队列积压场景），由用户刷新查看最终状态，不误报失败
        toast.info(i18n.global.t('gallery.processingTimeout'))
        stop()
        return
      }
      attempts++
      processingTimer = setTimeout(async () => {
        try {
          const ids = processingPhotos.map((p) => p.id).join(',')
          const res = await api(`/api/photos/status?ids=${ids}`)
          const json: ApiResponse<PhotoProcessingStatus[]> = await res.json()
          if (json.code === 200 && json.data) {
            for (const item of json.data) {
              const existing = processingPhotos.find((p) => p.id === item.id)
              if (existing) patch({ ...existing, ...item })
            }
          }
        } catch (err) {
          console.warn(
            `[photo store] 轮询照片处理状态失败: ${processingPhotos.map((p) => p.id).join(',')}`,
            err,
          )
        }
        poll()
      }, 3000)
    }
    poll()
  }

  return { start, stop }
}
