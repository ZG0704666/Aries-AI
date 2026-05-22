CREATE TABLE IF NOT EXISTS telemetry_clients (
    install_id_hash TEXT PRIMARY KEY,
    platform TEXT NOT NULL,
    first_seen_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    last_session_id TEXT NOT NULL,
    last_session_started_at INTEGER NOT NULL,
    total_sessions INTEGER NOT NULL DEFAULT 1,
    app_version TEXT,
    version_code INTEGER,
    locale TEXT,
    country TEXT,
    last_source TEXT
);

CREATE INDEX IF NOT EXISTS idx_telemetry_clients_last_seen_at
    ON telemetry_clients(last_seen_at);

CREATE INDEX IF NOT EXISTS idx_telemetry_clients_first_seen_at
    ON telemetry_clients(first_seen_at);

CREATE TABLE IF NOT EXISTS telemetry_heartbeat_buckets (
    install_id_hash TEXT NOT NULL,
    bucket_5m INTEGER NOT NULL,
    received_at INTEGER NOT NULL,
    session_id TEXT NOT NULL,
    app_version TEXT,
    version_code INTEGER,
    locale TEXT,
    country TEXT,
    PRIMARY KEY (install_id_hash, bucket_5m)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_heartbeat_buckets_bucket
    ON telemetry_heartbeat_buckets(bucket_5m);

CREATE INDEX IF NOT EXISTS idx_telemetry_heartbeat_buckets_received_at
    ON telemetry_heartbeat_buckets(received_at);
