/**
 * 视觉验证脚本：解锁 → 上传测试图 → 截取各页面（浅色/深色/移动端）
 * 产物在 frontend/.shots/ 目录，用 Read 工具人工检查设计质量
 * 前置：后端 dev profile 运行在 :8080
 */
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const SEQUENCE = ['up', 'up', 'down', 'down', 'left', 'right', 'left', 'right', 'B', 'A', 'B', 'A']
const KEYMAP = { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', A: 'a', B: 'b' }

mkdirSync('.shots', { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })

async function unlock() {
  for (const k of SEQUENCE) await page.keyboard.press(KEYMAP[k])
  await page.waitForSelector('.topbar', { timeout: 15000 })
}

async function uploadTestPhotos() {
  await page.locator('.upload-btn').click()
  await page.evaluate(async () => {
    const input = document.getElementById('uploadFileInput')
    const dt = new DataTransfer()
    const specs = [
      { color: '#2d6cdf', text: '海边日落' },
      { color: '#c85a5a', text: '城市夜景' },
      { color: '#3a8f5f', text: '山间小路' },
      { color: '#8a6fd0', text: '街角咖啡' },
      { color: '#d09a3a', text: '晨光' },
      { color: '#3f8f8f', text: '雨后' },
    ]
    for (let i = 0; i < specs.length; i++) {
      const c = document.createElement('canvas')
      c.width = 800
      c.height = 600
      const ctx = c.getContext('2d')
      ctx.fillStyle = specs[i].color
      ctx.fillRect(0, 0, 800, 600)
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 40px sans-serif'
      ctx.fillText(specs[i].text, 60, 320)
      const blob = await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.9))
      dt.items.add(new File([blob], `shot-${i + 1}.jpg`, { type: 'image/jpeg' }))
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

async function shot(name) {
  await page.waitForTimeout(600) // 等动效稳定
  await page.screenshot({ path: `.shots/${name}.png`, fullPage: false })
  console.log(`captured: ${name}`)
}

// 1. 锁定屏（红白机门）
await page.goto('http://localhost:5173/')
await page.waitForSelector('.arcade-gate')
await shot('01-gate-light')

// 2. 解锁 + 上传
await unlock()
await uploadTestPhotos()

// 3. 照片流（浅色）
await page.waitForTimeout(800)
await shot('02-gallery-light')

// 4. 灯箱
await page.locator('.photo-tile').first().click()
await page.waitForSelector('.img-full.show', { timeout: 10000 })
await shot('03-viewer-light')
await page.keyboard.press('Escape')

// 5. 深色照片流
await page.emulateMedia({ colorScheme: 'dark' })
await page.waitForTimeout(400)
await shot('04-gallery-dark')

// 6. 深色灯箱
await page.locator('.photo-tile').nth(1).click()
await page.waitForSelector('.img-full.show', { timeout: 10000 })
await shot('05-viewer-dark')
await page.keyboard.press('Escape')

// 7. 相册页
await page.locator('.mode-tab').nth(1).click()
await page.waitForURL(/\/albums/)
await shot('06-albums-dark')

// 8. 时间线
await page.locator('.mode-tab').nth(2).click()
await page.waitForURL(/\/timeline/)
await page.waitForTimeout(1500)
await shot('07-timeline-dark')

// 9. 地图
await page.locator('.mode-tab').nth(3).click()
await page.waitForURL(/\/map/)
await page.waitForTimeout(2500)
await shot('08-map-dark')

// 10. 回收站（直接导航，路由可深链）
await page.goto('http://localhost:5173/trash')
await page.waitForSelector('.trash-view')
await shot('09-trash-dark')

// 11. 移动端浅色
await page.setViewportSize({ width: 390, height: 844 })
await page.emulateMedia({ colorScheme: 'light' })
await page.goto('http://localhost:5173/')
await page.waitForSelector('.photo-tile')
await shot('10-mobile-light')

await browser.close()
console.log('done')
