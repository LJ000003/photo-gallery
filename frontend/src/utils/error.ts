import i18n from '../i18n'

/**
 * 从 API 响应提取错误消息：ApiResponse.message 优先，
 * 兜底带 status 的通用文案（非组件环境，用 i18n.global）。
 * usePhotoActions / PhotoEditDrawer / BatchEditDrawer 的单一实现。
 */
export async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json()
    return body.message || i18n.global.t('common.requestFailed', { status: res.status })
  } catch {
    return i18n.global.t('common.serverError', { status: res.status })
  }
}
