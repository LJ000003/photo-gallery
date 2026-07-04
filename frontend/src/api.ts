import type { ApiResponse } from './types/api'

const ADMIN_PASSWORD = import.meta.env.VITE_ADMIN_PASSWORD || 'photoadmin'

export async function api(
  url: string,
  options: RequestInit & { body?: unknown } = {},
): Promise<Response> {
  const token = localStorage.getItem('jwt_token')
  const headers: Record<string, string> = {}

  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }

  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }

  const res = await fetch(url, { ...options, headers })

  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('jwt_token')
    localStorage.removeItem('konami_unlocked')
    window.location.reload()
    throw new Error('登录已过期，请重新解锁')
  }

  return res
}

export async function requestToken(): Promise<string> {
  const res = await fetch('/api/auth/unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: ADMIN_PASSWORD }),
  })
  if (!res.ok) throw new Error('认证失败')
  const data: ApiResponse<{ token: string }> = await res.json()
  return data.data.token
}
