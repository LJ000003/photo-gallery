import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { ref } from 'vue'
import BatchEditModal from '../BatchEditModal.vue'
import i18n from '../../i18n'

// gsap mock that actually invokes onComplete callbacks so emits fire
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

vi.mock('../../stores/data', () => ({
  useDataStore: () => ({
    tags: ref([
      { id: 1, name: 'nature', color: '#00ff00' },
      { id: 2, name: 'urban', color: '#ff0000' },
    ]),
    categories: ref([
      { id: 1, name: 'landscape' },
      { id: 2, name: 'portrait' },
    ]),
    albums: ref([{ id: 1, name: 'vacation' }]),
    loadAll: vi.fn(),
    refreshTags: vi.fn(),
    refreshCategories: vi.fn(),
    refreshAlbums: vi.fn(),
  }),
}))

const mockToastError = vi.fn()

vi.mock('../../stores/toast', () => ({
  useToastStore: () => ({
    success: vi.fn(),
    error: mockToastError,
    info: vi.fn(),
  }),
}))

const mockApi = vi.fn()
vi.mock('../../api', () => ({
  api: (...args: unknown[]) => mockApi(...args),
  requestToken: vi.fn(),
}))

const mockPhoto = (id: number, tags: { id: number; name: string; color: string }[] = []) => ({
  id,
  name: `p${id}`,
  description: '',
  fileSize: 100,
  tags,
  albums: [],
})

const twoPhotos = [mockPhoto(1), mockPhoto(2)]

const updatedPhotos = [
  {
    id: 1,
    name: 'p1',
    description: '',
    fileSize: 100,
    tags: [{ id: 1, name: 'nature', color: '#00ff00' }],
  },
]

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mockApi.mockResolvedValue({
    ok: true,
    json: () => Promise.resolve({ code: 200, data: updatedPhotos }),
  })
})

describe('BatchEditModal', () => {
  function mountModal(photos = twoPhotos) {
    return mount(BatchEditModal, {
      props: { photos },
      global: { plugins: [i18n] },
    })
  }

  /** 提交表单并返回最后一次请求的 body */
  async function submit(wrapper: ReturnType<typeof mountModal>) {
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    const calls = mockApi.mock.calls
    const call = calls[calls.length - 1]
    return call ? JSON.parse(String((call[1] as { body: string }).body)) : null
  }

  it('renders title with photo count', () => {
    const wrapper = mountModal()
    expect(wrapper.find('h2').text()).toContain('Batch Edit Photos')
    expect(wrapper.find('h2').text()).toContain('(2)')
  })

  it('emits close when clicking close button', async () => {
    const wrapper = mountModal()
    await wrapper.find('.modal-close').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('clicking an add-tag chip puts the tag into addTagIds payload', async () => {
    const wrapper = mountModal()
    const addTagChips = wrapper.findAll('.tag-chips')[0].findAll('.tag-chip')
    await addTagChips[0].trigger('click')

    const body = await submit(wrapper)
    expect(body.addTagIds).toEqual([1])
    expect(body.removeTagIds).toEqual([])
  })

  it('add/remove chips are mutually exclusive', async () => {
    const wrapper = mountModal()
    const addTagChips = wrapper.findAll('.tag-chips')[0].findAll('.tag-chip')
    const removeTagChips = wrapper.findAll('.tag-chips')[1].findAll('.tag-chip')
    await addTagChips[0].trigger('click')
    await removeTagChips[0].trigger('click')

    const body = await submit(wrapper)
    expect(body.addTagIds).toEqual([])
    expect(body.removeTagIds).toEqual([1])
  })

  it('category defaults to NONE, CLEAR when 清除分类 selected', async () => {
    const wrapper = mountModal()
    const select = wrapper.find('select')

    let body = await submit(wrapper)
    expect(body.categoryOp).toBe('NONE')

    await select.setValue('clear')
    body = await submit(wrapper)
    expect(body.categoryOp).toBe('CLEAR')
    expect(body.categoryId).toBeNull()
  })

  it('category SET carries the categoryId', async () => {
    const wrapper = mountModal()
    const select = wrapper.find('select')
    await select.setValue('2')

    const body = await submit(wrapper)
    expect(body.categoryOp).toBe('SET')
    expect(body.categoryId).toBe(2)
  })

  it('inline-created tag lands in addTagIds', async () => {
    mockApi.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { id: 99, name: 'new', color: '#fff' } }),
    })
    const wrapper = mountModal()
    const newTagInput = wrapper.find('input[placeholder="New tag"]')
    await newTagInput.setValue('new')
    await newTagInput.trigger('keyup.enter')
    await flushPromises()

    expect(mockApi).toHaveBeenCalledWith('/api/tags', expect.objectContaining({ method: 'POST' }))
    const body = await submit(wrapper)
    expect(body.addTagIds).toContain(99)
  })

  it('emits saved with updated photos on success', async () => {
    const wrapper = mountModal()
    await submit(wrapper)
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect((wrapper.emitted('saved')![0] as unknown[])[0]).toEqual(updatedPhotos)
  })

  it('blocks submission and toasts when more than 50 photos', async () => {
    const manyPhotos = Array.from({ length: 51 }, (_, i) => mockPhoto(i + 1))
    const wrapper = mountModal(manyPhotos)
    await submit(wrapper)

    expect(mockToastError).toHaveBeenCalledWith('Maximum 50 photos per operation')
    expect(mockApi).not.toHaveBeenCalled()
  })
})
