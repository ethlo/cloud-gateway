CREATE TABLE log
(
    timestamp          DateTime64(3),
    gateway_request_id String,
    method             LowCardinality(String),
    path               LowCardinality(String),
    response_time      Int32,
    request_size       Nullable(Int32),
    response_size      Nullable(Int32),
    status             Int16,
    request_headers    Map(String, String),
    response_headers   Map(String, String)
)
    ENGINE = MergeTree
        ORDER BY timestamp
        SETTINGS index_granularity = 8192;