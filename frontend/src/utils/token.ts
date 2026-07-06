export function tokenParam(fileSize?: number): string {
  const t = localStorage.getItem('jwt_token') || localStorage.getItem('token')
  let q = t ? `?token=${t}` : ''
  if (fileSize) {
    q += q ? `&v=${fileSize}` : `?v=${fileSize}`
  }
  return q
}

export function tokenQS(fileSize?: number): string {
  const p = tokenParam(fileSize)
  return p ? '&' + p.slice(1) : ''
}
