import { useState } from 'react';
import type { User } from '../../models';
import { api, ApiError } from '../../data';
import { Avatar, Button, Card } from '../../components';

export function ProfileScreen({ user, onSignOut, onGroupsChanged }: { user: User; onSignOut: () => void; onGroupsChanged: () => void }) {
  const [mode, setMode] = useState<'create' | 'join'>('create');
  const [name, setName] = useState(''); const [code, setCode] = useState('');
  const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false);
  const submitGroup = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      if (mode === 'create') { if (!name.trim()) throw new Error('Enter a group name.'); await api.createGroup({ name: name.trim(), sportCode: 'badminton_doubles' }); setName(''); }
      else { if (!code.trim()) throw new Error('Enter an invite code.'); await api.joinGroup({ code: code.trim().toUpperCase() }); setCode(''); }
      onGroupsChanged(); setMessage(mode === 'create' ? 'Group created.' : 'You joined the group.');
    } catch (cause) { setMessage(cause instanceof ApiError ? cause.message : cause instanceof Error ? cause.message : 'Could not update groups.'); }
    finally { setBusy(false); }
  };
  return <><p className="eyebrow">ACCOUNT</p><h2>Profile</h2><Card className="identity"><Avatar person={user} size="lg" /><h3>{user.displayName}</h3><p>{user.email}</p><Button variant="ghost">Edit profile</Button></Card><Card><div className="card-heading"><h3>Groups</h3><span>PLAY WITH YOUR CREW</span></div><div className="segmented"><button className={mode === 'create' ? 'selected' : ''} onClick={() => setMode('create')}>Create group</button><button className={mode === 'join' ? 'selected' : ''} onClick={() => setMode('join')}>Join group</button></div><form className="group-form" onSubmit={submitGroup}><input aria-label={mode === 'create' ? 'Group name' : 'Invite code'} placeholder={mode === 'create' ? 'Saturday Smashers' : 'Invite code'} value={mode === 'create' ? name : code} onChange={event => mode === 'create' ? setName(event.target.value) : setCode(event.target.value)} />{message && <p className={message.endsWith('.') && !message.includes('Could') ? 'success' : 'form-error'}>{message}</p>}<Button type="submit" disabled={busy}>{busy ? 'Saving…' : mode === 'create' ? 'Create group' : 'Join group'}</Button></form></Card><Card><div className="setting-row"><span>Notifications</span><span className="toggle on">●</span></div><div className="setting-row"><span>Dark appearance</span><span className="muted">Always on</span></div></Card><Button variant="danger" onClick={onSignOut}>Sign out</Button></>;
}
