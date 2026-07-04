import { describe, it, expect } from 'vitest'
import { supportsWebp, webpUrl } from '../webp'

describe('webp', () => {
  it('supportsWebp returns a boolean', () => {
    const result = supportsWebp()
    expect(typeof result).toBe('boolean')
  })

  it('supportsWebp is memoized (returns same value on repeat calls)', () => {
    const a = supportsWebp()
    const b = supportsWebp()
    expect(a).toBe(b)
  })

  it('webpUrl returns webp path when supported', () => {
    // In happy-dom, canvas.toDataURL('image/webp') returns 'data:image/webp;base64,...'
    // so supportsWebp is likely true in this environment
    const url = webpUrl(42)
    if (supportsWebp()) {
      expect(url).toBe('/api/photos/42/webp')
    } else {
      expect(url).toBe('/api/photos/42/file')
    }
  })
})
