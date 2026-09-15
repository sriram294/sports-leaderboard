# API client

The client prefixes requests with `VITE_API_URL` (default `/api/v1`), preserves UUID/timestamp strings, parses RFC 7807 `code` and `detail`, and keeps cursor pagination intact. API modules own headers and retries; feature components do not construct ad-hoc URLs.

Leaderboard decoding treats `algorithmVersion`, `ratingPeriod`, `provisionalReason`,
`uniquePartners`, `maxPartnerShare`, and `limitedPartnerVariety` as optional so a
rollback to Wilson/team-v1 remains safe. When team-v2 metadata is present, the
server-provided Skill rating and canonical order are rendered without client-side
recalculation.
