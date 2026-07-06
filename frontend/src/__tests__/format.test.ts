import { describe, it, expect } from 'vitest'
import { formatSize } from '../utils/format'

describe('formatSize', () => {
  it('returns empty string for 0', () => {
    expect(formatSize(0)).toBe('')
  })

  it('returns empty string for undefined', () => {
    expect(formatSize(undefined)).toBe('')
  })

  it('formats bytes (< 1024)', () => {
    expect(formatSize(1)).toBe('1 B')
    expect(formatSize(512)).toBe('512 B')
    expect(formatSize(1023)).toBe('1023 B')
  })

  it('formats KB (1024 to < 1048576)', () => {
    expect(formatSize(1024)).toBe('1.0 KB')
    expect(formatSize(1536)).toBe('1.5 KB')
    expect(formatSize(1048575)).toBe('1024.0 KB')
  })

  it('formats MB (>= 1048576)', () => {
    expect(formatSize(1048576)).toBe('1.0 MB')
    expect(formatSize(5242880)).toBe('5.0 MB')
    expect(formatSize(1073741824)).toBe('1024.0 MB')
  })

  it('handles typical photo sizes', () => {
    expect(formatSize(2450000)).toBe('2.3 MB')
    expect(formatSize(45000)).toBe('43.9 KB')
    expect(formatSize(800)).toBe('800 B')
  })
})
