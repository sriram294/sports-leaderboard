import { describe, expect, it } from 'vitest';
import { deriveFinishChart, finishDescription, finishesDescription } from './monthly-finishes';

describe('monthly finish chart', () => {
  it('inverts ranks and only joins consecutive ranked months', () => {
    const chart = deriveFinishChart([
      { month: '2026-01', rank: 3, qualifiedPlayers: 6 },
      { month: '2026-02', rank: 1, qualifiedPlayers: 5 },
      { month: '2026-03', rank: null, qualifiedPlayers: 7 },
      { month: '2026-04', rank: 2, qualifiedPlayers: 7 },
      { month: '2026-05', rank: 4, qualifiedPlayers: 7 },
    ]);
    expect(chart.lowerBound).toBe(7);
    expect(chart.points[1].y).toBe(0);
    expect(chart.segments.map(s => [s.from.index, s.to.index])).toEqual([[0, 1], [3, 4]]);
  });

  it('keeps only the newest 12 months in chronological order', () => {
    const input = Array.from({ length: 14 }, (_, index) => {
      const date = new Date(Date.UTC(2025, 13 - index, 1));
      return { month: date.toISOString().slice(0, 7), rank: 1, qualifiedPlayers: 2 };
    });
    const chart = deriveFinishChart(input);
    expect(chart.finishes).toHaveLength(12);
    expect(chart.finishes.map(f => f.month)).toEqual([...chart.finishes.map(f => f.month)].sort());
  });

  it('describes ranked and gap months accessibly', () => {
    expect(finishDescription({ month: '2026-09', rank: 3, qualifiedPlayers: 8 })).toBe('September 2026: #3 of 8');
    expect(finishDescription({ month: '2026-10', rank: null, qualifiedPlayers: 8 })).toBe('October 2026: no qualified finish');
    expect(finishesDescription([{ month: '2026-10', rank: null, qualifiedPlayers: 8 }]))
      .toContain('October 2026: no qualified finish');
  });
});
