import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 'sriram294@gmail.com', avatarColor: '#F59E0B', photoUrl: null, avatarId: null };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 94, myRole: 'owner' };
const pushConfig = {
  VITE_FIREBASE_API_KEY: 'k', VITE_FIREBASE_AUTH_DOMAIN: 'p.firebaseapp.com', VITE_FIREBASE_PROJECT_ID: 'p',
  VITE_FIREBASE_MESSAGING_SENDER_ID: '123', VITE_FIREBASE_APP_ID: '1:123:web:abc', VITE_FCM_VAPID_KEY: 'vapid',
};

function seed(permission: 'granted' | 'denied') {
  return ([u, g, cfg, perm]: string[]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    localStorage.removeItem('playboard.push.token');
    const win = window as unknown as { __PLAYBOARD_PUSH_CONFIG__: unknown; __PLAYBOARD_FCM_TOKEN__: string; __captured: Record<string, unknown> };
    win.__PLAYBOARD_PUSH_CONFIG__ = JSON.parse(cfg);
    win.__PLAYBOARD_FCM_TOKEN__ = 'web-token-abc'; // seam: bypasses the Firebase SDK
    win.__captured = {};
    // Deterministic Notification permission.
    class FakeNotification { static permission = perm; static requestPermission() { return Promise.resolve(perm); } }
    (window as unknown as { Notification: unknown }).Notification = FakeNotification;
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const json = (b: unknown) => new Response(JSON.stringify(b), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/devices') && init?.method === 'POST') { win.__captured.register = JSON.parse(String(init.body)); return new Response(null, { status: 204 }); }
      if (path.endsWith('/devices') && init?.method === 'DELETE') { win.__captured.unregister = JSON.parse(String(init.body)); return new Response(null, { status: 204 }); }
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 10 });
      if (path.includes('/stats')) return json({ userId: 'me', displayName: 'Sriram', recentMatches: [] });
      if (path.includes('/matches')) return json({ matches: [] });
      return orig(input, init);
    };
  };
}

const captured = (page: import('@playwright/test').Page) => page.evaluate(() => (window as unknown as { __captured: Record<string, unknown> }).__captured);

test('enables push from a user action and registers the web token', async ({ page }) => {
  await page.addInitScript(seed('granted'), [JSON.stringify(user), JSON.stringify(group), JSON.stringify(pushConfig), 'granted']);
  await page.goto('/settings', { waitUntil: 'networkidle' });

  await expect(page.getByText('NOTIFICATIONS')).toBeVisible();
  const toggle = page.getByRole('switch', { name: 'Match and group alerts' });
  await expect(toggle).toHaveAttribute('aria-checked', 'false');
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-checked', 'true');
  await expect.poll(async () => (await captured(page)).register).toMatchObject({ token: 'web-token-abc', platform: 'web' });
});

test('leaves push off and explains when permission is blocked', async ({ page }) => {
  await page.addInitScript(seed('denied'), [JSON.stringify(user), JSON.stringify(group), JSON.stringify(pushConfig), 'denied']);
  await page.goto('/settings', { waitUntil: 'networkidle' });

  const toggle = page.getByRole('switch', { name: 'Match and group alerts' });
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-checked', 'false');
  await expect(page.getByText(/blocked in your browser/i)).toBeVisible();
  expect(await captured(page)).not.toHaveProperty('register');
});
