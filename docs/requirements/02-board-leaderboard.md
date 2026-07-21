# Board (Leaderboard) Screen

Source: `docs/prototype/leaderboard.pdf`, `docs/prototype/leaderboard_groups_dropdown.pdf`

## Purpose
Home tab. Shows the ranked leaderboard for the currently selected group.

## Layout
- Header: app title + signed-in user's avatar (top right, tap → Profile)
- **Group switcher**: current group name, avatar, member count, expand
  chevron. Expands to a panel listing:
  - Each group the user belongs to (name, avatar, member + match count),
    checkmark on the active one
  - "+ Create or join a group" action
- **Top Players podium**: #1/#2/#3 players by rating, each showing
  avatar, name, and rating. #1 is visually emphasized (larger, glowing
  highlight). Provisional players are never shown on the podium.
- **Share** action: renders the active group's leaderboard as an image and
  opens the Android share sheet.
- **Rankings table**: all players in the group as two-line rows — rank,
  avatar + name, and the active metric on the right; beneath the name a
  muted summary reading `37 games · 22-15 · 59% · +76`. The trailing DIFF
  is points for − against, signed, green when positive and red when
  negative. The right-hand header label is tappable and cycles the metric
  (Rating → Win% → Games → Diff); the large number follows it.
  Provisional players show `—` for rank, `prov` for the metric, and
  `· N more to rank` in place of the difference.

## Behavior / Requirements
1. Switching groups reloads podium + rankings for the selected group.
2. **Tapping a player (podium card or rankings row) opens that player's
   stats view** — the same layout as the [Profile](05-profile.md) screen's
   stats section (matches played, win rate, W/L, PF/PA, streak, best
   partner, recent matches), scoped to the tapped player instead of the
   signed-in user. Read-only (no sign-out/account section, since it isn't
   "your" profile).
3. Rankings table cycles through Rating, Win%, Games and DIFF via the header;
   Rating is the default. Provisional players are always sorted below every
   ranked player, whatever the metric.
5. Time filter offers **This Month** and **All Time** only. There is no weekly
   window: ratings are computed over the selected window, and one or two
   sessions is too few games for a confidence-adjusted rating to separate anyone.
4. Avatar rendering follows the global rule: uploaded photo, else colored
   initial ([00-overview.md](00-overview.md)).

## Data needed
- Per group: list of players with GP, W, L, PF, PA, win%. The table shows the
  PF − PA difference rather than PF itself; both raw totals remain on Profile.

## Current ranking behavior

- Canonical order is **rating desc, then points difference (PF − PA) desc,
  then wins desc**, with a final user-id key so fully tied rows can't shuffle
  between requests. UI-only metric sorts retain that canonical relative order
  for equal values.
- **Rating** is the Wilson score lower bound on the win rate, scaled 0–100 with
  one decimal — "the win rate we are confident this player is at least worth".
  It replaced raw win%, which was hostile to volume: a 6-1 record (86%, 7 games)
  outranked a 27-23 one (54%, 50 games). Weighting games played instead is
  worse — any formula where volume *adds* score lets a player climb by losing
  often.
- The rating alone does **not** demote a hot newcomer: 6-1 is genuinely strong
  evidence, so it rates above 22-15 and no honest confidence bound reverses
  that. What keeps such a player off the board is the threshold below.
- **minGamesToRank** is `max(1, min(10, ceil(median(games played) / 2)))` over
  players with at least one game. Below it a player is *provisional*: listed
  after the ranked players, not ranked, and never on the podium. It slides with
  group activity rather than being fixed at 10 — at the start of a month
  everyone has two or three games, and a fixed gate would empty the board,
  whereas a relative one ranks everybody because nobody holds an evidence
  advantage. The gate exists to prevent unfair *small-N vs large-N* comparison.
- Sorting uses the **unrounded** bound; only display rounds to one decimal. At
  one decimal, ties are common enough that ordering the rounded value would
  shuffle between requests.
- Points difference still breaks a tie between equal ratings, and is shown on
  the row's second line so the order between two equal ratings is visible
  rather than hidden.
- Win% is **rounded** for display, not truncated. Truncating showed 42.86%
  and 42.11% both as "42%", making distinct rates look tied while the server
  ranked them apart.
- Both the all-time and windowed leaderboards run through the same ranker, so
  a window covering all history is identical to the all-time board. They used
  to order separately (JPQL vs a Java comparator), which could report a rank
  change that never happened.

## Open questions

- Empty state: group with zero matches played.
- The rating is computed over the *selected* window, so it is not comparable
  across windows (a 60% player reads ~38.7 on This Month and ~46.2 on All
  Time). If that confuses people, the fix is an all-time rating with the
  filter changing only GP / W-L / Win%.
