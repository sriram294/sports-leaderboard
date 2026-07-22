import type { ReactNode } from 'react';
import type { Group } from './models';

/** Single first-letter initial, matching Android's `PlayerAvatar` (`displayName.take(1)`) —
 *  which is why a guest ("Guest 1") shows "G", not "G1". */
const avatarInitial = (name: string) => name.trim().charAt(0).toUpperCase() || '?';
import racketLogo from './assets/brand/logo-racket.png';

/** Anything renderable as an avatar: a user, ranking row, group member, or match player. */
export type AvatarPerson = {
  displayName: string;
  photoUrl?: string | null;
  avatarId?: string | null;
  avatarColor: string;
};

/**
 * Avatar with the app-wide fallback chain (PlayerAvatar.kt): uploaded photo →
 * bundled default avatar (`/avatars/{id}.png`) → colored-initial circle. The
 * initials circle is always drawn as the base, so a missing/broken image still
 * shows a graceful fallback.
 */
export function Avatar({ person, size = 'md', ring = false }: { person: AvatarPerson; size?: 'sm' | 'md' | 'lg' | number; ring?: boolean }) {
  const src = person.photoUrl || (person.avatarId ? `/avatars/${person.avatarId}.png` : undefined);
  const px = typeof size === 'number';
  return (
    <span
      className={px ? 'avatar' : `avatar ${size}`}
      style={{
        background: person.avatarColor,
        ...(px ? { width: size, height: size, fontSize: Math.round(size * 0.34) } : {}),
        ...(ring ? { boxShadow: `0 0 0 2px ${person.avatarColor}` } : {}),
      }}
    >
      {avatarInitial(person.displayName)}
      {src && (
        <img
          className="avatar-img"
          src={src}
          alt=""
          loading="lazy"
          onError={event => { event.currentTarget.style.display = 'none'; }}
        />
      )}
    </span>
  );
}

/** Group mark — filled rounded-square with the group's initial (GroupAvatar.kt). */
export function GroupAvatar({ group, size = 36 }: { group: { name: string; avatarColor: string }; size?: number }) {
  return (
    <span className="group-avatar" style={{ background: group.avatarColor, width: size, height: size }}>
      {group.name.trim()[0]?.toUpperCase() || '?'}
    </span>
  );
}

/** Playboard wordmark — racket logo as the "P" + "layboard" in Paytone One. */
export function Wordmark({ size = 'sm' }: { size?: 'sm' | 'lg' }) {
  return (
    <span className={`wordmark ${size}`} aria-label="Playboard">
      <img className="wordmark-racket" src={racketLogo} alt="" aria-hidden="true" />
      <span className="wordmark-text" aria-hidden="true">layboard</span>
    </span>
  );
}

/** W/L result chip used by the Board form bar and the Stats form rows. */
export function FormPill({ win }: { win: boolean }) {
  return <span className={`form-pill ${win ? 'win' : 'loss'}`} aria-label={win ? 'Win' : 'Loss'}>{win ? 'W' : 'L'}</span>;
}

export function Button({ children, onClick, variant = 'primary', type = 'button', disabled = false }: { children: ReactNode; onClick?: () => void; variant?: 'primary' | 'ghost' | 'danger'; type?: 'button' | 'submit'; disabled?: boolean }) {
  return <button type={type} className={`button ${variant}`} onClick={onClick} disabled={disabled}>{children}</button>;
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function Loading() {
  return <div className="loading" aria-label="Loading"><span /><span /><span /></div>;
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="empty error"><strong>Couldn’t load this</strong><p>{message}</p>{retry && <Button variant="ghost" onClick={retry}>Try again</Button>}</div>;
}

export function GroupPicker({ groups, active, onChange }: { groups: Group[]; active?: Group; onChange: (g: Group) => void }) {
  return <label className="group-picker"><span className="group-dot" style={{ background: active?.avatarColor }} /> <select value={active?.id || ''} onChange={e => { const g = groups.find(x => x.id === e.target.value); if (g) onChange(g); }}>{groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}</select><span>⌄</span></label>;
}
