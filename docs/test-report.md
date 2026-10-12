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
./mvnw verify               # 20 tests
cd ../frontend
npx ng test --watch=false   # Vitest, 63 tests
```

Latest run (2026-10-12): backend 54 unit + 22 integration tests, simulator 20, frontend 63, all passing.
End-to-end check: with `docker compose up`, all 50 seeded meters published a reading within the first
minute, payloads match the contract, and indexes increase between two readings.
Phase 2 (same stack): 102 readings from the 50 meters stored with no duplicate and no rejection; backend
stopped for 90 s then restarted: the 76 readings published meanwhile were all delivered and stored.

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
| 5 | `meter-rules.spec` | Frontend unit | Silent meter at 3 x interval (boundary), health priority, voltage range 207 / 253 V boundaries, signal quality |
| 5 | `alert-rules.spec` | Frontend unit | Status changes allowed per role (operator, own technician alerts, admin read-only) |
| 5 | `meter-form.dialog.spec` | Frontend unit | Serial format, serial prefix must match the energy type |
| 5 | `auth.guards.spec` | Frontend unit | Redirect to login with return URL, role restrictions on routes |
| 5 | `interceptors.spec` | Frontend unit | JWT added to API calls only, removed after logout; RFC 7807 messages |
| 5 | `mock-backend.spec` | Frontend unit | Mock API honours the contract: 401 / 403 (technician cannot create a meter), technicians see only their alerts, one open alert per (meter, rule), no negative consumption across gaps |
| 5 | `format.spec`, `app.spec` | Frontend unit | French number / relative-time formatting, root component |
