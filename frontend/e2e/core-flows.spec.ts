import { test, expect } from '@playwright/test'
import { unlockWithKeyboard, waitForGallery, uploadTestPhoto } from './helpers'

test.describe('核心流程', () => {
  test('Konami 解锁 → 照片流可见', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.arcade-gate')).toBeVisible()
    await unlockWithKeyboard(page)
    await waitForGallery(page)
  })

  test('顶部栏导航切换四个浏览模式', async ({ page }) => {
    await page.goto('/')
    await unlockWithKeyboard(page)
    await waitForGallery(page)

    // 限定顶栏内的模式分段（移动底栏中也有同名按钮）
    const tabs = page.locator('.topbar .mode-tab')
    await expect(tabs).toHaveCount(4)
    await tabs.nth(1).click() // 相册
    await expect(page).toHaveURL(/\/albums/)
    await tabs.nth(2).click() // 时间线
    await expect(page).toHaveURL(/\/timeline/)
    await tabs.nth(3).click() // 地图
    await expect(page).toHaveURL(/\/map/)
    await tabs.nth(0).click() // 照片
    await expect(page).toHaveURL(/\/$/)
  })

  test('上传 → 照片出现在网格（以计数行验证总量增长，容忍库中已有照片）', async ({ page }) => {
    await page.goto('/')
    await unlockWithKeyboard(page)
    await waitForGallery(page)
    // 计数行格式如「77 张照片」，解析数字；空库时计数行不渲染（totalCount=0），按 0 计
    const parseCount = async () => {
      const line = page.locator('.count-line')
      if (!(await line.isVisible())) return 0
      const m = (await line.textContent())?.match(/\d+/)
      return m ? Number(m[0]) : 0
    }
    const before = await parseCount()
    await uploadTestPhoto(page, { count: 2 })
    await expect.poll(parseCount, { timeout: 15000 }).toBeGreaterThanOrEqual(before + 2)
  })

  test('搜索过滤照片', async ({ page }) => {
    await page.goto('/')
    await unlockWithKeyboard(page)
    await waitForGallery(page)
    await uploadTestPhoto(page, { color: '#dc2626' })

    // 上传的文件名/内容以 e2e-<时间戳> 开头（helper 保证唯一），搜索固定前缀 e2e- 必命中
    const search = page.locator('.search-input input')
    await search.fill('e2e-')
    await expect(page.locator('.photo-tile').first()).toBeVisible({ timeout: 10000 })
    await search.fill('__不存在的名字__')
    await expect(page.locator('.photo-tile')).toHaveCount(0, { timeout: 10000 })
  })

  test('点击照片打开灯箱，Esc 关闭', async ({ page }) => {
    await page.goto('/')
    await unlockWithKeyboard(page)
    await waitForGallery(page)
    await uploadTestPhoto(page)

    await page.locator('.photo-tile').first().click()
    await expect(page.locator('.photo-viewer')).toBeVisible()
    await expect(page.locator('.viewer-name')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('.photo-viewer')).not.toBeVisible()
  })

  test('角落菜单：回收站与帮助', async ({ page }) => {
    await page.goto('/')
    await unlockWithKeyboard(page)
    await waitForGallery(page)

    await page.locator('.corner-btn').click()
    await page.getByText('回收站').click()
    await expect(page).toHaveURL(/\/trash/)

    await page.locator('.corner-btn').click()
    await page.getByText('帮助与快捷键').click()
    await expect(page.locator('.ant-modal').filter({ hasText: '快捷键' })).toBeVisible()
  })

  test('重新锁定回到解锁屏', async ({ page }) => {
    await page.goto('/')
    await unlockWithKeyboard(page)
    await waitForGallery(page)

    await page.locator('.corner-btn').click()
    await page.getByText('重新锁定').click()
    await expect(page.locator('.arcade-gate')).toBeVisible()
  })
})
