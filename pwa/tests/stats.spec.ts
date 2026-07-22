import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 's@e.com', avatarColor: '#F59E0B', avatarId: null, photoUrl: null };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 94, myRole: 'owner' };
const R = (rank: number, id: string, n: string, gp: number, pf: number, wr: number, cur: number, best: number) =>
  ({ rank, userId: id, displayName: n, avatarColor: '#888', avatarId: null, photoUrl: null, gamesPlayed: gp, wins: Math.round(gp * wr), losses: gp - Math.round(gp * wr), pointsFor: pf, pointsAgainst: pf - 20, winRate: wr, currentStreak: cur, bestStreak: best });
const rankings = [
  R(1, 'mugu', 'mugu', 10, 300, 0.9, 7, 7),
  R(2, 'dinesh', 'Dinesh K', 56, 1107, 0.54, 1, 4),
  R(3, 'mani', 'Mani partha', 47, 950, 0.53, -1, 10),
  R(4, 'pori', 'Pori', 32, 640, 0.41, 1, 2),
];
const ref = (id: string, n: string) => ({ userId: id, displayName: n, avatarColor: '#888', avatarId: null, photoUrl: null });
const mk = (id: string, t1: ReturnType<typeof ref>[], t2: ReturnType<typeof ref>[], s1: number, s2: number) =>
  ({ id, playedAt: '2026-07-22T10:00:00Z', sets: [{ setNo: 1, team1Score: s1, team2Score: s2 }], teams: [{ teamNo: 1, isWinner: s1 > s2, players: t1 }, { teamNo: 2, isWinner: s2 > s1, players: t2 }] });
const matches = [
  mk('a', [ref('mugu', 'mugu'), ref('pori', 'Pori')], [ref('mani', 'Mani partha'), ref('dinesh', 'Dinesh K')], 21, 15),
  mk('b', [ref('pori', 'Pori'), ref('mugu', 'mugu')], [ref('mani', 'Mani partha'), ref('dinesh', 'Dinesh K')], 21, 17),
  mk('c', [ref('mani', 'Mani partha'), ref('g1', 'Guest 1')], [ref('g2', 'Guest 2'), ref('g3', 'Guest 3')], 8, 21),
];

test('shows records, best partnership, form and biggest win', async ({ page }) => {
  await page.addInitScript(([u, g, rk, ms]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.endsWith('/trophies')) return json([]);
      if (path.includes('/leaderboard')) return json({ rankings: JSON.parse(rk), minGamesToRank: 10 });
      if (path.includes('/matches')) return json({ matches: JSON.parse(ms) });
      if (path.includes('/stats')) return json({ userId: 'me', displayName: 'Sriram', recentMatches: [] });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(group), JSON.stringify(rankings), JSON.stringify(matches)]);

  await page.goto('/stats', { waitUntil: 'networkidle' });

  // Records — total + a couple of leaders (win leader is mugu, most active Dinesh).
  await expect(page.getByText('94', { exact: true })).toBeVisible();
  await expect(page.getByText('WIN LEADER')).toBeVisible();
  await expect(page.getByText('56 games')).toBeVisible();
  await expect(page.getByText('10 in a row')).toBeVisible();

  // Best partnership: mugu & Pori won both their games together → 100%.
  await expect(page.getByText('mugu & Pori')).toBeVisible();
  await expect(page.getByText('2W / 2 games together')).toBeVisible();
  await expect(page.getByText('100%')).toBeVisible();

  // Form + biggest win.
  await expect(page.getByText('FORM · recent')).toBeVisible();
  await expect(page.getByText('+13 pts')).toBeVisible();
  await expect(page.getByText('Guest 2 & Guest 3')).toBeVisible();
});
