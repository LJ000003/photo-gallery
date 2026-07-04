import { ref } from 'vue'
import { api } from '../api'
import { usePhotoStore } from '../stores/photo'
import { useToastStore } from '../stores/toast'
import type { ApiResponse } from '../types/api'

export function usePhotoActions() {
  const photo = usePhotoStore()
  const toast = useToastStore()

  const shareModal = ref<{ photoIds: number[] } | null>(null)
  const shareUrl = ref('')
  const shareLoading = ref(false)

  async function extractErrorMessage(res: Response): Promise<string> {
    try {
      const data = await res.json()
      return data.message || `请求失败（${res.status}）`
    } catch {
      return `服务器返回异常（${res.status}），请稍后重试`
    }
  }

  async function deletePhoto(id: number): Promise<void> {
    try {
      const res = await api(`/api/photos/${id}`, { method: 'DELETE' })
      if (!res.ok) throw new Error(await extractErrorMessage(res))
      photo.removePhoto(id)
      toast.success('删除成功')
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '删除失败')
    }
  }

  async function deletePhotos(ids: number[]): Promise<void> {
    try {
      const res = await api('/api/photos/batch', {
        method: 'DELETE',
        body: JSON.stringify(ids)
      })
      if (!res.ok) throw new Error(await extractErrorMessage(res))
      photo.removePhotos(ids)
      toast.success(`已删除 ${ids.length} 张照片`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '批量删除失败')
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
        body: JSON.stringify({ photoIds: ids, expireDays: 7 })
      })
      if (!res.ok) {
        const msg = await extractErrorMessage(res)
        throw new Error(msg)
      }
      const json: ApiResponse<{ url: string }> = await res.json()
      shareUrl.value = window.location.origin + json.data.url
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '生成分享失败')
      shareModal.value = null
    } finally {
      shareLoading.value = false
    }
  }

  function copyShareLink(): void {
    const text = shareUrl.value
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(() => {
        toast.success('链接已复制，分享给朋友吧')
        shareModal.value = null
      }).catch(() => fallbackCopy(text))
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
      toast.success('链接已复制，分享给朋友吧')
      shareModal.value = null
    } catch {
      toast.error('复制失败，请手动复制')
    }
    document.body.removeChild(ta)
  }

  return { shareModal, shareUrl, shareLoading, deletePhoto, deletePhotos, generateShare, copyShareLink }
}
