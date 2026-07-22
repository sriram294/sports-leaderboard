export type Tab = 'board' | 'matches' | 'add' | 'stats' | 'profile';
export type User = { id: string; displayName: string; email: string; photoUrl?: string | null; avatarColor: string };
export type Group = { id: string; name: string; avatarColor: string; sportCode: string; memberCount: number; matchCount: number; myRole: 'owner' | 'admin' | 'member' };

/**
 * One leaderboard row (LeaderboardEntryDto / `data/model/PlayerRanking.kt`). `rank` is the
 * server-assigned position (rating desc, then points diff, then wins) and stays fixed even
 * when the table is re-sorted by another metric client-side.
 *
 * `rating`/`provisional`/`pointsAgainst`/`avatarId` are optional so pre-ratings fixtures and
 * the older scaffold consumers still type-check; the live contract always sends them.
 */
export type Ranking = {
  rank: number;
  userId: string;
  displayName: string;
  photoUrl?: string | null;
  avatarId?: string | null;
  avatarColor: string;
  gamesPlayed: number;
  wins: number;
  losses: number;
  pointsFor: number;
  pointsAgainst?: number;
  winRate: number;
  currentStreak: number;
  bestStreak: number;
  /** Confidence-adjusted win rate, 0–100. `null`/absent only on a pre-ratings backend. */
  rating?: number | null;
  /** Below the group's games threshold: listed, but not ranked. */
  provisional?: boolean;
};

/** `GET /groups/{id}/leaderboard` (LeaderboardResponse). */
export type LeaderboardResponse = { rankings: Ranking[]; minGamesToRank: number };

/** A player as referenced inside a match/team (PlayerRefDto). Guests are ordinary refs whose
 * `displayName` is "Guest N"; there is no explicit guest flag on the wire. */
export type PlayerRef = { userId: string; displayName: string; photoUrl?: string | null; avatarId?: string | null; avatarColor: string };
export type MatchTeam = { teamNo: number; isWinner: boolean; players: PlayerRef[] };
export type MatchSet = { setNo: number; team1Score: number; team2Score: number };
/** A recorded doubles match (MatchSummaryDto) — the Matches log row and `recentMatches`. */
export type Match = { id: string; playedAt: string; teams: MatchTeam[]; sets: MatchSet[] };
/** One audit-log entry on a match (MatchEventDto). */
export type MatchEvent = { userId: string; displayName: string; action: string; createdAt: string };
/** Full match detail (MatchDetailDto): the summary plus who recorded it and the audit log. */
export type MatchDetail = Match & { recordedBy: { userId: string; displayName: string }; recordedAt: string; events: MatchEvent[] };
/** `GET /groups/{id}/matches` (MatchListResponse) — a cursor-paginated page. */
export type MatchListResponse = { matches: Match[]; nextCursor?: string };
/** `GET /groups/{id}/members/{userId}/stats` (PlayerStatsDto) — only the fields Board reads. */
export type PlayerStats = { userId: string; displayName: string; recentMatches: Match[] };

export type Session = { accessToken: string; refreshToken: string; expiresAt: number; user: User };
