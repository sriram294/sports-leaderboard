import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, auth, type AuthTokens } from './data';
import type { User } from './models';

/**
 * Session state — the web analog of Android's `SessionViewModel` (Loading /
 * SignedIn / SignedOut). On boot, a stored session is validated with `/users/me`
 * (which transparently refreshes an expired access token) before the app renders,
 * so there's no login flash and no Board shown behind a dead token.
 */
export type SessionStatus = 'loading' | 'authed' | 'anon';

type SessionValue = {
  status: SessionStatus;
  user?: User;
  /** Persist tokens after a successful Google exchange and move to `authed`. */
  login: (tokens: AuthTokens) => void;
  /** Revoke the refresh token (best-effort) and clear local state. */
  signOut: () => void;
};

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<SessionStatus>(() => (auth.get() ? 'loading' : 'anon'));
  const [user, setUser] = useState<User | undefined>(() => auth.get()?.user);

  useEffect(() => {
    if (!auth.get()) return;
    let active = true;
    api.me()
      .then(me => {
        if (!active) return;
        setUser(me);
        setStatus('authed');
      })
      .catch(() => {
        if (!active) return;
        // Refresh failed / token revoked — request() already cleared the session.
        auth.set(null);
        setUser(undefined);
        setStatus('anon');
      });
    return () => { active = false; };
  }, []);

  const login = (tokens: AuthTokens) => {
    auth.set({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      expiresAt: Date.now() + tokens.expiresIn * 1000,
      user: tokens.user,
    });
    setUser(tokens.user);
    setStatus('authed');
  };

  const signOut = () => {
    api.logout().catch(() => undefined);
    auth.set(null);
    setUser(undefined);
    setStatus('anon');
  };

  return <SessionContext.Provider value={{ status, user, login, signOut }}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession must be used within SessionProvider');
  return value;
}
