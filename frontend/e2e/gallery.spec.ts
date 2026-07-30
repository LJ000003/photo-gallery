import { test, expect } from '@playwright/test';

const JPEG_BYTES = [
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
];

test.describe('Photo Gallery', () => {
  test('full flow: unlock → upload → list → delete', async ({ page }) => {
    // 1. Go to app and bypass Konami with API-obtained JWT
    await page.goto('/');
    const token = await page.evaluate(async () => {
      const res = await fetch('/api/v1/auth/unlock', { method: 'POST' });
      const json = await res.json();
      return json.data.token as string;
    });
    await page.evaluate((t) => {
      localStorage.setItem('konami_unlocked', 'true');
      localStorage.setItem('jwt_token', t);
    }, token);

    // 2. Reload — now unlocked
    await page.reload();
    await page.waitForSelector('.gallery-section', { timeout: 10000 });

    // 3. Verify gallery is visible
    await expect(page.locator('.gallery-section')).toBeVisible({ timeout: 5000 });

    // 4. Upload a test photo via the page's fetch (goes through Vite proxy to backend)
    const uploadedId = await page.evaluate(async (t) => {
      const bytes = new Uint8Array([
        0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
      ]);
      const blob = new Blob([bytes], { type: 'image/jpeg' });
      const form = new FormData();
      form.append('file', blob, 'e2e-test.jpg');
      form.append('name', 'E2E Test Photo');

      const res = await fetch('/api/v1/photos', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + t },
        body: form,
      });
      const json = await res.json();
      return json.id as number;
    }, token);
    expect(uploadedId).toBeGreaterThan(0);

    // 5. Reload and verify photo appears
    await page.reload();
    await page.waitForSelector('.photo-card', { timeout: 10000 });

    // 6. Delete the photo
    await page.evaluate(async (id) => {
      await fetch(`/api/v1/photos/${id}`, {
        method: 'DELETE',
        headers: { Authorization: 'Bearer ' + localStorage.getItem('jwt_token')! },
      });
    }, uploadedId);

    // 7. Reload and verify our photo is gone (soft-deleted)
    await page.reload();
    await page.waitForSelector('.gallery-section', { timeout: 5000 });
    const ourPhotoGone = await page.locator('.photo-card').count() === 0
      || !(await page.getByText('E2E Test Photo').isVisible().catch(() => false));
    expect(ourPhotoGone).toBeTruthy();
  });

  test('health check endpoint', async ({ request }) => {
    const resp = await request.get('http://localhost:8080/actuator/health');
    expect(resp.ok()).toBeTruthy();
    const body = await resp.json();
    expect(body.status).toBe('UP');
  });
});
