import { ref } from 'vue'
import { api } from '../api'
import i18n from '../i18n'
import { usePhotoStore } from '../stores/photo'
import { useToastStore } from '../stores/toast'
import { extractErrorMessage } from '../utils/error'
import { copyText } from '../utils/clipboard'
import type { ApiResponse } from '../types/api'

/**
 * 分享状态为模块级单例：多个视图（照片流/相册）共享同一份 shareModal 状态，
 * 任意组件调用 usePhotoActions() 都读写同一份状态
 */
const shareModal = ref<{ photoIds: number[] } | null>(null)
const shareUrl = ref('')
const shareLoading = ref(false)
// 当前弹窗分享的 DB token（generate 响应返回；撤销按钮只撤销它——
// 幂等复用的语义下同内容分享返回同一 token，之前发出的旧链接=同一链接）
const shareToken = ref('')
const shareRevoking = ref(false)

export function usePhotoActions() {
  const photo = usePhotoStore()
  const toast = useToastStore()

  async function restorePhoto(id: number): Promise<void> {
    try {
      const res = await api(`/api/photos/${id}/restore`, { method: 'POST' })
      if (!res.ok) throw new Error(await extractErrorMessage(res))
      await photo.resetAndReload()
      toast.success(i18n.global.t('trash.restored'))
    } catch (err) {
      toast.error(err instanceof Error ? err.message : i18n.global.t('trash.restoreFailed'))
    }
  }

  async function deletePhoto(id: number): Promise<void> {
    try {
      const res = await api(`/api/photos/${id}`, { method: 'DELETE' })
      if (!res.ok) throw new Error(await extractErrorMessage(res))
      photo.removePhoto(id)
      toast.add(i18n.global.t('actions.deleted'), 'success', 5000, {
        label: i18n.global.t('actions.undo'),
        onClick: () => restorePhoto(id),
      })
    } catch (err) {
      toast.error(err instanceof Error ? err.message : i18n.global.t('actions.deleteFailed'))
    }
  }

  async function deletePhotos(ids: number[]): Promise<void> {
    try {
      const res = await api('/api/photos/batch', {
        method: 'DELETE',
        body: JSON.stringify(ids),
      })
      if (!res.ok) throw new Error(await extractErrorMessage(res))
      photo.removePhotos(ids)
      toast.add(i18n.global.t('selection.deletedCount', { count: ids.length }), 'success', 5000, {
        label: i18n.global.t('actions.undo'),
        onClick: async () => {
          const failed: number[] = []
          for (const id of ids) {
            try {
              const res = await api(`/api/photos/${id}/restore`, { method: 'POST' })
              if (!res.ok) failed.push(id)
            } catch {
              failed.push(id)
            }
          }
          await photo.resetAndReload()
          if (failed.length > 0) {
            toast.error(
              i18n.global.t('trash.restoreFailedCount', {
                failed: failed.length,
                total: ids.length,
              }),
            )
          } else {
            toast.success(i18n.global.t('trash.restored'))
          }
        },
      })
    } catch (err) {
      toast.error(
        err instanceof Error
          ? err.message
          : i18n.global.t('actions.deleteFailed', { count: ids.length }),
      )
    }
  }

  async function generateShare(ids: number[]): Promise<void> {
    if (ids.length === 0) return
    shareLoading.value = true
    shareUrl.value = ''
    shareModal.value = { photoIds: ids }
    try {
      const res = await api('/api/share/generate', {
        method: 'POST',
        body: JSON.stringify({ photoIds: ids, expireDays: 7 }),
      })
      if (!res.ok) {
        const msg = await extractErrorMessage(res)
        throw new Error(msg)
      }
      const json: ApiResponse<{ url: string; token: string }> = await res.json()
      shareUrl.value = window.location.origin + json.data.url
      shareToken.value = json.data.token
    } catch (err) {
      toast.error(err instanceof Error ? err.message : i18n.global.t('share.generateFailed'))
      shareModal.value = null
    } finally {
      shareLoading.value = false
    }
  }

  /** 撤销当前分享链接（撤销后旧链接立即失效） */
  async function revokeShare(): Promise<void> {
    if (!shareToken.value) return
    shareRevoking.value = true
    try {
      const res = await api(`/api/share/${shareToken.value}/revoke`, { method: 'POST' })
      if (!res.ok) throw new Error(await extractErrorMessage(res))
      shareModal.value = null
      shareUrl.value = ''
      shareToken.value = ''
      toast.success(i18n.global.t('share.revoked'))
    } catch (err) {
      toast.error(err instanceof Error ? err.message : i18n.global.t('share.revokeFailed'))
    } finally {
      shareRevoking.value = false
    }
  }

  function copyShareLink(): void {
    void copyText(shareUrl.value).then((ok) => {
      if (ok) {
        toast.success(i18n.global.t('share.copied'))
        shareModal.value = null
      } else {
        toast.error(i18n.global.t('share.copyFailed'))
      }
    })
  }

  return {
    shareModal,
    shareUrl,
    shareLoading,
    shareToken,
    shareRevoking,
    deletePhoto,
    deletePhotos,
    generateShare,
    copyShareLink,
    revokeShare,
  }
}
