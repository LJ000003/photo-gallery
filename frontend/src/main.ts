import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'

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

// 全局兜底：捕获所有未被 onErrorCaptured 拦截的错误
app.config.errorHandler = (err, _instance, info) => {
  console.error(`[GlobalErrorHandler] ${info}:`, err)
  const msg = err instanceof Error ? err.message : String(err)
  const root = document.getElementById('app')
  if (root && !root.textContent?.trim()) {
    const title = i18n.global.t('common.errorTitle')
    const reload = i18n.global.t('common.errorReload')
    root.innerHTML = `<div style="display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;background:var(--c-bg,#f5f5f7);color:var(--c-text,#1d1d1f);font-family:var(--font-sans,system-ui,sans-serif);text-align:center"><div><h2 style="margin:0 0 8px;font-weight:600">${title}</h2><p style="color:var(--c-text-dim,#6e6e73);margin:0 0 20px;font-size:14px">${msg}</p><button onclick="location.reload()" style="padding:9px 24px;border-radius:999px;background:var(--c-accent,#2563eb);color:#fff;border:none;cursor:pointer;font-size:14px">${reload}</button></div></div>`
  }
}

// 路由导航异常处理
router.onError((err) => {
  console.error('[RouterError]', err)
  const msg = err instanceof Error ? err.message : String(err)
  const root = document.getElementById('app')
  if (root && !root.textContent?.trim()) {
    const title = i18n.global.t('common.errorPageFailed')
    const reload = i18n.global.t('common.errorReload')
    root.innerHTML = `<div style="display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;background:var(--c-bg,#f5f5f7);color:var(--c-text,#1d1d1f);font-family:var(--font-sans,system-ui,sans-serif);text-align:center"><div><h2 style="margin:0 0 8px;font-weight:600">${title}</h2><p style="color:var(--c-text-dim,#6e6e73);margin:0 0 20px;font-size:14px">${msg}</p><button onclick="location.reload()" style="padding:9px 24px;border-radius:999px;background:var(--c-accent,#2563eb);color:#fff;border:none;cursor:pointer;font-size:14px">${reload}</button></div></div>`
  }
})

app.mount('#app')

// PWA Service Worker（生产环境注册）
if ('serviceWorker' in navigator) {
  import('virtual:pwa-register').then(({ registerSW }) => {
    registerSW({ immediate: true })
  })
}
