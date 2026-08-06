import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import PhotoEditDrawer from '../PhotoEditDrawer.vue'
import { useToastStore } from '../../../stores/toast'
import zhCN from '../../../locales/zh-CN.json'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
})

const mkPhoto = () => ({
  id: 1,
  name: '海边日落',
  description: '',
  fileSize: 1024,
  createdAt: '2026-01-01T00:00:00',
  processingStatus: 'DONE',
  tags: [],
  albums: [],
})

/**
 * fetch 桩：GET（onMounted 的 loadAll 拉标签/分类/相册）返回空列表成功；
 * POST（行内新建）返回 400 失败——api() 对非 2xx 直接 throw ApiError（修复点）。
 */
function stubFetchFail(): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const isGet = !init || init.method === undefined || init.method === 'GET'
      return isGet
        ? {
            ok: true,
            status: 200,
            headers: new Headers({ 'X-Trace-Id': 'test' }),
            json: async () => ({ code: 200, data: [], message: 'ok' }),
          }
        : {
            ok: false,
            status: 400,
            headers: new Headers({ 'X-Trace-Id': 'test' }),
            json: async () => ({ code: 400, message: '标签名称不能为空', data: null }),
          }
    }),
  )
}

function mountDrawer() {
  setActivePinia(createPinia())
  return mount(PhotoEditDrawer, {
    props: { photo: mkPhoto() },
    global: { plugins: [createPinia(), i18n] },
  })
}

/** antd Drawer 内容 teleport 到 body（v6.5 记录：真实 Drawer 而非 stub），从 document 查询 */
function findInBody<T extends Element>(sel: string): T {
  const el = document.body.querySelector<T>(sel)
  if (!el) throw new Error('not found in body: ' + sel)
  return el
}

describe('PhotoEditDrawer 行内新建失败提示', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('addTag 失败时 toast.error 展示后端错误消息，不再静默', async () => {
    stubFetchFail()
    mountDrawer()
    const toast = useToastStore()

    const tagInput = findInBody<HTMLInputElement>('input[placeholder="新建标签"]')
    tagInput.value = '新标签'
    tagInput.dispatchEvent(new Event('input', { bubbles: true }))

    findInBody<HTMLButtonElement>('button[aria-label="新建标签"]').click()

    await vi.waitFor(() => {
      expect(toast.toasts.some((t) => t.type === 'error')).toBe(true)
    })
    expect(toast.toasts[toast.toasts.length - 1].message).toBe('标签名称不能为空')
  })

  it('addCat 失败时 toast.error 展示后端错误消息', async () => {
    stubFetchFail()
    mountDrawer()
    const toast = useToastStore()

    const catInput = findInBody<HTMLInputElement>('input[placeholder="新建分类"]')
    catInput.value = '新分类'
    catInput.dispatchEvent(new Event('input', { bubbles: true }))

    findInBody<HTMLButtonElement>('button[aria-label="新建分类"]').click()

    await vi.waitFor(() => {
      expect(toast.toasts.some((t) => t.type === 'error')).toBe(true)
    })
  })
})
