import { describe, it, expect, beforeEach, vi } from 'vitest'
import { api, requestToken } from '../api'

describe('api', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  it('injects Authorization header when jwt_token exists', async () => {
    localStorage.setItem('jwt_token', 'test-jwt')
    const mockFetch = vi.fn().mockResolvedValue({ status: 200, ok: true })
    vi.stubGlobal('fetch', mockFetch)

    await api('/api/photos')

    const headers = mockFetch.mock.calls[0][1].headers
    expect(headers['Authorization']).toBe('Bearer test-jwt')
  })

  it('does not inject Authorization when no token', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ status: 200, ok: true })
    vi.stubGlobal('fetch', mockFetch)

    await api('/api/photos')

    const headers = mockFetch.mock.calls[0][1].headers
    expect(headers['Authorization']).toBeUndefined()
  })

  it('sets Content-Type json for non-FormData body', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ status: 200, ok: true })
    vi.stubGlobal('fetch', mockFetch)

    await api('/api/photos', { method: 'POST', body: JSON.stringify({ name: 'test' }) })

    const headers = mockFetch.mock.calls[0][1].headers
    expect(headers['Content-Type']).toBe('application/json')
  })

  it('does NOT set Content-Type for FormData body', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ status: 200, ok: true })
    vi.stubGlobal('fetch', mockFetch)
    const fd = new FormData()
    fd.append('file', new Blob())

    await api('/api/photos', { method: 'POST', body: fd })

    const headers = mockFetch.mock.calls[0][1].headers
    expect(headers['Content-Type']).toBeUndefined()
  })

  it('on 401 clears auth and reloads', async () => {
    localStorage.setItem('jwt_token', 'expired')
    localStorage.setItem('konami_unlocked', 'true')

    const mockFetch = vi.fn().mockResolvedValue({ status: 401, ok: false })
    vi.stubGlobal('fetch', mockFetch)
    const reloadMock = vi.fn()
    vi.stubGlobal('location', { reload: reloadMock })

    await expect(api('/api/photos')).rejects.toThrow('登录已过期，请重新解锁')
    expect(localStorage.getItem('jwt_token')).toBeNull()
    expect(localStorage.getItem('konami_unlocked')).toBeNull()
  })

  it('on 403 also clears auth', async () => {
    localStorage.setItem('jwt_token', 'bad')

    const mockFetch = vi.fn().mockResolvedValue({ status: 403, ok: false })
    vi.stubGlobal('fetch', mockFetch)
    const reloadMock = vi.fn()
    vi.stubGlobal('location', { reload: reloadMock })

    await expect(api('/api/photos')).rejects.toThrow('登录已过期，请重新解锁')
    expect(localStorage.getItem('jwt_token')).toBeNull()
  })
})

describe('requestToken', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('returns token on successful auth', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { token: 'admin-jwt' } }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const token = await requestToken()
    expect(token).toBe('admin-jwt')
    expect(mockFetch.mock.calls[0][0]).toBe('/api/auth/unlock')
    expect(mockFetch.mock.calls[0][1].method).toBe('POST')
  })

  it('throws on auth failure', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false })
    vi.stubGlobal('fetch', mockFetch)

    await expect(requestToken()).rejects.toThrow('认证失败')
  })
})
