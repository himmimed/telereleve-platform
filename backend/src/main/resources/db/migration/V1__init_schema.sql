-- V1: initial schema of the Télérelève platform.
-- Enumerations are VARCHAR + CHECK rather than PostgreSQL ENUM types: they map directly to
-- JPA @Enumerated(STRING) and a new value is a simple constraint change (see docs/decisions.md).

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ---------------------------------------------------------------------------------------------
-- Users of the platform
-- ---------------------------------------------------------------------------------------------
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'OPERATOR', 'TECHNICIAN'))
);

-- ---------------------------------------------------------------------------------------------
-- Smart meters
-- ---------------------------------------------------------------------------------------------
CREATE TABLE meter (
    id                   UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    serial               VARCHAR(32)      NOT NULL,
    type                 VARCHAR(20)      NOT NULL,
    client_name          VARCHAR(120)     NOT NULL,
    address              VARCHAR(255)     NOT NULL,
    latitude             DOUBLE PRECISION NOT NULL,
    longitude            DOUBLE PRECISION NOT NULL,
    reading_interval_min INTEGER          NOT NULL DEFAULT 15,
    status               VARCHAR(20)      NOT NULL DEFAULT 'ACTIVE',
    last_reading_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_meter_serial UNIQUE (serial),
    CONSTRAINT chk_meter_type CHECK (type IN ('ELECTRICITY', 'WATER')),
    CONSTRAINT chk_meter_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_meter_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_meter_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_meter_interval CHECK (reading_interval_min > 0)
);

CREATE INDEX idx_meter_type_status ON meter (type, status);

-- ---------------------------------------------------------------------------------------------
-- Readings (time series)
-- TimescaleDB requires every unique key of a hypertable to contain the partitioning column,
-- so a standalone surrogate id is impossible. The natural key (meter_id, read_at) is the
-- primary key: it also enforces "one reading per meter and timestamp" (idempotent ingestion)
-- and serves the "readings of meter X between A and B" queries.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE reading (
    meter_id        UUID          NOT NULL REFERENCES meter (id),
    read_at         TIMESTAMPTZ   NOT NULL,
    index_value     NUMERIC(14,3) NOT NULL,
    voltage         NUMERIC(5,1),
    signal_strength SMALLINT      NOT NULL,
    received_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_reading PRIMARY KEY (meter_id, read_at),
    CONSTRAINT chk_reading_index CHECK (index_value >= 0)
);

-- One-day chunks: sized for the demo load (1000 meters x 1 reading/min = 1.44 M rows/day),
-- and dashboard queries mostly target the last day or week.
SELECT create_hypertable('reading', by_range('read_at', INTERVAL '1 day'));

-- ---------------------------------------------------------------------------------------------
-- Anomaly rule configuration (one row per rule A1..A5)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE alert_rule_config (
    rule_code    VARCHAR(4)    PRIMARY KEY,
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    threshold    NUMERIC(10,3),
    window_hours INTEGER,
    CONSTRAINT chk_rule_config_code CHECK (rule_code IN ('A1', 'A2', 'A3', 'A4', 'A5')),
    CONSTRAINT chk_rule_config_window CHECK (window_hours IS NULL OR window_hours > 0)
);

-- ---------------------------------------------------------------------------------------------
-- Alerts raised by the anomaly engine
-- ---------------------------------------------------------------------------------------------
CREATE TABLE alert (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    meter_id    UUID         NOT NULL REFERENCES meter (id),
    rule_code   VARCHAR(4)   NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    status      VARCHAR(12)  NOT NULL DEFAULT 'NEW',
    message     VARCHAR(500) NOT NULL,
    detected_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    assigned_to UUID         REFERENCES app_user (id),
    CONSTRAINT chk_alert_rule_code CHECK (rule_code IN ('A1', 'A2', 'A3', 'A4', 'A5')),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_alert_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED')),
    -- resolved_at is set exactly when the alert is resolved
    CONSTRAINT chk_alert_resolved_at CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

-- Business rule "at most one open alert per (meter, rule)", guaranteed by the database even if
-- two ingestion threads evaluate the same meter concurrently. Resolved alerts are not constrained.
CREATE UNIQUE INDEX uq_alert_open_per_meter_rule
    ON alert (meter_id, rule_code)
    WHERE status IN ('NEW', 'IN_PROGRESS');

CREATE INDEX idx_alert_status_severity ON alert (status, severity);
CREATE INDEX idx_alert_meter_detected ON alert (meter_id, detected_at DESC);
CREATE INDEX idx_alert_assigned_to ON alert (assigned_to);
