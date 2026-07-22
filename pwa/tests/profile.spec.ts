import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 'sriram294@gmail.com', avatarColor: '#F59E0B', photoUrl: null, avatarId: 'avatar3' };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 41, myRole: 'owner' };
const ref = (userId: string, displayName: string) => ({ userId, displayName, avatarColor: '#888', avatarId: null, photoUrl: null });
const stats = {
  userId: 'me', displayName: 'Sriram', avatarColor: '#F59E0B', avatarId: 'avatar3', photoUrl: null,
  wins: 23, losses: 18, pointsFor: 799, pointsAgainst: 740, winRate: 0.56, currentStreak: -2, bestStreak: 4, matchesPlayed: 41,
  bestPartner: { userId: 'b', displayName: 'Balaji', avatarColor: '#EF4444', avatarId: null, photoUrl: null, gamesTogether: 5, winsTogether: 4, winRate: 0.8 },
  trophies: [],
  recentMatches: [{
    id: 'r1', playedAt: '2026-07-22T10:00:00Z', sets: [{ setNo: 1, team1Score: 15, team2Score: 21 }],
    teams: [
      { teamNo: 1, isWinner: false, players: [ref('me', 'Sriram'), ref('pori', 'Pori')] },
      { teamNo: 2, isWinner: true, players: [ref('mani', 'Mani partha'), ref('mugu', 'mugu')] },
    ],
  }],
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([u, g, s]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    (window as unknown as { __renamed?: string }).__renamed = undefined;
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me') && init?.method === 'PATCH') {
        const name = JSON.parse(String(init.body)).displayName;
        (window as unknown as { __renamed?: string }).__renamed = name;
        return json({ ...JSON.parse(u), displayName: name });
      }
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.includes('/attendance')) return json({ playedAt: ['2026-07-22T12:00:00Z'] });
      if (path.includes('/stats')) return json(JSON.parse(s));
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 10 });
      if (path.includes('/matches')) return json({ matches: [] });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(group), JSON.stringify(stats)]);
});

test('renders own profile stats and renames via the edit sheet', async ({ page }) => {
  await page.goto('/profile', { waitUntil: 'networkidle' });

  // Hero + meta + tiles.
  await expect(page.getByRole('heading', { name: 'Sriram' })).toBeVisible();
  await expect(page.getByText('56%')).toBeVisible();
  await expect(page.getByText('CURRENT STREAK')).toBeVisible();
  await expect(page.getByText('-2', { exact: true })).toBeVisible();
  // Best partner + a recent match framed from the viewer.
  await expect(page.getByText('4W / 5 games together')).toBeVisible();
  await expect(page.getByText('Mani partha & mugu')).toBeVisible();
  await expect(page.getByText('LOSS', { exact: true })).toBeVisible();

  // Rename flow: pencil → sheet → save → PATCH body.
  await page.getByRole('button', { name: 'Edit name' }).click();
  const input = page.getByLabel('Display name');
  await input.fill('Sriram E');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('heading', { name: 'Sriram E' })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __renamed?: string }).__renamed)).toBe('Sriram E');

  // Avatar picker opens with the default grid + upload affordance.
  await page.getByRole('button', { name: 'Change avatar' }).click();
  await expect(page.getByText('Choose avatar')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Upload a photo' })).toBeVisible();
});
