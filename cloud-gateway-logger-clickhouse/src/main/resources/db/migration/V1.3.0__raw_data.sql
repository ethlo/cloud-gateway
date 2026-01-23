ALTER TABLE log
    ADD COLUMN exception_type LowCardinality(Nullable(String));
ALTER TABLE log
    ADD COLUMN exception_message Nullable(String) codec (ZSTD(3));