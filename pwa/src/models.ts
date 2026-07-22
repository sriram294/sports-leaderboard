export type Tab = 'board' | 'matches' | 'add' | 'stats' | 'profile';
export type User = { id: string; displayName: string; email: string; photoUrl?: string | null; avatarColor: string };
export type Group = { id: string; name: string; avatarColor: string; sportCode: string; memberCount: number; matchCount: number; myRole: 'owner' | 'admin' | 'member' };
export type Ranking = { rank: number; userId: string; displayName: string; photoUrl?: string | null; avatarColor: string; gamesPlayed: number; wins: number; losses: number; pointsFor: number; winRate: number; currentStreak: number; bestStreak: number };
export type Team = { teamNo: number; isWinner: boolean; players: Pick<User, 'id' | 'displayName' | 'photoUrl' | 'avatarColor'>[] };
export type Match = { id: string; playedAt: string; teams: Team[]; sets: { setNo: number; team1Score: number; team2Score: number }[]; recordedBy?: Pick<User, 'id' | 'displayName'> };
export type Session = { accessToken: string; refreshToken: string; expiresAt: number; user: User };
