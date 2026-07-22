import type { ReactNode } from 'react';
import type { Group, Tab, User } from '../models';
import { Avatar, GroupPicker } from '../components';

type Props = { user: User; groups: Group[]; group?: Group; tab: Tab; onGroupChange: (group: Group) => void; onNavigate: (tab: Tab) => void; onSignOut: () => void; children: ReactNode };

export function AppShell({ user, groups, group, tab, onGroupChange, onNavigate, children }: Props) {
  return <div className="app-shell">
    <header><div className="header-row">
      <GroupPicker groups={groups} active={group} onChange={onGroupChange} />
      <button className="profile-button" onClick={() => onNavigate('profile')}><Avatar person={user} size="sm" /></button>
    </div></header>
    <main className="content">{children}</main>
    <BottomNavigation tab={tab} onNavigate={onNavigate} />
  </div>;
}

function BottomNavigation({ tab, onNavigate }: { tab: Tab; onNavigate: (tab: Tab) => void }) {
  return <nav className="bottom-nav" aria-label="Primary">
    <NavItem label="Board" icon="♟" active={tab === 'board'} onClick={() => onNavigate('board')} />
    <NavItem label="Matches" icon="▤" active={tab === 'matches'} onClick={() => onNavigate('matches')} />
    <button className="add-button" aria-label="Add match" onClick={() => onNavigate('add')}>+</button>
    <NavItem label="Stats" icon="◒" active={tab === 'stats'} onClick={() => onNavigate('stats')} />
    <NavItem label="Profile" icon="◯" active={tab === 'profile'} onClick={() => onNavigate('profile')} />
  </nav>;
}

function NavItem({ label, icon, active, onClick }: { label: string; icon: string; active: boolean; onClick: () => void }) {
  return <button className={`nav-item ${active ? 'active' : ''}`} onClick={onClick}><span>{icon}</span>{label}</button>;
}
