import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUiStore } from '../ui'

describe('ui store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('starts locked when no localStorage data', () => {
    const ui = useUiStore()
    expect(ui.unlocked).toBe(false)
    expect(ui.token).toBeNull()
  })

  it('starts unlocked if konami_unlocked is "true" in localStorage', () => {
    localStorage.setItem('konami_unlocked', 'true')
    localStorage.setItem('jwt_token', 'test-token')
    setActivePinia(createPinia())
    const ui = useUiStore()
    expect(ui.unlocked).toBe(true)
    expect(ui.token).toBe('test-token')
  })

  it('unlock sets state and localStorage', () => {
    const ui = useUiStore()
    ui.unlock()
    expect(ui.unlocked).toBe(true)
    expect(localStorage.getItem('konami_unlocked')).toBe('true')
  })

  it('setToken stores jwt_token in localStorage', () => {
    const ui = useUiStore()
    ui.setToken('my-jwt')
    expect(ui.token).toBe('my-jwt')
    expect(localStorage.getItem('jwt_token')).toBe('my-jwt')
  })

  it('reLock clears all auth state', () => {
    const ui = useUiStore()
    ui.unlock()
    ui.setToken('jwt')
    ui.reLock()
    expect(ui.unlocked).toBe(false)
    expect(ui.token).toBeNull()
    expect(localStorage.getItem('konami_unlocked')).toBeNull()
    expect(localStorage.getItem('jwt_token')).toBeNull()
  })

  it('viewPhoto / editPhoto default to null', () => {
    const ui = useUiStore()
    expect(ui.viewPhoto).toBeNull()
    expect(ui.editPhoto).toBeNull()
  })

  it('sidebarOpen defaults to false', () => {
    const ui = useUiStore()
    expect(ui.sidebarOpen).toBe(false)
  })
})
