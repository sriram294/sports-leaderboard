import { Navigate, Route, Routes } from 'react-router-dom';
import { useSession } from '../session';
import { GroupProvider } from '../groups';
import { LoginScreen } from '../features/auth/LoginScreen';
import { Splash } from '../features/auth/Splash';
import { AppLayout } from './AppLayout';
import {
  AddRoute,
  BoardRoute,
  MatchesRoute,
  PlayerRoute,
  ProfileRoute,
  SettingsRoute,
  StatsRoute,
} from './routes';

export function App() {
  const { status } = useSession();

  if (status === 'loading') return <Splash />;
  if (status !== 'authed') return <LoginScreen />;

  return (
    <GroupProvider>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/board" replace />} />
          <Route path="board" element={<BoardRoute />} />
          <Route path="matches" element={<MatchesRoute />} />
          <Route path="add" element={<AddRoute />} />
          <Route path="stats" element={<StatsRoute />} />
          <Route path="profile" element={<ProfileRoute />} />
          <Route path="player/:userId" element={<PlayerRoute />} />
          <Route path="settings" element={<SettingsRoute />} />
          <Route path="*" element={<Navigate to="/board" replace />} />
        </Route>
      </Routes>
    </GroupProvider>
  );
}
