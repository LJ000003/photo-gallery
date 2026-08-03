import type { ApiResponse } from './types/api'
import i18n from './i18n'

const BASE = '/api/v1'

export class AuthError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AuthError'
  }
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function api(
  url: string,
  options: RequestInit & { body?: unknown; token?: string; skipAuth?: boolean } = {},
): Promise<Response> {
  const { token: customToken, skipAuth, ...fetchOptions } = options
  const token = customToken ?? localStorage.getItem('jwt_token')
  const headers: Record<string, string> = {}

  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }

  if (!(fetchOptions.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }

  headers['X-Trace-Id'] = crypto.randomUUID().slice(0, 8)

  const res = await fetch(url.replace(/^\/api/, BASE), { ...fetchOptions, headers })

  if (!skipAuth && (res.status === 401 || res.status === 403)) {
    localStorage.removeItem('jwt_token')
    localStorage.removeItem('konami_unlocked')
    window.location.reload()
    throw new AuthError(i18n.global.t('auth.expired'))
  }

  if (!res.ok) {
    const traceId = res.headers.get('X-Trace-Id') || headers['X-Trace-Id']
    console.error(`[${traceId}] ${fetchOptions.method || 'GET'} ${url} → ${res.status}`)
    let message = `请求失败（${res.status}）`
    try {
      const body = await res.json()
      if (body.message) message = body.message
    } catch {
      /* 响应体不是 JSON，使用默认消息 */
    }
    throw new ApiError(res.status, message)
  }

  return res
}

export async function requestToken(keys: string[]): Promise<string> {
  // Step 1: 获取一次性 nonce
  const challengeRes = await fetch(BASE + '/auth/challenge')
  if (!challengeRes.ok) throw new AuthError(i18n.global.t('auth.failed'))
  const challengeData: ApiResponse<{ nonce: string }> = await challengeRes.json()
  const nonce = challengeData.data.nonce

  // Step 2: 发送 nonce + 按键序列给后端验证
  const unlockRes = await fetch(BASE + '/auth/unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nonce, keys }),
  })
  if (!unlockRes.ok) {
    const err: ApiResponse<null> = await unlockRes.json()
    throw new AuthError(err.message || i18n.global.t('auth.failed'))
  }
  const data: ApiResponse<{ token: string }> = await unlockRes.json()
  return data.data.token
}
