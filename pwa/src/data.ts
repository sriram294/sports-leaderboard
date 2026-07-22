import type { Group, LeaderboardResponse, Match, MatchDetail, MatchListResponse, MembersResponse, PlayerStats, RecordMatchRequest, Session, User } from './models';

const API = import.meta.env.VITE_API_URL || '/api/v1';
export class ApiError extends Error { constructor(public status: number, public code: string, message: string) { super(message); } }
let session: Session | null = JSON.parse(localStorage.getItem('playboard.session') || 'null');
export const auth = { get: () => session, set: (s: Session | null) => { session = s; s ? localStorage.setItem('playboard.session', JSON.stringify(s)) : localStorage.removeItem('playboard.session'); } };
async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers); headers.set('Content-Type', 'application/json');
  if (session?.accessToken) headers.set('Authorization', `Bearer ${session.accessToken}`);
  const response = await fetch(`${API}${path}`, { ...init, headers });
  if (response.status === 401 && retry && session?.refreshToken) {
    const refreshed = await fetch(`${API}/auth/refresh`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: session.refreshToken }) });
    if (refreshed.ok) { const tokens = await refreshed.json(); auth.set({ ...session, ...tokens, expiresAt: Date.now() + tokens.expiresIn * 1000 }); return request<T>(path, init, false); }
    auth.set(null);
  }
  if (!response.ok) { let body: { detail?: string; code?: string } = {}; try { body = await response.json(); } catch {} throw new ApiError(response.status, body.code || 'REQUEST_FAILED', body.detail || 'Something went wrong'); }
  return response.status === 204 ? undefined as T : response.json();
}
/** Raw token payload returned by /auth/google and /auth/refresh. */
export type AuthTokens = { accessToken: string; refreshToken: string; expiresIn: number; user: User };

export const api = {
  googleSignIn: (idToken: string) => request<AuthTokens>('/auth/google', { method: 'POST', body: JSON.stringify({ idToken }) }),
  logout: () => request<void>('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: session?.refreshToken }) }),
  groups: () => request<{ groups: Group[] }>('/groups'),
  leaderboard: (id: string, from?: string, to?: string) => {
    const query = new URLSearchParams();
    if (from) query.set('from', from);
    if (to) query.set('to', to);
    const suffix = query.toString();
    return request<LeaderboardResponse>(`/groups/${id}/leaderboard${suffix ? `?${suffix}` : ''}`);
  },
  matches: (id: string, cursor?: string, mine = false) => {
    const query = new URLSearchParams();
    if (cursor) query.set('cursor', cursor);
    if (mine) query.set('mine', 'true');
    const suffix = query.toString();
    return request<MatchListResponse>(`/groups/${id}/matches${suffix ? `?${suffix}` : ''}`);
  },
  matchDetail: (groupId: string, matchId: string) => request<MatchDetail>(`/groups/${groupId}/matches/${matchId}`),
  members: (groupId: string) => request<MembersResponse>(`/groups/${groupId}/members`),
  createMatch: (id: string, body: RecordMatchRequest) => request<Match>(`/groups/${id}/matches`, { method: 'POST', body: JSON.stringify(body) }),
  editMatch: (groupId: string, matchId: string, body: RecordMatchRequest) => request<Match>(`/groups/${groupId}/matches/${matchId}`, { method: 'PATCH', body: JSON.stringify(body) }),
  deleteMatch: (groupId: string, matchId: string) => request<void>(`/groups/${groupId}/matches/${matchId}`, { method: 'DELETE' }),
  me: () => request<User>('/users/me'),
  stats: (groupId: string, userId: string) => request<PlayerStats>(`/groups/${groupId}/members/${userId}/stats`),
  createGroup: (body: unknown) => request<Group>('/groups', { method: 'POST', body: JSON.stringify(body) }),
  joinGroup: (body: unknown) => request<Group>('/groups/join', { method: 'POST', body: JSON.stringify(body) }),
  renameGroup: (id: string, body: unknown) => request<Group>(`/groups/${id}`, { method: 'PATCH', body: JSON.stringify(body) })
};
