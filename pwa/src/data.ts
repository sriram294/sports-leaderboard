import type { Group, Match, Ranking, Session, User } from './models';

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
export const api = {
  groups: () => request<{ groups: Group[] }>('/groups'),
  leaderboard: (id: string) => request<{ rankings: Ranking[] }>(`/groups/${id}/leaderboard`),
  matches: (id: string, cursor?: string) => request<{ matches: Match[]; nextCursor?: string }>(`/groups/${id}/matches${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`),
  match: (groupId: string, matchId: string) => request<Match>(`/groups/${groupId}/matches/${matchId}`),
  createMatch: (id: string, body: unknown) => request<Match>(`/groups/${id}/matches`, { method: 'POST', body: JSON.stringify(body) }),
  deleteMatch: (groupId: string, matchId: string) => request<void>(`/groups/${groupId}/matches/${matchId}`, { method: 'DELETE' }),
  me: () => request<User>('/users/me'),
  stats: (groupId: string, userId: string) => request<Record<string, unknown>>(`/groups/${groupId}/members/${userId}/stats`),
  createGroup: (body: unknown) => request<Group>('/groups', { method: 'POST', body: JSON.stringify(body) }),
  joinGroup: (body: unknown) => request<Group>('/groups/join', { method: 'POST', body: JSON.stringify(body) }),
  renameGroup: (id: string, body: unknown) => request<Group>(`/groups/${id}`, { method: 'PATCH', body: JSON.stringify(body) })
};
