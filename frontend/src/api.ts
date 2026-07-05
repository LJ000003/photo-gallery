import type { ApiResponse } from './types/api'
import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'

const ADMIN_PASSWORD = import.meta.env.VITE_ADMIN_PASSWORD || 'photoadmin'

function msg(key: string): string {
  const locale = navigator.language.startsWith('zh') ? zhCN : enUS
  const keys = key.split('.')
  let val: unknown = locale
  for (const k of keys) {
    val = (val as Record<string, unknown>)[k]
  }
  return (val as string) || key
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
    throw new Error(msg('auth.expired'))
  }

  return res
}

export async function requestToken(): Promise<string> {
  const res = await fetch('/api/auth/unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: ADMIN_PASSWORD }),
  })
  if (!res.ok) throw new Error(msg('auth.failed'))
  const data: ApiResponse<{ token: string }> = await res.json()
  return data.data.token
}
