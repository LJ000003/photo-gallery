import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import KonamiGate from '../KonamiGate.vue'
import zhCN from '../../../locales/zh-CN.json'

const CORRECT_KEYS = [
  'ArrowUp',
  'ArrowUp',
  'ArrowDown',
  'ArrowDown',
  'ArrowLeft',
  'ArrowRight',
  'ArrowLeft',
  'ArrowRight',
  'b',
  'a',
  'b',
  'a',
]

function mountGate() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(KonamiGate, {
    props: { resetTrigger: 0, successTrigger: 0 },
    global: { plugins: [i18n] },
  })
}

function pressKeys(keys: string[]): void {
  for (const k of keys) {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: k, cancelable: true }))
  }
}

describe('KonamiGate', () => {
  // KonamiGate onMounted 注册 window keydown 监听——每测后 unmount 防监听器累积
  let wrapper: ReturnType<typeof mountGate> | undefined
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
  })

  it('完整 12 键序列后 emit unlocked（参数为按键数组）', () => {
    wrapper = mountGate()
    pressKeys(CORRECT_KEYS)
    const events = wrapper.emitted('unlocked')
    expect(events).toHaveLength(1)
    // emitted 返回 emit 参数数组的数组：events[0] = [keysArray]
    expect(events![0] as unknown[]).toHaveLength(1)
    expect((events![0] as unknown[])[0]).toEqual([
      'up',
      'up',
      'down',
      'down',
      'left',
      'right',
      'left',
      'right',
      'B',
      'A',
      'B',
      'A',
    ])
  })

  it('非映射键被忽略，不打断序列计数', () => {
    wrapper = mountGate()
    // 中间夹杂未映射按键（x / 数字），不应影响 12 键计数
    pressKeys([
      'x',
      'ArrowUp',
      'ArrowUp',
      '1',
      'ArrowDown',
      'ArrowDown',
      'ArrowLeft',
      'ArrowRight',
      'ArrowLeft',
      'ArrowRight',
      'b',
      'a',
      'b',
      'a',
    ])
    expect(wrapper.emitted('unlocked')).toHaveLength(1)
  })

  it('未满 12 键不 emit', () => {
    wrapper = mountGate()
    pressKeys(CORRECT_KEYS.slice(0, 5))
    expect(wrapper.emitted('unlocked')).toBeUndefined()
  })

  it('successTrigger 递增后进入成功态（不再 verifying）', async () => {
    wrapper = mountGate()
    pressKeys(CORRECT_KEYS)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.arcade-title').text()).toBe('CHECKING...')

    await wrapper.setProps({ successTrigger: 1 })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.arcade-title').text()).toBe('CONGRATULATIONS')
    expect(wrapper.find('.arcade-gate').classes()).toContain('success')
  })

  it('resetTrigger 递增后清空输入并允许重新输入', async () => {
    wrapper = mountGate()
    pressKeys(CORRECT_KEYS.slice(0, 3))
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.progress-dot.filled')).toHaveLength(3)

    await wrapper.setProps({ resetTrigger: 1 })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.arcade-gate').classes()).toContain('shake')

    vi.advanceTimersByTime(600)
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.progress-dot.filled')).toHaveLength(0)

    // 清空后可重新输入并再次触发
    pressKeys(CORRECT_KEYS)
    expect(wrapper.emitted('unlocked')).toHaveLength(1)
  })
})
