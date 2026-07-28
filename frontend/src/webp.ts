let webpSupported: boolean | null = null

export function supportsWebp(): boolean {
  if (webpSupported !== null) return webpSupported
  const canvas = document.createElement('canvas')
  canvas.width = 1
  canvas.height = 1
  try {
    webpSupported = canvas.toDataURL('image/webp').startsWith('data:image/webp')
  } catch (e) {
    webpSupported = false
  }
  return webpSupported
}

export function webpUrl(photoId: number): string {
  return supportsWebp() ? `/api/v1/photos/${photoId}/webp` : `/api/v1/photos/${photoId}/file`
}

export function thumbUrl(photoId: number, width = 400): string {
  return `/api/v1/photos/${photoId}/thumbnail?w=${width}`
}

export function thumbSrcset(photoId: number): string {
  return `${thumbUrl(photoId, 200)} 200w, ${thumbUrl(photoId, 400)} 400w`
}
