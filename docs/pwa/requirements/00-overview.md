# PWA scope and parity

Playboard Web supports current Chrome, Edge, Safari, and Firefox on phone and desktop widths. It is dark-only, mobile-first, and centers a 560px application shell on wide screens. Android screenshots and the existing Android requirements are the parity source of truth; browser-specific equivalents are allowed for Google sign-in, share, file selection, and push.

Acceptance: install prompt is available, app shell works offline after first load, no horizontal scroll occurs at 320px, and every authenticated destination remains usable at keyboard focus.

Tests: shell visual review at 390px and 1280px, manifest/service-worker smoke test, and keyboard traversal of navigation and dialogs.
