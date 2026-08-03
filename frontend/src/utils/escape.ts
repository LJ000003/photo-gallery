const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

/** HTML 字符串插值转义（Leaflet popup 等直接拼 innerHTML 的场景） */
export function escapeHtml(value: string | null | undefined): string {
  if (value == null) return ''
  return String(value).replace(/[&<>"']/g, (c) => HTML_ESCAPES[c] ?? c)
}
