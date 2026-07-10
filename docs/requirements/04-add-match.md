# Add Match Screen

Source: `docs/prototype/add.pdf`, `docs/prototype/add_fields_selected.pdf`

## Purpose
Form to record a new doubles match result for the current group. Reached
via the floating center "+" tab.

## Layout
- Header: group switcher (same component as Board/Matches)
- "Recording as [name] · [email]" — identifies who is submitting
- **Build Teams**: two empty team slots (Team 1, Team 2), each with 2
  player placeholder circles ("+")
- Player chip list below (all group members) — tap to assign
- **Score by Set**: Set 1 row with two numeric inputs (Team 1 · Team 2),
  "+ Add Set" to append more sets
- **Who Won?**: two selectable cards, Team 1 / Team 2, showing assigned
  player names once teams are built
- **Record Match** primary button (disabled until form is valid)

## Behavior / Requirements
1. Tapping a player chip assigns them to the next open slot: fills Team 1's
   two slots first, then Team 2's. Already-assigned players are visually
   marked (outlined/highlighted in the chip list) and cannot be assigned
   twice.
2. Score inputs accept numeric set scores per team; "+ Add Set" appends
   another set row (supports best-of-3 or more).
3. "Who Won?" auto-highlights based on sets won once scores are entered,
   but remains tappable to override/confirm manually (prototype shows both
   an unset state with "? & ?" and a filled/selected state).
4. **Record Match** stays disabled (dim) until required fields are
   complete: both teams fully assigned, at least one set scored, a winner
   determined. Becomes active (bright) once valid.
5. On submit: creates a Match record (see schema in
   [03-matches.md](03-matches.md)), stamps `recordedBy`/`recordedAt`,
   updates leaderboard and player stats, navigates to Board or Matches
   (TBD) with confirmation.

## Data needed
- Current group's player roster (for the chip list).
- Signed-in user identity (for "Recording as").

## Open questions
- Can a non-participant record a match on behalf of others, or must the
  recorder be one of the 4 players? (Prototype's "Recording as Raj" while
  Raj is also assignable as a player suggests yes, non-participant
  recording is allowed — confirm.)
- Validation for invalid/tied set scores (e.g. 21-21) — badminton scoring
  rules (win by 2, cap at 30) not shown in prototype.
- Can a set be removed after "+ Add Set", or only appended?
- What happens on cancel/back navigation with a partially filled form —
  discard silently or confirm?
