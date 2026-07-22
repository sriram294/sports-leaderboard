# API client

The client prefixes requests with `VITE_API_URL` (default `/api/v1`), preserves UUID/timestamp strings, parses RFC 7807 `code` and `detail`, and keeps cursor pagination intact. API modules own headers and retries; feature components do not construct ad-hoc URLs.
