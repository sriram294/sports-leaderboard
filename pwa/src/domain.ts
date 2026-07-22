import type { Match, MatchTeam, Member, PlayerRef, Ranking, RecordMatchRequest } from './models';

export type SortKey = 'rank' | 'gamesPlayed' | 'wins' | 'losses' | 'pointsFor' | 'winRate';
export function sortRankings(rows: Ranking[], key: SortKey): Ranking[] { if (key === 'rank') return rows; return [...rows].sort((a, b) => (b[key] as number) - (a[key] as number) || a.rank - b.rank); }
export function initials(name: string) { return name.trim().split(/\s+/).slice(0, 2).map(p => p[0]).join('').toUpperCase() || '?'; }
export function formatRate(rate: number) { return `${Math.round(rate * 100)}%`; }
export function deriveInsights(rows: Ranking[]) { const total = rows.reduce((n, r) => n + r.gamesPlayed, 0) / 4; const wins = rows.reduce((n, r) => n + r.wins, 0) / 2; return { matches: Math.round(total), wins: Math.round(wins), bestStreak: Math.max(0, ...rows.map(r => r.bestStreak)) }; }

/* ==================== Board leaderboard helpers (ported from PlayerRanking.kt) ==================== */

/** Win rate as a whole percentage, rounded not truncated (0.826 → 83). */
export const winRatePercent = (r: Ranking) => Math.round(r.winRate * 100);
/** Points difference (for − against) — the first tiebreak once ratings are equal. */
export const pointsDiff = (r: Ranking) => r.pointsFor - (r.pointsAgainst ?? 0);
/** [pointsDiff] for display; positive values carry an explicit `+`. */
export const pointsDiffLabel = (r: Ranking) => { const d = pointsDiff(r); return d > 0 ? `+${d}` : `${d}`; };
/** The rating to one decimal, `"prov"` while unranked, or the win rate on a pre-ratings backend. */
export const ratingLabel = (r: Ranking) => r.rating == null ? `${winRatePercent(r)}%` : r.provisional ? 'prov' : r.rating.toFixed(1);
/** Games still needed before this player ranks; 0 once over the line. */
export const gamesNeeded = (r: Ranking, minGamesToRank: number) => Math.max(0, minGamesToRank - r.gamesPlayed);

/**
 * The row's second line, e.g. `"37 games · 22-15 · 59% · +76"`. Provisional players trade
 * the trailing points diff for what they need — how many more games until they rank.
 */
export function secondaryLine(r: Ranking, minGamesToRank: number): string {
  const head = `${r.gamesPlayed} games · ${r.wins}-${r.losses} · ${winRatePercent(r)}%`;
  const needed = gamesNeeded(r, minGamesToRank);
  return r.provisional && needed > 0 ? `${head} · ${needed} more to rank` : `${head} · ${pointsDiffLabel(r)}`;
}

/** What the big right-hand number shows, and what the table is sorted by. */
export type RankingSortMetric = 'rating' | 'winRate' | 'games' | 'pointsDiff';
export const METRIC_ORDER: RankingSortMetric[] = ['rating', 'winRate', 'games', 'pointsDiff'];
export const METRIC_LABEL: Record<RankingSortMetric, string> = { rating: 'RATING', winRate: 'WIN%', games: 'GAMES', pointsDiff: 'DIFF' };
/** The next metric in the cycle — the header is a single tappable control, not a row of them. */
export const nextMetric = (m: RankingSortMetric): RankingSortMetric => METRIC_ORDER[(METRIC_ORDER.indexOf(m) + 1) % METRIC_ORDER.length];

/** The right-hand value for the active metric. Unranked players read `"prov"` whatever the metric. */
export function metricValue(r: Ranking, metric: RankingSortMetric): string {
  if (r.provisional) return 'prov';
  switch (metric) {
    case 'rating': return ratingLabel(r);
    case 'winRate': return `${winRatePercent(r)}%`;
    case 'games': return `${r.gamesPlayed}`;
    case 'pointsDiff': return pointsDiffLabel(r);
  }
}

/* Tier colors — return a CSS custom-property reference so they track the active theme. */
const cssVar = (name: string) => `var(--${name})`;

/** Rank gutter color: #1 brand, #2 textPrimary, #3 winRateMid, else muted. */
export const rankColor = (rank: number) => cssVar(rank === 1 ? 'rank-1' : rank === 2 ? 'rank-2' : rank === 3 ? 'rank-3' : 'muted');
/** Win% tiers: ≥50 brand / ≥25 winRateMid / else winRateLow. */
export const winRateColor = (percent: number) => cssVar(percent >= 50 ? 'brand' : percent >= 25 ? 'rate-mid' : 'rate-low');
/** Rating tiers (Wilson lower bound sits below raw rate): ≥40 brand / ≥25 winRateMid / else winRateLow. */
export const ratingColor = (rating: number) => cssVar(rating >= 40 ? 'brand' : rating >= 25 ? 'rate-mid' : 'rate-low');
/** Points diff: outscoring reads green, being outscored red — matches the W/L colors. */
export const pointsDiffColor = (diff: number) => cssVar(diff > 0 ? 'stat-win' : diff < 0 ? 'stat-loss' : 'muted');

/** Color of the big right-hand metric value for a row. */
export function metricColor(r: Ranking, metric: RankingSortMetric): string {
  if (r.provisional) return cssVar('muted');
  switch (metric) {
    case 'winRate': return winRateColor(winRatePercent(r));
    case 'pointsDiff': return pointsDiffColor(pointsDiff(r));
    case 'games': return cssVar('text');
    case 'rating': return r.rating == null ? winRateColor(winRatePercent(r)) : ratingColor(r.rating);
  }
}

/** Players over the games threshold, in canonical (server) order. */
export const rankedPlayers = (rankings: Ranking[]) => rankings.filter(r => !r.provisional);
/** Top 3 by canonical ranking; provisional players are never crowned. */
export const podium = (rankings: Ranking[]) => rankedPlayers(rankings).slice(0, 3);

/**
 * Rows in display order: ranked players first, provisional last, each partition sorted by the
 * chosen [metric]. Partitioning first keeps a 3-game newcomer from floating up when sorting by
 * games. `rating` keeps server order (its tiebreaks would be lost by re-sorting).
 */
export function tableRows(rankings: Ranking[], metric: RankingSortMetric): Ranking[] {
  const ranked = rankings.filter(r => !r.provisional);
  const prov = rankings.filter(r => r.provisional);
  const by = (rows: Ranking[]): Ranking[] => {
    switch (metric) {
      case 'rating': return rows; // already rating-ordered from the server
      case 'winRate': return [...rows].sort((a, b) => b.winRate - a.winRate);
      case 'games': return [...rows].sort((a, b) => b.gamesPlayed - a.gamesPlayed);
      case 'pointsDiff': return [...rows].sort((a, b) => pointsDiff(b) - pointsDiff(a));
    }
  };
  return [...by(ranked), ...by(prov)];
}

/* ==================== Time range (ported from LeaderboardTimeRange.kt) ==================== */

/** Calendar window the Board is scoped to. `month` = current calendar month (default). */
export type TimeRange = 'month' | 'all';
export const RANGE_LABEL: Record<TimeRange, string> = { month: 'This Month', all: 'All Time' };

/**
 * The `[from, to)` window as ISO-8601 instant strings, computed in local time so calendar
 * boundaries land on local midnight. `null` for `all` (the backend reads the all-time snapshot).
 */
export function rangeWindow(range: TimeRange, now: Date = new Date()): { from: string; to: string } | null {
  if (range === 'all') return null;
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 1);
  return { from: start.toISOString(), to: end.toISOString() };
}

/* ==================== Form bar ==================== */

/** Whether a match was a win for `userId`; `null` if they didn't play in it (`Match.isWinFor`). */
export function isWinForMatch(match: Match, userId: string): boolean | null {
  const team = match.teams.find(t => t.players.some(p => p.userId === userId));
  return team ? team.isWinner : null;
}

/** The signed-in user's most recent results in a group, newest first, `true` = win (≤5). */
export function recentForm(matches: Match[], userId: string): boolean[] {
  return matches.map(m => isWinForMatch(m, userId)).filter((r): r is boolean => r !== null).slice(0, 5);
}

/* ==================== Add / Edit match (ported from AddMatchUiState.kt) ==================== */

/** Doubles: 2 players per team. Only sport is badminton_doubles. */
export const TEAM_SIZE = 2;

/** One set's raw score inputs — kept as strings so partial entry is representable. */
export type SetInput = { team1: string; team2: string };

/** A set parsed to `[team1, team2]`, or `null` if either side is blank/non-integer/negative. */
export function parseSet(set: SetInput): [number, number] | null {
  const a = Number(set.team1), b = Number(set.team2);
  if (set.team1 === '' || set.team2 === '' || !Number.isInteger(a) || !Number.isInteger(b) || a < 0 || b < 0) return null;
  return [a, b];
}

/** Every set fully entered with two non-negative integers and no tie. */
export const setsValid = (sets: SetInput[]) =>
  sets.length > 0 && sets.every(set => { const p = parseSet(set); return p != null && p[0] !== p[1]; });

/** Winner implied by sets won, or `null` if undetermined / tied on set count. */
export function autoWinner(sets: SetInput[]): 1 | 2 | null {
  if (!setsValid(sets)) return null;
  const pairs = sets.map(parseSet).filter((p): p is [number, number] => p != null);
  const t1 = pairs.filter(([a, b]) => a > b).length;
  const t2 = pairs.filter(([a, b]) => b > a).length;
  return t1 > t2 ? 1 : t2 > t1 ? 2 : null;
}

/**
 * Roster members not yet assigned — the picker's choices. The group's interchangeable guest
 * fillers collapse to a **single** "Guest" entry (the first still-unassigned guest): picking it
 * consumes one guest id, so the entry stays until all guests are used, then disappears.
 */
export function availablePlayers(roster: Member[], assignedIds: Set<string>): Member[] {
  const free = roster.filter(m => !assignedIds.has(m.userId));
  const guests = free.filter(m => m.role === 'guest');
  const reals = free.filter(m => m.role !== 'guest');
  return guests.length > 0 ? [...reals, guests[0]] : reals;
}

/** Guests are shown generically as "Guest"; their internal Guest 1/2/3 numbering is hidden. */
export const memberSlotLabel = (member: Member) => (member.role === 'guest' ? 'Guest' : member.displayName);

/** Assemble the create/edit request body (setNo is re-indexed from the entered order). */
export function buildRecordRequest(team1: string[], team2: string[], sets: SetInput[], winningTeamNo: number, playedAt: string): RecordMatchRequest {
  return {
    playedAt,
    teams: [{ teamNo: 1, playerIds: team1 }, { teamNo: 2, playerIds: team2 }],
    sets: sets.map(parseSet).filter((p): p is [number, number] => p != null)
      .map(([team1Score, team2Score], index) => ({ setNo: index + 1, team1Score, team2Score })),
    winningTeamNo,
  };
}

/* ==================== Matches log (ported from MatchesScreen.kt / MatchesUiState.kt) ==================== */

/** A team's players joined by " & " (full display names; guests read "Guest 1"). */
export const teamName = (team?: MatchTeam) => (team?.players ?? []).map(p => p.displayName).join(' & ');
/** The team with the given number. */
export const matchTeam = (match: Match, teamNo: number) => match.teams.find(t => t.teamNo === teamNo);
/** The winning team's number, or `null` (shouldn't happen for a valid match). */
export const winningTeamNo = (match: Match): number | null => match.teams.find(t => t.isWinner)?.teamNo ?? null;
/** Whether a player ref is a guest filler (no wire flag — inferred from the "Guest N" name). */
export const isGuestRef = (p: PlayerRef) => /^guest(\s|$)/i.test(p.displayName.trim());

/** Matches under one calendar day, newest day first / newest match first within it. */
export type MatchDateSection = { date: string; label: string; matches: Match[] };

/** `DD MMM` for a local `Date`, e.g. `22 Jul` — day-first regardless of locale, matching
 *  Android's `DateTimeFormatter.ofPattern("dd MMM")`. */
export const dateLabel = (date: Date) =>
  `${`${date.getDate()}`.padStart(2, '0')} ${date.toLocaleDateString(undefined, { month: 'short' })}`;
/** `DD MMM · HH:MM` for an ISO instant in local time (audit-log timestamps). */
export const timeLabel = (iso: string) => {
  const d = new Date(iso);
  return `${dateLabel(d)} · ${d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false })}`;
};
/** Human label for an audit action. */
export const actionLabel = (action: string) => {
  switch (action.toLowerCase()) {
    case 'created': return 'Recorded this match';
    case 'edited':
    case 'updated': return 'Edited this match';
    default: return action;
  }
};

/**
 * Group a newest-first match list into per-day sections (keyed by local calendar day),
 * preserving order. `YYYY-MM-DD` keys are stable for expand/collapse tracking.
 */
export function groupMatchesByDate(matches: Match[]): MatchDateSection[] {
  const sections: MatchDateSection[] = [];
  const index = new Map<string, MatchDateSection>();
  for (const match of matches) {
    const d = new Date(match.playedAt);
    const key = `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}-${`${d.getDate()}`.padStart(2, '0')}`;
    let section = index.get(key);
    if (!section) {
      section = { date: key, label: dateLabel(d), matches: [] };
      index.set(key, section);
      sections.push(section);
    }
    section.matches.push(match);
  }
  return sections;
}
