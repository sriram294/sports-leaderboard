import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Wordmark } from '../../components';
import { ApiError } from '../../data';
import { useSession } from '../../session';
import { GoogleSignInButton } from '../auth/GoogleSignInButton';

const REQUIRED_CONFIRMATION = 'DELETE';

/** Public Google Play deletion resource and authenticated deletion form. */
export function DeleteAccountPage() {
  const { status, user, deleteAccount } = useSession();
  const queryClient = useQueryClient();
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [deleted, setDeleted] = useState(false);

  const submit = async () => {
    if (confirmation !== REQUIRED_CONFIRMATION || busy) return;
    setBusy(true); setError(undefined);
    try {
      await deleteAccount(confirmation);
      queryClient.clear();
      setDeleted(true);
    } catch (cause) {
      setError(cause instanceof ApiError
        ? cause.message
        : 'Couldn’t delete your account. Check your connection and try again.');
    } finally { setBusy(false); }
  };

  return (
    <main className="public-account-page">
      <div className="public-account-card">
        <Wordmark size="lg" />
        <h1>Delete your Playboard account</h1>
        {deleted ? (
          <div role="status">
            <h2>Account deleted</h2>
            <p>Your profile and sign-in credentials have been permanently removed.</p>
          </div>
        ) : (
          <>
            <p>
              This permanently removes your Playboard profile, email, avatar, sign-in identities,
              device registrations, and active group memberships.
            </p>
            <p>
              Shared match scores and group records remain only as anonymous history under
              “Deleted player.” Groups you own transfer to another member or are archived.
            </p>
            {status === 'loading' && <p role="status">Checking your session…</p>}
            {status === 'anon' && (
              <section className="delete-signin">
                <h2>Verify your account</h2>
                <p>Sign in with the Google account you want to delete.</p>
                <GoogleSignInButton />
              </section>
            )}
            {status === 'authed' && (
              <section className="delete-form">
                <p>Signed in as <strong>{user?.email}</strong></p>
                <label htmlFor="delete-confirmation">Type DELETE to confirm</label>
                <input
                  id="delete-confirmation"
                  value={confirmation}
                  disabled={busy}
                  autoComplete="off"
                  onChange={event => { setConfirmation(event.target.value); setError(undefined); }}
                />
                {error && <p className="form-error" role="alert">{error}</p>}
                <button
                  type="button"
                  className="danger-button"
                  disabled={confirmation !== REQUIRED_CONFIRMATION || busy}
                  onClick={submit}
                >
                  {busy ? 'Deleting…' : 'Delete account permanently'}
                </button>
              </section>
            )}
          </>
        )}
        <p className="public-links"><Link to="/privacy">Privacy and data retention</Link></p>
      </div>
    </main>
  );
}
