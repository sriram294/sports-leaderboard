import { Wordmark } from '../../components';
import { GoogleSignInButton } from './GoogleSignInButton';

/**
 * Login gate. Google Identity Services renders its real button into a hidden
 * overlay stretched over our styled button, so a tap triggers GIS (the reliable
 * FedCM path) while the visible control matches the Android white pill. On
 * success the Google ID token is exchanged at /auth/google and the session starts.
 */
export function LoginScreen() {
  return (
    <main className="login">
      <div className="login-hero">
        <Wordmark size="lg" />
      </div>

      <div className="login-action">
        <GoogleSignInButton />

        <p className="legal">By continuing you agree to play fair and keep the game moving.</p>
      </div>
    </main>
  );
}
