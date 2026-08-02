import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import ViewModal from '../ViewModal.vue'
import { useUiStore } from '../../stores/ui'
import i18n from '../../i18n'
import type { Photo } from '../../types/photo'

// gsap mock that invokes onComplete synchronously so the close emit fires in tests
vi.mock('gsap', () => ({
  default: {
    to(_target: unknown, vars: { onComplete?: () => void }) {
      if (vars?.onComplete) vars.onComplete()
      return {}
    },
    fromTo: vi.fn(),
    registerPlugin: vi.fn(),
  },
}))

const mk = (id: number, name = `p${id}`): Photo => ({
  id,
  name,
  description: '',
  fileSize: 100,
})

describe('ViewModal slideshow', () => {
  let ui: ReturnType<typeof useUiStore>

  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    ui = useUiStore()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function mountView(photo: Photo, list: Photo[]): VueWrapper {
    ui.openViewer(photo, list)
    return mount(ViewModal, {
      props: { photo },
      global: { plugins: [i18n] },
    })
  }

  /** In the real app MainLayout binds :photo="ui.viewPhoto", so a store
   *  navigation updates the prop. In tests the prop is static — sync it. */
  async function syncProp(wrapper: VueWrapper): Promise<void> {
    const next = ui.viewPhoto
    if (next) await wrapper.setProps({ photo: next })
    await nextTick()
  }

  async function startPlaying(wrapper: VueWrapper): Promise<void> {
    await wrapper.find('.ss-play').trigger('click')
    await nextTick() // flush watch(playing) → schedule timer
  }

  async function advanceAndFlush(ms: number, wrapper: VueWrapper): Promise<void> {
    vi.advanceTimersByTime(ms)
    await syncProp(wrapper) // flush prop change + watch(photo.id) rescheduling
  }

  it('hides slideshow bar when list is empty (timeline/map)', () => {
    const wrapper = mountView(mk(1), [])
    expect(wrapper.find('.slideshow-bar').exists()).toBe(false)
  })

  it('hides slideshow bar for a single-photo list (upload)', () => {
    const wrapper = mountView(mk(1), [mk(1)])
    expect(wrapper.find('.slideshow-bar').exists()).toBe(false)
  })

  it('hides slideshow bar when current photo is not in the list', () => {
    const wrapper = mountView(mk(9), [mk(1), mk(2)])
    expect(wrapper.find('.slideshow-bar').exists()).toBe(false)
  })

  it('shows counter and advances to the next photo after 5s of playing', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[0], list)
    expect(wrapper.find('.ss-counter').text()).toBe('1/3')

    await startPlaying(wrapper)
    await advanceAndFlush(5000, wrapper)
    expect(ui.viewPhoto?.id).toBe(2)

    // keeps advancing after each interval
    await advanceAndFlush(5000, wrapper)
    expect(ui.viewPhoto?.id).toBe(3)
  })

  it('does not advance without starting playback', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[0], list)
    await advanceAndFlush(10000, wrapper)
    expect(ui.viewPhoto?.id).toBe(1)
  })

  it('wraps around from the last photo to the first during playback', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[2], list)
    await startPlaying(wrapper)
    await advanceAndFlush(5000, wrapper)
    expect(ui.viewPhoto?.id).toBe(1)
  })

  it('pauses and resumes playback', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[0], list)
    await startPlaying(wrapper)

    await wrapper.find('.ss-play').trigger('click') // pause
    await nextTick()
    await advanceAndFlush(10000, wrapper)
    expect(ui.viewPhoto?.id).toBe(1)

    await wrapper.find('.ss-play').trigger('click') // resume
    await nextTick()
    await advanceAndFlush(5000, wrapper)
    expect(ui.viewPhoto?.id).toBe(2)
  })

  it('manual next button advances without wrapping and disables at the end', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[0], list)

    await wrapper.findAll('.ss-btn')[2].trigger('click')
    await syncProp(wrapper)
    expect(ui.viewPhoto?.id).toBe(2)

    await wrapper.findAll('.ss-btn')[2].trigger('click')
    await syncProp(wrapper)
    expect(ui.viewPhoto?.id).toBe(3)
    expect(wrapper.findAll('.ss-btn')[2].attributes('disabled')).toBeDefined()

    // clicking a boundary button is a no-op (no wrap)
    await wrapper.findAll('.ss-btn')[2].trigger('click')
    await syncProp(wrapper)
    expect(ui.viewPhoto?.id).toBe(3)
  })

  it('manual prev button disables at the start', async () => {
    const list = [mk(1), mk(2)]
    const wrapper = mountView(list[0], list)
    expect(wrapper.findAll('.ss-btn')[0].attributes('disabled')).toBeDefined()

    await wrapper.findAll('.ss-btn')[0].trigger('click')
    await syncProp(wrapper)
    expect(ui.viewPhoto?.id).toBe(1)
  })

  it('resets full-image fade-in when switching photos', async () => {
    const list = [mk(1), mk(2)]
    const wrapper = mountView(list[0], list)
    await wrapper.find('.img-full').trigger('load')
    expect(wrapper.find('.img-full').classes()).toContain('show')

    await wrapper.findAll('.ss-btn')[2].trigger('click')
    await syncProp(wrapper)
    expect(wrapper.find('.img-full').classes()).not.toContain('show')
  })

  it('stops advancing immediately on close', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[0], list)
    await startPlaying(wrapper)

    await advanceAndFlush(2500, wrapper)
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)

    await advanceAndFlush(3000, wrapper)
    expect(ui.viewPhoto?.id).toBe(1) // timer cleared in onClose

    wrapper.unmount()
  })

  it('clears the timer on unmount (e / Escape paths)', async () => {
    const list = [mk(1), mk(2), mk(3)]
    const wrapper = mountView(list[0], list)
    await startPlaying(wrapper)

    await advanceAndFlush(2500, wrapper)
    wrapper.unmount()

    await advanceAndFlush(20000, wrapper)
    expect(ui.viewPhoto?.id).toBe(1)
  })
})
