import { useState } from 'react';
import { useGroups } from '../groups';
import { canInviteGroup, canManageGroup } from '../domain';
import { GroupAvatar } from '../components';
import { Icon } from '../icons';
import { AddMemberSheet, CreateJoinSheet, InviteSheet, RenameGroupSheet } from '../features/groups/GroupSheets';
import type { Group } from '../models';

/**
 * The slim group pill shown under the header on every tab: active group avatar +
 * name + caret, with the player count on the right. Tapping expands a panel to
 * switch between the user's groups and reach the group actions — rename (per-group
 * pencil, owner/admin), invite players, add member by email, and create/join.
 * Mirrors Android's `ui/switcher/GroupSwitcher.kt`.
 */
type Sheet =
  | { kind: 'createJoin' }
  | { kind: 'rename'; group: Group }
  | { kind: 'invite'; group: Group }
  | { kind: 'addMember'; group: Group };

export function GroupSwitcher() {
  const { groups, activeGroup, setActiveGroup } = useGroups();
  const [open, setOpen] = useState(false);
  const [sheet, setSheet] = useState<Sheet | null>(null);

  const openSheet = (next: Sheet) => { setOpen(false); setSheet(next); };

  const sheetNode = (() => {
    if (!sheet) return null;
    switch (sheet.kind) {
      case 'createJoin': return <CreateJoinSheet onClose={() => setSheet(null)} />;
      case 'rename': return <RenameGroupSheet group={sheet.group} onClose={() => setSheet(null)} />;
      case 'invite': return <InviteSheet groupId={sheet.group.id} groupName={sheet.group.name} onClose={() => setSheet(null)} />;
      case 'addMember': return <AddMemberSheet groupId={sheet.group.id} onClose={() => setSheet(null)} />;
    }
  })();

  if (!activeGroup) {
    return (
      <div className="group-switcher">
        <button className="group-switcher-pill empty accent" onClick={() => setSheet({ kind: 'createJoin' })}>
          <Icon name="add" size={18} /> Create or join a group
        </button>
        {sheetNode}
      </div>
    );
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
            <div key={group.id} className={`gs-row ${group.id === activeGroup.id ? 'active' : ''}`}>
              <button className="gs-row-select" onClick={() => { setActiveGroup(group.id); setOpen(false); }}>
                <GroupAvatar group={group} size={36} />
                <span className="gs-row-body">
                  <span className="gs-row-name">{group.name}</span>
                  <small>{group.memberCount} players · {group.matchCount} matches</small>
                </span>
              </button>
              {canManageGroup(group) && (
                <button className="gs-edit" aria-label={`Rename ${group.name}`} onClick={() => openSheet({ kind: 'rename', group })}>
                  <Icon name="edit" size={15} />
                </button>
              )}
            </div>
          ))}

          <div className="gs-divider" />
          {canInviteGroup(activeGroup) && (
            <button className="gs-action" onClick={() => openSheet({ kind: 'invite', group: activeGroup })}>
              <Icon name="invite" size={20} /> Invite players
            </button>
          )}
          {canManageGroup(activeGroup) && (
            <button className="gs-action" onClick={() => openSheet({ kind: 'addMember', group: activeGroup })}>
              <Icon name="mail" size={20} /> Add member by email
            </button>
          )}
          <button className="gs-action" onClick={() => openSheet({ kind: 'createJoin' })}>
            <Icon name="add" size={20} /> Create or join a group
          </button>
        </div>
      )}

      {sheetNode}
    </div>
  );
}
