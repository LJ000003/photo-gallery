/**
 * 客观设计验证：设计令牌 / 布局指标 / 对比度 / 响应式断点
 * 以脚本断言代替视觉检查（无视觉通道时的替代方案）
 */
import { chromium } from '@playwright/test'

const SEQUENCE = ['up', 'up', 'down', 'down', 'left', 'right', 'left', 'right', 'B', 'A', 'B', 'A']
const KEYMAP = { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', A: 'a', B: 'b' }

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`)
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })

async function unlock() {
  await page.waitForSelector('.arcade-gate')
  for (const k of SEQUENCE) await page.keyboard.press(KEYMAP[k])
  await page.waitForSelector('.topbar', { timeout: 15000 })
}

async function uploadTestPhotos() {
  await page.locator('.upload-btn').click()
  await page.evaluate(async () => {
    const input = document.getElementById('uploadFileInput')
    const dt = new DataTransfer()
    for (let i = 0; i < 2; i++) {
      const c = document.createElement('canvas')
      c.width = 640
      c.height = 480
      const ctx = c.getContext('2d')
      ctx.fillStyle = i === 0 ? '#2d6cdf' : '#c85a5a'
      ctx.fillRect(0, 0, 640, 480)
      const blob = await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.9))
      dt.items.add(new File([blob], `verify-${i + 1}.jpg`, { type: 'image/jpeg' }))
    }
    input.files = dt.files
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })
  await page.waitForSelector('.preview-grid .preview-item', { timeout: 5000 })
  await page.waitForSelector('.submit-btn:not([disabled])', { timeout: 15000 })
  await page.locator('.submit-btn').click()
  await page.waitForSelector('.upload-body', { state: 'hidden', timeout: 20000 })
  await page.waitForSelector('.photo-tile', { timeout: 30000 })
}

await page.goto('http://localhost:5173/')
await unlock()
if (!(await page.locator('.photo-tile').count())) {
  await uploadTestPhotos()
}
await page.waitForSelector('.photo-tile')

/* ---------- 设计令牌（浅色） ---------- */
await page.emulateMedia({ colorScheme: 'light' })
await page.waitForTimeout(300)
const light = await page.evaluate(() => {
  const cs = getComputedStyle(document.documentElement)
  const body = getComputedStyle(document.body)
  return {
    bg: cs.getPropertyValue('--c-bg').trim(),
    accent: cs.getPropertyValue('--c-accent').trim(),
    bodyBg: body.backgroundColor,
    bodyColor: body.color,
    font: body.fontFamily.split(',')[0],
  }
})
check('浅色主题背景 = #f5f5f7', light.bg === '#f5f5f7', light.bg)
check('主色 = #2563eb', light.accent === '#2563eb', light.accent)
check('body 使用 CSS 变量背景', light.bodyBg === 'rgb(245, 245, 247)', light.bodyBg)
check('字体栈以 Inter Variable 开头', light.font.includes('Inter Variable'), light.font)

/* ---------- 设计令牌（深色） ---------- */
await page.emulateMedia({ colorScheme: 'dark' })
await page.waitForTimeout(300)
const dark = await page.evaluate(() => {
  const cs = getComputedStyle(document.documentElement)
  return {
    bg: cs.getPropertyValue('--c-bg').trim(),
    accent: cs.getPropertyValue('--c-accent').trim(),
  }
})
check('深色主题背景 = #101014', dark.bg === '#101014', dark.bg)
check('深色主色 = #3b82f6', dark.accent === '#3b82f6', dark.accent)

/* ---------- 布局指标（深色下继续） ---------- */
const layout = await page.evaluate(() => {
  const topbar = document.querySelector('.topbar')
  const ts = getComputedStyle(topbar)
  const tiles = [...document.querySelectorAll('.photo-tile')]
  const tile = tiles[0]
  const tileRect = tile?.getBoundingClientRect()
  const first = tileRect
  const second = tiles[1]?.getBoundingClientRect()
  return {
    topbarSticky: ts.position === 'sticky',
    topbarBlur: ts.backdropFilter || ts.webkitBackdropFilter,
    topbarHeight: topbar.getBoundingClientRect().height,
    tileCount: tiles.length,
    tileRadius: tile ? getComputedStyle(tile.querySelector('.tile-photo')).borderRadius : null,
    tileAspect: first ? (first.height / first.width).toFixed(2) : null,
    gapX: first && second ? (second.left - first.right).toFixed(1) : null,
    horizontalScroll: document.documentElement.scrollWidth > window.innerWidth,
  }
})
check('顶栏 sticky', layout.topbarSticky)
check('顶栏毛玻璃模糊', !!layout.topbarBlur, layout.topbarBlur)
check('顶栏高度 52-57px（含 1px 边框）', layout.topbarHeight >= 52 && layout.topbarHeight <= 57, `${layout.topbarHeight}px`)
check('照片片 4:3 比例', layout.tileAspect === '0.75', layout.tileAspect)
check('照片间距 8px', Number(layout.gapX) === 8, `${layout.gapX}px`)
check('无横向滚动', !layout.horizontalScroll)
check('照片圆角 8px', layout.tileRadius === '8px', layout.tileRadius)

/* ---------- 对比度（AA 检查） ---------- */
const contrast = await page.evaluate(() => {
  function lum(color) {
    let r, g, b
    if (color.startsWith('#')) {
      const hex = color.slice(1)
      r = parseInt(hex.slice(0, 2), 16)
      g = parseInt(hex.slice(2, 4), 16)
      b = parseInt(hex.slice(4, 6), 16)
    } else {
      ;[r, g, b] = color.match(/\d+/g).map(Number)
    }
    const f = (v) => {
      v /= 255
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  function ratio(a, b) {
    const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x)
    return ((l1 + 0.05) / (l2 + 0.05)).toFixed(2)
  }
  const body = getComputedStyle(document.body)
  const text = body.color
  const bg = body.backgroundColor
  // 次要文字：取 --c-text-dim 的计算值（临时元素）
  const probe = document.createElement('div')
  probe.style.color = 'var(--c-text-dim)'
  document.body.appendChild(probe)
  const dim = getComputedStyle(probe).color
  probe.remove()
  return {
    textOnBg: ratio(text, bg),
    dimOnBg: ratio(dim, bg),
  }
})
check('正文对比度 ≥ 4.5 (AA)', Number(contrast.textOnBg) >= 4.5, `${contrast.textOnBg}:1`)
check('次要文字对比度 ≥ 4.5 (AA)', Number(contrast.dimOnBg) >= 4.5, `${contrast.dimOnBg}:1`)

/* ---------- 焦点环 ---------- */
const focus = await page.evaluate(() => {
  const btn = document.querySelector('.mode-tab')
  btn.focus()
  return getComputedStyle(btn).outlineStyle !== 'none' || getComputedStyle(btn).outlineWidth !== '0px'
})
check('焦点可见环存在', focus)

/* ---------- 响应式：移动端底栏 + 无横向溢出 ---------- */
await page.setViewportSize({ width: 390, height: 844 })
await page.waitForTimeout(400)
const mobile = await page.evaluate(() => {
  const tabbar = document.querySelector('.mobile-tabbar')
  const display = tabbar ? getComputedStyle(tabbar).display : 'none'
  const fab = document.querySelector('.fab')
  return {
    tabbarVisible: display !== 'none',
    fabExists: !!fab,
    horizontalScroll: document.documentElement.scrollWidth > window.innerWidth,
    topbarTabsHidden: getComputedStyle(document.querySelector('.topbar-tabs')).display === 'none',
  }
})
check('移动端底部导航显示', mobile.tabbarVisible)
check('移动端中央上传按钮存在', mobile.fabExists)
check('移动端无横向滚动', !mobile.horizontalScroll)
check('移动端顶栏隐藏模式分段', mobile.topbarTabsHidden)

/* ---------- 红白机门（重新锁定后） ---------- */
await page.emulateMedia({ colorScheme: 'light' })
await page.setViewportSize({ width: 1440, height: 900 })
await page.evaluate(() => {
  localStorage.removeItem('konami_unlocked')
  localStorage.removeItem('jwt_token')
  location.reload()
})
await page.waitForSelector('.arcade-gate')
const gate = await page.evaluate(() => {
  const cs = getComputedStyle(document.querySelector('.arcade-gate'))
  const title = getComputedStyle(document.querySelector('.arcade-title'))
  const btn = getComputedStyle(document.querySelector('.arcade-btn.dir'))
  return {
    bg: cs.backgroundColor,
    font: title.fontFamily.split(',')[0],
    btnRadius: btn.borderRadius,
    scanlines: !!document.querySelector('.scanlines'),
  }
})
check('红白机门：扫描线存在', gate.scanlines)
check('红白机门：像素字体', gate.font.includes('Press Start 2P'), gate.font)
check('红白机门：直角按钮（无圆角）', gate.btnRadius === '0px', gate.btnRadius)
check('红白机门：深色背景', gate.bg === 'rgb(16, 16, 20)', gate.bg)

await browser.close()
const failed = results.filter((r) => !r.ok).length
console.log(`\n${results.length - failed}/${results.length} 项通过`)
process.exit(failed ? 1 : 0)
