-- ─────────────────────────────────────────────────────────────────────────
-- Refresh tokens — server-side record backing rotation + revocation
-- ─────────────────────────────────────────────────────────────────────────

-- The refresh token handed to the client is a signed JWT whose `jti` claim
-- IS this row's id; the JWT itself is never stored. Verifying a refresh
-- token means: check the signature/expiry, then confirm this row still
-- exists and is unrevoked. `POST /auth/refresh` rotates it (revokes this
-- row, inserts a new one) so a stolen-and-reused old token is detectable.
create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    created_at  timestamptz not null default now()
);

create index idx_refresh_tokens_user on refresh_tokens(user_id) where revoked_at is null;
