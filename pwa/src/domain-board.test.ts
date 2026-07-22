import { describe, expect, it } from 'vitest';
import type { MatchSummary, Ranking } from './models';
import {
  isWinForMatch,
  metricColor,
  metricValue,
  nextMetric,
  podium,
  rangeWindow,
  rankColor,
  ratingLabel,
  ratingColor,
  recentForm,
  secondaryLine,
  tableRows,
  winRateColor,
} from './domain';

const row = (over: Partial<Ranking>): Ranking => ({
  rank: 1, userId: 'u', displayName: 'Player', avatarColor: '#fff',
  gamesPlayed: 10, wins: 6, losses: 4, pointsFor: 200, pointsAgainst: 150,
  winRate: 0.6, currentStreak: 1, bestStreak: 3, rating: 43.5, provisional: false,
  ...over,
});

describe('ranking display helpers', () => {
  it('builds the secondary line with a signed points diff', () => {
    expect(secondaryLine(row({ gamesPlayed: 37, wins: 22, losses: 15, pointsFor: 900, pointsAgainst: 824, winRate: 0.595 }), 10))
      .toBe('37 games · 22-15 · 60% · +76');
  });

  it('rounds win rate rather than truncating', () => {
    // 3/7 = 42.857% must read 43%, not 42%.
    expect(secondaryLine(row({ gamesPlayed: 7, wins: 3, losses: 4, winRate: 3 / 7, pointsFor: 100, pointsAgainst: 100 }), 1))
      .toBe('7 games · 3-4 · 43% · 0');
  });

  it('shows "N more to rank" for a provisional player short of the threshold', () => {
    expect(secondaryLine(row({ gamesPlayed: 5, provisional: true }), 10))
      .toBe('5 games · 6-4 · 60% · 5 more to rank');
  });

  it('labels rating to one decimal, "prov" while unranked, win% on a pre-ratings backend', () => {
    expect(ratingLabel(row({ rating: 59.6 }))).toBe('59.6');
    expect(ratingLabel(row({ provisional: true, rating: 48 }))).toBe('prov');
    expect(ratingLabel(row({ rating: null, winRate: 0.9 }))).toBe('90%');
  });

  it('picks the metric value per column, and "prov" for unranked whatever the metric', () => {
    const r = row({ rating: 40, winRate: 0.6, gamesPlayed: 10, pointsFor: 200, pointsAgainst: 150 });
    expect(metricValue(r, 'rating')).toBe('40.0');
    expect(metricValue(r, 'winRate')).toBe('60%');
    expect(metricValue(r, 'games')).toBe('10');
    expect(metricValue(r, 'pointsDiff')).toBe('+50');
    expect(metricValue(row({ provisional: true }), 'winRate')).toBe('prov');
  });
});

describe('tier colors', () => {
  it('colors ranks 1-3 then muted', () => {
    expect(rankColor(1)).toBe('var(--rank-1)');
    expect(rankColor(2)).toBe('var(--rank-2)');
    expect(rankColor(3)).toBe('var(--rank-3)');
    expect(rankColor(4)).toBe('var(--muted)');
  });
  it('tiers win% at 50/25 and rating at 40/25', () => {
    expect(winRateColor(50)).toBe('var(--brand)');
    expect(winRateColor(30)).toBe('var(--rate-mid)');
    expect(winRateColor(10)).toBe('var(--rate-low)');
    expect(ratingColor(45)).toBe('var(--brand)');
    expect(ratingColor(30)).toBe('var(--rate-mid)');
    expect(ratingColor(10)).toBe('var(--rate-low)');
  });
  it('greys a provisional metric whatever the column', () => {
    expect(metricColor(row({ provisional: true }), 'rating')).toBe('var(--muted)');
  });
});

describe('table ordering', () => {
  const rows: Ranking[] = [
    row({ rank: 1, userId: 'a', rating: 50, winRate: 0.9, gamesPlayed: 10, pointsFor: 300, pointsAgainst: 100 }),
    row({ rank: 2, userId: 'b', rating: 40, winRate: 0.5, gamesPlayed: 40, pointsFor: 400, pointsAgainst: 420 }),
    row({ rank: 3, userId: 'c', rating: 30, winRate: 0.6, gamesPlayed: 20, pointsFor: 200, pointsAgainst: 150 }),
    row({ userId: 'p', rating: 48, winRate: 0.8, gamesPlayed: 4, provisional: true, pointsFor: 90, pointsAgainst: 60 }),
  ];

  it('keeps server order for the rating metric, provisional always last', () => {
    expect(tableRows(rows, 'rating').map(r => r.userId)).toEqual(['a', 'b', 'c', 'p']);
  });
  it('re-sorts ranked players by the chosen metric without floating a provisional newcomer up', () => {
    // Sorting by games would put the 40-game player top, but the 4-game provisional stays last.
    expect(tableRows(rows, 'games').map(r => r.userId)).toEqual(['b', 'c', 'a', 'p']);
    expect(tableRows(rows, 'winRate').map(r => r.userId)).toEqual(['a', 'c', 'b', 'p']);
  });
  it('excludes provisional players from the podium', () => {
    expect(podium(rows).map(r => r.userId)).toEqual(['a', 'b', 'c']);
  });
});

describe('calendar window', () => {
  it('scopes "month" to the current calendar month and "all" to no window', () => {
    const now = new Date(2026, 6, 22, 15, 30); // 22 Jul 2026, local
    const window = rangeWindow('month', now);
    expect(window).not.toBeNull();
    expect(new Date(window!.from)).toEqual(new Date(2026, 6, 1));
    expect(new Date(window!.to)).toEqual(new Date(2026, 7, 1));
    expect(rangeWindow('all', now)).toBeNull();
  });
});

describe('sort metric cycle', () => {
  it('cycles rating → winRate → games → pointsDiff → rating', () => {
    expect(nextMetric('rating')).toBe('winRate');
    expect(nextMetric('winRate')).toBe('games');
    expect(nextMetric('games')).toBe('pointsDiff');
    expect(nextMetric('pointsDiff')).toBe('rating');
  });
});

describe('form derivation', () => {
  const match = (isWinnerForMe: boolean, includeMe = true): MatchSummary => ({
    id: Math.random().toString(), playedAt: '2026-07-20T00:00:00Z', sets: [],
    teams: [
      { teamNo: 1, isWinner: isWinnerForMe, players: includeMe ? [{ userId: 'me', displayName: 'Me', avatarColor: '#fff' }] : [{ userId: 'x', displayName: 'X', avatarColor: '#fff' }] },
      { teamNo: 2, isWinner: !isWinnerForMe, players: [{ userId: 'y', displayName: 'Y', avatarColor: '#fff' }] },
    ],
  });

  it('reports win/loss for the user, null when they did not play', () => {
    expect(isWinForMatch(match(true), 'me')).toBe(true);
    expect(isWinForMatch(match(false), 'me')).toBe(false);
    expect(isWinForMatch(match(true, false), 'me')).toBeNull();
  });

  it('collects at most the last 5 results, newest first, skipping matches the user missed', () => {
    const matches = [match(true), match(true, false), match(false), match(true), match(false), match(true), match(true)];
    expect(recentForm(matches, 'me')).toEqual([true, false, true, false, true]);
  });
});
