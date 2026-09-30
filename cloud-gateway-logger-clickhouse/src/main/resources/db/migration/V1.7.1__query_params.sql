ALTER TABLE log ADD COLUMN query_params Map(String, Array(String)) codec (ZSTD(3));
