import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useSession } from '../session';
import { useGroups } from '../groups';
import { formKey, leaderboardKey, matchesKey, useForm, useLeaderboard, useMatchesInfinite } from '../queries';
import type { TimeRange } from '../domain';
import { api } from '../data';
import { shareLeaderboard } from '../share';
import { Loading, ErrorState } from '../components';
import { Icon } from '../icons';
import { BoardScreen } from '../features/board/BoardScreen';
import { MatchHistoryScreen } from '../features/matches/MatchHistoryScreen';
import { AddMatchScreen } from '../features/add-match/AddMatchScreen';
import { StatsScreen } from '../features/stats/StatsScreen';
import { ProfileScreen } from '../features/profile/ProfileScreen';
import { PlayerScreen } from '../features/profile/PlayerScreen';

function NoGroup() {
  return (
    <div className="empty">
      <h3>No group yet</h3>
      <p>Create or join a group to start tracking matches.</p>
    </div>
  );
}

const errorMessage = (error: unknown) => (error instanceof Error ? error.message : 'Unable to reach Playboard');

export function BoardRoute() {
  const { activeGroup } = useGroups();
  const { user } = useSession();
  const navigate = useNavigate();
  // The window persists across group switches (mirrors Android's selectedTimeRange).
  const [range, setRange] = useState<TimeRange>('month');
  const { data, isLoading, error, refetch } = useLeaderboard(activeGroup?.id, range);
  // The form bar is secondary: it loads independently and never gates the board.
  const form = useForm(activeGroup?.id, user?.id);
  if (!activeGroup) return <NoGroup />;
  // Only the very first load spins; range switches keep the previous table (keepPreviousData).
  if (isLoading) return <Loading />;
  if (error) return <ErrorState message={errorMessage(error)} retry={() => refetch()} />;
  const rankings = data?.rankings ?? [];
  return (
    <BoardScreen
      rankings={rankings}
      minGamesToRank={data?.minGamesToRank ?? 1}
      groupId={activeGroup.id}
      user={user!}
      range={range}
      onRangeChange={setRange}
      recentForm={form.data ?? []}
      onPlayer={userId => navigate(`/player/${userId}`)}
      onShare={() => shareLeaderboard(activeGroup, rankings).catch(() => undefined)}
    />
  );
}

export function MatchesRoute() {
  const { activeGroup } = useGroups();
  const { user } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [mine, setMine] = useState(false);
  const query = useMatchesInfinite(activeGroup?.id, mine);
  if (!activeGroup) return <NoGroup />;
  // keepPreviousData keeps isLoading false after the first page, so a "My matches" toggle
  // updates the list in place rather than blanking to a spinner.
  if (query.isLoading) return <Loading />;
  if (query.error) return <ErrorState message={errorMessage(query.error)} retry={() => query.refetch()} />;
  const matches = query.data?.pages.flatMap(page => page.matches) ?? [];
  const canModerate = activeGroup.myRole === 'owner' || activeGroup.myRole === 'admin';
  const onDelete = async (matchId: string) => {
    await api.deleteMatch(activeGroup.id, matchId);
    // Deleting a match changes the leaderboard + the user's form, like recording one does.
    queryClient.invalidateQueries({ queryKey: matchesKey(activeGroup.id) });
    queryClient.invalidateQueries({ queryKey: leaderboardKey(activeGroup.id) });
    queryClient.invalidateQueries({ queryKey: formKey(activeGroup.id, user?.id) });
  };
  return (
    <MatchHistoryScreen
      group={activeGroup}
      groupId={activeGroup.id}
      matches={matches}
      currentUserId={user?.id}
      canModerate={canModerate}
      mine={mine}
      onToggleMine={() => setMine(value => !value)}
      canLoadMore={query.hasNextPage}
      isLoadingMore={query.isFetchingNextPage}
      onLoadMore={() => query.fetchNextPage()}
      onDelete={onDelete}
      onEdit={matchId => navigate('/add', { state: { editMatchId: matchId } })}
    />
  );
}

export function AddRoute() {
  const { activeGroup } = useGroups();
  const { user } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  if (!activeGroup) return <NoGroup />;
  return (
    <AddMatchScreen
      group={activeGroup}
      user={user!}
      onDone={() => {
        // Match mutation invalidates Board + Matches (and Stats, which reads them), plus
        // the signed-in user's form bar. Prefix keys match every windowed leaderboard variant.
        queryClient.invalidateQueries({ queryKey: leaderboardKey(activeGroup.id) });
        queryClient.invalidateQueries({ queryKey: matchesKey(activeGroup.id) });
        queryClient.invalidateQueries({ queryKey: formKey(activeGroup.id, user?.id) });
        navigate('/board');
      }}
    />
  );
}

export function StatsRoute() {
  const { activeGroup } = useGroups();
  const { data, isLoading, error, refetch } = useLeaderboard(activeGroup?.id);
  if (!activeGroup) return <NoGroup />;
  if (isLoading) return <Loading />;
  if (error) return <ErrorState message={errorMessage(error)} retry={() => refetch()} />;
  return <StatsScreen rankings={data?.rankings ?? []} />;
}

export function ProfileRoute() {
  const { user, signOut } = useSession();
  const queryClient = useQueryClient();
  return (
    <ProfileScreen
      user={user!}
      onSignOut={signOut}
      onGroupsChanged={() => queryClient.invalidateQueries({ queryKey: ['groups'] })}
    />
  );
}

export function PlayerRoute() {
  const { userId } = useParams();
  const { activeGroup } = useGroups();
  const navigate = useNavigate();
  const { data, isLoading } = useLeaderboard(activeGroup?.id);
  if (isLoading) return <Loading />;
  const ranking = data?.rankings.find(row => row.userId === userId);
  if (!ranking) return <ErrorState message="Player not found." retry={() => navigate('/board')} />;
  return <PlayerScreen ranking={ranking} onBack={() => navigate('/board')} />;
}

/** Placeholder — the full Settings screen lands in its own slice. */
export function SettingsRoute() {
  const navigate = useNavigate();
  return (
    <>
      <button className="back" onClick={() => navigate(-1)}><Icon name="back" size={16} /> Back</button>
      <h2>Settings</h2>
      <p className="muted">Account, appearance, and sign-out arrive in the Settings slice.</p>
    </>
  );
}
