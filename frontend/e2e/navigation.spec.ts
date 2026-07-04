import { test, expect } from '@playwright/test'

test.describe('navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')

    // Unlock via Konami sequence
    const konamiSeq = ['ArrowUp', 'ArrowUp', 'ArrowDown', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowLeft', 'ArrowRight', 'b', 'a', 'b', 'a']
    for (const key of konamiSeq) {
      await page.keyboard.press(key)
      await page.waitForTimeout(50)
    }

    // Wait for the main UI after unlock animation
    await page.waitForSelector('.header', { timeout: 5000 })
  })

  test('can navigate between view modes', async ({ page }) => {
    // Should land on gallery page by default
    await expect(page.locator('.gallery-section')).toBeVisible()

    // Navigate to albums view
    await page.click('a[href="/albums"]')
    await expect(page.locator('.album-wrap')).toBeVisible()

    // Navigate to timeline view
    await page.click('a[href="/timeline"]')
    await expect(page.locator('.timeline-wrap')).toBeVisible()

    // Navigate to map view
    await page.click('a[href="/map"]')
    await expect(page.locator('.map-wrap')).toBeVisible()

    // Navigate back to grid
    await page.click('a[href="/"]')
    await expect(page.locator('.gallery-section')).toBeVisible()
  })

  test('unauthenticated user sees Konami gate', async ({ page }) => {
    // Clear auth state
    await page.evaluate(() => {
      localStorage.clear()
    })
    await page.goto('/')

    // Should see the arcade gate
    await expect(page.locator('.arcade-gate')).toBeVisible()
  })
})
