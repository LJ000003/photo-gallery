import { AuthError } from './api'
import i18n from './i18n'

const MAX_DIM = 1920
const JPEG_QUALITY = 0.85
const COMPRESS_SIZE_THRESHOLD = 1024 * 1024 // 1MB

/**
 * 检测画布是否含透明像素。
 * 用于决定压缩编码：透明图必须保持 PNG——JPEG 会把透明像素填成黑色，
 * 旧实现曾把带 alpha 的 PNG/WebP 永久压成黑底 JPEG 且扩展名与内容不符。
 * 输出用 PNG 而非 WebP：后端 ImageIO 无法解码 WebP 原图，处理链会标 FAILED。
 */
export function hasTransparentPixels(
  canvas: HTMLCanvasElement,
  ctx: CanvasRenderingContext2D,
): boolean {
  const { width, height } = canvas
  const data = ctx.getImageData(0, 0, width, height).data
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] < 255) return true
  }
  return false
}

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

      // 透明图保持 PNG 编码（JPEG 会填黑并永久丢失 alpha）
      const outType = hasTransparentPixels(canvas, ctx) ? 'image/png' : 'image/jpeg'
      const outQuality = outType === 'image/jpeg' ? JPEG_QUALITY : undefined

      canvas.toBlob(
        (blob) => {
          if (!blob || blob.size >= file.size) {
            resolve(file)
            return
          }
          resolve(new File([blob], file.name, { type: outType }))
        },
        outType,
        outQuality,
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
    xhr.open('POST', url.replace(/^\/api/, '/api/v1'))

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
        reject(new AuthError(i18n.global.t('auth.expired')))
        return
      }
      let data: unknown = null
      try {
        data = JSON.parse(xhr.responseText)
      } catch {
        /* 响应体非 JSON（如 HTML 错误页）时保留 null，仅记录便于排查 */
        console.warn('[upload] 响应非 JSON，跳过解析:', xhr.responseText?.slice(0, 200))
      }
      resolve({ ok: xhr.status >= 200 && xhr.status < 300, status: xhr.status, data })
    }

    xhr.onerror = () => reject(new Error('网络错误'))
    xhr.ontimeout = () => reject(new Error('上传超时'))
    xhr.send(formData)
  })
}
