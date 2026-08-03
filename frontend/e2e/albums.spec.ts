import { test, expect } from '@playwright/test'
import { unlockWithKeyboard, waitForGallery } from './helpers'

// 未分配卡片与详情页数量一致（历史 bug：未分配 photoCount 硬编码 0）
// DB 状态无关：只断言卡片与详情数量一致，不要求库里有未分配照片
test('未分配卡片与详情页数量一致', async ({ page }) => {
  await page.goto('/')
  await unlockWithKeyboard(page)
  await waitForGallery(page)

  await page.locator('.topbar .mode-tab').nth(1).click()
  await expect(page).toHaveURL(/\/albums/)

  const unassignedCard = page.locator('.album-card.unassigned')
  await expect(unassignedCard).toBeVisible()
  const cardText = await unassignedCard.textContent()
  const cardMatch = cardText?.match(/(\d+)\s*张/)
  expect(cardMatch).toBeTruthy()

  // 点进未分配 → 详情页 header 数量与卡片一致
  await unassignedCard.locator('.album-main').click()
  await expect(page.locator('.album-detail')).toBeVisible()
  const headerText = await page.locator('.detail-count').textContent()
  const headerMatch = headerText?.match(/(\d+)/)
  expect(headerMatch).toBeTruthy()
  expect(headerMatch![1]).toBe(cardMatch![1])
})
