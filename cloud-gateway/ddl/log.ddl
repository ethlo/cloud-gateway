CREATE TABLE log
(
    timestamp DateTime64(3),
    gateway_request_id String,
    method LowCardinality(String),
    path LowCardinality(String),
    response_time Int64,
    status Int16,
    request_headers Map(String, String) codec(ZSTD(1)),
    response_headers Map(String, String) codec(ZSTD(1)),
    request_body_size Nullable(Int32),
    response_body_size Nullable(Int32),
    request_body Nullable(String) codec(ZSTD(3)),
    response_body Nullable(String) codec(ZSTD(3))
)
    ENGINE = MergeTree
        ORDER BY timestamp
        SETTINGS index_granularity = 8192;