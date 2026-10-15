-- Hourly summary of the readings: first and last reading of each meter in each hour (UTC hours, which
-- are also whole hours in Morocco's fixed UTC+1).
-- Network-wide consumption (dashboard) reads 24 rows per meter and day here instead of every reading:
-- 24 000 rows instead of 1.44 million for 1000 meters read every minute (docs/decisions.md D36).
-- Maintained by the backend in the same statement as each reading insert (ReadingRepository).
CREATE TABLE reading_hourly (
    meter_id    UUID          NOT NULL REFERENCES meter (id),
    hour        TIMESTAMPTZ   NOT NULL,
    first_at    TIMESTAMPTZ   NOT NULL,
    first_index NUMERIC(14,3) NOT NULL,
    last_at     TIMESTAMPTZ   NOT NULL,
    last_index  NUMERIC(14,3) NOT NULL,
    CONSTRAINT pk_reading_hourly PRIMARY KEY (meter_id, hour),
    CONSTRAINT chk_reading_hourly_order CHECK (first_at <= last_at)
);

-- One chunk per month: about 720 000 rows for 1000 meters.
SELECT create_hypertable('reading_hourly', by_range('hour', INTERVAL '30 days'));

-- Readings stored before this migration.
INSERT INTO reading_hourly (meter_id, hour, first_at, first_index, last_at, last_index)
SELECT meter_id, date_trunc('hour', read_at, 'UTC'), min(read_at), first(index_value, read_at),
       max(read_at), last(index_value, read_at)
FROM reading
GROUP BY meter_id, date_trunc('hour', read_at, 'UTC');
