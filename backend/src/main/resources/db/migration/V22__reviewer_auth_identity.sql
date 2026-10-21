-- Allow the store-review username/password login (POST /auth/reviewer) to map onto
-- a dedicated user through the same provider-neutral identity table as Google/Apple.
alter table user_auth_identities drop constraint user_auth_identities_provider_check;
alter table user_auth_identities
    add constraint user_auth_identities_provider_check
    check (provider in ('google', 'apple', 'reviewer'));
