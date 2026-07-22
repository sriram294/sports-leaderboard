import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 'sriram@example.com', avatarColor: '#F59E0B' };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 3, myRole: 'owner' };

const ref = (id: string, name: string) => ({ userId: id, displayName: name, avatarColor: '#888', avatarId: null, photoUrl: null });
const match = (id: string, day: string, mine: boolean) => ({
  id, playedAt: `2026-07-${day}T10:00:00Z`,
  sets: [{ setNo: 1, team1Score: 21, team2Score: 15 }],
  teams: [
    { teamNo: 1, isWinner: true, players: [mine ? ref('me', 'Sriram') : ref('x', 'Raja'), ref('g1', 'Guest 1')] },
    { teamNo: 2, isWinner: false, players: [ref('b', 'Balaji'), ref('c', 'Mani partha')] },
  ],
});
// Two days; one match is the signed-in user's, for the "My matches" filter.
const allMatches = [match('m1', '22', true), match('m2', '22', false), match('m3', '21', false)];

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([u, g, matchesJson]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    const all = JSON.parse(matchesJson);
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const parsed = new URL(url, location.origin);
      const path = parsed.pathname;
      const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (/\/matches\/[^/]+$/.test(path)) {
        const found = all.find((m: { id: string }) => path.endsWith(m.id));
        return json({ ...found, recordedBy: { userId: 'me', displayName: 'Sriram' }, recordedAt: found.playedAt, events: [{ userId: 'me', displayName: 'Sriram', action: 'created', createdAt: found.playedAt }] });
      }
      if (path.includes('/matches')) {
        const mine = parsed.searchParams.get('mine') === 'true';
        return json({ matches: mine ? all.filter((m: { teams: { players: { userId: string }[] }[] }) => m.teams.some(t => t.players.some(p => p.userId === 'me'))) : all });
      }
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 10 });
      if (path.includes('/stats')) return json({ userId: 'me', displayName: 'Sriram', recentMatches: [] });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(group), JSON.stringify(allMatches)]);
});

test('lists matches by day, expands a card, and filters to my matches', async ({ page }) => {
  await page.goto('/matches', { waitUntil: 'networkidle' });

  // Grouped, newest day first; both day headers present.
  await expect(page.getByText('22 Jul · 2 matches')).toBeVisible();
  await expect(page.getByText('21 Jul · 1 match')).toBeVisible();
  await expect(page.getByText('3 matches', { exact: true })).toBeVisible();

  // Expanding a card fetches detail: game breakdown + winner + history + moderator actions.
  await page.locator('.match-row').first().click();
  await expect(page.getByText('GAME BREAKDOWN')).toBeVisible();
  await expect(page.getByText('Winner: Sriram & Guest 1')).toBeVisible();
  await expect(page.getByText('Recorded this match', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete match' })).toBeVisible();

  // Delete confirm is a guarded dialog; cancel leaves the match in place.
  await page.getByRole('button', { name: 'Delete match' }).click();
  await expect(page.getByText('Delete match?')).toBeVisible();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByText('Delete match?')).not.toBeVisible();

  // "My matches" scopes to the one match the signed-in user played.
  await page.getByRole('button', { name: 'My matches' }).click();
  await expect(page.getByText('1 match', { exact: true })).toBeVisible();
  await expect(page.getByText('22 Jul · 1 match')).toBeVisible();
  await expect(page.getByText('21 Jul', { exact: false })).not.toBeVisible();
});
