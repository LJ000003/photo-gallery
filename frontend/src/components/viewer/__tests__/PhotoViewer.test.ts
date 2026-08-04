import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import PhotoViewer from '../PhotoViewer.vue'
import { useUiStore } from '../../../stores/ui'
import zhCN from '../../../locales/zh-CN.json'
import type { Photo } from '../../../types/photo'

// vi.hoisted：vi.mock 工厂被提升到文件顶部，普通顶层变量此时未初始化，必须用 hoisted 包住
const { confirmMock } = vi.hoisted(() => ({ confirmMock: vi.fn() }))

// 避免真实 deletePhoto 走网络
vi.mock('../../../composables/usePhotoActions', () => ({
  usePhotoActions: () => ({ deletePhoto: vi.fn(), generateShare: vi.fn() }),
}))

// antd Modal.confirm 捕获（删除确认按钮的 onOk）
vi.mock('ant-design-vue', () => ({
  Modal: { confirm: confirmMock },
  Button: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
}))

function mkPhoto(id: number, name = `photo-${id}`): Photo {
  return { id, name, fileSize: 1024 } as Photo
}

function mountViewer(photo: Photo, list: Photo[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  const ui = useUiStore()
  ui.openViewer(photo, list)
  return mount(PhotoViewer, {
    props: { photo },
    global: {
      plugins: [pinia, i18n],
      stubs: {
        ViewerStage: {
          template:
            '<div class="vs-stub" @click="$emit(\'close\')">' +
            '<button class="vs-prev" @click="$emit(\'prev\')">prev</button>' +
            '<button class="vs-next" @click="$emit(\'next\')">next</button></div>',
          emits: ['close', 'prev', 'next', 'loaded'],
        },
        ViewerBottom: {
          template: '<div class="vb-stub"></div>',
          emits: [
            'navigate-prev',
            'navigate-next',
            'toggle-slideshow',
            'toggle-exif',
            'toggle-fullscreen',
          ],
        },
        ExifPanel: true,
      },
    },
  })
}

describe('PhotoViewer', () => {
  // useViewerControls 在 onMounted 注册 window keydown 监听——每测后必须 unmount，
  // 否则监听器在测试间累积，后续 dispatch 会触发所有未卸载实例的 handler
  let wrapper: ReturnType<typeof mountViewer> | undefined
  beforeEach(() => {
    confirmMock.mockReset()
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
  })

  it('渲染照片名称', () => {
    wrapper = mountViewer(mkPhoto(1), [mkPhoto(1)])
    expect(wrapper.find('.viewer-name').text()).toContain('photo-1')
  })

  it('点击下一张按钮 → ui.navigateViewer 前进', async () => {
    wrapper = mountViewer(mkPhoto(1), [mkPhoto(1), mkPhoto(2)])
    const ui = useUiStore()
    await wrapper.find('.vs-next').trigger('click')
    expect(ui.viewPhoto?.id).toBe(2)
  })

  it('点击上一张按钮 → ui.navigateViewer 后退', async () => {
    wrapper = mountViewer(mkPhoto(2), [mkPhoto(1), mkPhoto(2)])
    const ui = useUiStore()
    await wrapper.find('.vs-prev').trigger('click')
    expect(ui.viewPhoto?.id).toBe(1)
  })

  it('舞台点击（close emit）→ emit close', async () => {
    wrapper = mountViewer(mkPhoto(1), [mkPhoto(1)])
    await wrapper.find('.vs-stub').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('Esc（非全屏）→ emit close', async () => {
    wrapper = mountViewer(mkPhoto(1), [mkPhoto(1)])
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('删除按钮 → Modal.confirm 确认后调 ui.removeViewerPhoto + deletePhoto', async () => {
    wrapper = mountViewer(mkPhoto(1), [mkPhoto(1), mkPhoto(2)])
    const ui = useUiStore()
    confirmMock.mockImplementation((opts: { onOk?: () => void }) => opts.onOk?.())

    await wrapper.find('.danger').trigger('click')
    expect(confirmMock).toHaveBeenCalled()
    expect(ui.viewPhotos.some((p) => p.id === 1)).toBe(false)
  })

  it('方向键左右切换照片', async () => {
    wrapper = mountViewer(mkPhoto(1), [mkPhoto(1), mkPhoto(2)])
    const ui = useUiStore()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    expect(ui.viewPhoto?.id).toBe(2)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }))
    expect(ui.viewPhoto?.id).toBe(1)
  })
})
