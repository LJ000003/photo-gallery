/**
 * 主题单一来源（Single Source of Truth）
 *
 * 设计语言：苹果系极简。中性色阶 + 唯一强调色（宁静蓝），无装饰色。
 * 结构：此处常量同时驱动
 *   1. ant-design-vue 的 ConfigProvider theme（defaultAlgorithm / darkAlgorithm）
 *   2. 自定义照片面 UI 的 CSS 变量（applyCssVars 写入 :root 内联样式）
 * 明暗跟随系统 prefers-color-scheme，watchColorScheme 提供变更监听。
 */
import { theme as antdTheme } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

export const FONT_STACK =
  "'Inter Variable', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, " +
  "'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif"

/** 中性色 + 语义色。UI 组件零装饰阴影（Apple 法则：阴影只属于照片本身） */
const LIGHT = {
  bg: '#f5f5f7',
  surface: '#ffffff',
  surface2: '#ececf1',
  border: 'rgba(0, 0, 0, 0.08)',
  borderStrong: 'rgba(0, 0, 0, 0.16)',
  text: '#1d1d1f',
  textDim: '#6e6e73',
  accent: '#2563eb',
  accentHover: '#1d4ed8',
  accentSoft: 'rgba(37, 99, 235, 0.08)',
  danger: '#dc2626',
  dangerSoft: 'rgba(220, 38, 38, 0.08)',
  success: '#16a34a',
  successSoft: 'rgba(22, 163, 74, 0.08)',
  warning: '#b45309',
  warningSoft: 'rgba(180, 83, 9, 0.1)',
}

const DARK = {
  bg: '#101014',
  surface: '#1a1a1e',
  surface2: '#232328',
  border: 'rgba(255, 255, 255, 0.12)',
  borderStrong: 'rgba(255, 255, 255, 0.22)',
  text: '#f5f5f7',
  textDim: '#98989f',
  accent: '#3b82f6',
  accentHover: '#60a5fa',
  accentSoft: 'rgba(59, 130, 246, 0.14)',
  danger: '#f87171',
  dangerSoft: 'rgba(248, 113, 113, 0.12)',
  success: '#4ade80',
  successSoft: 'rgba(74, 222, 128, 0.12)',
  warning: '#fbbf24',
  warningSoft: 'rgba(251, 191, 36, 0.14)',
}

export type ThemePalette = typeof LIGHT

export function palette(isDark: boolean): ThemePalette {
  return isDark ? DARK : LIGHT
}

export function isSystemDark(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

/** 订阅系统明暗变化，返回取消函数 */
export function watchColorScheme(cb: (isDark: boolean) => void): () => void {
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  const handler = (e: MediaQueryListEvent) => cb(e.matches)
  mq.addEventListener('change', handler)
  return () => mq.removeEventListener('change', handler)
}

/** 自定义 CSS 变量（照片面 UI 使用；静态兜底见 styles/tokens.css，内联样式优先级最高） */
const CSS_VARS = [
  '--c-bg',
  '--c-surface',
  '--c-surface-2',
  '--c-border',
  '--c-border-strong',
  '--c-text',
  '--c-text-dim',
  '--c-accent',
  '--c-accent-hover',
  '--c-accent-soft',
  '--c-danger',
  '--c-danger-soft',
  '--c-success',
  '--c-success-soft',
  '--c-warning',
  '--c-warning-soft',
] as const

export function applyCssVars(isDark: boolean): void {
  const c = palette(isDark)
  const root = document.documentElement
  for (const name of CSS_VARS) {
    root.style.setProperty(name, c[name.replace('--c-', '') as keyof ThemePalette])
  }
  root.style.setProperty(
    '--shadow-photo',
    isDark ? '0 8px 24px rgba(0,0,0,0.45)' : '0 8px 24px rgba(0,0,0,0.12)',
  )
  root.style.colorScheme = isDark ? 'dark' : 'light'
}

export function getThemeConfig(isDark: boolean): ThemeConfig {
  const c = palette(isDark)
  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: c.accent,
      colorInfo: c.accent,
      colorLink: c.accent,
      colorLinkHover: c.accentHover,
      colorBgBase: c.bg,
      colorBgLayout: c.bg,
      colorBgContainer: c.surface,
      colorBgElevated: c.surface,
      colorBorder: c.border,
      colorBorderSecondary: c.border,
      colorText: c.text,
      colorTextSecondary: c.textDim,
      colorTextTertiary: c.textDim,
      colorError: c.danger,
      colorErrorBg: c.dangerSoft,
      colorSuccess: c.success,
      colorSuccessBg: c.successSoft,
      colorWarning: c.warning,
      colorWarningBg: c.warningSoft,
      colorFillSecondary: c.surface2,
      colorSplit: c.border,
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 6,
      fontFamily: FONT_STACK,
      fontSize: 14,
      // Apple 法则：按下按钮整体 scale(0.95)（antd 内置按压缩放）
    },
    components: {
      Button: {
        // 主行动用 pill（Apple 签名胶囊），工具按钮保持 8px
        borderRadius: 999,
        controlHeight: 36,
      },
      Input: { controlHeight: 36 },
      Select: { controlHeight: 36 },
      Modal: { borderRadiusLG: 16 },
      Drawer: { paddingLG: 24 },
      Tag: { borderRadiusSM: 6 },
    },
  }
}
