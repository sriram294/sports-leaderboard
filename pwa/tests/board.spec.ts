import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 's@e.com', avatarColor: '#F59E0B', avatarId: null, photoUrl: null };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 10, myRole: 'owner' };
const rankings = [
  {
    rank: 1, userId: 'mugu', displayName: 'mugu', avatarColor: '#888', avatarId: null, photoUrl: null,
    gamesPlayed: 10, wins: 9, losses: 1, pointsFor: 300, pointsAgainst: 280, winRate: 0.9,
    currentStreak: 7, bestStreak: 7, rating: 50.1, provisional: false,
    recentForm: [true, false, true, true, false, true, true, true, false, true],
  },
  {
    rank: 2, userId: 'dinesh', displayName: 'Dinesh K', avatarColor: '#888', avatarId: null, photoUrl: null,
    gamesPlayed: 3, wins: 1, losses: 2, pointsFor: 60, pointsAgainst: 70, winRate: 0.33,
    currentStreak: -1, bestStreak: 1, rating: 20.4, provisional: true,
    recentForm: [false, true, false],
  },
];

test('leaderboard rows show per-player form dots, oldest first', async ({ page }) => {
  await page.addInitScript(([u, g, rk]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.includes('/leaderboard')) return json({ rankings: JSON.parse(rk), minGamesToRank: 5 });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(group), JSON.stringify(rankings)]);

  await page.goto('/board', { waitUntil: 'networkidle' });

  const muguRow = page.locator('.ranking-row', { hasText: 'mugu' });
  await expect(muguRow.locator('.form-dot')).toHaveCount(10);
  // Oldest (index 0, a win) first — first dot carries the win color, not the newest result's.
  await expect(muguRow.locator('.form-dot').first()).toHaveClass(/win/);

  const dineshRow = page.locator('.ranking-row', { hasText: 'Dinesh K' });
  await expect(dineshRow.locator('.form-dot')).toHaveCount(3);
});
