-- Avatar photo_url used to be stored as an absolute URL, freezing whatever
-- PUBLIC_BASE_URL was set at upload time into every row. Moving the API to a
-- custom domain therefore left existing photos pointing at the old Railway
-- subdomain, which would break the moment that host is retired.
--
-- Strip the scheme + host so only the path remains (/avatars/<file>);
-- AvatarUrlResolver now prepends PUBLIC_BASE_URL at read time.
UPDATE users
SET photo_url = regexp_replace(photo_url, '^https?://[^/]+', '')
WHERE photo_url IS NOT NULL
  AND photo_url ~ '^https?://';
