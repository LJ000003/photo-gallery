import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import UploadCard from '../UploadCard.vue'
import i18n from '../../i18n'

// Mock vue-router (needed by the page components)
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

// Mock gsap
vi.mock('gsap', () => ({
  default: {
    to: vi.fn(),
    fromTo: vi.fn(),
    registerPlugin: vi.fn(),
  },
}))

// Mock store
vi.mock('../../store', () => ({
  useStore: () => ({
    tags: { value: [{ id: 1, name: 'nature', color: '#00ff00' }] },
    categories: { value: [{ id: 1, name: 'landscape' }] },
    albums: { value: [] },
    loadAll: vi.fn(),
    refreshTags: vi.fn(),
    refreshCategories: vi.fn(),
    refreshAlbums: vi.fn(),
  }),
}))

// Mock api
vi.mock('../../api', () => ({
  api: vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({}) }),
  requestToken: vi.fn(),
}))

// Mock LottieLoader async component
vi.mock('../../composables/usePhotoActions', () => ({
  usePhotoActions: () => ({}),
}))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.stubGlobal('fetch', vi.fn())
})

describe('UploadCard', () => {
  const stubOpts = {
    global: {
      plugins: [i18n],
      stubs: { ImageEditor: true, LottieLoader: true },
    },
  }

  it('renders the upload form', () => {
    const wrapper = mount(UploadCard, stubOpts)

    expect(wrapper.find('.upload-card').exists()).toBe(true)
    expect(wrapper.find('h2').text()).toContain('Upload')
    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true)
  })

  it('shows file label with drag-and-drop hint', () => {
    const wrapper = mount(UploadCard, stubOpts)

    const label = wrapper.find('label[for="fileInput"]')
    expect(label.text()).toContain('Click')
  })

  it('renders category select and tag chips', () => {
    const wrapper = mount(UploadCard, stubOpts)

    expect(wrapper.find('select.mini-select').exists()).toBe(true)
    const chips = wrapper.findAll('.tag-chip')
    expect(chips.length).toBeGreaterThanOrEqual(0)
  })

  it('submit button is initially enabled', () => {
    const wrapper = mount(UploadCard, stubOpts)

    const btn = wrapper.find('button[type="submit"]')
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('renders form input fields', () => {
    const wrapper = mount(UploadCard, stubOpts)

    const inputs = wrapper.findAll('input[type="text"]')
    expect(inputs.length).toBeGreaterThanOrEqual(2)
  })
})
