# Playboard PWA plan

The PWA is a separate Vite/React/TypeScript client in [`pwa/`](../../pwa/). The Spring Boot API remains the source of truth. The application uses feature-oriented modules, a single authenticated shell, and a shared active-group selection so Board, Matches, Add, Stats, and Profile cannot drift apart.

## Delivery order

1. Foundation and design system
2. Google sign-in and token refresh
3. Authenticated navigation, groups, and switching
4. Board and leaderboard
5. Match history/detail
6. Add/edit match
7. Profile and player stats
8. Insights
9. Browser sharing and push
10. Accessibility, offline shell, and release hardening

Each slice must cover loading, empty, API failure, permission failure where applicable, mobile layout, keyboard interaction, and a focused test before release. Backend changes require a documented contract gap first.

## Local development

```bash
cd pwa
npm install
VITE_API_URL=http://localhost:8080/api/v1 npm run dev
```

Set `VITE_GOOGLE_CLIENT_ID` to the web OAuth client accepted by `GOOGLE_CLIENT_ID`. Static hosting must serve `index.html` for deep links and expose `manifest.webmanifest` and `sw.js` from the app root.
