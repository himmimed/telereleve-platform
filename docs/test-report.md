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
cd frontend
npx ng test --watch=false   # Vitest, 63 tests
```

## Current inventory

| Phase | Test class | Type | What it checks |
|---|---|---|---|
| 0 | `SchemaMigrationIT` | Integration (TimescaleDB) | Flyway V1 applies, `reading` is a hypertable, duplicate readings and negative indexes are rejected, one open alert per (meter, rule), health endpoint UP. Passed (7/7) on 2026-10-12. |
| 5 | `meter-rules.spec` | Frontend unit | Silent meter at 3 x interval (boundary), health priority, voltage range 207 / 253 V boundaries, signal quality |
| 5 | `alert-rules.spec` | Frontend unit | Status changes allowed per role (operator, own technician alerts, admin read-only) |
| 5 | `meter-form.dialog.spec` | Frontend unit | Serial format, serial prefix must match the energy type |
| 5 | `auth.guards.spec` | Frontend unit | Redirect to login with return URL, role restrictions on routes |
| 5 | `interceptors.spec` | Frontend unit | JWT added to API calls only, removed after logout; RFC 7807 messages |
| 5 | `mock-backend.spec` | Frontend unit | Mock API honours the contract: 401 / 403 (technician cannot create a meter), technicians see only their alerts, one open alert per (meter, rule), no negative consumption across gaps |
| 5 | `format.spec`, `app.spec` | Frontend unit | French number / relative-time formatting, root component |
