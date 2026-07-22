import { describe, expect, it } from 'vitest';
import type { Match, Ranking } from './models';
import {
  computeBestPartnership,
  computeBiggestWin,
  computeRecentForm,
  computeRecords,
  monthlyTrophyLabel,
} from './domain';

const rank = (over: Partial<Ranking>): Ranking => ({
  rank: 1, userId: 'u', displayName: 'P', avatarColor: '#fff',
  gamesPlayed: 10, wins: 6, losses: 4, pointsFor: 200, pointsAgainst: 150,
  winRate: 0.6, currentStreak: 0, bestStreak: 0, ...over,
});

const rankings: Ranking[] = [
  rank({ rank: 1, userId: 'mugu', displayName: 'mugu', gamesPlayed: 10, wins: 9, pointsFor: 300, winRate: 0.9, currentStreak: 7, bestStreak: 7 }),
  rank({ rank: 2, userId: 'dinesh', displayName: 'Dinesh K', gamesPlayed: 56, wins: 30, pointsFor: 1107, winRate: 0.54, currentStreak: 1, bestStreak: 4 }),
  rank({ rank: 3, userId: 'mani', displayName: 'Mani partha', gamesPlayed: 47, wins: 25, pointsFor: 950, winRate: 0.53, currentStreak: -1, bestStreak: 10 }),
  rank({ rank: 4, userId: 'new', displayName: 'Newbie', gamesPlayed: 1, wins: 1, pointsFor: 21, winRate: 1, currentStreak: 1, bestStreak: 1 }),
];

describe('records', () => {
  it('derives each record from the leaderboard, ignoring a 1-game 100% for the win leader', () => {
    const r = computeRecords(rankings, 94);
    expect(r.totalMatches).toBe(94);
    expect(r.winLeader?.userId).toBe('mugu'); // Newbie's 1-game 100% is excluded (< 2 games)
    expect(r.mostPoints?.userId).toBe('dinesh');
    expect(r.mostActive?.userId).toBe('dinesh');
    expect(r.longestStreak?.userId).toBe('mani'); // best streak 10
    expect(r.currentStreak?.userId).toBe('mugu'); // hot streak 7
  });
  it('hides streak records below the minimum run', () => {
    const cool = [rank({ userId: 'a', currentStreak: 1, bestStreak: 1 })];
    const r = computeRecords(cool, 3);
    expect(r.longestStreak).toBeUndefined();
    expect(r.currentStreak).toBeUndefined();
  });
});

const ref = (userId: string, displayName: string) => ({ userId, displayName, avatarColor: '#fff' });
const match = (id: string, t1: ReturnType<typeof ref>[], t2: ReturnType<typeof ref>[], t1Score: number, t2Score: number): Match => ({
  id, playedAt: '2026-07-22T10:00:00Z',
  sets: [{ setNo: 1, team1Score: t1Score, team2Score: t2Score }],
  teams: [
    { teamNo: 1, isWinner: t1Score > t2Score, players: t1 },
    { teamNo: 2, isWinner: t2Score > t1Score, players: t2 },
  ],
});

describe('best partnership', () => {
  it('picks the pair with the best win rate (min 2 games), order-independent', () => {
    const matches = [
      match('m1', [ref('mugu', 'mugu'), ref('pori', 'Pori')], [ref('a', 'A'), ref('b', 'B')], 21, 10),
      match('m2', [ref('pori', 'Pori'), ref('mugu', 'mugu')], [ref('a', 'A'), ref('b', 'B')], 21, 15),
      match('m3', [ref('a', 'A'), ref('b', 'B')], [ref('c', 'C'), ref('d', 'D')], 21, 5),
    ];
    const p = computeBestPartnership(matches);
    expect(p).toMatchObject({ gamesTogether: 2, winsTogether: 2, winRate: 1 });
    expect([p!.player1.userId, p!.player2.userId].sort()).toEqual(['mugu', 'pori']);
  });
  it('is null when no pair reaches the minimum games', () => {
    expect(computeBestPartnership([match('m1', [ref('a', 'A'), ref('b', 'B')], [ref('c', 'C'), ref('d', 'D')], 21, 5)])).toBeNull();
  });
});

describe('recent form', () => {
  it("gives each ranked player their last results newest-first, dropping absentees", () => {
    const matches = [
      match('m1', [ref('mugu', 'mugu'), ref('x', 'X')], [ref('y', 'Y'), ref('z', 'Z')], 21, 10), // mugu win
      match('m2', [ref('y', 'Y'), ref('z', 'Z')], [ref('mugu', 'mugu'), ref('x', 'X')], 21, 12), // mugu loss
    ];
    const form = computeRecentForm(matches, [rank({ userId: 'mugu', displayName: 'mugu' }), rank({ userId: 'ghost', displayName: 'Ghost' })]);
    expect(form).toHaveLength(1); // Ghost played nothing → dropped
    expect(form[0].player.userId).toBe('mugu');
    expect(form[0].results).toEqual([true, false]);
  });
});

describe('biggest win', () => {
  it('is the recent match with the largest total-points margin', () => {
    const matches = [
      match('close', [ref('a', 'A'), ref('b', 'B')], [ref('c', 'C'), ref('d', 'D')], 21, 19),
      match('blowout', [ref('a', 'A'), ref('b', 'B')], [ref('c', 'C'), ref('d', 'D')], 8, 21),
    ];
    const big = computeBiggestWin(matches);
    expect(big?.match.id).toBe('blowout');
    expect(big?.margin).toBe(13);
  });
  it('is null with no matches', () => {
    expect(computeBiggestWin([])).toBeNull();
  });
});

describe('monthly trophy label', () => {
  it('formats YYYY-MM as MON \'YY', () => {
    expect(monthlyTrophyLabel('2026-07')).toBe("JUL '26");
    expect(monthlyTrophyLabel('2025-12')).toBe("DEC '25");
  });
});
