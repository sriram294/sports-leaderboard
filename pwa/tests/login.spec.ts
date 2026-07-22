import { expect, test } from '@playwright/test';

test('signs in through the Google credential exchange', async ({ page }) => {
  await page.route('https://accounts.google.com/gsi/client', route => route.abort());
  await page.addInitScript(() => {
    (window as any).google = {
      accounts: {
        id: {
          initialize: (options: { callback: (response: { credential: string }) => void }) => {
            (window as any).__googleCallback = options.callback;
          },
          // The login screen renders GIS's button into a hidden overlay stretched
          // over the styled button; fill it with a full-size clickable target.
          renderButton: (parent: HTMLElement) => {
            const target = document.createElement('div');
            target.style.position = 'absolute';
            target.style.inset = '0';
            target.addEventListener('click', () => (window as any).__googleCallback({ credential: 'test-google-id-token' }));
            parent.appendChild(target);
          },
          prompt: () => (window as any).__googleCallback({ credential: 'test-google-id-token' }),
        },
      },
    };
  });
  await page.route('**/api/v1/auth/google', async route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900, user: { id: 'user-1', displayName: 'Test Player', email: 'test@example.com', avatarColor: '#9ade28' } }) }));
  await page.route('**/api/v1/groups', async route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ groups: [{ id: 'group-1', name: 'Test Group', avatarColor: '#9ade28', sportCode: 'badminton_doubles', memberCount: 2, matchCount: 0, myRole: 'owner' }] }) }));
  await page.route('**/api/v1/users/me', async route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'user-1', displayName: 'Test Player', email: 'test@example.com', avatarColor: '#9ade28' }) }));
  await page.route('**/api/v1/groups/group-1/leaderboard', async route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ rankings: [] }) }));
  await page.route('**/api/v1/groups/group-1/matches', async route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ matches: [] }) }));

  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
  // GIS delivers the credential via its callback (a real tap on the overlay button
  // ends here); invoke it directly to exercise the exchange → session → Board flow.
  await page.evaluate(() => (window as any).__googleCallback({ credential: 'test-google-id-token' }));
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Matches' })).toBeVisible();
});
