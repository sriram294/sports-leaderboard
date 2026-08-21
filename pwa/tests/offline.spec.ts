import { expect, test } from '@playwright/test';

test.use({ serviceWorkers: 'allow' });

test('reloads the application shell offline after service-worker installation', async ({ page, context }) => {
  await page.goto('/', { waitUntil: 'networkidle' });
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
  await context.setOffline(true);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
});
