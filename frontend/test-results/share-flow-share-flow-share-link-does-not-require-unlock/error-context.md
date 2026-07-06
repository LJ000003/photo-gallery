# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: share-flow.spec.ts >> share flow >> share link does not require unlock
- Location: e2e\share-flow.spec.ts:31:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('.share-page')
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for locator('.share-page')

```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test'
  2  | 
  3  | test.describe('share flow', () => {
  4  |   test.beforeEach(async ({ page }) => {
  5  |     await page.goto('/')
  6  | 
  7  |     // Unlock
  8  |     const konamiSeq = ['ArrowUp', 'ArrowUp', 'ArrowDown', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowLeft', 'ArrowRight', 'b', 'a', 'b', 'a']
  9  |     for (const key of konamiSeq) {
  10 |       await page.keyboard.press(key)
  11 |       await page.waitForTimeout(50)
  12 |     }
  13 | 
  14 |     await page.waitForSelector('.header', { timeout: 5000 })
  15 |   })
  16 | 
  17 |   test('share link page shows viewer-only mode', async ({ page }) => {
  18 |     // Navigate directly to /share/some-token
  19 |     // This should render ShareViewer without unlock
  20 |     await page.evaluate(() => {
  21 |       localStorage.clear()
  22 |     })
  23 |     await page.goto('/share/fake-test-token')
  24 | 
  25 |     // Should show share page header (even if token is invalid)
  26 |     await expect(page.locator('.share-page')).toBeVisible()
  27 |     // Should have a gallery section (even if empty due to bad token)
  28 |     await expect(page.getByText('分享的照片')).toBeVisible()
  29 |   })
  30 | 
  31 |   test('share link does not require unlock', async ({ page }) => {
  32 |     await page.evaluate(() => {
  33 |       localStorage.clear()
  34 |     })
  35 |     await page.goto('/share/another-token')
  36 | 
  37 |     // Should NOT see the Konami gate
  38 |     await expect(page.locator('.arcade-gate')).not.toBeVisible()
  39 |     // Should see share page content
> 40 |     await expect(page.locator('.share-page')).toBeVisible()
     |                                               ^ Error: expect(locator).toBeVisible() failed
  41 |   })
  42 | })
  43 | 
```