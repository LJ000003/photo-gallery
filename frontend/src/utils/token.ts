/**
 * 图片 URL 鉴权参数（追加式拼接，自动处理 ? / & 分隔符）。
 * 旧实现把 24h 管理员 JWT 放进 query string（`?token=`）——会随访问日志、
 * 浏览器历史、Referer 泄漏。后端已改为「短时签名」（HMAC 时间桶，绑定
 * photoId、~10 分钟有效、无任何会话权限），各响应 DTO 携带 mediaToken：
 *
 *   appendMediaParams(`/api/v1/photos/${p.id}/thumbnail?w=400`, p)
 *   // → /api/v1/photos/1/thumbnail?w=400&sig=MTIz...
 *
 * 优先用 per-photo 签名；签名缺失时回退显式 token（分享页的 viewer token，
 * 该 token 本身就是分享凭据，公开链接不在此列）。
 *
 * 注意：URL 可能已带 `?w=400` 等参数，分隔符必须是 `&` 而非 `?`——
 * 旧实现 `thumbUrl(id) + '?sig='` 在首页网格上拼出 `?w=400?sig=` 双问号，
 * 后端解析不到 sig 导致所有缩略图 401。
 */
export function appendMediaParams(url: string, o?: { mediaToken?: string | null } | null): string {
  const sig = o?.mediaToken
  if (!sig) return url
  return url + (url.includes('?') ? '&' : '?') + 'sig=' + encodeURIComponent(sig)
}

export function appendTokenParam(url: string, token?: string | null): string {
  if (!token) return url
  return url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token)
}

/**
 * 追加客户端媒体版本号（?v=）：图片变换（旋转/镜像/裁剪）后旧图仍被
 * HTTP 缓存（缩略图 7 天 Cache-Control + Workbox CacheFirst）命中，
 * 递增 version 强制回源。无 version 时原样返回。
 */
export function mediaUrlWithVersion(url: string, o?: { version?: number | null } | null): string {
  if (!o?.version) return url
  return url + (url.includes('?') ? '&' : '?') + 'v=' + o.version
}
