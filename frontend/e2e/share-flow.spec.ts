import { test, expect } from '@playwright/test'

test.describe('share flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    const token = await page.evaluate(async () => {
      const res = await fetch('/api/v1/auth/unlock', { method: 'POST' })
      const json = await res.json()
      return json.data.token as string
    })
    await page.evaluate((t) => {
      localStorage.setItem('konami_unlocked', 'true')
      localStorage.setItem('jwt_token', t)
    }, token)
    await page.reload()
    await page.waitForSelector('.header', { timeout: 5000 })
  })

  test('share link page shows viewer-only mode', async ({ page }) => {
    // Navigate directly to /share/some-token
    // This should render ShareViewer without unlock
    await page.evaluate(() => {
      localStorage.clear()
    })
    await page.goto('/share/fake-test-token')

    // Should show share page header (even if token is invalid)
    await expect(page.locator('.share-page')).toBeVisible()
    // Should have a gallery section (even if empty due to bad token)
    await expect(page.getByText('分享的照片')).toBeVisible()
  })

  test('share link does not require unlock', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.clear()
    })
    await page.goto('/share/another-token')

    // Should NOT see the Konami gate
    await expect(page.locator('.arcade-gate')).not.toBeVisible()
    // Should see share page content
    await expect(page.locator('.share-page')).toBeVisible()
  })
})
