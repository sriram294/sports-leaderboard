import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 'sriram@example.com', avatarColor: '#F59E0B', photoUrl: null, avatarId: null };
const owned = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 94, myRole: 'owner', sessionStart: '06:00', sessionEnd: '08:00' };
const joined = { id: 'g2', name: 'Office League', avatarColor: '#3DB4FF', sportCode: 'badminton_doubles', memberCount: 4, matchCount: 4, myRole: 'member' };

const member = (userId: string, displayName: string, role: string) => ({ userId, displayName, avatarColor: '#888', avatarId: null, photoUrl: null, role });
const members = {
  members: [member('me', 'Sriram', 'owner'), member('sugaram', 'Sugaram', 'member'), member('deenesh', 'deenesh dhadha', 'admin')],
  guests: [member('guest1', 'Guest 1', 'guest')],
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([u, g1, g2, mem]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    const captured: Record<string, unknown> = {};
    (window as unknown as { __captured: Record<string, unknown> }).__captured = captured;
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const method = init?.method ?? 'GET';
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });

      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups') && method === 'GET') return json({ groups: [JSON.parse(g1), JSON.parse(g2)] });
      if (path.endsWith('/groups') && method === 'POST') { captured.create = body; return json({ ...JSON.parse(g1), id: 'gnew', name: body.name }, 201); }
      if (path.endsWith('/groups/join') && method === 'POST') { captured.join = body; return json({ ...JSON.parse(g2), id: 'gjoin' }); }
      if (/\/groups\/[^/]+\/invites$/.test(path)) { captured.invite = true; return json({ code: 'SMASH42', expiresAt: '2026-08-01T00:00:00Z' }); }
      if (/\/groups\/[^/]+\/session$/.test(path)) { captured.session = body; return json({ ...JSON.parse(g1), sessionStart: body.start, sessionEnd: body.end }); }
      if (/\/groups\/[^/]+\/members\/[^/]+$/.test(path) && method === 'PATCH') { captured.role = { path, body }; return json(member('sugaram', 'Sugaram', body.role)); }
      if (/\/groups\/[^/]+\/members\/[^/]+$/.test(path) && method === 'DELETE') { captured.remove = path; return new Response(null, { status: 204 }); }
      if (/\/groups\/[^/]+\/members$/.test(path)) return json(JSON.parse(mem));
      if (/\/groups\/[^/]+$/.test(path) && method === 'PATCH') { captured.rename = body; return json({ ...JSON.parse(g1), name: body.name }); }
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 10 });
      if (path.includes('/stats')) return json({ userId: 'me', displayName: 'Sriram', recentMatches: [] });
      if (path.includes('/matches')) return json({ matches: [] });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(owned), JSON.stringify(joined), JSON.stringify(members)]);
});

const captured = (page: import('@playwright/test').Page) => page.evaluate(() => (window as unknown as { __captured: Record<string, unknown> }).__captured);

test('management: lists managed groups, shows session + members with role-gated controls', async ({ page }) => {
  await page.goto('/groups', { waitUntil: 'networkidle' });

  // Only the owned group is manageable; the member-role group is filtered out.
  await expect(page.getByText('GROUPS YOU MANAGE')).toBeVisible();
  const managed = page.locator('.manage-row');
  await expect(managed).toHaveCount(1);
  await expect(managed).toContainText('Old Monk Badminton');

  // Drill into detail.
  await managed.click();
  await expect(page.getByRole('heading', { name: 'Old Monk Badminton' })).toBeVisible();
  await expect(page.getByText('06:00 – 08:00')).toBeVisible();

  // Self row is labelled and has no action buttons; others are actionable (owner sees role toggles).
  await expect(page.getByText('Sriram (you)')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Make admin' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Demote' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Remove' })).toHaveCount(2);

  // Promote a member → captured PATCH body.
  await page.getByRole('button', { name: 'Make admin' }).click();
  await expect.poll(async () => (await captured(page)).role).toMatchObject({ body: { role: 'admin' } });
});

test('management: edits the session window', async ({ page }) => {
  await page.goto('/groups', { waitUntil: 'networkidle' });
  await page.locator('.manage-row').click();
  await page.getByRole('button', { name: 'Edit' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Save' }).click();
  await expect.poll(async () => (await captured(page)).session).toMatchObject({ start: '06:00', end: '08:00' });
});

test('switcher: renames a group and generates an invite code', async ({ page }) => {
  await page.goto('/board', { waitUntil: 'networkidle' });

  // Expand the switcher panel.
  await page.getByRole('button', { name: /Old Monk Badminton/ }).first().click();
  await expect(page.getByText('YOUR GROUPS')).toBeVisible();

  // Invite from the panel action → code shown.
  await page.getByRole('button', { name: 'Invite players' }).click();
  await expect(page.getByText('SMASH42')).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).click();

  // Rename via the per-group pencil (owner/admin only).
  await page.getByRole('button', { name: /Old Monk Badminton/ }).first().click();
  await page.getByRole('button', { name: 'Rename Old Monk Badminton' }).click();
  const input = page.getByRole('textbox', { name: 'Group name' });
  await input.fill('Old Monk Reloaded');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect.poll(async () => (await captured(page)).rename).toMatchObject({ name: 'Old Monk Reloaded' });
});

test('switcher: creates a group via the create/join sheet', async ({ page }) => {
  await page.goto('/board', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: /Old Monk Badminton/ }).first().click();
  await page.getByRole('button', { name: 'Create or join a group' }).click();
  const nameInput = page.getByRole('textbox', { name: 'Group name' });
  await nameInput.fill('Sunday Club');
  await nameInput.press('Enter');
  await expect.poll(async () => (await captured(page)).create).toMatchObject({ name: 'Sunday Club' });
});
