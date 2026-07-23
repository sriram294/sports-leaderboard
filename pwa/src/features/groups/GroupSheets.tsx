import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../data';
import { useGroups } from '../../groups';
import { groupErrorMessage } from '../../domain';
import { membersKey } from '../../queries';
import { Icon } from '../../icons';
import type { Group } from '../../models';

/**
 * The group create/join/invite/add-member/rename bottom sheets, mirroring the Android
 * switcher sheets (`ui/switcher/*Sheet.kt`). Shared by the header GroupSwitcher and the
 * `/groups` management screen so both surfaces behave identically. Each sheet owns its own
 * TanStack invalidation and maps the API's stable `code` values to a message.
 */

function SheetShell({ title, onClose, busy, children }: { title: string; onClose: () => void; busy?: boolean; children: React.ReactNode }) {
  return (
    <div className="sheet-backdrop" onClick={() => !busy && onClose()}>
      <div className="sheet" role="dialog" aria-modal="true" onClick={event => event.stopPropagation()}>
        <div className="sheet-head">
          <span>{title}</span>
          <button className="icon-button" onClick={onClose} aria-label="Close" disabled={busy}><Icon name="close" size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** Create a new group, or join one by invite code — makes the resulting group active. */
export function CreateJoinSheet({ onClose }: { onClose: () => void }) {
  const { setActiveGroup } = useGroups();
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<'create' | 'join'>('create');
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  const submit = async () => {
    if (!value.trim() || busy) return;
    setBusy(true);
    setError(undefined);
    try {
      const group = mode === 'create' ? await api.createGroup(value.trim()) : await api.joinGroup(value.trim().toUpperCase());
      await queryClient.invalidateQueries({ queryKey: ['groups'] });
      setActiveGroup(group.id);
      onClose();
    } catch (cause) {
      setError(groupErrorMessage(cause instanceof ApiError ? cause.code : undefined, mode === 'create' ? 'Couldn’t create the group. Try again.' : 'Couldn’t join. Check the code and try again.'));
      setBusy(false);
    }
  };

  return (
    <SheetShell title="Groups" onClose={onClose} busy={busy}>
      <div className="segmented">
        <button className={mode === 'create' ? 'selected' : ''} onClick={() => { setMode('create'); setValue(''); setError(undefined); }}>Create group</button>
        <button className={mode === 'join' ? 'selected' : ''} onClick={() => { setMode('join'); setValue(''); setError(undefined); }}>Join group</button>
      </div>
      <input
        className="rename-input" value={value} autoFocus
        aria-label={mode === 'create' ? 'Group name' : 'Invite code'}
        placeholder={mode === 'create' ? 'Saturday Smashers' : 'Invite code'}
        onChange={event => { setValue(event.target.value); setError(undefined); }}
        onKeyDown={event => { if (event.key === 'Enter') submit(); }}
      />
      {error && <p className="form-error">{error}</p>}
      <button className="record-button" disabled={!value.trim() || busy} onClick={submit}>
        {busy ? 'Saving…' : mode === 'create' ? 'Create group' : 'Join group'}
      </button>
    </SheetShell>
  );
}

/** Rename a group (owner/admin). */
export function RenameGroupSheet({ group, onClose }: { group: Group; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(group.name);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const submit = async () => {
    if (!name.trim() || busy) return;
    setBusy(true);
    setFailed(false);
    try {
      await api.renameGroup(group.id, name.trim());
      await queryClient.invalidateQueries({ queryKey: ['groups'] });
      onClose();
    } catch { setFailed(true); setBusy(false); }
  };
  return (
    <SheetShell title="Rename group" onClose={onClose} busy={busy}>
      <input
        className="rename-input" value={name} autoFocus aria-label="Group name"
        onChange={event => { setName(event.target.value); setFailed(false); }}
        onKeyDown={event => { if (event.key === 'Enter') submit(); }}
      />
      {failed && <p className="form-error">Couldn’t rename. Try again.</p>}
      <button className="record-button" disabled={!name.trim() || busy} onClick={submit}>{busy ? 'Saving…' : 'Save'}</button>
    </SheetShell>
  );
}

/** Generate + share an invite code for a group (owner/admin). */
export function InviteSheet({ groupId, groupName, onClose }: { groupId: string; groupName: string; onClose: () => void }) {
  const [code, setCode] = useState<string>();
  const [failed, setFailed] = useState(false);
  const [copied, setCopied] = useState(false);

  const generate = () => {
    setFailed(false);
    setCode(undefined);
    api.createInvite(groupId).then(invite => setCode(invite.code)).catch(() => setFailed(true));
  };
  useEffect(generate, [groupId]);

  const copy = () => { if (code) navigator.clipboard?.writeText(code).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500); }).catch(() => undefined); };
  const share = () => { if (code && navigator.share) navigator.share({ title: 'Join my Playboard group', text: `Join "${groupName}" on Playboard with code ${code}` }).catch(() => undefined); };

  return (
    <SheetShell title="Invite players" onClose={onClose}>
      {!code && !failed && <p className="invite-status">Generating a code…</p>}
      {failed && (
        <div className="invite-status">
          <p className="form-error">Couldn’t create an invite.</p>
          <button className="outline-action" onClick={generate}>Try again</button>
        </div>
      )}
      {code && (
        <>
          <p className="muted invite-hint">Share this code — anyone can join {groupName} with it:</p>
          <p className="invite-code">{code}</p>
          <div className="invite-actions">
            <button className="outline-action" onClick={copy}>{copied ? 'Copied!' : 'Copy code'}</button>
            {typeof navigator !== 'undefined' && 'share' in navigator && <button className="outline-action" onClick={share}>Share</button>}
          </div>
        </>
      )}
    </SheetShell>
  );
}

/** Add a member to a group by email + name (owner/admin). */
export function AddMemberSheet({ groupId, onClose }: { groupId: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  const submit = async () => {
    if (!name.trim() || !email.trim() || busy) return;
    if (!email.includes('@')) { setError('Enter a valid email address.'); return; }
    setBusy(true);
    setError(undefined);
    try {
      await api.addMember(groupId, email.trim(), name.trim());
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['groups'] }),
        queryClient.invalidateQueries({ queryKey: membersKey(groupId) }),
      ]);
      onClose();
    } catch (cause) {
      setError(groupErrorMessage(cause instanceof ApiError ? cause.code : undefined, 'Couldn’t add the member. Try again.'));
      setBusy(false);
    }
  };

  return (
    <SheetShell title="Add member by email" onClose={onClose} busy={busy}>
      <input className="rename-input add-field" value={name} autoFocus aria-label="Name" placeholder="Name"
        onChange={event => { setName(event.target.value); setError(undefined); }} />
      <input className="rename-input add-field" value={email} type="email" aria-label="Email" placeholder="sam@gmail.com"
        onChange={event => { setEmail(event.target.value); setError(undefined); }}
        onKeyDown={event => { if (event.key === 'Enter') submit(); }} />
      {error && <p className="form-error">{error}</p>}
      <button className="record-button" disabled={!name.trim() || !email.trim() || busy} onClick={submit}>{busy ? 'Adding…' : 'Add member'}</button>
    </SheetShell>
  );
}
