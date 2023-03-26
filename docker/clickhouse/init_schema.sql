CREATE TABLE IF NOT EXISTS log
(
    timestamp          DateTime64(3),
    route_id String,
    route_uri String,
    gateway_request_id String,
    method             LowCardinality(String),
    path               LowCardinality(String),
    response_time      Int64,
    status             Int16,
    is_error           UInt8,
    request_headers    Map(String, String) codec (ZSTD(1)),
    response_headers   Map(String, String) codec (ZSTD(1)),
    request_body_size  Nullable(Int32),
    response_body_size Nullable(Int32),
    request_body       Nullable(String) codec (ZSTD(3)),
    response_body      Nullable(String) codec (ZSTD(3))
)
    ENGINE = MergeTree
        ORDER BY timestamp
        SETTINGS index_granularity = 8192;