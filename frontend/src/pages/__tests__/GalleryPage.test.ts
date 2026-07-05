import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import GalleryPage from '../GalleryPage.vue'
import i18n from '../../i18n'

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

function basePlugins(photoState: Record<string, unknown> = {}) {
  return [
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
          ...photoState,
        },
      },
    }),
    i18n,
  ]
}

describe('GalleryPage', () => {
  function createWrapper(photoState?: Record<string, unknown>) {
    return mount(GalleryPage, {
      global: {
        plugins: basePlugins(photoState),
        stubs: {
          PhotoCard: {
            template: '<div class="photo-card-stub">{{ photo?.name }}</div>',
            props: ['photo', 'selected', 'searchQuery', 'dataInsert'],
          },
          UploadCard: { template: '<div class="upload-card-stub"></div>' },
          LottieLoader: { template: '<div class="lottie-stub"></div>' },
        },
      },
    })
  }

  it('renders gallery section header', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.gallery-section').exists()).toBe(true)
    expect(wrapper.find('h2').text()).toContain('My Photos')
  })

  it('renders search input', () => {
    const wrapper = createWrapper()
    const searchInput = wrapper.find('.search-input')
    expect(searchInput.exists()).toBe(true)
    expect(searchInput.attributes('placeholder')).toContain('Search')
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
    expect(sortOpts.length).toBe(3)
  })

  it('renders UploadCard component', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.upload-card-stub').exists()).toBe(true)
  })

  it('shows skeleton cards when loading with no photos', () => {
    const wrapper = createWrapper({ loading: true, hasMore: true })
    expect(wrapper.findAll('.skeleton-card').length).toBe(6)
  })

  it('renders photo cards when photos exist', () => {
    const testPhotos = [
      { id: 1, name: 'p1', description: '', fileSize: 100 },
      { id: 2, name: 'p2', description: '', fileSize: 200 },
    ]

    const wrapper = mount(GalleryPage, {
      global: {
        plugins: basePlugins({ photos: testPhotos, totalCount: 2, hasMore: false, loading: false }),
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
