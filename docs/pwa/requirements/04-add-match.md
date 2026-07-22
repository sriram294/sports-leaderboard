# Add and edit match

Use the existing create and patch match contracts. Build two teams from group members/guests, prevent duplicates, validate badminton sets and winner, show recorder identity, and retain form state on failure. New matches route to Board; edits route to Matches. Match mutation invalidates Board, Matches, Profile, and Stats.

Tests cover team validation, duplicate players, scores, winner derivation, submit payload, disabled states, failed submit preservation, and unsaved navigation.
