import type { MonthlyFinish } from '../../models';

export type FinishPoint = { index: number; x: number; y: number; rank: number };
export type FinishSegment = { from: FinishPoint; to: FinishPoint };
export type FinishChart = { finishes: MonthlyFinish[]; lowerBound: number; points: FinishPoint[]; segments: FinishSegment[] };

/** Sorts/caps the wire series and derives normalized coordinates with rank #1 at y=0. */
export function deriveFinishChart(input: MonthlyFinish[]): FinishChart {
  const finishes = [...input].sort((a, b) => a.month.localeCompare(b.month)).slice(-12);
  const lowerBound = Math.max(3, ...finishes.map(f => f.qualifiedPlayers));
  const denominator = Math.max(1, finishes.length - 1);
  const points = finishes.flatMap((finish, index): FinishPoint[] => finish.rank == null ? [] : [{
    index,
    x: index / denominator,
    y: (Math.min(lowerBound, Math.max(1, finish.rank)) - 1) / (lowerBound - 1),
    rank: finish.rank,
  }]);
  const byIndex = new Map(points.map(point => [point.index, point]));
  const segments = finishes.slice(0, -1).flatMap((_, index): FinishSegment[] => {
    const from = byIndex.get(index);
    const to = byIndex.get(index + 1);
    return from && to ? [{ from, to }] : [];
  });
  return { finishes, lowerBound, points, segments };
}

export function finishMonthLabel(month: string, long = false): string {
  const [year, number] = month.split('-').map(Number);
  return new Intl.DateTimeFormat('en', { month: long ? 'long' : 'short', ...(long ? { year: 'numeric' } : {}), timeZone: 'UTC' })
    .format(new Date(Date.UTC(year, number - 1, 1)));
}

export function finishDescription(finish: MonthlyFinish): string {
  const month = finishMonthLabel(finish.month, true);
  return finish.rank == null ? `${month}: no qualified finish` : `${month}: #${finish.rank} of ${finish.qualifiedPlayers}`;
}

export function finishesDescription(finishes: MonthlyFinish[]): string {
  const months = finishes.map(finishDescription).join('. ');
  return months ? `Finishing positions are recorded after each month closes. ${months}`
    : 'Finishing positions are recorded after each month closes.';
}
