import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import PhotoTile from '../PhotoTile.vue'
import zhCN from '../../../locales/zh-CN.json'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
})

const mkPhoto = (over: Partial<Record<string, unknown>> = {}) => ({
  id: 1,
  name: '海边日落',
  description: '',
  fileSize: 1024 * 1024,
  processingStatus: 'DONE',
  ...over,
})

function mountTile(props: Record<string, unknown> = {}) {
  return mount(PhotoTile, {
    props: { photo: mkPhoto(), ...props },
    global: { plugins: [createPinia(), i18n] },
  })
}

describe('PhotoTile', () => {
  it('渲染照片名称与缩略图 URL（带 token 参数）', () => {
    localStorage.setItem('jwt_token', 'test-token')
    const wrapper = mountTile()
    const img = wrapper.find('img.tile-img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toContain('/api/v1/photos/1/thumbnail')
    expect(img.attributes('src')).toContain('token=test-token')
    expect(img.attributes('alt')).toBe('海边日落')
    localStorage.clear()
  })

  it('点击图片区域触发 view 事件', async () => {
    const wrapper = mountTile()
    await wrapper.find('.tile-photo').trigger('click')
    const emitted = wrapper.emitted('view')
    expect(emitted).toBeTruthy()
    expect((emitted![0][0] as { id: number }).id).toBe(1)
  })

  it('选择圈点击触发 toggleSelect', async () => {
    const wrapper = mountTile()
    await wrapper.find('.check-bubble').trigger('click')
    const emitted = wrapper.emitted('toggleSelect')
    expect(emitted).toBeTruthy()
    expect(emitted![0][0] as number).toBe(1)
  })

  it('selectable=false 时不渲染选择圈', () => {
    const wrapper = mountTile({ selectable: false })
    expect(wrapper.find('.check-bubble').exists()).toBe(false)
  })

  it('处理中状态显示遮罩', () => {
    const wrapper = mountTile({ photo: mkPhoto({ processingStatus: 'PROCESSING' }) })
    expect(wrapper.find('.tile-status.processing').exists()).toBe(true)
  })

  it('处理失败状态显示重试按钮', () => {
    const wrapper = mountTile({ photo: mkPhoto({ processingStatus: 'FAILED', errorMessage: '超时' }) })
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
    expect(wrapper.find('.warn-text').text()).toContain('超时')
  })

  it('搜索模式下显示名称条且高亮命中词', () => {
    const wrapper = mountTile({ searchQuery: '日落' })
    const caption = wrapper.find('.tile-caption')
    expect(caption.exists()).toBe(true)
    expect(caption.find('mark.search-hl').text()).toBe('日落')
  })
})
