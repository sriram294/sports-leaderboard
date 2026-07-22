import { useEffect, useState } from 'react';
import { api } from '../data';
import type { Group, Match, Ranking, Tab, User } from '../models';
import { shareLeaderboard } from '../share';
import { useSession } from '../session';
import { AddMatchScreen } from '../features/add-match/AddMatchScreen';
import { BoardScreen } from '../features/board/BoardScreen';
import { LoginScreen } from '../features/auth/LoginScreen';
import { Splash } from '../features/auth/Splash';
import { MatchHistoryScreen } from '../features/matches/MatchHistoryScreen';
import { PlayerScreen } from '../features/profile/PlayerScreen';
import { ProfileScreen } from '../features/profile/ProfileScreen';
import { StatsScreen } from '../features/stats/StatsScreen';
import { AppShell } from './AppShell';

const demoUser: User = {
  id: 'demo', displayName: 'Raj Kumar', email: 'raj@example.com', avatarColor: '#9ade28',
};

const demoGroups: Group[] = [{
  id: 'demo-group', name: 'Saturday Smashers', avatarColor: '#c7ea2b',
  sportCode: 'badminton_doubles', memberCount: 6, matchCount: 18, myRole: 'owner',
}];

const demoRankings: Ranking[] = [
  { rank: 1, userId: '1', displayName: 'Priya Sharma', avatarColor: '#ff3d8a', gamesPlayed: 18, wins: 14, losses: 4, pointsFor: 432, winRate: .78, currentStreak: 4, bestStreak: 7 },
  { rank: 2, userId: '2', displayName: 'Raj Kumar', avatarColor: '#9ade28', gamesPlayed: 16, wins: 11, losses: 5, pointsFor: 390, winRate: .69, currentStreak: 2, bestStreak: 5 },
  { rank: 3, userId: '3', displayName: 'Dev Menon', avatarColor: '#3db4ff', gamesPlayed: 17, wins: 10, losses: 7, pointsFor: 371, winRate: .59, currentStreak: 1, bestStreak: 4 },
  { rank: 4, userId: '4', displayName: 'Marcus Lee', avatarColor: '#ffb020', gamesPlayed: 12, wins: 6, losses: 6, pointsFor: 278, winRate: .5, currentStreak: 0, bestStreak: 3 },
];

export function App() {
  const { status, user: sessionUser, signOut } = useSession();
  const authed = status === 'authed';
  const [groups, setGroups] = useState<Group[]>([]);
  const [group, setGroup] = useState<Group>();
  const [user, setUser] = useState<User>(sessionUser || demoUser);
  const [tab, setTab] = useState<Tab>('board');
  const [rankings, setRankings] = useState<Ranking[]>(demoRankings);
  const [matches, setMatches] = useState<Match[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [player, setPlayer] = useState<Ranking>();

  const activeGroups = groups.length ? groups : demoGroups;
  const activeGroup = group || activeGroups[0];

  useEffect(() => {
    if (!authed) return;
    api.groups()
      .then(result => {
        setGroups(result.groups);
        setGroup(result.groups[0]);
        return api.me();
      })
      .then(setUser)
      .catch(() => undefined);
  }, [authed]);

  const loadGroupData = async () => {
    if (!activeGroup || !authed) return;
    setLoading(true);
    setError('');
    try {
      const [board, history] = await Promise.all([
        api.leaderboard(activeGroup.id), api.matches(activeGroup.id),
      ]);
      setRankings(board.rankings);
      setMatches(history.matches);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to reach Playboard');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadGroupData(); }, [activeGroup?.id, authed]);

  const refreshGroups = () => {
    api.groups().then(result => {
      setGroups(result.groups);
      if (!group || !result.groups.some(item => item.id === group.id)) setGroup(result.groups[0]);
    }).catch(() => setError('Could not refresh groups.'));
  };

  const navigate = (nextTab: Tab) => { setTab(nextTab); setPlayer(undefined); };
  const selectGroup = (nextGroup: Group) => { setGroup(nextGroup); setPlayer(undefined); };
  const share = () => shareLeaderboard(activeGroup, rankings)
    .catch(cause => setError(cause instanceof Error ? cause.message : 'Sharing failed.'));

  if (status === 'loading') return <Splash />;
  if (status !== 'authed') return <LoginScreen />;

  return (
    <AppShell user={user} groups={activeGroups} group={activeGroup} tab={tab}
      onGroupChange={selectGroup} onNavigate={navigate} onSignOut={signOut}>
      {loading && <div className="loading"><span /><span /><span /></div>}
      {error && <div className="empty error"><strong>Couldn’t load this</strong><p>{error}</p><button className="button ghost" onClick={loadGroupData}>Try again</button></div>}
      {!loading && !error && player && <PlayerScreen ranking={player} onBack={() => setPlayer(undefined)} />}
      {!loading && !error && !player && tab === 'board' && <BoardScreen rankings={rankings} user={user} onPlayer={setPlayer} onShare={share} />}
      {!loading && !error && !player && tab === 'matches' && <MatchHistoryScreen group={activeGroup} matches={matches} onReload={loadGroupData} />}
      {!loading && !error && !player && tab === 'add' && <AddMatchScreen group={activeGroup} user={user} onDone={() => { navigate('board'); loadGroupData(); }} />}
      {!loading && !error && !player && tab === 'stats' && <StatsScreen rankings={rankings} />}
      {!loading && !error && !player && tab === 'profile' && <ProfileScreen user={user} onGroupsChanged={refreshGroups} onSignOut={signOut} />}
    </AppShell>
  );
}
