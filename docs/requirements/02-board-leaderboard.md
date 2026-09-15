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
- **Rankings table**: all players in the group as three-line rows — rank,
  avatar + name, and the active metric on the right; beneath the name a
  muted summary reading `37 games · 22-15 · 59% · +76`, then a row of small
  win/loss dots (up to 10, oldest on the left) showing that player's recent
  form within the selected window. Players with fewer than 10 matches show
  fewer dots; a player with none shows no dots row at all. The trailing DIFF
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
3. Rankings table cycles through Skill rating, Win%, Games and DIFF via the header;
   Skill rating is the default. Provisional players are always sorted below every
   ranked player, whatever the metric.
5. Time filter offers **This Month** and **All Time** only. There is no weekly
   window: Skill rating carries through the evaluation cutoff while the selected
   range changes statistics and qualifying games.
4. Avatar rendering follows the global rule: uploaded photo, else colored
   initial ([00-overview.md](00-overview.md)).

## Data needed
- Per group: list of players with GP, W, L, PF, PA, win%. The table shows the
  PF − PA difference rather than PF itself; both raw totals remain on Profile.
- Per player: `recentForm` — up to 10 results within the selected window, in
  chronological order (oldest first), so the dots row reads left-to-right
  without any client-side reversal. Ships with the leaderboard response, not
  a separate call.
- The leaderboard response also carries `algorithmVersion`, `ratingPeriod`,
  `provisionalReason`, `uniquePartners`, `maxPartnerShare`, and
  `limitedPartnerVariety` for version-aware clients and informational warnings.

## Current ranking behavior

- With the default `PLAYBOARD_TEAM_V2_ENABLED=true`, canonical order is the
  unrounded conservative skill score (`mean − 3 × uncertainty`) descending,
  then points difference (PF − PA) descending, wins descending, and finally the
  PostgreSQL-compatible UUID string. API ranks are sequential; clients preserve
  server order for the Skill rating metric and keep metric sorts stable inside
  the qualified and provisional sections.
- Each regular player starts independently at mean `25` and uncertainty `25/3`.
  Team-v2 applies the two-team Gaussian update with `β = 25/6` per participant,
  adds `(25/300)²` to a regular player's variance before each appearance, and
  updates all participants from the same pre-match state. The conservative score
  is displayed as `100 / (1 + exp(-(score − 17) / 3))`, rounded half-up to one
  decimal. It measures skill confidence from partners, opponents, results, and
  uncertainty; it is not win percentage.
- Skill is cumulative through the evaluation cutoff. The selected This Month or
  All Time filter changes only statistics, recent form, streaks, and
  qualification. Historical matches are replayed in `(played_at, match UUID)`
  order through the cutoff. Former regular members remain in the replay;
  guests use a fixed prior per appearance and never accumulate skill.
- **minGamesToRank** is `max(1, min(10, ceil(median(games played) / 2)))` over
  eligible players with at least one game in the selected range. Players below
  it are provisional (`provisionalReason = "games"`), appear after qualified
  players, are excluded from the podium, and show no numbered rank in the
  clients. The existing row caption remains `N more to rank`.
- Partner variety never changes qualification or skill gains. The API exposes
  `uniquePartners`, `maxPartnerShare`, and `limitedPartnerVariety` for the
  selected range; the client may treat the flag as informational and does not
  render an additional partner-diversity subcaption.
- Malformed matches are skipped consistently by rating and leaderboard
  statistics, with a diagnostic log. Bulk roster loading avoids queries per
  match or participant, and on-demand replay means edits, deletions, and
  backdated matches take effect on the next read.
- Setting `PLAYBOARD_TEAM_V2_ENABLED=false` restores Wilson/team-v1 routing and
  its legacy metadata and notification behavior. Older clients also retain
  their legacy explanation when they receive that version.

## Team-v2 presentation

Label the metric **Skill rating**. Render qualified players under **Rankings**
and provisional players under **Not yet ranked**, with muted ratings, no
numbered rank, and the existing progress caption such as `4/10 qualifying
games` or `N more to rank`. The board does not add explanatory subcaptions for
the ranking model, partner diversity, or threshold games; the API warning is
available for informational use without implying that variety is required to
qualify.

## Open questions

- Empty state: group with zero matches played.
- Skill rating is cumulative to the evaluation cutoff; the selected window
  changes GP, W-L, statistics, and qualification only.
