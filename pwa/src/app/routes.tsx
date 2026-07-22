import { useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useSession } from '../session';
import { useGroups } from '../groups';
import { leaderboardKey, matchesKey, useLeaderboard, useMatches } from '../queries';
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
  const { data, isLoading, error, refetch } = useLeaderboard(activeGroup?.id);
  if (!activeGroup) return <NoGroup />;
  if (isLoading) return <Loading />;
  if (error) return <ErrorState message={errorMessage(error)} retry={() => refetch()} />;
  const rankings = data?.rankings ?? [];
  return (
    <BoardScreen
      rankings={rankings}
      user={user!}
      onPlayer={ranking => navigate(`/player/${ranking.userId}`)}
      onShare={() => shareLeaderboard(activeGroup, rankings).catch(() => undefined)}
    />
  );
}

export function MatchesRoute() {
  const { activeGroup } = useGroups();
  const queryClient = useQueryClient();
  const { data, isLoading, error, refetch } = useMatches(activeGroup?.id);
  if (!activeGroup) return <NoGroup />;
  if (isLoading) return <Loading />;
  if (error) return <ErrorState message={errorMessage(error)} retry={() => refetch()} />;
  return (
    <MatchHistoryScreen
      group={activeGroup}
      matches={data?.matches ?? []}
      onReload={() => queryClient.invalidateQueries({ queryKey: matchesKey(activeGroup.id) })}
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
        // Match mutation invalidates Board + Matches (and Stats, which reads them).
        queryClient.invalidateQueries({ queryKey: leaderboardKey(activeGroup.id) });
        queryClient.invalidateQueries({ queryKey: matchesKey(activeGroup.id) });
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
