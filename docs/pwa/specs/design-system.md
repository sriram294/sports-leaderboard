# Design system

The visual foundation for the PWA, ported from the Android app's `ui/theme/*` and
`ui/components/*` so the two clients read as one product. Parity target: the v4.4
screenshots in `docs/android screens/*.jpeg`.

## Tokens (`src/theme.css`)

Ported verbatim from `ui/theme/Color.kt`. Dark is the default; light is a manual
switch (see Theming). Exposed as CSS custom properties on `:root`.

| Token | Dark | Light |
|---|---|---|
| `--brand` (lime accent) | `#9ADE28` | `#4E8C0A` |
| `--on-brand` | `#0A0A0A` | `#FFFFFF` |
| `--bg` | `#0A0A0A` | `#FAFAFA` |
| `--surface` | `#141414` | `#FFFFFF` |
| `--surface-2` | `#1D1D1D` | `#F0F0F0` |
| `--text` | `#F5F5F5` | `#1A1A1A` |
| `--muted` | `#9E9E9E` | `#6B6B6B` |
| `--line` | `#292929` | `#E4E4E4` |
| `--stat-win` | `#4ADE80` | `#16A34A` |
| `--stat-loss` | `#F87171` | `#DC2626` |
| `--rate-mid` | `#FACC15` | `#CA8A04` |
| `--rate-low` | `#60A5FA` | `#2563EB` |
| `--glow-warm` | `rgba(154,222,40,.149)` | `rgba(154,222,40,.169)` |
| `--glow-cool` | `rgba(91,140,255,.102)` | `rgba(37,99,235,.090)` |

Rank colors: #1 `--brand`, #2 `--text`, #3 `--rate-mid`. Win% tiers ≥50 `--brand` /
≥25 `--rate-mid` / else `--rate-low`. Rating tiers (Wilson) ≥40 / ≥25 / else, same colors.
Radii 12–16px, 8px spacing rhythm, safe-area padding. Lime = active/positive; red = destructive/error only.

## Typography

- **Manrope** (self-hosted static weights 400/500/600/700/800) is the single UI face
  — `src/assets/fonts/manrope-*.ttf`, declared as `@font-face`.
- **Paytone One** — display face used only for the "layboard" wordmark.
- **Tabular figures** (`font-feature-settings: 'tnum'`) applied globally so numeric
  columns don't jitter (matches Android).

## Ambient glow background

The app's defining visual (`ui/components/PlayboardBackground.kt`): a fixed layer
(`body::before`, `z-index:-1`) painting the opaque `--bg` base plus two low-opacity
radial glows — warm lime top-left, cool blue low-right. **Screens and the app shell
must stay transparent** (no opaque `background`) so the glow shows through.

## Theming

`src/theme.ts` is the web analog of `ThemeStore.kt`: the choice (`dark`|`light`) is
persisted in `localStorage` and reflected as `<html data-theme>`, which drives the
token overrides. Default dark; does **not** follow the OS theme. `initTheme()` runs
before first paint (no flash); `useTheme()` is the React binding used by the Settings toggle.

## Shared components (`src/components.tsx`, `src/icons.tsx`)

- **`Icon`** — inline-SVG set ported from `res/drawable/*.xml` (Material Symbols +
  custom marks); tinted via `currentColor`. Names: board, matches, stats, profile,
  add, settings, share, group, back, edit, invite, mail, expand, close.
- **`Avatar`** — fallback chain from `PlayerAvatar.kt`: uploaded photo → bundled
  default (`/avatars/{id}.png`) → colored-initial circle (always the base layer).
- **`Wordmark`** — racket logo as the "P" + "layboard" in Paytone One (sizes sm/lg).
- **`FormPill`** — W/L result chip (Board form bar, Stats form rows).
- **`Button` / `Card` / `Loading` / `ErrorState` / `GroupPicker`** — theme-aware primitives.

Shared controls remain semantic `<button>`/`<input>`. Per-screen CSS is retokenized
as each screen's slice lands; a few scaffold aliases (`--lime`, `--red`) remain until then.
