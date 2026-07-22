import { describe, expect, it } from 'vitest';
import { leaderboardText } from './share';
import type { Group, Ranking } from './models';

describe('leaderboard sharing', () => {
  it('creates concise share text from canonical ranking order', () => {
    const group = { id: 'g', name: 'Saturday Smashers', avatarColor: '#fff', sportCode: 'badminton_doubles', memberCount: 2, matchCount: 1, myRole: 'member' } satisfies Group;
    const row = { rank: 1, userId: 'u', displayName: 'Priya Sharma', avatarColor: '#fff', gamesPlayed: 1, wins: 1, losses: 0, pointsFor: 21, winRate: 1, currentStreak: 1, bestStreak: 1 } satisfies Ranking;
    expect(leaderboardText(group, [row])).toBe('Saturday Smashers leaderboard — 1. Priya Sharma');
  });
});
