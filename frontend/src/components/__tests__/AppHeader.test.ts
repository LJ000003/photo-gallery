import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import i18n from '../../i18n'
import AppHeader from '../AppHeader.vue'

function mountHeader() {
  return mount(AppHeader, { global: { plugins: [i18n] } })
}

// vi.hoisted：mock 模块被提升执行，只能引用 hoisted 变量
const { toastMock, apiMock } = vi.hoisted(() => ({
  toastMock: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
  apiMock: vi.fn(),
}))

// 注意路径：测试在 src/components/__tests__/ 下，需 ../../ 才能解析到 src/api.ts
vi.mock('../../api', () => ({ api: apiMock, requestToken: vi.fn() }))
vi.mock('../../stores/toast', () => ({ useToastStore: () => toastMock }))

function mockResponse(filename = 'photo-gallery-backup-2026-08-02.tar.gz') {
  return {
    ok: true,
    headers: {
      get: (name: string) =>
        name === 'Content-Disposition' ? `attachment; filename="${filename}"` : null,
    },
    blob: () => Promise.resolve(new Blob(['test'])),
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // happy-dom 无 URL.createObjectURL，stub 之
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => 'blob:mock'),
    revokeObjectURL: vi.fn(),
  })
})

describe('AppHeader', () => {
  it('renders export button next to help button', () => {
    const wrapper = mountHeader()
    expect(wrapper.find('.export-btn').exists()).toBe(true)
    expect(wrapper.find('.help-btn').exists()).toBe(true)
  })

  it('exportBackup triggers download with server filename', async () => {
    apiMock.mockResolvedValue(mockResponse())
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = mountHeader()

    await wrapper.find('.export-btn').trigger('click')
    await flushPromises()

    expect(apiMock).toHaveBeenCalledWith('/api/backup/export', {
      method: 'POST',
      body: JSON.stringify({}),
    })
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(toastMock.success).toHaveBeenCalled()
  })

  it('exportBackup falls back to default filename without Content-Disposition', async () => {
    apiMock.mockResolvedValue(mockResponse(null as unknown as string))
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = mountHeader()

    await wrapper.find('.export-btn').trigger('click')
    await flushPromises()

    expect(clickSpy).toHaveBeenCalledOnce()
    expect(toastMock.success).toHaveBeenCalled()
  })

  it('exportBackup failure shows error toast', async () => {
    apiMock.mockRejectedValue(new Error('导出失败'))
    const wrapper = mountHeader()

    await wrapper.find('.export-btn').trigger('click')
    await flushPromises()

    expect(toastMock.error).toHaveBeenCalled()
    expect(toastMock.success).not.toHaveBeenCalled()
  })

  it('prevents double export while pending', async () => {
    let resolve!: (v: unknown) => void
    apiMock.mockReturnValue(new Promise((r) => (resolve = r)))
    const wrapper = mountHeader()

    await wrapper.find('.export-btn').trigger('click')
    await wrapper.find('.export-btn').trigger('click')
    resolve(mockResponse())
    await flushPromises()

    expect(apiMock).toHaveBeenCalledTimes(1)
  })
})
