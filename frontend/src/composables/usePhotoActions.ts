import { ref } from 'vue'
import { api } from '../api'
import i18n from '../i18n'
import { usePhotoStore } from '../stores/photo'
import { useToastStore } from '../stores/toast'
import { extractErrorMessage } from '../utils/error'
import type { ApiResponse } from '../types/api'

/**
 * 分享状态为模块级单例：多个视图（照片流/相册）共享同一份 shareModal 状态，
 * 任意组件调用 usePhotoActions() 都读写同一份状态
 */
const shareModal = ref<{ photoIds: number[] } | null>(null)
const shareUrl = ref('')
const shareLoading = ref(false)

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
        err instanceof Error ? err.message : i18n.global.t('actions.deleteFailed', { count: ids.length }),
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
      const json: ApiResponse<{ url: string }> = await res.json()
      shareUrl.value = window.location.origin + json.data.url
    } catch (err) {
      toast.error(err instanceof Error ? err.message : i18n.global.t('share.generateFailed'))
      shareModal.value = null
    } finally {
      shareLoading.value = false
    }
  }

  function copyShareLink(): void {
    const text = shareUrl.value
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard
        .writeText(text)
        .then(() => {
          toast.success(i18n.global.t('share.copied'))
          shareModal.value = null
        })
        .catch(() => fallbackCopy(text))
    } else {
      fallbackCopy(text)
    }
  }

  function fallbackCopy(text: string): void {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    ta.style.top = '-9999px'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    try {
      document.execCommand('copy')
      toast.success(i18n.global.t('share.copied'))
      shareModal.value = null
    } catch {
      toast.error(i18n.global.t('share.copyFailed'))
    }
    document.body.removeChild(ta)
  }

  return {
    shareModal,
    shareUrl,
    shareLoading,
    deletePhoto,
    deletePhotos,
    generateShare,
    copyShareLink,
  }
}
