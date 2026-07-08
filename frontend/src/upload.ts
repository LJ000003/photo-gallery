import { AuthError } from './api'

const MAX_DIM = 1920
const JPEG_QUALITY = 0.85
const COMPRESS_SIZE_THRESHOLD = 1024 * 1024 // 1MB

export function compressImage(file: File): Promise<File> {
  // 非图片、GIF、JPEG 不做客户端压缩，保留原始数据（尤其 JPEG 的 EXIF）
  if (!file.type.startsWith('image/')) return Promise.resolve(file)
  if (file.type === 'image/jpeg' || file.type === 'image/gif') return Promise.resolve(file)

  return new Promise((resolve) => {
    const img = new Image()
    const url = URL.createObjectURL(file)

    img.onload = () => {
      URL.revokeObjectURL(url)

      const { width, height } = img
      const needsResize = width > MAX_DIM || height > MAX_DIM
      const needsCompress = file.size > COMPRESS_SIZE_THRESHOLD

      if (!needsResize && !needsCompress) {
        resolve(file)
        return
      }

      let w = width
      let h = height
      if (needsResize) {
        if (w > h) {
          h = Math.round((h * MAX_DIM) / w)
          w = MAX_DIM
        } else {
          w = Math.round((w * MAX_DIM) / h)
          h = MAX_DIM
        }
      }

      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(img, 0, 0, w, h)

      canvas.toBlob(
        (blob) => {
          if (!blob || blob.size >= file.size) {
            resolve(file)
            return
          }
          resolve(new File([blob], file.name, { type: 'image/jpeg' }))
        },
        'image/jpeg',
        JPEG_QUALITY,
      )
    }

    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(file)
    }

    img.src = url
  })
}

export async function compressImages(files: File[]): Promise<File[]> {
  return Promise.all(files.map(compressImage))
}

export function uploadWithProgress(
  url: string,
  formData: FormData,
  onProgress: (pct: number) => void,
): Promise<{ ok: boolean; status: number; data: unknown }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url)

    const token = localStorage.getItem('jwt_token')
    if (token) {
      xhr.setRequestHeader('Authorization', 'Bearer ' + token)
    }

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }

    xhr.onload = () => {
      if (xhr.status === 401 || xhr.status === 403) {
        localStorage.removeItem('jwt_token')
        localStorage.removeItem('konami_unlocked')
        window.location.reload()
        reject(new AuthError('登录已过期，请重新解锁'))
        return
      }
      let data: unknown = null
      try {
        data = JSON.parse(xhr.responseText)
      } catch {
        /* keep null */
      }
      resolve({ ok: xhr.status >= 200 && xhr.status < 300, status: xhr.status, data })
    }

    xhr.onerror = () => reject(new Error('网络错误'))
    xhr.ontimeout = () => reject(new Error('上传超时'))
    xhr.send(formData)
  })
}
