import { Link } from 'react-router-dom';
import { Wordmark } from '../../components';

/** Public disclosure supporting Playboard's account-deletion flow. */
export function PrivacyPage() {
  return (
    <main className="public-account-page">
      <article className="public-account-card privacy-copy">
        <Wordmark size="lg" />
        <h1>Playboard privacy and data retention</h1>
        <p>Playboard is developed by Sriram Elangovan.</p>
        <h2>Account data</h2>
        <p>We use your Google identity, email, display name, and avatar to provide your account and group experience.</p>
        <h2>When you delete your account</h2>
        <p>
          Your email, profile, avatar, authentication identities, refresh tokens, notification registrations,
          and active memberships are permanently removed. A later sign-in creates a new unrelated account.
        </p>
        <h2>Anonymous shared history</h2>
        <p>
          Match scores, aggregate statistics, trophies, and audit records shared with a group are retained for
          group integrity, but your identity is replaced with “Deleted player” and cannot be reconnected to a new account.
        </p>
        <p><Link to="/delete-account">Delete your Playboard account</Link></p>
      </article>
    </main>
  );
}
