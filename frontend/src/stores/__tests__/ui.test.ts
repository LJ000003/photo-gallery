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

  it('uploadOpen / filterOpen / helpOpen default to false', () => {
    const ui = useUiStore()
    expect(ui.uploadOpen).toBe(false)
    expect(ui.filterOpen).toBe(false)
    expect(ui.helpOpen).toBe(false)
  })
})

describe('ui store viewer / slideshow', () => {
  const mk = (id: number, name = `p${id}`) => ({ id, name, description: '', fileSize: 100 })

  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('openViewer sets photo + list and resets slideshowPlaying', () => {
    const ui = useUiStore()
    const list = [mk(1), mk(2), mk(3)]
    ui.slideshowPlaying = true
    ui.openViewer(list[1], list)
    expect(ui.viewPhoto).toEqual(list[1])
    expect(ui.viewPhotos).toEqual(list)
    expect(ui.slideshowPlaying).toBe(false)
  })

  it('openViewer defaults list to empty array', () => {
    const ui = useUiStore()
    ui.openViewer(mk(1))
    expect(ui.viewPhotos).toEqual([])
  })

  it('navigateViewer moves forward/backward within list', () => {
    const ui = useUiStore()
    const list = [mk(1), mk(2), mk(3)]
    ui.openViewer(list[0], list)
    ui.navigateViewer(1)
    expect(ui.viewPhoto).toEqual(list[1])
    ui.navigateViewer(-1)
    expect(ui.viewPhoto).toEqual(list[0])
  })

  it('navigateViewer does not wrap at boundaries without wrap flag', () => {
    const ui = useUiStore()
    const list = [mk(1), mk(2), mk(3)]
    ui.openViewer(list[0], list)
    ui.navigateViewer(-1)
    expect(ui.viewPhoto).toEqual(list[0])
    ui.viewPhoto = list[2]
    ui.navigateViewer(1)
    expect(ui.viewPhoto).toEqual(list[2])
  })

  it('navigateViewer wraps at boundaries with wrap flag', () => {
    const ui = useUiStore()
    const list = [mk(1), mk(2), mk(3)]
    ui.openViewer(list[2], list)
    ui.navigateViewer(1, true)
    expect(ui.viewPhoto).toEqual(list[0])
    ui.navigateViewer(-1, true)
    expect(ui.viewPhoto).toEqual(list[2])
  })

  it('navigateViewer no-ops on empty list, single-item list, or photo not in list', () => {
    const ui = useUiStore()
    const list = [mk(1), mk(2)]
    ui.openViewer(mk(9), [])
    ui.navigateViewer(1)
    expect(ui.viewPhoto?.id).toBe(9)

    ui.openViewer(list[0], [list[0]])
    ui.navigateViewer(1, true)
    expect(ui.viewPhoto).toEqual(list[0])

    ui.openViewer(mk(42), list)
    ui.navigateViewer(1)
    expect(ui.viewPhoto?.id).toBe(42)
  })

  it('navigateViewer no-ops when no photo is open', () => {
    const ui = useUiStore()
    ui.viewPhotos = [mk(1), mk(2)]
    ui.navigateViewer(1)
    expect(ui.viewPhoto).toBeNull()
  })

  it('toggleSlideshow flips playing state', () => {
    const ui = useUiStore()
    expect(ui.slideshowPlaying).toBe(false)
    ui.toggleSlideshow()
    expect(ui.slideshowPlaying).toBe(true)
    ui.toggleSlideshow()
    expect(ui.slideshowPlaying).toBe(false)
  })

  it('closeViewer clears photo and list', () => {
    const ui = useUiStore()
    ui.openViewer(mk(1), [mk(1), mk(2)])
    ui.closeViewer()
    expect(ui.viewPhoto).toBeNull()
    expect(ui.viewPhotos).toEqual([])
  })
})
