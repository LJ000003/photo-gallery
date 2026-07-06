import { describe, it, expect, beforeEach } from 'vitest'
import { tokenParam, tokenQS } from '../utils/token'

describe('tokenParam', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns empty string when no token stored', () => {
    expect(tokenParam()).toBe('')
  })

  it('returns query string with jwt_token', () => {
    localStorage.setItem('jwt_token', 'abc123')
    expect(tokenParam()).toBe('?token=abc123')
  })

  it('falls back to token key when jwt_token missing', () => {
    localStorage.setItem('token', 'fallback456')
    expect(tokenParam()).toBe('?token=fallback456')
  })

  it('prefers jwt_token over token when both present', () => {
    localStorage.setItem('jwt_token', 'primary')
    localStorage.setItem('token', 'fallback')
    expect(tokenParam()).toBe('?token=primary')
  })

  it('appends fileSize as v param when provided', () => {
    localStorage.setItem('jwt_token', 'abc')
    expect(tokenParam(1024)).toBe('?token=abc&v=1024')
  })

  it('handles fileSize without token', () => {
    expect(tokenParam(500)).toBe('?v=500')
  })

  it('handles fileSize of 0 as falsy (no v param)', () => {
    localStorage.setItem('jwt_token', 'abc')
    expect(tokenParam(0)).toBe('?token=abc')
  })
})

describe('tokenQS', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns empty string when no token', () => {
    expect(tokenQS()).toBe('')
  })

  it('strips leading ? and prepends &', () => {
    localStorage.setItem('jwt_token', 'abc')
    expect(tokenQS()).toBe('&token=abc')
  })

  it('with fileSize', () => {
    localStorage.setItem('jwt_token', 'abc')
    expect(tokenQS(1024)).toBe('&token=abc&v=1024')
  })

  it('with fileSize only (no token)', () => {
    expect(tokenQS(100)).toBe('&v=100')
  })
})
