import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ShareViewer from '../ShareViewer.vue'
import zhCN from '../../../locales/zh-CN.json'

const { apiMock } = vi.hoisted(() => ({ apiMock: vi.fn() }))
vi.mock('../../../api', () => ({
  api: apiMock,
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { token: 'test-token' } }) }))

function okPage() {
  return {
    data: {
      content: [{ id: 1, name: 'p1', fileSize: 1024 }],
      last: true,
      totalPages: 1,
      totalElements: 1,
      size: 20,
      number: 0,
    },
  }
}

function mountViewer() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(ShareViewer, {
    global: {
      plugins: [i18n],
      stubs: { PhotoGrid: true, GridSkeleton: true, EmptyState: true },
    },
  })
}

describe('ShareViewer', () => {
  beforeEach(() => {
    apiMock.mockReset()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('挂载后立即请求分享内容（loading 初始 true 不拦截首次请求）', async () => {
    apiMock.mockResolvedValue({ json: async () => okPage() })
    mountViewer()
    await flushPromises()
    // 回归保护：曾因 load() 内 if (loading.value || ...) return 拦截首次挂载，
    // /api/v1/share/view 从未发出，页面卡死骨架屏
    expect(apiMock).toHaveBeenCalledWith(
      '/api/share/view?page=0&size=20',
      expect.objectContaining({ token: 'test-token', skipAuth: true }),
    )
  })

  it('成功响应填充照片列表并结束加载', async () => {
    apiMock.mockResolvedValue({ json: async () => okPage() })
    const wrapper = mountViewer()
    await flushPromises()
    expect(wrapper.find('.share-page').exists()).toBe(true)
    expect(apiMock).toHaveBeenCalledTimes(1)
  })
})
