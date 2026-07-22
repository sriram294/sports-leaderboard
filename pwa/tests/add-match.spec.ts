import { expect, test } from '@playwright/test';

const user = { id: 'me', displayName: 'Sriram', email: 'sriram294@gmail.com', avatarColor: '#F59E0B' };
const group = { id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles', memberCount: 12, matchCount: 20, myRole: 'owner' };
const m = (userId: string, displayName: string, role = 'member') => ({ userId, displayName, avatarColor: '#888', avatarId: null, photoUrl: null, role });
const members = { members: [m('me', 'Sriram'), m('pori', 'Pori'), m('mani', 'Mani partha'), m('mugu', 'mugu')], guests: [m('g1', 'Guest 1', 'guest')] };

test('builds two teams, scores a set, and records the match', async ({ page }) => {
  await page.addInitScript(([u, g, mem]) => {
    localStorage.setItem('playboard.session', JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 9e5, user: JSON.parse(u) }));
    localStorage.setItem('playboard.group', 'g1');
    (window as unknown as { __posted?: unknown }).__posted = undefined;
    const orig = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : (input as Request).url ?? String(input);
      const path = new URL(url, location.origin).pathname;
      const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (path.endsWith('/users/me')) return json(JSON.parse(u));
      if (path.endsWith('/groups')) return json({ groups: [JSON.parse(g)] });
      if (path.endsWith('/members')) return json(JSON.parse(mem));
      if (path.includes('/matches') && init?.method === 'POST') {
        (window as unknown as { __posted?: unknown }).__posted = JSON.parse(String(init.body));
        return json({ id: 'new-match' });
      }
      if (path.includes('/leaderboard')) return json({ rankings: [], minGamesToRank: 10 });
      if (path.includes('/stats')) return json({ userId: 'me', displayName: 'Sriram', recentMatches: [] });
      if (path.includes('/matches')) return json({ matches: [] });
      return orig(input, init);
    };
  }, [JSON.stringify(user), JSON.stringify(group), JSON.stringify(members)]);

  await page.goto('/add', { waitUntil: 'networkidle' });

  // Record Match is disabled until the form is valid.
  await expect(page.getByRole('button', { name: 'Record Match' })).toBeDisabled();

  // Fill all four slots via the picker (each pick assigns to the slot's team, then closes).
  for (let i = 0; i < 4; i++) {
    await page.locator('.slot.empty').first().click();
    await page.locator('.sheet-row').first().click();
  }

  // Score a set → Team 1 auto-wins and its Who-Won card is selected.
  await page.getByLabel('Team 1 set 1').fill('21');
  await page.getByLabel('Team 2 set 1').fill('15');
  await expect(page.locator('.winner-card.selected')).toContainText('Team 1');

  // Record → POST body is well-formed and we navigate to the Board.
  await expect(page.getByRole('button', { name: 'Record Match' })).toBeEnabled();
  await page.getByRole('button', { name: 'Record Match' }).click();
  await expect(page.getByText('TOP PLAYERS')).toBeVisible();

  const posted = await page.evaluate(() => (window as unknown as { __posted?: Record<string, unknown> }).__posted);
  expect(posted).toMatchObject({
    teams: [{ teamNo: 1, playerIds: ['me', 'pori'] }, { teamNo: 2, playerIds: ['mani', 'mugu'] }],
    sets: [{ setNo: 1, team1Score: 21, team2Score: 15 }],
    winningTeamNo: 1,
  });
});
