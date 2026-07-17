-- The default avatar pack was replaced (25 DiceBear SVGs -> 16 new PNGs), so
-- every previously-assigned avatar_id now points at a file that no longer ships
-- in the app. Reassign a fresh random avatar to anyone still holding an old id.
-- Rows with a null avatar_id (guests, and users who uploaded a photo) are left
-- untouched. Guarded by NOT IN the new set so a re-run is a no-op.
update users
set avatar_id = (array[
        'avatar0','avatar1','avatar2','avatar3','avatar4','avatar5','avatar6','avatar7',
        'avatar8','avatar9','avatar10','avatar11','avatar12','avatar13','avatar14','avatar15'
    ])[floor(random() * 16) + 1]
where avatar_id is not null
  and avatar_id not in (
        'avatar0','avatar1','avatar2','avatar3','avatar4','avatar5','avatar6','avatar7',
        'avatar8','avatar9','avatar10','avatar11','avatar12','avatar13','avatar14','avatar15'
  );
