ALTER TABLE log ADD COLUMN request_raw Nullable(String) codec (ZSTD(3));
ALTER TABLE log ADD COLUMN response_raw Nullable(String) codec (ZSTD(3));