import { describe, expect, it } from 'vitest';
import type { Match } from './models';
import {
  actionLabel,
  groupMatchesByDate,
  isGuestRef,
  matchTeam,
  teamName,
  winningTeamNo,
} from './domain';

const ref = (userId: string, displayName: string) => ({ userId, displayName, avatarColor: '#fff' });
const match = (id: string, playedAt: string, winner: 1 | 2): Match => ({
  id, playedAt,
  sets: [{ setNo: 1, team1Score: winner === 1 ? 21 : 15, team2Score: winner === 1 ? 15 : 21 }],
  teams: [
    { teamNo: 1, isWinner: winner === 1, players: [ref('a', 'Sriram'), ref('b', 'Pori')] },
    { teamNo: 2, isWinner: winner === 2, players: [ref('c', 'Mani partha'), ref('g1', 'Guest 1')] },
  ],
});

describe('match display helpers', () => {
  const m = match('m1', '2026-07-22T10:00:00Z', 1);
  it('joins full display names with " & "', () => {
    expect(teamName(matchTeam(m, 1))).toBe('Sriram & Pori');
    expect(teamName(matchTeam(m, 2))).toBe('Mani partha & Guest 1');
    expect(teamName(undefined)).toBe('');
  });
  it('resolves the winning team number', () => {
    expect(winningTeamNo(m)).toBe(1);
    expect(winningTeamNo(match('m2', '2026-07-22T10:00:00Z', 2))).toBe(2);
  });
  it('detects guest refs from the "Guest N" name only', () => {
    expect(isGuestRef(ref('g1', 'Guest 1'))).toBe(true);
    expect(isGuestRef(ref('g2', 'guest'))).toBe(true);
    expect(isGuestRef(ref('a', 'Sriram'))).toBe(false);
    expect(isGuestRef(ref('x', 'Guster'))).toBe(false);
  });
  it('labels audit actions', () => {
    expect(actionLabel('created')).toBe('Recorded this match');
    expect(actionLabel('edited')).toBe('Edited this match');
    expect(actionLabel('updated')).toBe('Edited this match');
    expect(actionLabel('mystery')).toBe('mystery');
  });
});

describe('grouping by day', () => {
  it('groups a newest-first list into per-day sections preserving order', () => {
    // Two days; local-day keys. Use midday UTC times to avoid timezone day-flips in CI.
    const matches = [
      match('m1', '2026-07-22T12:00:00Z', 1),
      match('m2', '2026-07-22T09:00:00Z', 2),
      match('m3', '2026-07-21T12:00:00Z', 1),
    ];
    const sections = groupMatchesByDate(matches);
    expect(sections).toHaveLength(2);
    expect(sections[0].matches.map(x => x.id)).toEqual(['m1', 'm2']);
    expect(sections[1].matches.map(x => x.id)).toEqual(['m3']);
    // Section date keys are YYYY-MM-DD and distinct.
    expect(sections[0].date).not.toBe(sections[1].date);
    expect(sections[0].date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
  it('returns no sections for an empty log', () => {
    expect(groupMatchesByDate([])).toEqual([]);
  });
});
