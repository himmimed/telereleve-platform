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
