import { useState } from 'react';
import { useTheme } from '../../theme';
import { Icon } from '../../icons';
import pkg from '../../../package.json';

/**
 * Account + application settings, opened from the signed-in user's profile gear.
 * Mirrors Android's `ui/profile/SettingsScreen.kt`: ACCOUNT (identity, check-for-updates,
 * sign out) and APPEARANCE (dark-theme switch), with an app-info footer. The APK self-update
 * flow is replaced by a service-worker refresh — the web analog of "check for updates".
 */
export function SettingsScreen({ email, onBack, onSignOut }: { email: string; onBack: () => void; onSignOut: () => void }) {
  const [theme, setTheme] = useTheme();
  const [updateStatus, setUpdateStatus] = useState<string>();

  const checkForUpdates = async () => {
    setUpdateStatus('Checking…');
    try {
      const registration = await navigator.serviceWorker?.getRegistration();
      if (registration) {
        await registration.update();
        if (registration.waiting || registration.installing) {
          setUpdateStatus('Updating…');
          window.location.reload();
          return;
        }
      }
      setUpdateStatus('You’re on the latest version.');
    } catch {
      setUpdateStatus('Couldn’t check right now.');
    }
  };

  return (
    <>
      <button className="back" onClick={onBack}><Icon name="back" size={16} /> Profile</button>
      <h2 className="settings-title">Settings</h2>

      <p className="section-label">ACCOUNT</p>
      <div className="settings-account">
        <span className="settings-account-name">Signed in with Google</span>
        <span className="muted">{email}</span>
      </div>

      <div className="settings-divider" />
      <button className="settings-link accent" onClick={checkForUpdates}>
        <span>Check for updates</span>
        <span className="settings-chevron">›</span>
      </button>
      {updateStatus && <p className="muted settings-update-status">{updateStatus}</p>}

      <div className="settings-divider" />
      <button className="settings-link" onClick={onSignOut}>
        <span>Sign out</span>
        <span className="settings-chevron">›</span>
      </button>

      <p className="section-label">APPEARANCE</p>
      <div className="settings-toggle-row">
        <span className="settings-toggle-label">Dark theme</span>
        <button
          role="switch"
          aria-checked={theme === 'dark'}
          aria-label="Dark theme"
          className={`switch ${theme === 'dark' ? 'on' : ''}`}
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
        >
          <span className="switch-thumb" />
        </button>
      </div>

      <footer className="settings-footer">
        <p className="settings-version">Playboard v{pkg.version}</p>
        <p>Made by Sriram Elangovan</p>
        <p>Avatars: “3D Web3 Avatars” by Koncepted (Figma Community)</p>
      </footer>
    </>
  );
}
