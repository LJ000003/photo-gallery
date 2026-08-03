import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ToastStack from '../ToastStack.vue'
import { useToastStore } from '../../../stores/toast'
import zhCN from '../../../locales/zh-CN.json'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
})

describe('ToastStack', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.useFakeTimers()
  })

  it('渲染 toast 队列并自动消失', async () => {
    const toast = useToastStore()
    const wrapper = mount(ToastStack, { global: { plugins: [pinia, i18n] } })
    toast.success('已保存')
    await flushPromises()
    expect(wrapper.find('.toast-item').exists()).toBe(true)
    expect(wrapper.text()).toContain('已保存')

    vi.advanceTimersByTime(3100)
    await flushPromises()
    expect(wrapper.find('.toast-item').exists()).toBe(false)
  })

  it('支持操作按钮（撤销类）', async () => {
    const toast = useToastStore()
    const onClick = vi.fn()
    const wrapper = mount(ToastStack, { global: { plugins: [pinia, i18n] } })
    toast.add('已删除', 'success', 5000, { label: '撤销', onClick })
    await flushPromises()

    const action = wrapper.find('.toast-action')
    expect(action.text()).toBe('撤销')
    await action.trigger('click')
    expect(onClick).toHaveBeenCalledOnce()
    await flushPromises()
    expect(wrapper.find('.toast-item').exists()).toBe(false)
  })

  it('语义类型渲染对应图标', async () => {
    const toast = useToastStore()
    const wrapper = mount(ToastStack, { global: { plugins: [pinia, i18n] } })
    toast.success('ok')
    toast.error('err')
    toast.info('info')
    await flushPromises()
    expect(wrapper.findAll('.toast-item')).toHaveLength(3)
    expect(wrapper.find('.toast-success').exists()).toBe(true)
    expect(wrapper.find('.toast-error').exists()).toBe(true)
    expect(wrapper.find('.toast-info').exists()).toBe(true)
  })
})
