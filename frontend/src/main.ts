import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)

// 全局兜底：捕获所有未被 onErrorCaptured 拦截的错误
app.config.errorHandler = (err, instance, info) => {
  console.error(`[GlobalErrorHandler] ${info}:`, err)

  // 如果 App.vue 的 onErrorCaptured 已经捕获（同步渲染错误），
  // 这里作为最后一道防线，防止极端情况下的白屏
  const msg = err instanceof Error ? err.message : String(err)
  const root = document.getElementById('app')
  if (root && !root.textContent?.trim()) {
    root.innerHTML = `<div style="display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;background:var(--bg,#0f0f1a);color:var(--text,#e0e0e0);font-family:system-ui,sans-serif;text-align:center"><div><h2 style="margin:0 0 8px">应用遇到错误</h2><p style="color:var(--text-dim,#888);margin:0 0 20px;font-size:14px">${msg}</p><button onclick="location.reload()" style="padding:10px 24px;border-radius:8px;background:var(--accent,#00d4ff);color:#fff;border:none;cursor:pointer;font-size:14px">刷新页面</button></div></div>`
  }
}

// 路由导航异常处理
router.onError((err) => {
  console.error('[RouterError]', err)
  const msg = err instanceof Error ? err.message : String(err)
  const root = document.getElementById('app')
  if (root && !root.textContent?.trim()) {
    root.innerHTML = `<div style="display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;background:var(--bg,#0f0f1a);color:var(--text,#e0e0e0);font-family:system-ui,sans-serif;text-align:center"><div><h2 style="margin:0 0 8px">页面加载失败</h2><p style="color:var(--text-dim,#888);margin:0 0 20px;font-size:14px">${msg}</p><button onclick="location.reload()" style="padding:10px 24px;border-radius:8px;background:var(--accent,#00d4ff);color:#fff;border:none;cursor:pointer;font-size:14px">刷新页面</button></div></div>`
  }
})

app.mount('#app')

// PWA Service Worker（生产环境注册）
if ('serviceWorker' in navigator) {
  import('virtual:pwa-register').then(({ registerSW }) => {
    registerSW({ immediate: true })
  })
}
