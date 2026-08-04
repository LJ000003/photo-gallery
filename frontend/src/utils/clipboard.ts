/**
 * 复制文本到剪贴板 —— 统一入口（原 usePhotoActions/ErrorBoundary 各有一份重复实现）。
 * Clipboard API 优先（需安全上下文）；失败或非安全上下文回退 textarea + execCommand
 * （已弃用 API，但仍是非 HTTPS 环境可用的最后兜底）。
 * @returns 是否复制成功（调用方据此决定成功/失败提示）
 */
export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 权限被拒等场景，回退到 legacy 路径
    }
  }
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  ta.style.top = '-9999px'
  document.body.appendChild(ta)
  ta.focus()
  ta.select()
  let ok = false
  try {
    // execCommand 返回 boolean（不抛异常），失败时静默返回 false
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(ta)
  return ok
}
