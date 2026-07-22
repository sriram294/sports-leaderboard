import { Outlet, useNavigate } from 'react-router-dom';
import { Wordmark } from '../components';
import { Icon } from '../icons';
import { GroupSwitcher } from './GroupSwitcher';
import { BottomNav } from './BottomNav';

/**
 * Authenticated app frame: sticky header (wordmark + settings gear), the group
 * switcher pill, the routed screen (<Outlet/>), and the bottom nav — present on
 * every tab (MainScreen.kt).
 */
export function AppLayout() {
  const navigate = useNavigate();
  return (
    <div className="app-shell">
      <header>
        <div className="header-row">
          <Wordmark size="sm" />
          <button className="icon-button gear" aria-label="Settings" onClick={() => navigate('/settings')}>
            <Icon name="settings" size={24} />
          </button>
        </div>
      </header>
      <div className="group-switcher-wrap">
        <GroupSwitcher />
      </div>
      <main className="content">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}
