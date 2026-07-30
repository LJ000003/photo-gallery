import type { Page } from '@playwright/test'

const KONAMI_KEYS = ['up','up','down','down','left','right','left','right','B','A','B','A']

export async function unlock(page: Page): Promise<void> {
  await page.goto('/')
  const token = await page.evaluate(async (keys) => {
    const cRes = await fetch('/api/v1/auth/challenge')
    const cJson = await cRes.json()
    const nonce = cJson.data.nonce as string
    const uRes = await fetch('/api/v1/auth/unlock', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nonce, keys }),
    })
    const uJson = await uRes.json()
    return uJson.data.token as string
  }, KONAMI_KEYS)
  await page.evaluate((t) => {
    localStorage.setItem('konami_unlocked', 'true')
    localStorage.setItem('jwt_token', t)
  }, token)
  await page.reload()
  await page.waitForSelector('.header', { timeout: 8000 })
}
