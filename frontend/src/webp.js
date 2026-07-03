let webpSupported = null;

export function supportsWebp() {
  if (webpSupported !== null) return webpSupported;
  const canvas = document.createElement('canvas');
  canvas.width = 1;
  canvas.height = 1;
  try {
    webpSupported = canvas.toDataURL('image/webp').startsWith('data:image/webp');
  } catch (e) {
    webpSupported = false;
  }
  return webpSupported;
}

export function webpUrl(photoId) {
  return supportsWebp() ? `/api/photos/${photoId}/webp` : `/api/photos/${photoId}/file`;
}
