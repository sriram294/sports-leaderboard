import { describe, expect, it } from 'vitest';
import type { Match } from './models';
import {
  attendanceDays,
  dayKey,
  heatmapMonths,
  heatmapWindow,
  monthCells,
  percent,
  recentMatchRow,
  streakLabel,
  weekColumns,
} from './domain';

describe('stat formatting', () => {
  it('rounds win rate to a whole percent', () => {
    expect(percent(0.826)).toBe(83);
    expect(percent(0.5)).toBe(50);
  });
  it('signs the streak (loss negative, win positive)', () => {
    expect(streakLabel(-2)).toBe('-2');
    expect(streakLabel(4)).toBe('+4');
    expect(streakLabel(0)).toBe('0');
  });
});

describe('heatmap months', () => {
  it('returns the last N calendar months, oldest first, including the current one', () => {
    const months = heatmapMonths(new Date(2026, 6, 22), 3); // Jul 2026
    expect(months).toEqual([{ year: 2026, month: 5 }, { year: 2026, month: 6 }, { year: 2026, month: 7 }]);
  });
  it('crosses a year boundary correctly', () => {
    expect(heatmapMonths(new Date(2026, 0, 10), 3)).toEqual([
      { year: 2025, month: 11 }, { year: 2025, month: 12 }, { year: 2026, month: 1 },
    ]);
  });
});

describe('month cells (Monday-first)', () => {
  it('pads leading blanks for the weekday the 1st falls on, and fills to whole weeks', () => {
    // 1 Jul 2026 is a Wednesday → 2 leading blanks (Mon, Tue).
    const cells = monthCells({ year: 2026, month: 7 });
    expect(cells.slice(0, 3)).toEqual([null, null, '2026-07-01']);
    expect(cells.length % 7).toBe(0);
    expect(cells).toContain('2026-07-31');
    // No day leaks past the month.
    expect(cells.filter(c => c && !c.startsWith('2026-07'))).toEqual([]);
  });
  it('splits into 7-row Mon..Sun columns', () => {
    const columns = weekColumns({ year: 2026, month: 7 });
    expect(columns.every(col => col.length === 7)).toBe(true);
  });
});

describe('heatmap window & attendance', () => {
  it('spans first-of-first-month to first-of-month-after-last', () => {
    const window = heatmapWindow([{ year: 2026, month: 5 }, { year: 2026, month: 6 }, { year: 2026, month: 7 }]);
    expect(new Date(window.from)).toEqual(new Date(2026, 4, 1));
    expect(new Date(window.to)).toEqual(new Date(2026, 7, 1));
  });
  it('maps played instants to a set of local day-keys', () => {
    const days = attendanceDays(['2026-07-22T10:00:00Z', '2026-07-22T14:00:00Z', '2026-07-20T09:00:00Z']);
    expect(days.has(dayKey(new Date('2026-07-22T10:00:00Z')))).toBe(true);
    expect(days.size).toBe(2); // two distinct local days
  });
});

describe('recent match rows', () => {
  const ref = (userId: string, displayName: string) => ({ userId, displayName, avatarColor: '#fff' });
  const match: Match = {
    id: 'm1', playedAt: '2026-07-22T10:00:00Z',
    sets: [{ setNo: 1, team1Score: 15, team2Score: 21 }],
    teams: [
      { teamNo: 1, isWinner: false, players: [ref('me', 'Sriram'), ref('pori', 'Pori')] },
      { teamNo: 2, isWinner: true, players: [ref('mani', 'Mani partha'), ref('mugu', 'mugu')] },
    ],
  };
  it('frames the match as partner(s) vs opponents from the viewer, with win/loss', () => {
    const row = recentMatchRow(match, 'me');
    expect(row).toMatchObject({ isWin: false, partnerNames: 'Pori', opponentNames: 'Mani partha & mugu' });
    // From the winning side, it's a win with the other pair as opponents.
    expect(recentMatchRow(match, 'mugu')).toMatchObject({ isWin: true, partnerNames: 'Mani partha', opponentNames: 'Sriram & Pori' });
  });
});
