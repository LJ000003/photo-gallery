import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createTestingPinia } from '@pinia/testing'
import GalleryPage from '../GalleryPage.vue'
import { nextTick } from 'vue'

const mockReplace = vi.fn()
const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    replace: mockReplace,
    push: mockPush,
  }),
  useRoute: () => ({
    query: {},
    path: '/',
  }),
}))

vi.mock('gsap', () => ({
  default: {
    to: vi.fn(),
    fromTo: vi.fn(),
    registerPlugin: vi.fn(),
  },
  ScrollToPlugin: {},
}))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('GalleryPage', () => {
  function createWrapper() {
    return mount(GalleryPage, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              photo: {
                photos: [],
                page: 0,
                hasMore: false,
                loading: false,
                totalCount: 0,
                sortBy: 'time',
                sortOrder: 'asc',
                selectedTagIds: [],
                selectedCategoryIds: [],
                selectedPhotoIds: new Set(),
                searchQuery: '',
              },
            },
          }),
        ],
        stubs: {
          PhotoCard: {
            template: '<div class="photo-card-stub">{{ photo?.name }}</div>',
            props: ['photo', 'selected', 'searchQuery', 'dataInsert'],
          },
          UploadCard: { template: '<div class="upload-card-stub"><h2>上传照片</h2></div>' },
          LottieLoader: { template: '<div class="lottie-stub"></div>' },
        },
      },
    })
  }

  it('renders gallery section header', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.gallery-section').exists()).toBe(true)
    expect(wrapper.find('h2').text()).toContain('我的照片')
  })

  it('renders search input', () => {
    const wrapper = createWrapper()
    const searchInput = wrapper.find('.search-input')
    expect(searchInput.exists()).toBe(true)
    expect(searchInput.attributes('placeholder')).toContain('搜索')
  })

  it('renders empty state when no photos', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.empty-state').exists()).toBe(true)
  })

  it('renders view switch links', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.view-switch').exists()).toBe(true)
  })

  it('renders sort buttons', () => {
    const wrapper = createWrapper()
    const sortOpts = wrapper.findAll('.sort-opt')
    expect(sortOpts.length).toBe(3) // time, name, size
  })

  it('renders UploadCard component', () => {
    const wrapper = createWrapper()
    const uc = wrapper.find('.upload-card-stub')
    expect(uc.exists()).toBe(true)
  })

  it('shows skeleton cards when loading with no photos', () => {
    const wrapper = mount(GalleryPage, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              photo: {
                photos: [],
                page: 0,
                hasMore: true,
                loading: true,
                totalCount: 0,
                sortBy: 'time',
                sortOrder: 'asc',
                selectedTagIds: [],
                selectedCategoryIds: [],
                selectedPhotoIds: new Set(),
                searchQuery: '',
              },
            },
          }),
        ],
        stubs: {
          PhotoCard: true,
          UploadCard: true,
          LottieLoader: true,
        },
      },
    })

    expect(wrapper.findAll('.skeleton-card').length).toBe(6)
  })

  it('renders photo cards when photos exist', () => {
    const testPhotos = [
      { id: 1, name: 'p1', description: '', fileSize: 100 },
      { id: 2, name: 'p2', description: '', fileSize: 200 },
    ]

    const wrapper = mount(GalleryPage, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              photo: {
                photos: testPhotos,
                page: 1,
                hasMore: false,
                loading: false,
                totalCount: 2,
                sortBy: 'time',
                sortOrder: 'asc',
                selectedTagIds: [],
                selectedCategoryIds: [],
                selectedPhotoIds: new Set(),
                searchQuery: '',
              },
            },
          }),
        ],
        stubs: {
          PhotoCard: {
            template: '<div class="photo-card-stub">{{ photo.name }}</div>',
            props: ['photo', 'selected', 'searchQuery', 'dataInsert'],
          },
          UploadCard: true,
          LottieLoader: true,
        },
      },
    })

    expect(wrapper.findAll('.photo-card-stub').length).toBe(2)
  })
})
