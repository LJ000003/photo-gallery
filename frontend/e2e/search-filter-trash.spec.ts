import { test, expect } from '@playwright/test'
import { unlockWithKeyboard, waitForGallery, uploadTestPhoto } from './helpers'

// 搜索 / 筛选 / 回收站文本的回归测试（历史 bug：搜索 500、筛选面板定位视口外、回收站头部恒显“空的”）
// 注意 DB 状态无关：搜索先上传唯一照片；标签不存在时跳过收敛断言；回收站按行数分支断言

test('搜索：命中→无结果空态→清空恢复', async ({ page }) => {
  await page.goto('/')
  await unlockWithKeyboard(page)
  await waitForGallery(page)
  await uploadTestPhoto(page)

  const search = page.locator('.search-input input')
  await search.fill('e2e-')
  await expect(page.locator('.photo-tile').first()).toBeVisible({ timeout: 10000 })

  await search.fill('__绝对不存在的名字__')
  await expect(page.locator('.photo-tile')).toHaveCount(0, { timeout: 10000 })

  await search.fill('')
  await expect(page.locator('.photo-tile').first()).toBeVisible({ timeout: 10000 })
})

test('标签筛选：点击 chip 列表收敛，重置恢复', async ({ page }) => {
  await page.goto('/')
  await unlockWithKeyboard(page)
  await waitForGallery(page)

  // 打开筛选面板（按钮即 Popover trigger）
  await page.locator('[aria-label="筛选"]').click()
  const chips = page.locator('.chip')
  await expect(chips.first()).toBeVisible({ timeout: 5000 }).catch(() => {})
  const chipCount = await chips.count()

  if (chipCount === 0) {
    // 库里没有标签：至少确认面板可打开、有空态提示
    await expect(page.locator('.section-empty').first()).toBeVisible()
    return
  }

  // 点第一个标签 → 列表收敛（不能仍是全量）
  await chips.first().click()
  await page.waitForTimeout(1200)
  const tiles = page.locator('.photo-tile')
  const tileCount = await tiles.count()
  const emptyVisible = await page
    .locator('.photos-view .empty-state, .photos-view .ant-empty')
    .first()
    .isVisible()
    .catch(() => false)
  expect(tileCount < 20 || emptyVisible).toBe(true)

  // 重置筛选 → 列表恢复（面板在 chip 点击后仍保持打开）
  await page.locator('.reset-btn').click()
  await page.waitForTimeout(1200)
  await expect(page.locator('.photo-tile').first()).toBeVisible({ timeout: 10000 })
})

test('回收站：头部提示与列表内容一致（有内容时不再显示“空的”）', async ({ page }) => {
  await page.goto('/')
  await unlockWithKeyboard(page)
  await waitForGallery(page)

  await page.locator('.corner-btn').click()
  await page.getByText('回收站').click()
  await expect(page).toHaveURL(/\/trash/)

  const hint = page.locator('.trash-hint')
  await expect(hint).toBeVisible({ timeout: 10000 })
  const text = (await hint.textContent()) || ''
  const rowCount = await page.locator('.trash-row').count()
  if (rowCount > 0) {
    expect(text).toContain('30 天') // 有数据 → 自动清空提示，绝不显示“回收站是空的”
  } else {
    expect(text).toBe('回收站是空的')
  }
})
