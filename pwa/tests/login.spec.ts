import { expect, test } from '@playwright/test';

const user = { id: 'user-1', displayName: 'Test Player', email: 'test@example.com', avatarColor: '#9ade28' };
const group = { id: 'group-1', name: 'Test Group', avatarColor: '#9ade28', sportCode: 'badminton_doubles', memberCount: 2, matchCount: 0, myRole: 'owner' };

test('signs in through the Google credential exchange', async ({ page }) => {
  await page.route('https://accounts.google.com/gsi/client', route => route.abort());
  await page.addInitScript(() => {
    (window as any).google = {
      accounts: {
        id: {
          initialize: (options: { callback: (response: { credential: string }) => void }) => {
            (window as any).__googleCallback = options.callback;
          },
          renderButton: () => undefined,
          prompt: () => (window as any).__googleCallback({ credential: 'test-google-id-token' }),
        },
      },
    };
  });

  // Single catch-all mock — per-path globs intercept inconsistently under the dev server.
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/auth/google')) return json({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900, user });
    if (path.endsWith('/users/me')) return json(user);
    if (path.endsWith('/groups')) return json({ groups: [group] });
    if (path.includes('/leaderboard')) return json({ rankings: [] });
    if (path.includes('/matches')) return json({ matches: [] });
    return json({});
  });

  await page.goto('/', { waitUntil: 'networkidle' });
  // Wait for the settled login screen (survives Vite's cold-start optimize reload).
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible({ timeout: 20000 });
  // GIS delivers the credential via its callback (a real tap on the overlay button
  // ends here); invoke it directly to exercise the exchange → session → Board flow.
  await page.evaluate(() => (window as any).__googleCallback({ credential: 'test-google-id-token' }));

  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
  // Bottom-nav destinations are router links in the shell.
  await expect(page.getByRole('link', { name: 'Matches' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Board' })).toBeVisible();
});
