import type { Ranking } from './models';
export type SortKey = 'rank' | 'gamesPlayed' | 'wins' | 'losses' | 'pointsFor' | 'winRate';
export function sortRankings(rows: Ranking[], key: SortKey): Ranking[] { if (key === 'rank') return rows; return [...rows].sort((a, b) => (b[key] as number) - (a[key] as number) || a.rank - b.rank); }
export function initials(name: string) { return name.trim().split(/\s+/).slice(0, 2).map(p => p[0]).join('').toUpperCase() || '?'; }
export function formatRate(rate: number) { return `${Math.round(rate * 100)}%`; }
export function deriveInsights(rows: Ranking[]) { const total = rows.reduce((n, r) => n + r.gamesPlayed, 0) / 4; const wins = rows.reduce((n, r) => n + r.wins, 0) / 2; return { matches: Math.round(total), wins: Math.round(wins), bestStreak: Math.max(0, ...rows.map(r => r.bestStreak)) }; }
