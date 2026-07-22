import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../data';
import { useSession } from '../../session';
import { Wordmark } from '../../components';
import { GoogleLogo } from '../../icons';

type LoginError = { message: string; code?: string };

/**
 * Login gate. Google Identity Services renders its real button into a hidden
 * overlay stretched over our styled button, so a tap triggers GIS (the reliable
 * FedCM path) while the visible control matches the Android white pill. On
 * success the Google ID token is exchanged at /auth/google and the session starts.
 */
export function LoginScreen() {
  const { login } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<LoginError | null>(null);
  const buttonHost = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    const googleApi = (window as unknown as { google?: any }).google;
    if (!clientId) { setError({ message: 'Google sign-in is not configured yet.', code: 'NO_CLIENT_ID' }); return; }
    if (!googleApi) { setError({ message: 'Google Identity Services did not load.', code: 'GIS_UNAVAILABLE' }); return; }

    googleApi.accounts.id.initialize({
      client_id: clientId,
      use_fedcm_for_prompt: true,
      callback: async (response: { credential: string }) => {
        setBusy(true); setError(null);
        try {
          const tokens = await api.googleSignIn(response.credential);
          login(tokens);
        } catch (cause) {
          setError(cause instanceof ApiError
            ? { message: 'Sign-in was rejected.', code: cause.code }
            : { message: cause instanceof Error ? cause.message : 'Sign-in failed.' });
        } finally { setBusy(false); }
      },
    });

    if (buttonHost.current) {
      googleApi.accounts.id.renderButton(buttonHost.current, {
        type: 'standard', theme: 'filled_black', size: 'large',
        text: 'continue_with', shape: 'pill', width: 360, logo_alignment: 'center',
      });
    }
  }, [login]);

  const copyCode = (code: string) => navigator.clipboard?.writeText(code).catch(() => undefined);

  return (
    <main className="login">
      <div className="login-hero">
        <Wordmark size="lg" />
        <p className="login-tagline">Badminton, <em>beautifully tracked.</em></p>
      </div>

      <div className="login-action">
        {error && (
          <div className="form-error" role="alert">
            <span>{error.message}</span>
            {error.code && (
              <code className="error-code" title="Tap to copy" onClick={() => copyCode(error.code!)}>
                Error code: {error.code}
              </code>
            )}
          </div>
        )}

        <div className="google-signin-wrap">
          <button type="button" className="google-button" disabled={busy}>
            <GoogleLogo />
            {busy ? 'Connecting…' : 'Continue with Google'}
          </button>
          <div className="google-signin-overlay" ref={buttonHost} aria-hidden="true" />
        </div>

        <p className="legal">By continuing you agree to play fair and keep the game moving.</p>
      </div>
    </main>
  );
}
