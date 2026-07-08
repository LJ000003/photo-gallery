import type { ApiResponse } from './types/api'
import i18n from './i18n'

export class AuthError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AuthError'
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

  const res = await fetch(url, { ...fetchOptions, headers })

  if (!skipAuth && (res.status === 401 || res.status === 403)) {
    localStorage.removeItem('jwt_token')
    localStorage.removeItem('konami_unlocked')
    window.location.reload()
    throw new AuthError(i18n.global.t('auth.expired'))
  }

  return res
}

export async function requestToken(): Promise<string> {
  const res = await fetch('/api/auth/unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) throw new AuthError(i18n.global.t('auth.failed'))
  const data: ApiResponse<{ token: string }> = await res.json()
  return data.data.token
}
