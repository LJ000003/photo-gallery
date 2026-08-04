import { describe, expect, it } from 'vitest'
import { appendMediaParams, appendTokenParam } from '../utils/token'

describe('appendMediaParams', () => {
  it('URL 无查询参数时用 ? 拼接', () => {
    expect(appendMediaParams('/api/v1/photos/1/thumbnail', { mediaToken: 'MTIzLjQ1Ni5hYmM' })).toBe(
      '/api/v1/photos/1/thumbnail?sig=MTIzLjQ1Ni5hYmM',
    )
  })

  it('URL 已有查询参数时用 & 拼接（回归：?w=400?sig= 双问号导致 401）', () => {
    expect(
      appendMediaParams('/api/v1/photos/1/thumbnail?w=400', { mediaToken: 'MTIzLjQ1Ni5hYmM' }),
    ).toBe('/api/v1/photos/1/thumbnail?w=400&sig=MTIzLjQ1Ni5hYmM')
  })

  it('无 mediaToken 时原样返回 URL', () => {
    const url = '/api/v1/photos/1/thumbnail?w=400'
    expect(appendMediaParams(url, { mediaToken: undefined })).toBe(url)
    expect(appendMediaParams(url, null)).toBe(url)
    expect(appendMediaParams(url, undefined)).toBe(url)
    expect(appendMediaParams(url, {})).toBe(url)
  })

  it('mediaToken 需要 URL 编码（base64url 一般安全，但兜底）', () => {
    expect(appendMediaParams('/t', { mediaToken: 'a+b/c=' })).toBe('/t?sig=a%2Bb%2Fc%3D')
  })

  it('不再携带 fileSize 缓存破坏参数（签名含时间桶，URL 本身即缓存键）', () => {
    expect(appendMediaParams('/t', { mediaToken: 'x' }).includes('v=')).toBe(false)
  })
})

describe('appendTokenParam', () => {
  it('URL 无查询参数时用 ? 拼接', () => {
    expect(appendTokenParam('/api/v1/photos/1/webp', 'abc.def')).toBe(
      '/api/v1/photos/1/webp?token=abc.def',
    )
  })

  it('URL 已有查询参数时用 & 拼接', () => {
    expect(appendTokenParam('/api/v1/photos/1/thumbnail?w=200', 'abc.def')).toBe(
      '/api/v1/photos/1/thumbnail?w=200&token=abc.def',
    )
  })

  it('空 token 原样返回 URL', () => {
    expect(appendTokenParam('/t', '')).toBe('/t')
    expect(appendTokenParam('/t', null)).toBe('/t')
    expect(appendTokenParam('/t', undefined)).toBe('/t')
  })

  it('token 需要 URL 编码', () => {
    expect(appendTokenParam('/t', 'a b&c')).toBe('/t?token=a%20b%26c')
  })
})
