import { test, expect } from '@playwright/test';
import { unlock } from './helpers';

test.describe('Photo Gallery', () => {
  test('full flow: unlock → upload → list → delete', async ({ page }) => {
    await unlock(page);

    // 1. Verify gallery is visible
    await expect(page.locator('.gallery-section')).toBeVisible({ timeout: 5000 });

    // 2. Retrieve JWT token for upload API call
    const token = await page.evaluate(() => localStorage.getItem('jwt_token'));

    // 3. Upload a test photo via the page's fetch (Canvas generates a valid JPEG)
    const uploadedId = await page.evaluate(async (t) => {
      const canvas = document.createElement('canvas');
      canvas.width = 1;
      canvas.height = 1;
      const ctx = canvas.getContext('2d')!;
      ctx.fillStyle = '#336699';
      ctx.fillRect(0, 0, 1, 1);
      const blob = await new Promise<Blob>((resolve) =>
        canvas.toBlob((b) => resolve(b!), 'image/jpeg', 0.95)
      );
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
