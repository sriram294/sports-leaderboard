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

export type PlayerRef = { userId: string; displayName: string; photoUrl?: string | null; avatarId?: string | null; avatarColor: string };
/** A team inside a `MatchSummary` (from a player's `recentMatches`), used to derive form. */
export type MatchSummaryTeam = { teamNo: number; isWinner: boolean; players: PlayerRef[] };
/** Compact match shape returned inside `PlayerStats.recentMatches` (MatchSummaryDto). */
export type MatchSummary = { id: string; playedAt: string; teams: MatchSummaryTeam[]; sets: { setNo: number; team1Score: number; team2Score: number }[] };
/** `GET /groups/{id}/members/{userId}/stats` (PlayerStatsDto) — only the fields Board reads. */
export type PlayerStats = { userId: string; displayName: string; recentMatches: MatchSummary[] };

export type Team = { teamNo: number; isWinner: boolean; players: Pick<User, 'id' | 'displayName' | 'photoUrl' | 'avatarColor'>[] };
export type Match = { id: string; playedAt: string; teams: Team[]; sets: { setNo: number; team1Score: number; team2Score: number }[]; recordedBy?: Pick<User, 'id' | 'displayName'> };
export type Session = { accessToken: string; refreshToken: string; expiresAt: number; user: User };
