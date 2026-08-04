import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { logError } from './utils/logger'

// 字体（自托管，font-display: swap 由 fontsource 默认提供）
import '@fontsource-variable/inter'
// 样式：令牌兜底层 + 基础层（主题变量由 theme.ts 运行时注入）
import './styles/tokens.css'
import './styles/base.css'

// 主题：首帧前同步系统明暗（避免闪烁），后续变化由 App.vue 监听
import { applyCssVars, isSystemDark } from './theme'
applyCssVars(isSystemDark())

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)

/**
 * 致命错误兜底页 —— CSP 兼容版本：
 * 原实现 innerHTML 拼未转义 msg + 内联 onclick（script-src 'self' 下失效 + HTML 注入向量）。
 * 改用 DOM API：textContent 天然转义，事件用 addEventListener 绑定。
 */
function renderFatalError(title: string, msg: string, reloadLabel: string): void {
  const root = document.getElementById('app')
  if (!root || root.textContent?.trim()) return
  root.innerHTML = ''
  const wrap = document.createElement('div')
  wrap.style.cssText =
    'display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;' +
    'background:var(--c-bg,#f5f5f7);color:var(--c-text,#1d1d1f);' +
    'font-family:var(--font-sans,system-ui,sans-serif);text-align:center'
  const box = document.createElement('div')
  const h2 = document.createElement('h2')
  h2.style.cssText = 'margin:0 0 8px;font-weight:600'
  h2.textContent = title
  const p = document.createElement('p')
  p.style.cssText = 'color:var(--c-text-dim,#6e6e73);margin:0 0 20px;font-size:14px'
  p.textContent = msg
  const btn = document.createElement('button')
  btn.style.cssText =
    'padding:9px 24px;border-radius:999px;background:var(--c-accent,#2563eb);color:#fff;' +
    'border:none;cursor:pointer;font-size:14px'
  btn.textContent = reloadLabel
  btn.addEventListener('click', () => window.location.reload())
  box.append(h2, p, btn)
  wrap.append(box)
  root.append(wrap)
}

// 全局兜底：捕获所有未被 onErrorCaptured 拦截的错误
app.config.errorHandler = (err, _instance, info) => {
  logError(err, `GlobalErrorHandler ${info}`)
  const msg = err instanceof Error ? err.message : String(err)
  renderFatalError(i18n.global.t('common.errorTitle'), msg, i18n.global.t('common.errorReload'))
}

// 路由导航异常处理
router.onError((err) => {
  logError(err, 'RouterError')
  const msg = err instanceof Error ? err.message : String(err)
  renderFatalError(
    i18n.global.t('common.errorPageFailed'),
    msg,
    i18n.global.t('common.errorReload'),
  )
})

app.mount('#app')

// PWA Service Worker（生产环境注册）
if ('serviceWorker' in navigator) {
  import('virtual:pwa-register').then(({ registerSW }) => {
    registerSW({ immediate: true })
  })
}
