import { NavLink } from 'react-router-dom';
import { Icon, type IconName } from '../icons';

/** Persistent 5-item bottom bar with the center floating "+" (MainTab.kt). */
export function BottomNav() {
  return (
    <nav className="bottom-nav" aria-label="Primary">
      <NavItem to="/board" label="Board" icon="board" />
      <NavItem to="/matches" label="Matches" icon="matches" />
      <NavLink to="/add" className="add-button" aria-label="Add match">
        <Icon name="add" size={30} />
      </NavLink>
      <NavItem to="/stats" label="Stats" icon="stats" />
      <NavItem to="/profile" label="Profile" icon="profile" />
    </nav>
  );
}

function NavItem({ to, label, icon }: { to: string; label: string; icon: IconName }) {
  return (
    <NavLink to={to} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
      <Icon name={icon} size={22} />
      <span className="nav-label">{label}</span>
    </NavLink>
  );
}
