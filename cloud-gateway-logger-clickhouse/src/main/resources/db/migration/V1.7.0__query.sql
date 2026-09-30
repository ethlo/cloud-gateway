ALTER TABLE log ADD COLUMN query Nullable(String) codec (ZSTD(3));
