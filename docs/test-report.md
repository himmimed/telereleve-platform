# Test report

All tests pass on 2026-10-15:
- backend: 166 unit tests and 48 integration tests;
- simulator: 35 tests;
- frontend: 71 tests.

Backend line coverage is 96 % (JaCoCo, unit and integration tests together), including 96.5 % for the
`anomaly` package. The build fails below 70 % for that package (`check-anomaly-coverage`).

## Acceptance criteria

| Criterion (specification) | Result | Evidence |
|---|---|---|
| 1000 simulated meters publish readings; all are stored with no loss and no duplicates | Met | Load test: 532 610 published, 532 610 stored, 0 duplicate, 0 rejected, across 2 backend restarts and an 8-minute backend outage |
| Each injected anomaly (A1-A5) produces the expected alert, and only one | Met | Load test with 1000 meters: exactly 5 alerts, one per anomalous meter, none on the 995 others; 2 e-mails (HIGH, CRITICAL) |
| Dashboard (KPIs, charts, map) loads in under 2 s with 1000 meters | Met | 0.8 s from the sign-in click with 1.58 million readings in the last 24 h (after D36; 2.5-4 s before) |
| Role permissions are enforced (verified by tests) | Met | `SecurityRulesTest` (every route x every role), `AuthIT`, `UserApiIT`, `AlertServiceRolesTest` |
| All tests pass in CI; anomaly package coverage >= 70 % | Met locally | The CI runs the same commands (`.github/workflows/ci.yml`); the repository has no GitHub remote yet, so no CI run could be observed. Coverage 96.5 % |
| `docker compose up --build` starts the whole platform on a clean machine | Met | New compose project with empty volumes: 6 services healthy in 84 s (images partly cached) |
| README lets a newcomer run and understand the project in 10 minutes | Done | README rewritten: 4 steps to run, a guided tour, troubleshooting |

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
npx ng test --watch=false   # Vitest, 71 tests
```

## Load test with 1000 meters (Phase 6)

### Setup

- **Machine:** one Windows 10 laptop running Docker Desktop (WSL 2, 7.2 GiB of memory for Docker). All six
  containers ran on it, together with the test tools.
- **Stack:** a separate compose project (`docker compose -p ceac-clean up --build`) with empty volumes. This
  is also the clean-machine test.
- **Load:**
  - `APP_SEED_METER_COUNT=1000`: 700 electricity and 300 water meters;
  - the simulator first publishes 4 days of history (387 000 readings at 400 per second), then one reading
    per meter per minute;
  - five anomalous meters, one per rule (compose defaults).
- **Duration:** the test ran for about 24 hours of wall-clock time. The laptop slept twice (12 h and 10 h).
  The platform resumed both times, but each sleep is a gap in the live readings.

### Results

| Measure | Result |
|---|---|
| Clean start (`up --build`, empty volumes) | All services healthy in 84 s (images partly cached) |
| Readings published / stored | **532 610 / 532 610**: 0 failed publication, 0 duplicate key, 0 rejected reading |
| Backend outage (stopped 8 min under live load) | 8 098 messages queued by Mosquitto; after restart, 8 000 readings for those 8 minutes, from all 1000 meters |
| Ingestion delay at steady state (`received_at - read_at`) | 0.51 s on average, 1.03 s at most (998 readings per minute) |
| Ingestion capacity (draining a backlog) | About 50 readings/s at first, then 67-116 readings/s after D34: 4 to 7 times the live load of 1000 meters (16.7 readings/s) |
| Broker backlog during the history replay | Peak about 344 000 messages (limit 500 000), 0 dropped |
| Alerts | A1 W-000004, A2 E-000012 (raised and auto-resolved), A3 E-000011, A4 E-000005, A5 E-000008, and nothing else |
| E-mails | 2: `[HAUTE] A3 ... CEAC-E-000011`, `[CRITIQUE] A4 ... CEAC-E-000005` |
| A3 during the replay | Postponed while ingestion caught up (D26); raised on E-000011 alone once the backlog was drained |
| Storage | 532 610 readings take 72 MB (6 daily chunks) |
| Migration V2 on 1.97 million readings | 16 s |

**Dashboard time.** For a worst case, a synthetic full day was added at one reading per meter per minute:
1.58 million readings in the last 24 hours, 1.97 million in total. This data was inserted only to measure
timings, after the correctness checks above. Its arbitrary index values then caused A4 alerts on every meter.

| Call (through nginx, 3 runs) | Before D36 | After D36 |
|---|---|---|
| `GET /api/dashboard/consumption-trend?step=hour` (24 h) | 2.46-4.05 s | 0.15-0.23 s |
| `GET /api/dashboard/consumption-trend?step=day` (7 days) | 1.32-2.02 s | 0.22-0.30 s |
| `GET /api/dashboard/stats` | 1.27-1.63 s | 0.10-0.35 s |
| `GET /api/meters/map` (1000 markers) | 0.04-0.10 s | 0.05-0.12 s |
| `GET /api/alerts`, `GET /api/meters` (pages) | 0.02-0.19 s | 0.02-0.12 s |
| **Whole dashboard in the browser, from the sign-in click** | about 3-4 s (estimated from the slowest call) | **0.79 s** |

The application shell itself loads in 0.18 s (496 KB transferred). The map draws its 1000 markers.

**Resources.**

| Container | During the history replay | Live load only |
|---|---|---|
| Backend | 45-107 % CPU (before D34), about 45 % after; 370-450 MiB | about 5 % CPU |
| TimescaleDB | 40-50 % CPU, 120-175 MiB | about 10 % CPU |
| Mosquitto | about 15 % CPU, up to 171 MiB with 344 000 messages queued | low |
| Simulator | 40 % CPU while publishing history at 400 per second, 130 MiB | 2.5 % CPU |
| nginx (frontend) | 8 MiB | 8 MiB |

### Problems found and fixed during the test

| Problem | Fix |
|---|---|
| Ingestion at about 50 readings/s: 23 database round trips per reading | 6 round trips per reading (D34), unchanged results |
| Network curve and "consumption today" read every reading (2.5-4 s) | Hourly summary table maintained with each insert (D36) |
| No map at all in the Docker image: Leaflet's functions are under `default` in the production bundle | Shared loader that accepts both shapes (`leafletApi`), unit-tested (D35) |
| Frontend container "unhealthy": `localhost` resolves to IPv6 in Alpine | Health check on `127.0.0.1` (D35) |
| Some hashed bundles (`chunk-C-H3G-Ow.js`) not cached | nginx pattern accepts `-` and `_` in hashes |
| "1 critiques" on the dashboard | Singular and plural follow the count |

### Limits of this test

- Everything ran on one laptop: a server would have more CPU and lower latency between the containers.
- Ingestion is one ordered consumer, so each meter's readings stay in sequence for A4. It handles 4 to 7
  times the 1000-meter load measured here. The next step would be several consumers partitioned by meter.
- The live phase was interrupted twice by the laptop sleeping. Those gaps are real outages from the
  platform's point of view: it resumed with no loss, and A2 ignored the gaps (D31).

## End-to-end checks of the earlier phases

- **Phase 1:** with `docker compose up`, all 50 seeded meters published a reading within the first minute.
  Payloads match the contract, and indexes increase between two readings.
- **Phase 2:** 102 readings from the 50 meters were stored with no duplicate and no rejection. The backend
  was stopped for 90 s then restarted: the 76 readings published meanwhile were all delivered and stored.
- **Phase 3** (clean stack; demo scenario of 4 days of history, one anomaly per rule started 25 h ago):
  - exactly one alert per injected anomaly and none elsewhere: A1 W-000004, A2 E-000012 (raised then
    auto-resolved), A3 E-000011, A4 E-000005, A5 E-000008;
  - 2 e-mails (A3 HIGH, A4 CRITICAL);
  - the first run, before D26, flagged all 50 meters as silent. That is how the problem was found.
- **Phase 4** (signed in through the API): every role restriction gave the expected 401 / 403, and Swagger
  UI and health stay public. Restarting the simulator after a day off raised 48 false A2 spikes, fixed by D31.
- **Phase 5** (frontend on the real API): every screen was checked for each role. Two problems were found
  and fixed:
  - day bars labelled one day early in a browser set to UTC (D33);
  - 37 of the 50 demo meters had the same client name.

## Test inventory

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
| 5 | `meter-rules.spec` | Frontend unit | Silent meter at 3 x interval (boundary), health priority, voltage range 207 / 253 V boundaries, signal quality |
| 5 | `alert-rules.spec` | Frontend unit | Status changes allowed per role (operator, own technician alerts, admin read-only) |
| 5 | `meter-form.dialog.spec` | Frontend unit | Serial format, serial prefix must match the energy type |
| 5 | `auth.guards.spec` | Frontend unit | Redirect to login with return URL, role restrictions on routes |
| 5 | `interceptors.spec` | Frontend unit | JWT added to API calls only, removed after logout; RFC 7807 messages |
| 5 | `mock-backend.spec` | Frontend unit | Mock API honours the contract: 401 / 403 (technician cannot create a meter), technicians see only their alerts, one open alert per (meter, rule), no negative consumption across gaps |
| 5 | `format.spec`, `app.spec` | Frontend unit | French number / relative-time formatting, root component |
| 5 | `DashboardServiceTest` | Unit (Mockito) | Map status SILENT before ALERT before OK; silence follows A3 (exactly 3 intervals tolerated) and its configured multiple; every severity counted (0 when none); no consumption query at local midnight |
| 5 | `DashboardApiIT` | Integration (TimescaleDB) | Network curve with a gap, a decrease, a meter without earlier reading and an inactive meter: equals the sum of the per-meter curves; 400 on invalid step / period; stats and map for a technician (active / silent counts, open alerts per severity, consumption today, statuses, positions) |
| 5 | `SecurityRulesTest` (extended) | Web slice | Map, stats and trend open to the three roles, 401 without token |
| 5 | `FesMeterGeneratorTest` (extended) | Unit | Client names and streets vary between meters (regression) |
| 5 | `calendar.spec` | Frontend unit | Periods start at Moroccan midnight / first of the month across years; bucket labels and dates shown on UTC+1 whatever the browser time zone (D33) |
| 6 | `AnomalyEngineTest` (extended) | Unit (Mockito) | No second `raise` while the alert is open, no `resolve` when none is open (D34) |
| 6 | `ConsumptionApiIT` (extended) | Integration (TimescaleDB) | Latest reading at or before several instants in one query: exact match, between readings, after the last one, before the first one |
| 6 | `DashboardApiIT` (extended) | Integration (TimescaleDB) | Hourly summary after an out-of-order reading and a duplicate; network curve over a period ending within an hour, equal to the per-meter curve (D36) |
| 6 | `SchemaMigrationIT` (extended) | Integration (TimescaleDB) | Flyway V2 applied, `reading_hourly` is a hypertable |
| 6 | `map-tiles.spec` | Frontend unit | Leaflet API found on the module (development server) or under `default` (production bundle); the real package loads |

## Known limits and next steps

- **No GitHub remote yet:** the CI workflow has not run on GitHub. Its commands were run locally.
- **Simulator restarts:** injected anomalies start relative to the simulator's own start time. Restarting
  it on an existing database replays a history that contradicts the stored one for the anomalous meters, and
  raises real A2 / A4 alerts there. A fresh stack (`docker compose down -v`) is consistent. An absolute
  start time (`SIM_ANOMALY_START`) would remove this.
- **Ingestion scaling:** one ordered consumer handles 67-116 readings/s on the test laptop; beyond that,
  partition the consumers by meter.
- **Data retention:** no retention or compression policy yet (72 MB for 532 000 readings, about 135 bytes
  each: some 200 MB per day for 1000 meters read every minute; TimescaleDB compression would divide it).
- **Session:** the token lives in memory, so reloading the page signs the user out (D16). A refresh token in
  an HttpOnly cookie would fix this.
