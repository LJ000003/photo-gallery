import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useKeyboardShortcuts } from '../useKeyboardShortcuts'
import { useUiStore } from '../../stores/ui'

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useRoute: () => ({ query: {}, path: '/', name: undefined }),
}))

const Harness = defineComponent({
  setup() {
    useKeyboardShortcuts()
    return () => h('div', [h('button', 'click me'), h('input')])
  },
})

const mk = (id: number, name = `p${id}`) => ({ id, name, description: '', fileSize: 100 })

function press(key: string): void {
  document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }))
}

describe('useKeyboardShortcuts slideshow', () => {
  let ui: ReturnType<typeof useUiStore>

  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    ui = useUiStore()
  })

  afterEach(() => {
    // happy-dom keeps activeElement pointing at removed nodes; reset so
    // isInputFocused() from a previous test can't swallow shortcuts
    ;(document.activeElement as HTMLElement | null)?.blur?.()
    document.body.innerHTML = ''
  })

  function mountHarness(): ReturnType<typeof mount> {
    return mount(Harness, { attachTo: document.body })
  }

  it('space toggles slideshow playback in the viewer', () => {
    ui.unlock()
    ui.openViewer(mk(1), [mk(1), mk(2)])
    const wrapper = mountHarness()

    press(' ')
    expect(ui.slideshowPlaying).toBe(true)
    press(' ')
    expect(ui.slideshowPlaying).toBe(false)

    wrapper.unmount()
  })

  it('space does not toggle when focus is on a button (native activation takes over)', () => {
    ui.unlock()
    ui.openViewer(mk(1), [mk(1), mk(2)])
    const wrapper = mountHarness()

    document.querySelector('button')?.focus()
    expect(document.activeElement?.tagName).toBe('BUTTON')
    press(' ')
    expect(ui.slideshowPlaying).toBe(false)

    wrapper.unmount()
  })

  it('space does not trigger in an input field', () => {
    ui.unlock()
    ui.openViewer(mk(1), [mk(1), mk(2)])
    const wrapper = mountHarness()

    document.querySelector('input')?.focus()
    press(' ')
    expect(ui.slideshowPlaying).toBe(false)

    wrapper.unmount()
  })

  it('space has no effect outside the viewer scope', () => {
    ui.unlock()
    const wrapper = mountHarness()

    press(' ')
    expect(ui.slideshowPlaying).toBe(false)

    wrapper.unmount()
  })

  it('arrow keys navigate within the viewer list', () => {
    ui.unlock()
    const list = [mk(1), mk(2), mk(3)]
    ui.openViewer(list[0], list)
    const wrapper = mountHarness()

    press('ArrowRight')
    expect(ui.viewPhoto?.id).toBe(2)
    press('ArrowRight')
    expect(ui.viewPhoto?.id).toBe(3)
    press('ArrowLeft')
    expect(ui.viewPhoto?.id).toBe(2)

    wrapper.unmount()
  })

  it('arrow keys no-op at the list boundary', () => {
    ui.unlock()
    const list = [mk(1), mk(2)]
    ui.openViewer(list[0], list)
    const wrapper = mountHarness()

    press('ArrowLeft')
    expect(ui.viewPhoto?.id).toBe(1)

    wrapper.unmount()
  })

  it('arrow keys no-op when no list is provided (timeline/map)', () => {
    ui.unlock()
    ui.openViewer(mk(9))
    const wrapper = mountHarness()

    press('ArrowRight')
    expect(ui.viewPhoto?.id).toBe(9)

    wrapper.unmount()
  })

  it('escape closes the viewer and clears the list', () => {
    ui.unlock()
    const list = [mk(1), mk(2)]
    ui.openViewer(list[0], list)
    const wrapper = mountHarness()

    press('Escape')
    expect(ui.viewPhoto).toBeNull()
    expect(ui.viewPhotos).toEqual([])

    wrapper.unmount()
  })

  it('ignores all keys while locked', () => {
    // unlocked defaults to false (localStorage cleared in beforeEach)
    ui.openViewer(mk(1), [mk(1), mk(2)])
    const wrapper = mountHarness()

    press(' ')
    expect(ui.slideshowPlaying).toBe(false)
    press('ArrowRight')
    expect(ui.viewPhoto?.id).toBe(1)
    press('Escape')
    expect(ui.viewPhoto).not.toBeNull()

    wrapper.unmount()
  })
})
