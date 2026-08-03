import { expect, type Page } from '@playwright/test'

/** Konami 序列（与后端 application.properties 的 auth.konami-sequence 一致） */
export const KONAMI_SEQUENCE = [
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
]

/** 通过键盘输入完整 Konami 序列（必须先等门挂载，否则按键会丢失） */
export async function unlockWithKeyboard(page: Page): Promise<void> {
  await page.waitForSelector('.arcade-gate')
  const keyMap: Record<string, string> = {
    up: 'ArrowUp',
    down: 'ArrowDown',
    left: 'ArrowLeft',
    right: 'ArrowRight',
    A: 'a',
    B: 'b',
  }
  for (const key of KONAMI_SEQUENCE) {
    await page.keyboard.press(keyMap[key])
    await page.waitForTimeout(30) // 稳定输入节奏，避免丢键
  }
  // 等 1.5s 成功动画 + 解锁
  await expect(page.locator('.topbar')).toBeVisible({ timeout: 10000 })
}

/** 等待首屏照片网格出现（或空态） */
export async function waitForGallery(page: Page): Promise<void> {
  await expect(page.locator('.photos-view')).toBeVisible({ timeout: 15000 })
}

/**
 * 上传测试图片（浏览器内 canvas 生成，避免依赖外部资源）
 * 注意：内容必须逐次唯一（含时间戳），否则与历史运行字节相同会触发
 * 后端 SHA-256 去重静默跳过，导致断言失败
 */
export async function uploadTestPhoto(
  page: Page,
  options: { name?: string; color?: string; count?: number } = {},
): Promise<void> {
  const count = options.count ?? 1
  const color = options.color ?? '#2563eb'
  const runTag = Date.now().toString(36)
  // 打开上传抽屉
  await page.locator('.upload-btn').click()
  await expect(page.locator('.upload-body')).toBeVisible()

  // 在浏览器里生成测试图片并设置到隐藏 input
  await page.evaluate(
    async ({ count, color, runTag }) => {
      const input = document.getElementById('uploadFileInput') as HTMLInputElement
      const dt = new DataTransfer()
      for (let i = 0; i < count; i++) {
        const canvas = document.createElement('canvas')
        canvas.width = 640
        canvas.height = 480
        const ctx = canvas.getContext('2d')!
        ctx.fillStyle = color
        ctx.fillRect(0, 0, 640, 480)
        ctx.fillStyle = '#ffffff'
        ctx.font = '48px sans-serif'
        ctx.fillText(`e2e-${runTag}-${i + 1}`, 60, 260)
        const blob = await new Promise<Blob>((resolve) =>
          canvas.toBlob((b) => resolve(b!), 'image/jpeg', 0.9),
        )
        const file = new File([blob], `e2e-${runTag}-${i + 1}.jpg`, { type: 'image/jpeg' })
        dt.items.add(file)
      }
      input.files = dt.files
      input.dispatchEvent(new Event('change', { bubbles: true }))
    },
    { count, color, runTag },
  )

  await expect(page.locator('.preview-grid .preview-item')).toHaveCount(count)
  // 等压缩完成（按钮启用）再提交，避免竞态
  await expect(page.locator('.submit-btn')).toBeEnabled({ timeout: 15000 })
  await page.locator('.submit-btn').click()

  // 抽屉关闭即上传完成
  await expect(page.locator('.upload-body')).not.toBeVisible({ timeout: 20000 })
  // 等处理完成（轮询照片列表出现）
  await expect(page.locator('.photo-tile').first()).toBeVisible({ timeout: 30000 })
}
