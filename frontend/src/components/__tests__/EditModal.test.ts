import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import EditModal from '../EditModal.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

// gsap mock that actually invokes onComplete callbacks so emits fire
vi.mock('gsap', () => ({
  default: {
    to(_target: unknown, vars: { onComplete?: () => void }) {
      // Fire onComplete synchronously so emits work in tests
      if (vars?.onComplete) vars.onComplete()
      return {}
    },
    fromTo: vi.fn(),
    registerPlugin: vi.fn(),
  },
}))

vi.mock('../../store', () => ({
  useStore: () => ({
    tags: {
      value: [
        { id: 1, name: 'nature', color: '#00ff00' },
        { id: 2, name: 'urban', color: '#ff0000' },
      ],
    },
    categories: {
      value: [
        { id: 1, name: 'landscape' },
        { id: 2, name: 'portrait' },
      ],
    },
    albums: { value: [{ id: 1, name: 'vacation' }] },
    loadAll: vi.fn(),
    refreshTags: vi.fn(),
    refreshCategories: vi.fn(),
    refreshAlbums: vi.fn(),
  }),
}))

vi.mock('../../api', () => ({
  api: vi
    .fn()
    .mockResolvedValue({ ok: true, json: () => Promise.resolve({ data: {}, code: 200 }) }),
  requestToken: vi.fn(),
}))

const mockPhoto = {
  id: 1,
  name: 'Sunset Beach',
  description: 'A beautiful sunset',
  fileSize: 204800,
  tags: [{ id: 1, name: 'nature', color: '#00ff00' }],
  category: { id: 1, name: 'landscape' },
  albums: [{ id: 1, name: 'vacation', description: '', createdAt: '2024-01-01', photoCount: 5 }],
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('EditModal', () => {
  it('renders form with photo name pre-filled', async () => {
    const wrapper = mount(EditModal, {
      props: { photo: mockPhoto },
    })

    // onMounted is async — wait for it
    await flushPromises()
    await wrapper.vm.$nextTick()

    const nameInput = wrapper.find('input[required]')
    expect((nameInput.element as HTMLInputElement).value).toBe('Sunset Beach')
  })

  it('renders edit title', () => {
    const wrapper = mount(EditModal, {
      props: { photo: mockPhoto },
    })
    expect(wrapper.find('h2').text()).toBe('编辑照片信息')
  })

  it('renders submit button', () => {
    const wrapper = mount(EditModal, {
      props: { photo: mockPhoto },
    })
    const btn = wrapper.find('button[type="submit"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toBe('保存')
  })

  it('emits close when clicking close button', async () => {
    const wrapper = mount(EditModal, {
      props: { photo: mockPhoto },
    })

    const closeBtn = wrapper.find('.modal-close')
    await closeBtn.trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits saved on successful form submit', async () => {
    const wrapper = mount(EditModal, {
      props: { photo: mockPhoto },
    })

    // Wait for onMounted to populate the form
    await flushPromises()
    await wrapper.vm.$nextTick()

    // Fill required name
    const nameInput = wrapper.find('input[required]')
    await nameInput.setValue('Updated Name')

    // Submit form
    await wrapper.find('form').trigger('submit.prevent')

    // Wait for async handlers
    await flushPromises()

    expect(wrapper.emitted('saved')).toBeTruthy()
  })
})
