import { useEffect, useRef, useState } from 'react';
import type { Session } from '../../models';
import { Button } from '../../components';

export function LoginScreen({ onLogin }: { onLogin: (session: Session) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const buttonHost = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    const googleApi = (window as any).google;
    if (!clientId) { setError('Google sign-in is not configured yet.'); return; }
    if (!googleApi) { setError('Google Identity Services did not load.'); return; }

    googleApi.accounts.id.initialize({
      client_id: clientId,
      use_fedcm_for_prompt: true,
      callback: async (response: { credential: string }) => {
        setBusy(true); setError('');
        try {
          const res = await fetch(`${import.meta.env.VITE_API_URL || '/api/v1'}/auth/google`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idToken: response.credential }),
          });
          if (!res.ok) throw new Error('Sign-in was rejected.');
          const tokens = await res.json();
          onLogin({ ...tokens, expiresAt: Date.now() + tokens.expiresIn * 1000 });
        } catch (cause) {
          setError(cause instanceof Error ? cause.message : 'Sign-in failed.');
        } finally { setBusy(false); }
      },
    });

    if (buttonHost.current) {
      googleApi.accounts.id.renderButton(buttonHost.current, {
        type: 'standard', theme: 'filled_black', size: 'large',
        text: 'continue_with', shape: 'pill', width: 360, logo_alignment: 'center',
      });
    }
  }, [onLogin]);

  return <main className="login"><div className="brand-mark">P</div><p className="eyebrow">PLAYBOARD</p><h1>Badminton,<br /><em>beautifully tracked.</em></h1><p className="login-copy">Your games. Your crew. One leaderboard.</p>{error && <p className="form-error">{error}</p>}<div className="google-signin-wrap"><Button disabled={busy}>{busy ? 'Connecting…' : 'Continue with Google'}</Button><div className="google-signin-overlay" ref={buttonHost} aria-hidden="true" /></div><p className="legal">By continuing, you agree to play fair and keep the game moving.</p></main>;
}
