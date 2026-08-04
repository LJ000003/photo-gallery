import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import UploadDrawer from '../UploadDrawer.vue'
import { useToastStore } from '../../../stores/toast'
import zhCN from '../../../locales/zh-CN.json'
import type { Photo } from '../../../types/photo'

// 绕开 canvas 压缩与真实 XHR
vi.mock('../../../upload', () => ({
  compressImages: vi.fn(async (files: File[]) => files),
  uploadWithProgress: vi.fn(),
}))

const { compressImages, uploadWithProgress } = vi.mocked(await import('../../../upload'))

function mountDrawer() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  const wrapper = mount(UploadDrawer, {
    props: { open: true },
    global: {
      plugins: [pinia, i18n],
      stubs: {
        // antd 组件显式 name 带 A 前缀（ADrawer），vue-test-utils 按组件 name 匹配 stub，
        // 只给 'Drawer' 不生效 → 真实 Drawer teleport 到 body，wrapper.find 找不到内容
        Drawer: { template: '<div class="drawer-stub"><slot /></div>' },
        ADrawer: { template: '<div class="drawer-stub"><slot /></div>' },
        ImageEditor: true,
        Input: { template: '<input class="input-stub" />' },
        Select: { template: '<div class="select-stub" />' },
        Progress: { template: '<div class="progress-stub" />' },
        Spin: { template: '<div class="spin-stub" />' },
        Button: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      },
    },
  })
  return wrapper
}

function setInputFiles(wrapper: ReturnType<typeof mountDrawer>, files: File[]) {
  const input = wrapper.find('.file-input').element as HTMLInputElement
  Object.defineProperty(input, 'files', {
    value: files,
    configurable: true,
  })
  return input
}

/** happy-dom 无 blob: 支持，且替换整个 URL 类会破坏其选择器解析——只按需 stub 两个方法 */
function stubObjectURL(): void {
  vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:mock'), revokeObjectURL: vi.fn() })
}

function okResponse(data: unknown, status = 200) {
  return {
    ok: status < 400,
    status,
    data: data as { code: number; message: string; data: Photo },
  }
}

describe('UploadDrawer', () => {
  beforeEach(() => {
    compressImages.mockClear()
    uploadWithProgress.mockClear()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('选择文件后显示选中数量并触发压缩', async () => {
    stubObjectURL()
    const wrapper = mountDrawer()
    const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' })
    setInputFiles(wrapper, [file])

    await wrapper.find('.file-input').trigger('change')
    await vi.waitFor(() => {
      expect(compressImages).toHaveBeenCalledTimes(1)
    })
    expect(wrapper.text()).toContain('已选 1 张')
  })

  it('超过 50 张被拒绝并提示', async () => {
    stubObjectURL()
    const wrapper = mountDrawer()
    const toast = useToastStore()
    const files = Array.from(
      { length: 51 },
      (_, i) => new File(['x'], `${i}.jpg`, { type: 'image/jpeg' }),
    )
    setInputFiles(wrapper, files)

    await wrapper.find('.file-input').trigger('change')
    expect(toast.toasts.some((t) => t.message.includes('一次最多上传 50 张'))).toBe(true)
  })

  it('提交成功 → emit uploaded 并关闭抽屉', async () => {
    stubObjectURL()
    uploadWithProgress.mockResolvedValue(okResponse({ code: 200, data: [{ id: 1 } as Photo] }))
    const wrapper = mountDrawer()
    const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' })
    setInputFiles(wrapper, [file])
    await wrapper.find('.file-input').trigger('change')
    await vi.waitFor(() => {
      expect(compressImages).toHaveBeenCalled()
    })

    await wrapper.find('.submit-btn').trigger('click')
    await vi.waitFor(() => {
      expect(uploadWithProgress).toHaveBeenCalled()
    })
    expect(wrapper.emitted('uploaded')).toHaveLength(1)
    expect(wrapper.emitted('update:open')).toEqual([[false]])
  })

  it('409 查重 → 展示「查看已有」toast 且不 emit uploaded', async () => {
    stubObjectURL()
    uploadWithProgress.mockResolvedValue(
      okResponse({ code: 409, message: '照片已存在', data: { id: 9 } as Photo }, 409),
    )
    const wrapper = mountDrawer()
    const toast = useToastStore()
    const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' })
    setInputFiles(wrapper, [file])
    await wrapper.find('.file-input').trigger('change')
    await vi.waitFor(() => {
      expect(compressImages).toHaveBeenCalled()
    })

    await wrapper.find('.submit-btn').trigger('click')
    await vi.waitFor(() => {
      expect(toast.toasts.some((t) => t.message.includes('照片已存在'))).toBe(true)
    })
    expect(wrapper.emitted('uploaded')).toBeUndefined()
  })
})
