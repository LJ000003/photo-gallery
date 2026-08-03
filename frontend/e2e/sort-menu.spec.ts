import { test, expect } from '@playwright/test'
import { unlockWithKeyboard, waitForGallery } from './helpers'

test('排序菜单：三项字段、点击切换方向、单边箭头', async ({ page }) => {
  await page.goto('/')
  await unlockWithKeyboard(page)
  await waitForGallery(page)

  // 打开排序菜单
  await page.locator('.tool-btn[aria-label="排序"]').click()
  const menuItems = page.locator('.ant-dropdown-menu-item')
  await expect(menuItems).toHaveCount(3)
  const texts = await menuItems.allTextContents()
  expect(texts.join(',')).toContain('时间')
  expect(texts.join(',')).toContain('名称')
  expect(texts.join(',')).toContain('大小')

  // 初始：time desc → 箭头朝下，URL 无 sort 参数（默认）
  await expect(page).toHaveURL(/\/$/)
  const down = page.locator('.dir-arrow .anticon-caret-down')
  await expect(down).toBeVisible()

  // 点击「名称」→ name asc，箭头朝上，URL sortBy=name&sortOrder=asc
  await menuItems.nth(1).click()
  await expect(page).toHaveURL(/sortBy=name/)
  await expect(page).not.toHaveURL(/sortOrder=/)
  await expect(page.locator('.dir-arrow .anticon-caret-up')).toBeVisible()

  // 再点「名称」→ name desc，箭头朝下
  await page.locator('.tool-btn[aria-label="排序"]').click()
  await page.locator('.ant-dropdown-menu-item').nth(1).click()
  await expect(page).toHaveURL(/sortBy=name&sortOrder=desc/)
  await expect(page.locator('.dir-arrow .anticon-caret-down')).toBeVisible()

  // 点「上传时间」→ 切回 time，用记忆方向 desc，箭头朝下
  await page.locator('.tool-btn[aria-label="排序"]').click()
  await page.locator('.ant-dropdown-menu-item').nth(0).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.locator('.dir-arrow .anticon-caret-down')).toBeVisible()

  // 点「上传时间」→ time asc 最旧优先，箭头朝上
  await page.locator('.tool-btn[aria-label="排序"]').click()
  await page.locator('.ant-dropdown-menu-item').nth(0).click()
  await expect(page).toHaveURL(/sortOrder=desc/)
  await expect(page.locator('.dir-arrow .anticon-caret-up')).toBeVisible()
})


test('排序按钮文字与方向图标实时反映', async ({ page }) => {
  await page.goto('/')
  await unlockWithKeyboard(page)
  await waitForGallery(page)

  const sortBtn = page.locator('.tool-btn[aria-label="排序"]')
  await expect(sortBtn.locator('.sort-label')).toHaveText('时间')
  await expect(sortBtn.locator('.caret')).toHaveClass(/caret-down/)

  // 点「名称」→ 文字变「名称」，正序 → 图标朝上
  await sortBtn.click()
  await page.locator('.ant-dropdown-menu-item').nth(1).click()
  await expect(sortBtn.locator('.sort-label')).toHaveText('名称')
  await expect(sortBtn.locator('.caret')).toHaveClass(/caret-up/)

  // 再点「名称」→ 倒序 → 图标朝下
  await sortBtn.click()
  await page.locator('.ant-dropdown-menu-item').nth(1).click()
  await expect(sortBtn.locator('.caret')).toHaveClass(/caret-down/)

  // 点「大小」→ 文字变「大小」
  await sortBtn.click()
  await page.locator('.ant-dropdown-menu-item').nth(2).click()
  await expect(sortBtn.locator('.sort-label')).toHaveText('大小')

  // 点「时间」→ 回到「时间」，最新优先 → 图标朝下
  await sortBtn.click()
  await page.locator('.ant-dropdown-menu-item').nth(0).click()
  await expect(sortBtn.locator('.sort-label')).toHaveText('时间')
  await expect(sortBtn.locator('.caret')).toHaveClass(/caret-down/)
})
