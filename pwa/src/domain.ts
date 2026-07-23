import type { Group, Match, MatchSet, MatchTeam, Member, PlayerRef, Ranking, RecordMatchRequest } from './models';

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

/* ==================== Profile (ported from ProfileUiState.kt / AttendanceCalendar.kt) ==================== */

/** Win rate 0–1 → whole-percent integer (e.g. 0.826 → 83). */
export const percent = (rate: number) => Math.round(rate * 100);
/** Signed streak for the tile: negative = loss streak, positive = win streak (e.g. `-2`, `+4`). */
export const streakLabel = (streak: number) => (streak > 0 ? `+${streak}` : `${streak}`);

/** A calendar month, 1-indexed, for the attendance heatmap. */
export type HeatMonth = { year: number; month: number };
/** Monday-first weekday initials for the heatmap Y axis. */
export const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/** The [count] calendar months ending with [today]'s month, oldest first. */
export function heatmapMonths(today: Date = new Date(), count = 3): HeatMonth[] {
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(today.getFullYear(), today.getMonth() - (count - 1 - i), 1);
    return { year: d.getFullYear(), month: d.getMonth() + 1 };
  });
}

/** Short month label, e.g. `Jul`. */
export const monthShortLabel = ({ year, month }: HeatMonth) =>
  new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: 'short' });

/** Local `YYYY-MM-DD` key for a date. */
export function dayKey(date: Date): string {
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`;
}

/**
 * A month's cells laid out **Monday-first**: leading `null` blanks for the weekday the 1st
 * falls on, then each day's `YYYY-MM-DD` key, padded with trailing `null`s to whole weeks.
 * Chunk into 7s for Mon..Sun week-columns.
 */
export function monthCells({ year, month }: HeatMonth): (string | null)[] {
  const first = new Date(year, month - 1, 1);
  const leadingBlanks = (first.getDay() + 6) % 7; // JS: Sun=0 → 6, Mon=1 → 0 … Sat=6 → 5
  const daysInMonth = new Date(year, month, 0).getDate();
  const cells: (string | null)[] = Array(leadingBlanks).fill(null);
  for (let day = 1; day <= daysInMonth; day++) cells.push(dayKey(new Date(year, month - 1, day)));
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

/** Split a month's cells into Mon..Sun week-columns. */
export function weekColumns(month: HeatMonth): (string | null)[][] {
  const cells = monthCells(month);
  return Array.from({ length: cells.length / 7 }, (_, i) => cells.slice(i * 7, i * 7 + 7));
}

/** The `[from, to)` window covering [months] as ISO instants (local first-of-month boundaries). */
export function heatmapWindow(months: HeatMonth[]): { from: string; to: string } {
  const first = months[0];
  const last = months[months.length - 1];
  return {
    from: new Date(first.year, first.month - 1, 1).toISOString(),
    to: new Date(last.year, last.month, 1).toISOString(),
  };
}

/** The set of local day-keys a player was in a match, from an attendance payload. */
export const attendanceDays = (playedAt: string[]): Set<string> => new Set(playedAt.map(iso => dayKey(new Date(iso))));

/** One "Recent Matches" row, framed from the viewed player's perspective (with vs against). */
export type RecentMatchRow = { matchId: string; playedAt: string; isWin: boolean; partnerNames: string; opponentNames: string; sets: MatchSet[] };

/** Build a [RecentMatchRow] from a match, relative to `userId`. */
export function recentMatchRow(match: Match, userId: string): RecentMatchRow {
  const myTeam = match.teams.find(team => team.players.some(p => p.userId === userId));
  const opponents = match.teams.find(team => team.teamNo !== myTeam?.teamNo);
  const partners = (myTeam?.players ?? []).filter(p => p.userId !== userId);
  const names = (players: PlayerRef[]) => players.map(p => p.displayName).join(' & ');
  return {
    matchId: match.id,
    playedAt: match.playedAt,
    isWin: isWinForMatch(match, userId) === true,
    partnerNames: names(partners),
    opponentNames: names(opponents?.players ?? []),
    sets: match.sets,
  };
}

/* ==================== Stats / Insights (ported from StatsComputations.kt) ==================== */

/** Min games before a player can be the win-rate "Win leader" record. */
export const MIN_LEADER_GAMES = 2;
/** Min games together before a pair qualifies as the best partnership. */
export const MIN_PARTNERSHIP_GAMES = 2;
/** How many recent results a player's "form" shows. */
export const FORM_WINDOW = 5;
/** Min run before a streak is worth showing as a record. */
export const MIN_STREAK = 2;

export type Records = {
  totalMatches: number;
  winLeader?: Ranking;
  mostPoints?: Ranking;
  mostActive?: Ranking;
  longestStreak?: Ranking;
  currentStreak?: Ranking;
};

/** First element maximizing `by` (ties keep the earlier, higher-ranked entry), or undefined. */
function firstMaxBy<T>(items: T[], by: (item: T) => number): T | undefined {
  let best: T | undefined;
  for (const item of items) if (best === undefined || by(item) > by(best)) best = item;
  return best;
}

/**
 * All-time [Records] from the server-sorted [rankings] and the group's [matchCount]. The win
 * leader is the top entry with ≥ [MIN_LEADER_GAMES] games (so a lone 1-game 100% doesn't
 * headline), else the top-ranked entry. Streaks show only from [MIN_STREAK] up.
 */
export function computeRecords(rankings: Ranking[], matchCount: number): Records {
  const longest = firstMaxBy(rankings, r => r.bestStreak);
  const hot = firstMaxBy(rankings, r => r.currentStreak);
  return {
    totalMatches: matchCount,
    winLeader: rankings.find(r => r.gamesPlayed >= MIN_LEADER_GAMES) ?? rankings[0],
    mostPoints: firstMaxBy(rankings, r => r.pointsFor),
    mostActive: firstMaxBy(rankings, r => r.gamesPlayed),
    longestStreak: longest && longest.bestStreak >= MIN_STREAK ? longest : undefined,
    currentStreak: hot && hot.currentStreak >= MIN_STREAK ? hot : undefined,
  };
}

export type BestPartnership = { player1: PlayerRef; player2: PlayerRef; gamesTogether: number; winsTogether: number; winRate: number };

/**
 * The teammate pair with the best win rate together across [matches] (min
 * [MIN_PARTNERSHIP_GAMES] games, tie-broken by games). Pairs are keyed order-independently.
 */
export function computeBestPartnership(matches: Match[]): BestPartnership | null {
  type Agg = { p1: PlayerRef; p2: PlayerRef; games: number; wins: number };
  const byPair = new Map<string, Agg>();
  for (const match of matches) {
    for (const team of match.teams) {
      const players = team.players;
      for (let i = 0; i < players.length; i++) {
        for (let j = i + 1; j < players.length; j++) {
          const a = players[i], b = players[j];
          const [first, second] = a.userId <= b.userId ? [a, b] : [b, a];
          const key = `${first.userId}|${second.userId}`;
          let agg = byPair.get(key);
          if (!agg) { agg = { p1: first, p2: second, games: 0, wins: 0 }; byPair.set(key, agg); }
          agg.games++;
          if (team.isWinner) agg.wins++;
        }
      }
    }
  }
  let best: (Agg & { winRate: number }) | null = null;
  for (const agg of byPair.values()) {
    if (agg.games < MIN_PARTNERSHIP_GAMES) continue;
    const winRate = agg.wins / agg.games;
    if (!best || winRate > best.winRate || (winRate === best.winRate && agg.games > best.games)) best = { ...agg, winRate };
  }
  return best ? { player1: best.p1, player2: best.p2, gamesTogether: best.games, winsTogether: best.wins, winRate: best.winRate } : null;
}

export type PlayerForm = { player: PlayerRef; results: boolean[] };

/** Each ranked player's last [FORM_WINDOW] results, newest-first; players absent from the window are dropped. */
export function computeRecentForm(matches: Match[], rankings: Ranking[]): PlayerForm[] {
  const forms: PlayerForm[] = [];
  for (const rank of rankings) {
    const results = matches
      .map(match => match.teams.find(team => team.players.some(p => p.userId === rank.userId))?.isWinner)
      .filter((r): r is boolean => r === true || r === false)
      .slice(0, FORM_WINDOW);
    if (results.length > 0) {
      forms.push({ player: { userId: rank.userId, displayName: rank.displayName, avatarColor: rank.avatarColor, avatarId: rank.avatarId, photoUrl: rank.photoUrl }, results });
    }
  }
  return forms;
}

export type BiggestWin = { match: Match; margin: number };

const pointsMargin = (match: Match) => Math.abs(
  match.sets.reduce((sum, s) => sum + s.team1Score, 0) - match.sets.reduce((sum, s) => sum + s.team2Score, 0),
);

/** The recent match with the largest total-points margin (summed across sets). */
export function computeBiggestWin(matches: Match[]): BiggestWin | null {
  let best: BiggestWin | null = null;
  for (const match of matches) {
    const margin = pointsMargin(match);
    if (margin > 0 && (!best || margin > best.margin)) best = { match, margin };
  }
  return best;
}

/** Badge label for a monthly-winner trophy month (`"2026-07"` → `"JUL '26"`). */
export function monthlyTrophyLabel(month: string): string {
  const [year, m] = month.split('-').map(Number);
  const short = new Date(year, (m || 1) - 1, 1).toLocaleDateString('en-US', { month: 'short' });
  return `${short.toUpperCase()} '${`${year}`.slice(-2)}`;
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

// ─────────────────────────────── Groups & management (Group.kt / GroupManagementUiState.kt) ───

/** The signed-in user owns this group — the only role that may change others' roles. */
export const isGroupOwner = (group: Group) => group.myRole === 'owner';

/** Owners and admins may create invites (backend gates `POST .../invites` the same way). */
export const canInviteGroup = (group: Group) => group.myRole === 'owner' || group.myRole === 'admin';

/** Owners and admins may manage a group — rename, add/remove members, session window. */
export const canManageGroup = (group: Group) => group.myRole === 'owner' || group.myRole === 'admin';

/** The viewer may change roles only in a group they own. */
export const canChangeRoles = (group: Group) => isGroupOwner(group);

/**
 * Whether [member] can be removed by [currentUserId] in [group]: never the owner, guests, or
 * self; an admin viewer can remove only plain members (an owner can remove admins too).
 */
export function canRemoveMember(group: Group, member: Member, currentUserId?: string): boolean {
  if (member.userId === currentUserId || member.role === 'owner' || member.role === 'guest') return false;
  return isGroupOwner(group) || member.role === 'member';
}

/** The role toggle label + target role for a member (owner-only action; guests/owner excluded upstream). */
export const roleToggle = (member: Member): { label: string; next: 'admin' | 'member' } =>
  member.role === 'admin' ? { label: 'Demote', next: 'member' } : { label: 'Make admin', next: 'admin' };

/** A group's daily-window label, or `null` when either bound is unset. */
export function sessionWindowLabel(group: Group): string | null {
  if (!group.sessionStart || !group.sessionEnd) return null;
  return `${group.sessionStart} – ${group.sessionEnd}`;
}

/** "HH:mm" → minutes since midnight, or `null` if unparseable. */
export function parseHm(time: string | null | undefined): number | null {
  if (!time) return null;
  const [h, m] = time.split(':').map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}

/** A session window is valid when both times are given with start < end (matches `422 GROUP_SESSION_INVALID`). */
export function sessionValid(start: string, end: string): boolean {
  const s = parseHm(start);
  const e = parseHm(end);
  return s !== null && e !== null && s < e;
}

/** Maps a group action's stable error `code` to a user-facing message. */
export function groupErrorMessage(code: string | undefined, fallback: string): string {
  switch (code) {
    case 'GROUP_INVITE_INVALID': return 'That invite code is wrong or expired.';
    case 'GROUP_MEMBER_EXISTS': return 'They’re already a member of this group.';
    case 'GROUP_ROLE_FORBIDDEN': return 'You don’t have permission to do that.';
    case 'GROUP_ROLE_INVALID': return 'That role change isn’t allowed.';
    case 'GROUP_OWNER_PROTECTED': return 'The owner can’t be removed.';
    case 'GROUP_CANNOT_REMOVE_GUEST': return 'Guests can’t be removed here.';
    case 'GROUP_CANNOT_REMOVE_SELF': return 'You can’t remove yourself.';
    case 'GROUP_SESSION_INVALID': return 'Start must be before end.';
    default: return fallback;
  }
}
