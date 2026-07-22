import { useState } from 'react';
import { useGroups } from '../groups';
import { GroupAvatar } from '../components';
import { Icon } from '../icons';

/**
 * The slim group pill shown under the header on every tab: active group avatar +
 * name + caret, with the player count on the right. Tapping expands a panel to
 * switch between the user's groups. Create/join/invite actions land in the Groups
 * slice; this slice covers display + switching.
 */
export function GroupSwitcher() {
  const { groups, activeGroup, setActiveGroup } = useGroups();
  const [open, setOpen] = useState(false);

  if (!activeGroup) {
    return <div className="group-switcher"><div className="group-switcher-pill empty">No group yet</div></div>;
  }

  return (
    <div className="group-switcher">
      <button className="group-switcher-pill" onClick={() => setOpen(value => !value)} aria-expanded={open}>
        <GroupAvatar group={activeGroup} size={34} />
        <span className="gs-name">{activeGroup.name}</span>
        <Icon name="expand" size={18} className="gs-caret" />
        <span className="gs-count">{activeGroup.memberCount} players</span>
      </button>

      {open && (
        <div className="group-switcher-panel">
          <p className="eyebrow">YOUR GROUPS</p>
          {groups.map(group => (
            <button
              key={group.id}
              className={`gs-row ${group.id === activeGroup.id ? 'active' : ''}`}
              onClick={() => { setActiveGroup(group.id); setOpen(false); }}
            >
              <GroupAvatar group={group} size={30} />
              <span className="gs-row-name">{group.name}</span>
              <small>{group.memberCount} players</small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
