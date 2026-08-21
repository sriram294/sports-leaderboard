import { useEffect, useRef, type ReactNode, type RefObject } from 'react';
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
    <span className={`wordmark ${size}`} role="img" aria-label="Playboard">
      <img className="wordmark-racket" src={racketLogo} alt="" aria-hidden="true" />
      <span className="wordmark-text" aria-hidden="true">layboard</span>
    </span>
  );
}

/** A row of small win/loss dots — the leaderboard's per-player form trend, oldest result first. */
export function FormDots({ results }: { results: boolean[] }) {
  if (results.length === 0) return null;
  return (
    <span className="form-dots" aria-label={`Recent form: ${results.map(w => (w ? 'win' : 'loss')).join(', ')}`}>
      {results.map((win, i) => <span key={i} className={`form-dot ${win ? 'win' : 'loss'}`} />)}
    </span>
  );
}

export function Button({ children, onClick, variant = 'primary', type = 'button', disabled = false }: { children: ReactNode; onClick?: () => void; variant?: 'primary' | 'ghost' | 'danger'; type?: 'button' | 'submit'; disabled?: boolean }) {
  return <button type={type} className={`button ${variant}`} onClick={onClick} disabled={disabled}>{children}</button>;
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function Loading() {
  return <div className="loading" role="status" aria-label="Loading"><span /><span /><span /></div>;
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="empty error" role="alert"><strong>Couldn’t load this</strong><p>{message}</p>{retry && <Button variant="ghost" onClick={retry}>Try again</Button>}</div>;
}

/** Focus trap, Escape dismissal, and focus restoration shared by modal sheets/dialogs. */
export function useDialogA11y(onClose: () => void, active = true): RefObject<HTMLDivElement | null> {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!active) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const dialog = dialogRef.current;
    const focusable = () => [...(dialog?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])') ?? [])];
    queueMicrotask(() => (dialog?.querySelector<HTMLElement>('[autofocus]') ?? focusable()[0] ?? dialog)?.focus());
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); closeRef.current(); return; }
      if (event.key !== 'Tab') return;
      const items = focusable();
      if (items.length === 0) { event.preventDefault(); return; }
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    dialog?.addEventListener('keydown', onKeyDown);
    return () => { dialog?.removeEventListener('keydown', onKeyDown); previous?.focus(); };
  }, [active]);
  return dialogRef;
}

export function GroupPicker({ groups, active, onChange }: { groups: Group[]; active?: Group; onChange: (g: Group) => void }) {
  return <label className="group-picker"><span className="group-dot" style={{ background: active?.avatarColor }} /> <select value={active?.id || ''} onChange={e => { const g = groups.find(x => x.id === e.target.value); if (g) onChange(g); }}>{groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}</select><span>⌄</span></label>;
}
