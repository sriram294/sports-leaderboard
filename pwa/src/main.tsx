import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { App } from './app/App';
import { SessionProvider } from './session';
import { initTheme } from './theme';
import './theme.css';

// Reflect the stored theme onto <html> before first paint.
initTheme();

// Web analog of Android's `dataRevision`: match/group mutations invalidate these
// queries so Board, Matches, Profile, and Stats reload in lockstep (wired up per
// slice as screens migrate to useQuery).
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () =>
    navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).catch(() => undefined),
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <App />
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
