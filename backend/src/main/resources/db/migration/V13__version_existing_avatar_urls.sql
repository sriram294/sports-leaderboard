-- Existing avatar files used a stable /avatars/<user-id>.<ext> path. Add a
-- one-time version token so clients that cached bytes at that URL request the
-- current file after this deployment. New uploads use a unique filename and do
-- not depend on this query parameter.
UPDATE users
SET photo_url = photo_url
        || CASE WHEN position('?' IN photo_url) > 0 THEN '&' ELSE '?' END
        || 'v=' || (extract(epoch FROM updated_at) * 1000)::bigint
WHERE photo_url IS NOT NULL
  AND photo_url NOT LIKE '%?%v=%'
  AND photo_url NOT LIKE '%&v=%';
