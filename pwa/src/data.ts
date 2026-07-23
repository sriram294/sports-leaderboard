import type { Group, InviteResponse, LeaderboardResponse, Match, MatchDetail, MatchListResponse, Member, MembersResponse, MonthlyTrophy, PlayerAttendance, PlayerStats, RecordMatchRequest, Session, User } from './models';

const API = import.meta.env.VITE_API_URL || '/api/v1';
export class ApiError extends Error { constructor(public status: number, public code: string, message: string) { super(message); } }
let session: Session | null = JSON.parse(localStorage.getItem('playboard.session') || 'null');
export const auth = { get: () => session, set: (s: Session | null) => { session = s; s ? localStorage.setItem('playboard.session', JSON.stringify(s)) : localStorage.removeItem('playboard.session'); } };
async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  // Let the browser set multipart boundaries; only JSON bodies get an explicit content type.
  const headers = new Headers(init.headers); if (!(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
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
  renameUser: (displayName: string) => request<User>('/users/me', { method: 'PATCH', body: JSON.stringify({ displayName }) }),
  selectAvatar: (avatarId: string) => request<User>('/users/me/avatar', { method: 'PATCH', body: JSON.stringify({ avatarId }) }),
  uploadPhoto: (file: File) => { const form = new FormData(); form.append('file', file); return request<User>('/users/me/photo', { method: 'POST', body: form }); },
  stats: (groupId: string, userId: string) => request<PlayerStats>(`/groups/${groupId}/members/${userId}/stats`),
  attendance: (groupId: string, userId: string, from: string, to: string) =>
    request<PlayerAttendance>(`/groups/${groupId}/members/${userId}/attendance?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  trophies: (groupId: string) => request<MonthlyTrophy[]>(`/groups/${groupId}/trophies`),
  createGroup: (name: string) => request<Group>('/groups', { method: 'POST', body: JSON.stringify({ name, sportCode: 'badminton_doubles' }) }),
  joinGroup: (code: string) => request<Group>('/groups/join', { method: 'POST', body: JSON.stringify({ code }) }),
  renameGroup: (id: string, name: string) => request<Group>(`/groups/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) }),
  createInvite: (id: string) => request<InviteResponse>(`/groups/${id}/invites`, { method: 'POST', body: JSON.stringify({}) }),
  addMember: (id: string, email: string, displayName: string) => request<Member>(`/groups/${id}/members`, { method: 'POST', body: JSON.stringify({ email, displayName }) }),
  removeMember: (id: string, userId: string) => request<void>(`/groups/${id}/members/${userId}`, { method: 'DELETE' }),
  changeMemberRole: (id: string, userId: string, role: 'admin' | 'member') => request<Member>(`/groups/${id}/members/${userId}`, { method: 'PATCH', body: JSON.stringify({ role }) }),
  updateSession: (id: string, start: string | null, end: string | null) => request<Group>(`/groups/${id}/session`, { method: 'PATCH', body: JSON.stringify({ start, end }) }),
};
