import { api } from '../api'
import i18n from '../i18n'
import { useToastStore } from '../stores/toast'
import type { Photo } from '../types/photo'
import type { ApiResponse } from '../types/api'

export interface ProcessingPollingOptions {
  /** 当前列表快照（只读过滤 PROCESSING 用） */
  getPhotos: () => Photo[]
  /** 单张照片原地更新（轮询命中 / 超时标 FAILED 共用） */
  patch: (photo: Photo) => void
}

/**
 * 处理中照片状态轮询（从 photo store 抽出）：
 * 3s 间隔并行拉取全部 PROCESSING 照片（非串行逐张）；20 轮（60s）超时本地标 FAILED
 * + toast（FAILED 态出现重试按钮），不再静默停止。行为与原实现完全一致。
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
        // 超时（20×3s=60s）不再静默停止：本地标 FAILED（FAILED 态出现重试按钮）+ 提示
        const timeoutMsg = i18n.global.t('gallery.processingTimeoutMessage')
        getPhotos().forEach((p) => {
          if (p.processingStatus === 'PROCESSING') {
            patch({ ...p, processingStatus: 'FAILED', errorMessage: timeoutMsg })
          }
        })
        toast.info(i18n.global.t('gallery.processingTimeout'))
        stop()
        return
      }
      attempts++
      processingTimer = setTimeout(async () => {
        try {
          // 本轮全部处理中照片并行拉取（旧实现逐张串行，50 张 = 50 个串行请求）
          await Promise.all(
            processingPhotos.map(async (p) => {
              const res = await api(`/api/photos/${p.id}`)
              const json: ApiResponse<Photo> = await res.json()
              if (json.code === 200 && json.data) patch(json.data)
            }),
          )
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
