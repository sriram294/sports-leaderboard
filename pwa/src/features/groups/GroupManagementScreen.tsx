import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useGroups } from '../../groups';
import { useSession } from '../../session';
import { useMembers, membersKey } from '../../queries';
import { api, ApiError } from '../../data';
import { canChangeRoles, canManageGroup, canRemoveMember, groupErrorMessage, roleToggle, sessionValid, sessionWindowLabel } from '../../domain';
import { Avatar, GroupAvatar, Loading } from '../../components';
import { Icon } from '../../icons';
import type { Group, Member } from '../../models';
import { AddMemberSheet, InviteSheet } from './GroupSheets';

/**
 * Group-management drill-down (owner/admin): the groups the signed-in user manages → per-group
 * member management (remove, role changes, add/invite) + the daily session window. Self-contained
 * list↔detail navigation; [onExit] leaves the drill-down (back to Profile). Mirrors Android's
 * `ui/group/GroupManagementScreen.kt`.
 */
export function GroupManagementScreen({ onExit }: { onExit: () => void }) {
  const { groups } = useGroups();
  const managed = groups.filter(canManageGroup);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = managed.find(group => group.id === selectedId) ?? null;

  if (selected) {
    return <GroupDetail group={selected} onBack={() => setSelectedId(null)} />;
  }
  return (
    <>
      <button className="back" onClick={onExit}><Icon name="back" size={16} /> Profile</button>
      {managed.length === 0 ? (
        <p className="empty group-manage-empty">You don’t manage any groups.<br />Only a group’s owner or admins can manage it.</p>
      ) : (
        <>
          <p className="section-label">GROUPS YOU MANAGE</p>
          <div className="manage-list">
            {managed.map(group => (
              <button key={group.id} className="card manage-row" onClick={() => setSelectedId(group.id)}>
                <GroupAvatar group={group} size={40} />
                <span className="manage-row-body">
                  <span className="manage-row-name">{group.name}</span>
                  <small>{group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.matchCount} matches</small>
                </span>
                <RoleBadge role={group.myRole} />
                <span className="manage-chevron">›</span>
              </button>
            ))}
          </div>
        </>
      )}
    </>
  );
}

function GroupDetail({ group, onBack }: { group: Group; onBack: () => void }) {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const members = useMembers(group.id);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [removeTarget, setRemoveTarget] = useState<Member | null>(null);
  const [sheet, setSheet] = useState<'invite' | 'add' | 'session' | null>(null);

  const invalidate = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['groups'] }),
    queryClient.invalidateQueries({ queryKey: membersKey(group.id) }),
  ]);

  const run = async (action: () => Promise<unknown>, fallback: string) => {
    setBusy(true);
    setError(undefined);
    try { await action(); await invalidate(); } catch (cause) {
      setError(groupErrorMessage(cause instanceof ApiError ? cause.code : undefined, fallback));
    } finally { setBusy(false); }
  };

  const roster = (members.data?.members ?? []).filter(member => member.role !== 'guest');
  const window = sessionWindowLabel(group);

  return (
    <>
      <button className="back" onClick={onBack}><Icon name="back" size={16} /> Groups</button>
      <h2 className="manage-title">{group.name}</h2>

      <p className="section-label">SESSION TIME</p>
      <div className="card session-card">
        <div className="session-info">
          <span className={window ? 'session-window' : 'session-window unset'}>{window ?? 'No session time set'}</span>
          <small>Daily playing time — used for reminders later.</small>
        </div>
        <button className="pill-btn" disabled={busy} onClick={() => setSheet('session')}>Edit</button>
      </div>

      <div className="members-head">
        <p className="section-label">MEMBERS</p>
        <div className="members-actions">
          <button className="pill-btn" disabled={busy} onClick={() => setSheet('invite')}>Invite</button>
          <button className="pill-btn" disabled={busy} onClick={() => setSheet('add')}>Add</button>
        </div>
      </div>

      {members.isLoading ? <Loading /> : members.error ? (
        <button className="link-retry" onClick={() => members.refetch()}>Couldn’t load members. Retry</button>
      ) : (
        <div className="member-list">
          {roster.map(member => {
            const isSelf = member.userId === user?.id;
            const showRole = canChangeRoles(group) && member.role !== 'owner' && member.role !== 'guest' && !isSelf;
            const showRemove = canRemoveMember(group, member, user?.id);
            const toggle = roleToggle(member);
            return (
              <div key={member.userId} className="card member-row">
                <Avatar person={member} size={36} />
                <span className="member-body">
                  <span className="member-name">{isSelf ? `${member.displayName} (you)` : member.displayName}</span>
                  <RoleBadge role={member.role} />
                </span>
                {showRole && <button className="pill-btn" disabled={busy} onClick={() => run(() => api.changeMemberRole(group.id, member.userId, toggle.next), 'Couldn’t change role. Try again.')}>{toggle.label}</button>}
                {showRemove && <button className="pill-btn danger" disabled={busy} onClick={() => setRemoveTarget(member)}>Remove</button>}
              </div>
            );
          })}
        </div>
      )}

      {error && <p className="form-error manage-error">{error}</p>}

      {sheet === 'invite' && <InviteSheet groupId={group.id} groupName={group.name} onClose={() => setSheet(null)} />}
      {sheet === 'add' && <AddMemberSheet groupId={group.id} onClose={() => setSheet(null)} />}
      {sheet === 'session' && (
        <SessionSheet group={group} busy={busy} onClose={() => setSheet(null)}
          onSave={(start, end) => run(() => api.updateSession(group.id, start, end), 'Couldn’t save the session time. Try again.').then(() => setSheet(null))} />
      )}
      {removeTarget && (
        <ConfirmSheet
          title={`Remove ${removeTarget.displayName}?`}
          body="They’ll drop off this group’s roster and leaderboard. Their recorded matches stay."
          confirmLabel="Remove"
          busy={busy}
          onConfirm={() => { const target = removeTarget; setRemoveTarget(null); run(() => api.removeMember(group.id, target.userId), 'Couldn’t remove member. Try again.'); }}
          onClose={() => setRemoveTarget(null)}
        />
      )}
    </>
  );
}

function RoleBadge({ role }: { role: string }) {
  const strong = role === 'owner' || role === 'admin';
  return <span className={`role-badge ${strong ? 'strong' : ''}`}>{role.toUpperCase()}</span>;
}

function SessionSheet({ group, busy, onClose, onSave }: { group: Group; busy: boolean; onClose: () => void; onSave: (start: string | null, end: string | null) => void }) {
  const [start, setStart] = useState(group.sessionStart ?? '19:00');
  const [end, setEnd] = useState(group.sessionEnd ?? '21:00');
  const valid = sessionValid(start, end);
  return (
    <div className="sheet-backdrop" onClick={() => !busy && onClose()}>
      <div className="sheet" role="dialog" aria-modal="true" onClick={event => event.stopPropagation()}>
        <div className="sheet-head"><span>Session time</span><button className="icon-button" onClick={onClose} aria-label="Close" disabled={busy}><Icon name="close" size={20} /></button></div>
        <label className="time-field"><span>Start</span><input type="time" value={start} onChange={event => setStart(event.target.value)} /></label>
        <label className="time-field"><span>End</span><input type="time" value={end} onChange={event => setEnd(event.target.value)} /></label>
        {!valid && <p className="form-error">Start must be before end.</p>}
        <div className="session-sheet-actions">
          {group.sessionStart && <button className="outline-action" disabled={busy} onClick={() => onSave(null, null)}>Clear</button>}
          <button className="record-button" disabled={!valid || busy} onClick={() => onSave(start, end)}>{busy ? 'Saving…' : 'Save'}</button>
        </div>
      </div>
    </div>
  );
}

function ConfirmSheet({ title, body, confirmLabel, busy, onConfirm, onClose }: { title: string; body: string; confirmLabel: string; busy: boolean; onConfirm: () => void; onClose: () => void }) {
  return (
    <div className="sheet-backdrop" onClick={() => !busy && onClose()}>
      <div className="sheet" role="dialog" aria-modal="true" onClick={event => event.stopPropagation()}>
        <div className="sheet-head"><span>{title}</span></div>
        <p className="muted confirm-body">{body}</p>
        <div className="session-sheet-actions">
          <button className="outline-action" disabled={busy} onClick={onClose}>Cancel</button>
          <button className="outline-action delete" disabled={busy} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
