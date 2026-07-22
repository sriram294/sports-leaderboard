import { describe, expect, it } from 'vitest';
import { deriveInsights, formatRate, initials, sortRankings } from './domain';
import type { Ranking } from './models';

const rows: Ranking[] = [
  { rank: 1, userId: 'a', displayName: 'Priya Sharma', avatarColor: '#fff', gamesPlayed: 8, wins: 6, losses: 2, pointsFor: 200, winRate: .75, currentStreak: 2, bestStreak: 4 },
  { rank: 2, userId: 'b', displayName: 'Raj Kumar', avatarColor: '#fff', gamesPlayed: 8, wins: 5, losses: 3, pointsFor: 220, winRate: .625, currentStreak: 1, bestStreak: 3 }
];

describe('leaderboard domain helpers', () => {
  it('sorts by a selected column while preserving canonical rank for ties', () => {
    expect(sortRankings(rows, 'pointsFor').map(r => r.userId)).toEqual(['b', 'a']);
    expect(sortRankings(rows, 'rank')).toEqual(rows);
  });
  it('formats rates and initials for UI fallbacks', () => {
    expect(formatRate(.625)).toBe('63%');
    expect(initials('Priya Sharma')).toBe('PS');
    expect(initials('')).toBe('?');
  });
  it('derives stable group insights from rankings', () => {
    expect(deriveInsights(rows)).toEqual({ matches: 4, wins: 6, bestStreak: 4 });
  });
});
