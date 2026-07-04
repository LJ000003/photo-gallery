import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useToastStore } from '../toast'

describe('toast store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('adds toast with default type info', () => {
    const toast = useToastStore()
    toast.success('ok')
    expect(toast.toasts).toHaveLength(1)
    expect(toast.toasts[0].message).toBe('ok')
    expect(toast.toasts[0].type).toBe('success')
  })

  it('adds error toast', () => {
    const toast = useToastStore()
    toast.error('fail')
    expect(toast.toasts[0].type).toBe('error')
    expect(toast.toasts[0].message).toBe('fail')
  })

  it('adds info toast', () => {
    const toast = useToastStore()
    toast.info('note')
    expect(toast.toasts[0].type).toBe('info')
  })

  it('auto-removes toast after default 3000ms', () => {
    const toast = useToastStore()
    toast.info('ephemeral')
    expect(toast.toasts).toHaveLength(1)
    vi.advanceTimersByTime(3000)
    expect(toast.toasts).toHaveLength(0)
  })

  it('manually removes toast by id', () => {
    const toast = useToastStore()
    toast.info('msg1')
    toast.error('msg2')
    const id = toast.toasts[0].id
    toast.remove(id)
    expect(toast.toasts).toHaveLength(1)
    expect(toast.toasts[0].message).toBe('msg2')
  })

  it('assigns unique incrementing ids', () => {
    const toast = useToastStore()
    toast.info('a')
    toast.info('b')
    toast.info('c')
    expect(toast.toasts[0].id).toBeLessThan(toast.toasts[1].id)
    expect(toast.toasts[1].id).toBeLessThan(toast.toasts[2].id)
  })
})
