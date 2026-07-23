import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 'sriram294@gmail.com', avatarColor: '#F59E0B', photoUrl: null, avatarId: null };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 94, myRole: 'owner' };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([u, g]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    localStorage.removeItem('playboard.theme');
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const json = (b: unknown) => new Response(JSON.stringify(b), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/auth/logout')) return json({});
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 10 });
      if (path.includes('/stats')) return json({ userId: 'me', displayName: 'Sriram', recentMatches: [] });
      if (path.includes('/matches')) return json({ matches: [] });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(group)]);
});

test('renders account, footer, and toggles the theme', async ({ page }) => {
  await page.goto('/settings', { waitUntil: 'networkidle' });

  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await expect(page.getByText('Signed in with Google')).toBeVisible();
  await expect(page.getByText('sriram294@gmail.com')).toBeVisible();
  await expect(page.getByText(/Playboard v\d/)).toBeVisible();
  await expect(page.getByText('Made by Sriram Elangovan')).toBeVisible();

  // Defaults to dark; toggling flips <html data-theme> and persists.
  const toggle = page.getByRole('switch', { name: 'Dark theme' });
  await expect(toggle).toHaveAttribute('aria-checked', 'true');
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-checked', 'false');
  await expect.poll(() => page.evaluate(() => document.documentElement.getAttribute('data-theme'))).toBe('light');
  await expect.poll(() => page.evaluate(() => localStorage.getItem('playboard.theme'))).toBe('light');
});

test('signs out back to the login screen', async ({ page }) => {
  await page.goto('/settings', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible({ timeout: 20000 });
  await expect.poll(() => page.evaluate(() => localStorage.getItem('playboard.session'))).toBeNull();
});
