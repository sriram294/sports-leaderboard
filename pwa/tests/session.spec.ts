import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 's@example.com', avatarColor: '#F59E0B' };
const group = { id: 'g1', name: 'Smashers', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 4, matchCount: 1, myRole: 'owner' };

test('coalesces concurrent 401 responses into one rotating refresh', async ({ page }) => {
  await page.addInitScript(([u, g]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'old', refreshToken: 'refresh-1', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    (window as unknown as { __refreshes: number }).__refreshes = 0;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(typeof input === 'string' ? input : (input as Request).url, location.origin).pathname;
      const headers = new Headers(init?.headers);
      const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.endsWith('/auth/refresh')) {
        (window as unknown as { __refreshes: number }).__refreshes += 1;
        await new Promise(resolve => setTimeout(resolve, 50));
        return json({ accessToken: 'new', refreshToken: 'refresh-2', expiresIn: 900 });
      }
      if (headers.get('Authorization') === 'Bearer old') return json({ code: 'TOKEN_EXPIRED', detail: 'Expired' }, 401);
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 5 });
      if (path.includes('/matches')) return json({ matches: [] });
      if (path.includes('/trophies')) return json([]);
      return json({});
    };
  }, [JSON.stringify(user), JSON.stringify(group)]);

  await page.goto('/stats', { waitUntil: 'networkidle' });
  await expect(page.getByText('RECORDS')).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __refreshes: number }).__refreshes)).toBe(1);
});

test('returns to Login when refresh is rejected', async ({ page }) => {
  await page.addInitScript(([u, g]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'old', refreshToken: 'bad', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    window.fetch = async (input: RequestInfo | URL) => {
      const path = new URL(typeof input === 'string' ? input : (input as Request).url, location.origin).pathname;
      const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      return json({ code: 'TOKEN_EXPIRED', detail: 'Expired' }, 401);
    };
  }, [JSON.stringify(user), JSON.stringify(group)]);

  await page.goto('/board', { waitUntil: 'networkidle' });
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
  await expect.poll(() => page.evaluate(() => localStorage.getItem('playboard.session'))).toBeNull();
});
