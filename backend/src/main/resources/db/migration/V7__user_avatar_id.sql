-- Default avatars: a lightweight id referencing an SVG bundled in the app
-- (assets/avatars/<id>.svg). Mutually exclusive with photo_url — exactly one of
-- {photo_url, avatar_id} is set (guests keep neither → colored initial).
alter table users add column avatar_id text;

-- Backfill: give existing non-guest members who never uploaded a photo a random
-- avatar so the app stops being a wall of colored initials. Guests (the shared
-- filler pool) deliberately stay gray, so they are excluded.
update users
set avatar_id = (array[
        '12ac343930','1d2f2b4717','30cf50cc99','39c95f7d8f','436068898d',
        '46cb006030','5f42c81abb','618be3e104','7841b5f8c9','80ad28b394',
        '863d9dc474','8ecc918224','8fcadf2717','91cfff00a6','b0fceea6c5',
        'b1b3146fcf','bb357d3d46','c0b772274a','cec8d2bf95','d288110b4b',
        'da87cb62a1','e7142554e7','e9c1d9762b','fb7ecc7bf0','ff268b8670'
    ])[floor(random() * 25) + 1]
where photo_url is null
  and email not like 'guest-%@playboard.local';
