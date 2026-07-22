import { useQuery } from '@tanstack/react-query';
import { api } from './data';

/**
 * Shared read queries keyed by group. Mutations (add/edit/delete match, group
 * changes) invalidate these keys so Board, Matches, Stats and Profile reload in
 * lockstep — the web analog of Android's `dataRevision`.
 */
export const leaderboardKey = (groupId?: string) => ['leaderboard', groupId] as const;
export const matchesKey = (groupId?: string) => ['matches', groupId] as const;

export const useLeaderboard = (groupId?: string) =>
  useQuery({ queryKey: leaderboardKey(groupId), queryFn: () => api.leaderboard(groupId!), enabled: !!groupId });

export const useMatches = (groupId?: string) =>
  useQuery({ queryKey: matchesKey(groupId), queryFn: () => api.matches(groupId!), enabled: !!groupId });
