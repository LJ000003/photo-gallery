import { test, expect } from '@playwright/test'

test('锁屏进度点箭头字符统一（几何三角）', async ({ page }) => {
  await page.goto('/')
  await page.waitForSelector('.arcade-gate')
  await page.keyboard.press('ArrowUp')
  await expect(page.locator('.progress-dot').nth(0)).toHaveText('▲')
  await page.keyboard.press('ArrowLeft')
  await expect(page.locator('.progress-dot').nth(1)).toHaveText('◀')
  await page.keyboard.press('ArrowDown')
  await expect(page.locator('.progress-dot').nth(2)).toHaveText('▼')
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('.progress-dot').nth(3)).toHaveText('▶')
})
