import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 's@example.com', avatarColor: '#F59E0B' };
const group = { id: 'g1', name: 'Smashers', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 4, matchCount: 0, myRole: 'owner' };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([u, g]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    window.fetch = async (input: RequestInfo | URL) => {
      const path = new URL(typeof input === 'string' ? input : (input as Request).url, location.origin).pathname;
      const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 5 });
      return json({});
    };
  }, [JSON.stringify(user), JSON.stringify(group)]);
});

test('Board has no serious accessibility violations or 320px page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await page.goto('/board', { waitUntil: 'networkidle' });
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations.filter(violation => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('manifest exposes reachable square install icons', async ({ request }) => {
  const manifest = await (await request.get('/manifest.webmanifest')).json();
  expect(manifest.icons).toEqual(expect.arrayContaining([
    expect.objectContaining({ sizes: '192x192' }),
    expect.objectContaining({ sizes: '512x512' }),
  ]));
  await expect((await request.get('/icons/icon-192.png')).ok()).toBe(true);
  await expect((await request.get('/icons/icon-512.png')).ok()).toBe(true);
});

test('light theme remains accessible at phone and centered desktop widths', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('playboard.theme', 'light'));
  for (const viewport of [{ width: 390, height: 844 }, { width: 1280, height: 900 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/board', { waitUntil: 'networkidle' });
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.filter(violation => ['critical', 'serious'].includes(violation.impact ?? ''))).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  }
  expect(await page.locator('.app-shell').evaluate(element => element.getBoundingClientRect().width)).toBeLessThanOrEqual(562);
});
