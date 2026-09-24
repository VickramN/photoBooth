-- Wipe existing dev data: image.img held full public R2 URLs, which are
-- meaningless as the new private object_key column this migration creates.
TRUNCATE TABLE image;

ALTER TABLE image RENAME COLUMN img TO object_key;
