import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 's@example.com', avatarColor: '#F59E0B' };

test('public deletion resource is available without a session', async ({ page }) => {
  await page.goto('/delete-account');
  await expect(page.getByRole('heading', { name: 'Delete your Playboard account' })).toBeVisible();
  await expect(page.getByText('Sign in with the Google account you want to delete.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Privacy and data retention' })).toBeVisible();
});

test('requires DELETE and clears account-scoped browser data on success', async ({ page }) => {
  await page.addInitScript(u => {
    localStorage.setItem('playboard.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u),
    }));
    localStorage.setItem('playboard.group', 'g1');
    (window as unknown as { __deleteBody?: string }).__deleteBody = undefined;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(typeof input === 'string' ? input : (input as Request).url, location.origin).pathname;
      if (path.endsWith('/users/me') && init?.method === 'DELETE') {
        (window as unknown as { __deleteBody?: string }).__deleteBody = String(init.body);
        return new Response(null, { status: 204 });
      }
      if (path.endsWith('/users/me')) {
        return new Response(JSON.stringify(JSON.parse(u)), {
          status: 200, headers: { 'Content-Type': 'application/json' },
        });
      }
      return new Response(JSON.stringify({}), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      });
    };
  }, JSON.stringify(user));

  await page.goto('/delete-account');
  const confirmation = page.getByLabel('Type DELETE to confirm');
  const submit = page.getByRole('button', { name: 'Delete account permanently' });
  await confirmation.fill('delete');
  await expect(submit).toBeDisabled();
  await confirmation.fill('DELETE');
  await submit.click();

  await expect(page.getByRole('heading', { name: 'Account deleted' })).toBeVisible();
  await expect.poll(() => page.evaluate(() => localStorage.getItem('playboard.session'))).toBeNull();
  await expect.poll(() => page.evaluate(() => localStorage.getItem('playboard.group'))).toBeNull();
  await expect.poll(() => page.evaluate(() => (window as unknown as { __deleteBody?: string }).__deleteBody))
    .toBe(JSON.stringify({ confirmation: 'DELETE' }));
});
