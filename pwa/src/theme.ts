import { useCallback, useEffect, useState } from 'react';

/**
 * Theme preference store — the web analog of Android's `data/settings/ThemeStore.kt`.
 * Playboard defaults to dark and offers a manual light switch in Settings; it does
 * NOT follow the OS theme. The choice is persisted in localStorage and reflected as
 * a `data-theme` attribute on <html>, which drives the token overrides in theme.css.
 */
export type ThemeMode = 'dark' | 'light';

const STORAGE_KEY = 'playboard.theme';

export function getStoredTheme(): ThemeMode {
  return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark';
}

/** Reflect the mode onto <html data-theme> so CSS token overrides apply. */
export function applyTheme(mode: ThemeMode): void {
  document.documentElement.setAttribute('data-theme', mode);
}

/** Call once before first render to avoid a flash of the wrong theme. */
export function initTheme(): ThemeMode {
  const mode = getStoredTheme();
  applyTheme(mode);
  return mode;
}

function persist(mode: ThemeMode): void {
  localStorage.setItem(STORAGE_KEY, mode);
  applyTheme(mode);
  window.dispatchEvent(new CustomEvent('playboard:theme', { detail: mode }));
}

/** React binding: `[mode, setMode]`, kept in sync across components/tabs. */
export function useTheme(): [ThemeMode, (mode: ThemeMode) => void] {
  const [mode, setMode] = useState<ThemeMode>(getStoredTheme);

  useEffect(() => {
    const onChange = (event: Event) => {
      const next = (event as CustomEvent<ThemeMode>).detail;
      if (next) setMode(next);
    };
    window.addEventListener('playboard:theme', onChange);
    return () => window.removeEventListener('playboard:theme', onChange);
  }, []);

  const update = useCallback((next: ThemeMode) => {
    setMode(next);
    persist(next);
  }, []);

  return [mode, update];
}
