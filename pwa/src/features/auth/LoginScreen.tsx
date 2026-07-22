import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../data';
import { useSession } from '../../session';
import { Wordmark } from '../../components';
import { GoogleLogo } from '../../icons';

type LoginError = { message: string; code?: string };

type GoogleIdentityServices = {
  accounts: {
    id: {
      initialize: (options: {
        client_id: string;
        use_fedcm_for_prompt: boolean;
        callback: (response: { credential: string }) => void;
      }) => void;
      renderButton: (host: HTMLElement, options: Record<string, string | number>) => void;
    };
  };
};

/** Wait for GIS when its async script has not finished loading at first render. */
function loadGoogleIdentityServices(): Promise<GoogleIdentityServices> {
  const existing = (window as unknown as { google?: GoogleIdentityServices }).google;
  if (existing) return Promise.resolve(existing);

  const script = document.querySelector<HTMLScriptElement>(
    'script[src="https://accounts.google.com/gsi/client"]',
  );
  if (!script) return Promise.reject(new Error('Google Identity Services script is missing.'));

  return new Promise((resolve, reject) => {
    const onLoad = () => {
      const google = (window as unknown as { google?: GoogleIdentityServices }).google;
      if (google) resolve(google);
      else reject(new Error('Google Identity Services loaded without its API.'));
    };
    script.addEventListener('load', onLoad, { once: true });
    script.addEventListener('error', () => reject(new Error('Google Identity Services did not load.')), { once: true });
  });
}

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
    if (!clientId) { setError({ message: 'Google sign-in is not configured yet.', code: 'NO_CLIENT_ID' }); return; }
    let cancelled = false;

    loadGoogleIdentityServices().then((googleApi) => {
      if (cancelled) return;
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
    }).catch(() => {
      if (!cancelled) setError({ message: 'Google Identity Services did not load.', code: 'GIS_UNAVAILABLE' });
    });

    return () => { cancelled = true; };
  }, [login]);

  const copyCode = (code: string) => navigator.clipboard?.writeText(code).catch(() => undefined);

  return (
    <main className="login">
      <div className="login-hero">
        <Wordmark size="lg" />
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
