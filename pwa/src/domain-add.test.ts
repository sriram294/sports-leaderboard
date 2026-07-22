import { describe, expect, it } from 'vitest';
import type { Member } from './models';
import {
  autoWinner,
  availablePlayers,
  buildRecordRequest,
  memberSlotLabel,
  parseSet,
  setsValid,
} from './domain';

const member = (userId: string, displayName: string, role: Member['role'] = 'member'): Member => ({ userId, displayName, avatarColor: '#fff', role });

describe('set parsing & validation', () => {
  it('parses two non-negative integers, rejecting blanks/negatives/non-integers', () => {
    expect(parseSet({ team1: '21', team2: '15' })).toEqual([21, 15]);
    expect(parseSet({ team1: '', team2: '15' })).toBeNull();
    expect(parseSet({ team1: '21', team2: '' })).toBeNull();
    expect(parseSet({ team1: '-1', team2: '5' })).toBeNull();
    expect(parseSet({ team1: '2.5', team2: '5' })).toBeNull();
  });
  it('requires every set complete and non-tied', () => {
    expect(setsValid([{ team1: '21', team2: '15' }])).toBe(true);
    expect(setsValid([])).toBe(false);
    expect(setsValid([{ team1: '21', team2: '21' }])).toBe(false); // tie
    expect(setsValid([{ team1: '21', team2: '15' }, { team1: '10', team2: '' }])).toBe(false);
  });
});

describe('auto winner', () => {
  it('is the side that won more sets, null when tied or invalid', () => {
    expect(autoWinner([{ team1: '21', team2: '15' }])).toBe(1);
    expect(autoWinner([{ team1: '15', team2: '21' }])).toBe(2);
    expect(autoWinner([{ team1: '21', team2: '15' }, { team1: '10', team2: '21' }])).toBeNull(); // 1–1
    expect(autoWinner([{ team1: '21', team2: '15' }, { team1: '10', team2: '21' }, { team1: '21', team2: '19' }])).toBe(1);
    expect(autoWinner([{ team1: '', team2: '' }])).toBeNull();
  });
});

describe('available players (guest collapse)', () => {
  const roster: Member[] = [
    member('a', 'Sriram'), member('b', 'Pori'),
    member('g1', 'Guest 1', 'guest'), member('g2', 'Guest 2', 'guest'), member('g3', 'Guest 3', 'guest'),
  ];
  it('shows all real members plus a single collapsed guest entry', () => {
    const out = availablePlayers(roster, new Set());
    expect(out.map(m => m.userId)).toEqual(['a', 'b', 'g1']); // only the first guest surfaces
  });
  it('advances the collapsed guest as guests get consumed, then drops it', () => {
    expect(availablePlayers(roster, new Set(['g1'])).map(m => m.userId)).toEqual(['a', 'b', 'g2']);
    expect(availablePlayers(roster, new Set(['a', 'g1', 'g2', 'g3'])).map(m => m.userId)).toEqual(['b']);
  });
  it('labels guests generically', () => {
    expect(memberSlotLabel(member('g1', 'Guest 1', 'guest'))).toBe('Guest');
    expect(memberSlotLabel(member('a', 'Sriram'))).toBe('Sriram');
  });
});

describe('request assembly', () => {
  it('builds teams, re-indexed sets, winner, and the given playedAt', () => {
    const body = buildRecordRequest(
      ['a', 'b'], ['c', 'd'],
      [{ team1: '21', team2: '15' }, { team1: '19', team2: '21' }, { team1: '21', team2: '18' }],
      1, '2026-07-22T10:00:00.000Z',
    );
    expect(body).toEqual({
      playedAt: '2026-07-22T10:00:00.000Z',
      teams: [{ teamNo: 1, playerIds: ['a', 'b'] }, { teamNo: 2, playerIds: ['c', 'd'] }],
      sets: [
        { setNo: 1, team1Score: 21, team2Score: 15 },
        { setNo: 2, team1Score: 19, team2Score: 21 },
        { setNo: 3, team1Score: 21, team2Score: 18 },
      ],
      winningTeamNo: 1,
    });
  });
});
