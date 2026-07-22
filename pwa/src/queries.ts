import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from './data';
import { rangeWindow, recentForm, type TimeRange } from './domain';

/**
 * Shared read queries keyed by group. Mutations (add/edit/delete match, group
 * changes) invalidate these keys so Board, Matches, Stats and Profile reload in
 * lockstep — the web analog of Android's `dataRevision`.
 *
 * The keys below are *prefixes* used for invalidation; the actual query keys append
 * the calendar range / user, and TanStack matches by prefix, so invalidating
 * `leaderboardKey(id)` refreshes every windowed variant.
 */
export const leaderboardKey = (groupId?: string) => ['leaderboard', groupId] as const;
export const matchesKey = (groupId?: string) => ['matches', groupId] as const;
export const formKey = (groupId?: string, userId?: string) => ['form', groupId, userId] as const;

/**
 * Board leaderboard for a calendar window. `keepPreviousData` keeps the current table
 * on screen while a range switch fetches, so the header (with the range selector) never
 * blinks out to a spinner. Stats defaults to `all` to preserve its all-time semantics.
 */
export const useLeaderboard = (groupId?: string, range: TimeRange = 'all') =>
  useQuery({
    queryKey: [...leaderboardKey(groupId), range],
    queryFn: () => { const window = rangeWindow(range); return api.leaderboard(groupId!, window?.from, window?.to); },
    enabled: !!groupId,
    placeholderData: keepPreviousData,
  });

export const useMatches = (groupId?: string) =>
  useQuery({ queryKey: matchesKey(groupId), queryFn: () => api.matches(groupId!), enabled: !!groupId });

/**
 * The signed-in user's last-5 results for the Board form bar. Secondary to the
 * leaderboard: derived from the player-stats endpoint's `recentMatches`.
 */
export const useForm = (groupId?: string, userId?: string) =>
  useQuery({
    queryKey: formKey(groupId, userId),
    queryFn: async () => recentForm((await api.stats(groupId!, userId!)).recentMatches ?? [], userId!),
    enabled: !!groupId && !!userId,
  });
