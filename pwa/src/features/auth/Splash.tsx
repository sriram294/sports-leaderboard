import { Wordmark } from '../../components';

/** Shown while a stored session is validated on boot (Android's splash gate). */
export function Splash() {
  return (
    <main className="splash">
      <Wordmark size="lg" />
      <div className="loading" aria-label="Loading"><span /><span /><span /></div>
    </main>
  );
}
