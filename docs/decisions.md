# Technical decisions

Short records of non-obvious choices: the context, the decision and why. Newest at the bottom.

## D1 - Spring Boot 3.5.x rather than 4.0

**Context.** The specification fixes Spring Boot 3.x. By late 2026 the 3.5 line is out of free (OSS)
support and 4.0 is current.
**Decision.** Spring Boot 3.5 (latest patch, 3.5.16).
**Why.** It respects the specification, and its ecosystem (springdoc-openapi 2.x, Testcontainers 1.x,
Spring Integration MQTT 6.x) is mature and widely documented. Migrating to 4.x later (Jackson 3, new
Testcontainers coordinates) is a contained, well-documented task.

## D2 - `reading` has no surrogate id: primary key `(meter_id, read_at)`

**Context.** The specification lists an `id` column plus `UNIQUE (meter_id, timestamp)`. TimescaleDB
requires every unique index of a hypertable to include the partitioning (time) column, so a standalone
`id` primary key cannot exist.
**Decision.** Primary key `(meter_id, read_at)`; no `id` column.
**Why.** The natural key is exactly the business identity of a reading. It also enforces idempotent
ingestion (a re-published reading violates the key and is ignored) and backs the most frequent query
("readings of meter X between A and B") with no extra index.
The column is named `read_at` instead of `timestamp`, which is a SQL type name and needs quoting.

## D3 - Hypertable chunk interval of 1 day

**Decision.** `create_hypertable('reading', by_range('read_at', INTERVAL '1 day'))`.
**Why.** The demo load (1000 meters, 1 reading/min) is about 1.44 M rows/day. TimescaleDB recommends
that the most recent chunks (data + indexes) fit in about 25 % of memory; one-day chunks keep them small,
and dashboard queries mostly target the last day or week, so few chunks are scanned.

## D4 - "One open alert per (meter, rule)" enforced by a partial unique index

**Decision.** `CREATE UNIQUE INDEX ... ON alert (meter_id, rule_code) WHERE status IN ('NEW','IN_PROGRESS')`,
in addition to the check done by the alert service.
**Why.** A check in Java alone has a race: two readings of the same meter processed concurrently could
both see "no open alert" and both insert one. The database index makes duplicates impossible; resolved
alerts are outside the index, so history is kept.

## D5 - Enumerations as `VARCHAR` + `CHECK`, not PostgreSQL `ENUM`

**Why.** They map directly to JPA `@Enumerated(EnumType.STRING)` without custom types, and adding a
value is a simple constraint change in a migration (altering a PostgreSQL ENUM is more restrictive).
The `CHECK` still rejects invalid values at the database level.

## D6 - Mailpit instead of MailHog

**Decision.** `axllent/mailpit` as the development mail catcher.
**Why.** MailHog is no longer maintained. Mailpit is its maintained successor and a drop-in replacement
with the same ports (SMTP 1025, web UI 8025).

## D7 - Mosquitto credentials generated at container start

**Decision.** A small entrypoint script runs `mosquitto_passwd` with `MQTT_USERNAME` / `MQTT_PASSWORD`
from the environment before starting the broker; anonymous access is disabled.
**Why.** No credential (not even a hashed password file) is committed, and changing the password only
requires editing `.env`.

## D8 - One `.env` for docker compose and local runs

**Decision.** `application.yml` imports `optional:file:.env[.properties]` (and `../.env`).
**Why.** The `KEY=value` format of `.env` is valid Java properties, so `mvnw spring-boot:run` uses the
same credentials as `docker compose` with no duplication. Real environment variables take precedence,
and the import is optional, so containers and CI are unaffected. Passwords have no default value: the
application or compose fails fast with an explicit message rather than starting with a weak password.

## D9 - Independent Maven projects, no parent POM

**Decision.** `backend/` and `simulator/` each have their own `pom.xml` and Maven wrapper.
**Why.** They are separate deployables with different dependencies (Spring Boot vs a lightweight
standalone app) and separate Docker builds. A parent POM would couple their build contexts for little
benefit in a two-module repository.

## D10 - Unit tests and integration tests separated

**Decision.** `*Test` classes run with Surefire (`mvnw test`), `*IT` classes (Testcontainers) with
Failsafe (`mvnw verify`). A single JaCoCo agent covers both, and the report is written on `verify`.
**Why.** Fast feedback while coding (`mvnw test` needs no Docker), full verification in CI.

## D11 - Status colours validated for colour-vision deficiency

**Decision.** OK / alert / critical use green `#166534`, amber `#d97706`, red `#b91c1c` in light mode and
`#239a5e`, `#e39b26`, `#e5534b` in dark mode; "silent" is a deliberate neutral grey.
**Why.** The first choice (green `#15803d` vs amber `#c2780a`) was almost indistinguishable for protanopes
(ΔE 5.3). The final pairs pass a colour-blindness separation check (ΔE ≥ 8) and a 3:1 contrast against the
surfaces. Colour is never the only signal anyway: every status has a written label, and map markers also
differ by shape (alerts larger, silent meters drawn as hollow rings).

## D12 - API extensions needed by the screens (frontend built ahead of the backend)

The specification's endpoint list does not cover four needs of the pages. The Angular services and the
mock API already use these contracts; the backend implements them in phases 2 to 4.

| Need | Contract |
|---|---|
| Dashboard consumption curve | `GET /api/dashboard/consumption-trend?from&to&step` returns `[{bucket, electricityKwh, waterM3}]` |
| Alert history of one meter | `meterId` filter on `GET /api/alerts` |
| Technicians an operator can assign | `GET /api/users/technicians` (ADMIN, OPERATOR), returns `[{id, fullName}]` |
| Popup on the map | `type` and `clientName` added to `GET /api/meters/map` items |

"Total consumption today" is returned as `{electricityKwh, waterM3}`: kWh and m³ cannot be added together.

## D13 - Frontend first, on a mock API

**Context.** The frontend was requested before the backend phases.
**Decision.** Components call typed services (`core/api`), never `HttpClient` directly. While
`environment.useMockApi` is `true`, an HTTP interceptor (`core/mock`) answers `/api` calls in the browser
with simulated data, following the specified contract: role permissions (401/403), RFC 7807 errors,
pagination, one open alert per (meter, rule). Consumption is computed on the fly from a daily profile, so
every period (24 h to 12 months) is consistent and indexes only increase.
**Why.** The UI could be designed and tested end to end now. Switching to the real API is one flag; no
component changes. Unit tests pin the mock to the contract so it cannot drift silently.

## D14 - OpenStreetMap tiles, themed with a CSS filter

**Decision.** Standard OpenStreetMap tiles with a CSS filter (desaturated in light mode, inverted in dark mode).
**Why.** The CARTO basemaps first used now require an API key; OSM tiles need none, only attribution, which
keeps the project free of third-party secrets. For production traffic, a self-hosted or commercial tile
server should replace the public OSM servers (their usage policy targets light use).

## D15 - Typography and numbers

Geist for text and Geist Mono for identifiers (serials, usernames, rule codes). Quantities use Geist with
tabular figures rather than the mono face: a monospaced comma reads as "5 ,9" in French decimals.

## D16 - JWT in memory: reloading signs the user out

As specified, the token lives only in memory (an Angular signal), so a script injected into the page cannot
read it from storage. The visible consequence is that a page reload (F5) returns to the login page; the
guard keeps the requested URL (`/connexion?retour=...`) so the user lands back where they were. A refresh
token in an HttpOnly cookie would remove this friction (to discuss in phase 4).

## D17 - Bundle budget

The production initial bundle is about 855 kB raw / 186 kB compressed (Angular, Material core, the mock
data). Charts (Chart.js) and the map (Leaflet) are lazy-loaded with their pages. The initial-bundle warning
budget is set to 900 kB; the mock layer (about 25 kB) leaves the bundle when the real API is enabled.

## D18 - Demo data seeded by a Java runner, not by Flyway

**Decision.** `DevDataSeeder` (an `ApplicationRunner` active only with the `dev` profile) creates the meter
park, using `FesMeterGenerator`. Users (phase 4, BCrypt hashes) and default rule settings (phase 3) will be
added to it in their phases.
**Why.** Flyway migrations should be identical in every environment; a dev-only migration would leave the
database history inconsistent when the profile changes. The Java seeder is also configurable
(`APP_SEED_METER_COUNT`: 50 for demos, 1000 for the load test) and idempotent: it only adds missing serials,
and meter N is always the same meter, so growing the park from 50 to 1000 keeps the first 50 unchanged.

## D19 - Simulator: meter list from the backend, stateless consumption model

**Meter list.** The simulator fetches the active meters from `GET /api/meters` at startup (with retries
while the backend starts). The backend stays the single source of truth for serials, as for real meters
registered before installation. The "share of water meters" setting therefore lives in the seeder
(`app.seed.water-share`, 30 %). Phase 4 will give the simulator a technical account for this call.
**Index model.** The index is a pure function of (serial, time): base index plus everything consumed
since 1 January 2025, with a daily profile (low at night, morning and evening peaks), +/-10 % noise per
day and +/-15 % per hour. It never decreases, even across simulator restarts, so normal operation can
never trigger rule A4 by mistake. The profile uses the fixed UTC+1 offset (Moroccan standard time) rather
than the Africa/Casablanca zone, whose Ramadan clock change would repeat an hour.
**Spreading.** Each meter publishes at its own offset within the interval, so 1000 meters do not hit the
broker in the same second.

## D20 - Shared web plumbing in a `common` package

`PageResponse` (stable JSON for pages instead of serialising Spring's `PageImpl`), `NotFoundException`,
`ConflictException` and `ApiExceptionHandler` (RFC 7807 errors with French `detail` and a field `errors`
map) are used by every feature package, so they live in `common/` rather than in one feature.

## D21 - Calendar of days and months: fixed UTC+1

**Context.** Day and month buckets were first aligned with the `Africa/Casablanca` zone. The time-zone
databases of Java and of the PostgreSQL image disagree on Morocco's offset (Ramadan clock changes), so
TimescaleDB and Java computed day boundaries one hour apart and the daily consumption came out as 0.
**Decision.** Days and months use Moroccan standard time as a fixed offset, `Etc/GMT-1` (UTC+1; POSIX
names invert the sign), in Java and in `time_bucket`. The simulator's daily profile uses the same offset.
**Why.** A fixed offset is identical in every time-zone database, and it never repeats or skips an hour.
During Ramadan, a "day" bucket starts at 01:00 instead of midnight on the wall clock, which is acceptable
for consumption statistics.

## D22 - Ingestion guarantees: at-least-once delivery, exactly-once storage

- **QoS 1 + persistent session.** The backend subscribes with a fixed client id and `cleanSession=false`:
  while it is stopped, Mosquitto queues the readings and delivers them on reconnection. Verified with the
  compose stack: backend stopped for 90 s, the 76 readings published meanwhile (all 50 meters) were stored.
- **Manual acknowledgement.** A message is acknowledged once processed (stored, duplicate, or rejected on
  purpose). On an infrastructure error (database down) it is left unacknowledged for redelivery.
- **Idempotent storage.** `INSERT ... ON CONFLICT (meter_id, read_at) DO NOTHING` on the primary key (D2):
  redeliveries and meter retries never create duplicates.
- **Order.** Messages are processed one at a time, in arrival order, which keeps each meter's readings in
  sequence for rule A4. At 1000 meters per minute that is about 17 messages per second, far below the
  capacity of one thread (measured in phase 6).
- **`last_reading_at` only moves forward** (`GREATEST` in a single UPDATE), so a late, older reading never
  makes a meter look silent.
- **Bad messages** (invalid JSON, unknown or inactive meter, serial different from the topic, type
  mismatch, timestamp more than 5 minutes ahead, negative index, missing field) are logged once at WARN and
  dropped. Old timestamps are accepted: a meter catching up after an outage sends readings from hours ago.

## D23 - Consumption: per-bucket first / last index, computed in Java

TimescaleDB returns, per `time_bucket`, the first and last index (`first()` / `last()`), so only a few rows
leave the database even for a year of minute readings. `ConsumptionCalculator` (pure Java, unit-tested)
then subtracts each bucket's last index from the previous known one: empty buckets give 0 and the next
bucket with readings receives the consumption accumulated during the gap; a decreasing index counts as 0.
Ranges are capped (hour: 31 days, day: 400 days, month: 10 years) to bound the response size.

## D24 - CSV export for Excel in French

`;` separator, decimal comma, timestamps in Moroccan time with their offset (`2026-10-12T11:15:00+01:00`),
UTF-8 with a BOM so Excel detects the encoding, CRLF line endings (RFC 4180). The period is validated
before any byte is written, so an invalid request still gets a JSON ProblemDetail. Rows are streamed from
the database, never loaded in memory at once.
