import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';
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

/**
 * Cursor-paginated match log (Android's `getMatches(cursor, mine)` loop). `mine` scopes to
 * the signed-in user's matches; toggling it is a new query key, so it refetches from page 1.
 * "Load older matches" calls `fetchNextPage`, following `nextCursor` until it's absent.
 */
export const useMatchesInfinite = (groupId?: string, mine = false) =>
  useInfiniteQuery({
    queryKey: [...matchesKey(groupId), mine],
    queryFn: ({ pageParam }) => api.matches(groupId!, pageParam, mine),
    enabled: !!groupId,
    initialPageParam: undefined as string | undefined,
    getNextPageParam: lastPage => lastPage.nextCursor ?? undefined,
    placeholderData: keepPreviousData,
  });

/** The group roster (real members + guest fillers) for the Add-match player picker. */
export const useMembers = (groupId?: string) =>
  useQuery({ queryKey: ['members', groupId], queryFn: () => api.members(groupId!), enabled: !!groupId });

/** Full detail (teams, per-set scores, audit log) for one expanded match card. */
export const useMatchDetail = (groupId?: string, matchId?: string) =>
  useQuery({
    queryKey: ['matchDetail', groupId, matchId],
    queryFn: () => api.matchDetail(groupId!, matchId!),
    enabled: !!groupId && !!matchId,
  });

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
