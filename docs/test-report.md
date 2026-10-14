# Test report

> Completed in Phase 6 (test inventory, coverage figures, performance results with 1000 meters).

## How to run the tests

```sh
cd backend
./mvnw test     # unit tests only, no Docker needed
./mvnw verify   # unit + integration tests (Testcontainers, Docker required) + JaCoCo report
```

The coverage report is written to `backend/target/site/jacoco/index.html`.

```sh
cd simulator
./mvnw verify               # 35 tests
cd ../frontend
npx ng test --watch=false   # Vitest, 63 tests
```

Latest run (2026-10-14): backend 156 unit + 41 integration tests, simulator 35, frontend 63, all passing.
JaCoCo: `anomaly` package at 89 % line coverage; the build fails below 70 % (`check-anomaly-coverage`).
End-to-end check: with `docker compose up`, all 50 seeded meters published a reading within the first
minute, payloads match the contract, and indexes increase between two readings.
Phase 2 (same stack): 102 readings from the 50 meters stored with no duplicate and no rejection; backend
stopped for 90 s then restarted: the 76 readings published meanwhile were all delivered and stored.
Phase 3 (clean stack, demo scenario: 4 days of history, one anomaly per rule started 25 h ago): exactly
one alert per injected anomaly and none elsewhere (A1 W-000004, A2 E-000012 raised then auto-resolved,
A3 E-000011, A4 E-000005, A5 E-000008); 2 e-mails received in Mailpit (A3 HIGH, A4 CRITICAL); the
silent-meter check was postponed twice while the 19 400 history readings were processed. The first run of
this test, before D26, had flagged all 50 meters as silent: that is how the problem was found.
Phase 4 (same stack, signed in through the API): no token 401; admin lists meters 200; technician
creating or listing meters 403 and sees only their own alerts; operator listing users 403 but can list
technicians; wrong password gives the French ProblemDetail; Swagger UI and health stay public; the
simulator signs in with its technical account and readings keep flowing. Restarting the simulator after
a day off then raised 48 false A2 spikes (the gap counted as one hour), all auto-resolved by the next
readings: fixed by D31 and covered by `SpikeRuleTest`.

## Current inventory

| Phase | Test class | Type | What it checks |
|---|---|---|---|
| 0 | `SchemaMigrationIT` | Integration (TimescaleDB) | Flyway V1 applies, `reading` is a hypertable, duplicate readings and negative indexes are rejected, one open alert per (meter, rule), health endpoint UP. Passed (7/7) on 2026-10-12. |
| 1 | `MeterServiceTest` | Unit (Mockito) | Creation with trimmed text, duplicate serial refused on create and update, unknown meter |
| 1 | `MeterRequestValidationTest` | Unit | Serial format, serial prefix must match the type, coordinates and 1-1440 min interval bounds, mandatory fields |
| 1 | `FesMeterGeneratorTest` | Unit | 1000 unique serials, exact water share, serial letter, positions around Fès, meter N stable when the park grows |
| 1 | `MeterControllerTest` | Web slice (MockMvc) | Page JSON shape, size > 1000 and unknown enum refused, 201 + Location, field errors / 404 / 409 as ProblemDetail, French message for malformed JSON |
| 1 | `MeterApiIT` | Integration (TimescaleDB) | Create / read / update, 409 on duplicate, filters and text search, `%` searched literally, pagination sorted by serial, idempotent dev seed that grows from 50 to 120 |
| 1 | `ConsumptionModelTest` | Simulator unit | Index never decreases (3 days, minute by minute), deterministic, daily total within +/-11 % of the meter average, night < evening, voltage within 230 V +/- 2 %, RSSI range |
| 1 | `ReadingPayloadTest` | Simulator unit | JSON fields exactly as the MQTT contract, no voltage for water, topic pattern |
| 1 | `SimulatorConfigTest`, `MeterRegistryClientTest`, `MeterSimulatorTest` | Simulator unit | Env configuration and secret hiding, paginated meter fetch and HTTP errors, transmissions spread over the minute, QoS 1 |
| 2 | `ReadingValidatorTest` | Unit | Accepts a valid / old / zero-index reading; +5 min boundary for future timestamps; rejects negative index, unknown or inactive meter, serial different from the topic, type mismatch, missing fields |
| 2 | `ConsumptionCalculatorTest` | Unit | Difference of successive indexes, first index when no earlier reading, gaps (0 then catch-up), decreasing index never negative, one point per bucket |
| 2 | `ConsumptionStepTest` | Unit | Hour / day / month alignment on UTC+1, calendar months, lower-case codes |
| 2 | `ReadingIngestionServiceTest` | Unit (Mockito) | Stores then updates the meter and publishes the event; duplicates and invalid JSON touch nothing else |
| 2 | `MqttIngestionIT` | Integration (TimescaleDB + Mosquitto) | Specified scenario: 10 readings (one with a lower index) stored, re-published, still exactly 10 rows; 7 kinds of invalid messages dropped while ingestion continues; a late reading does not move `last_reading_at` back |
| 2 | `ConsumptionApiIT` | Integration (TimescaleDB) | Hourly consumption with a gap and a decrease (3, 0, 5, 1), daily bucket on the Moroccan calendar, 400 on invalid step / period, French CSV export, JSON error for a too long export, last reading in meter details |
| 3 | `ZeroConsumptionRuleTest` | Unit | A1: index frozen 24 h raises; any consumption, too little history or inactive meter does not; configured window |
| 3 | `SpikeRuleTest` | Unit | A2: raises at 3.1x, not at 2.9x nor exactly 3x; skipped with 2 days of history, works with 3; skipped when that hour usually consumes 0; configured multiplier; a 41 h gap is not a spike, readings off the hour still give the right rate (D31) |
| 3 | `SilentMeterRuleTest` | Unit | A3: scheduled; exactly 3 intervals tolerated, 1 s more raises (HIGH); duration wording; never-reporting meter measured from registration; configured multiple |
| 3 | `DecreasingIndexRuleTest` | Unit | A4: decrease raises CRITICAL; equal or growing index, first reading, late reading compared with its real predecessor do not; never auto-resolved |
| 3 | `VoltageOutOfRangeRuleTest` | Unit | A5: 207 and 253 V accepted, 206.9 and 253.1 V raise; water meters and missing voltage ignored; configured tolerance |
| 3 | `AnomalyEngineTest`, `AnomalySchedulerTest` | Unit (Mockito) | Raise / auto-resolve / never resolve A4, disabled rule, trigger filtering, inactive meter, a failing rule does not stop the others; scheduler covers every active meter and waits during an ingestion backlog |
| 3 | `RuleConfigServiceTest`, `AlertStatusTest`, `AlertMailNotifierTest`, `IngestionMonitorTest` | Unit | Default settings and validation; allowed status changes; mail only for HIGH / CRITICAL, French subject and body, mail failure logged not thrown; backlog detection |
| 3 | `AlertLifecycleIT` | Integration (TimescaleDB) | One A4 alert for repeated decreases, never auto-resolved; A5 auto-resolved then reopened as a new alert; A3 raised by the schedule and cleared when the meter reports; disabled rule raises nothing; PATCH transitions (400 / 409), order and filters of GET /api/alerts, open alerts in meter details |
| 3 | `MqttIngestionIT` (extended) | Integration (TimescaleDB + Mosquitto) | Specified scenario completed: 10 readings, re-published, still 10 rows **and exactly one open A4 alert** |
| 3 | `MeterBehaviourTest`, `AnomalyPlanTest` | Simulator unit | Each injected anomaly produces its symptom (frozen index, 5x hour, one decrease, 258-262 V, no publication); explicit / percentage plans, deterministic, no voltage anomaly on water meters, typo detection |
| 4 | `SecurityRulesTest` | Web slice (MockMvc + SecurityConfig) | Full role matrix of every route: 401 without token, 403 per role (technician POST /api/meters, operator /api/users, admin PATCH alert...), unlisted route refused, public login / health / Swagger |
| 4 | `UserServiceTest` | Unit (Mockito) | BCrypt hash, password required on create, duplicate username, admin cannot demote / disable / delete themself, last admin protected, alerts unassigned on delete or role change |
| 4 | `AlertServiceRolesTest`, `AlertUpdateRequestTest` | Unit | Technician limited to their own alerts, cannot reassign nor reopen; assignment only to an enabled technician; absent `assignedTo` vs `null` (D30) |
| 4 | `AuthIT` | Integration (TimescaleDB) | Login, same message for unknown user and wrong password, disabled account, tampered and expired tokens, account disabled or role changed after login applies at once, health and API docs public |
| 4 | `UserApiIT` | Integration (TimescaleDB) | User CRUD, assignment, technician sees only their alerts, admin cannot PATCH an alert, deleting a technician unassigns their alerts |
| 4 | `DevProfileIT` | Integration (TimescaleDB, dev profile) | Demo accounts, simulator account, meters and rules seeded and usable (added after a binding error broke only the dev profile) |
| 4 | `MeterRegistryClientTest` (extended) | Simulator unit | Signs in then sends the bearer token; stops with a clear message when the password is refused |
