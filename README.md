# CEAC Télérelève Platform

Remote meter reading platform for **CEAC Maroc** (Fès), a manufacturer of electricity and water meters.
Smart meters publish their readings over **MQTT**; the platform stores them as **time series**, detects
anomalies (fault, fraud, abnormal consumption) and shows everything on a web dashboard.

Real meters are out of scope: a realistic **meter simulator** produces the readings. All data is simulated.

> Status: **Phases 0 to 3 done** (meters, simulator, MQTT ingestion, consumption, anomaly detection A1-A5,
> alerts and e-mails); frontend built ahead of the backend, on a simulated API. See [Roadmap](#roadmap).

## Tech stack

| Layer | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Actuator), Flyway |
| Messaging | Eclipse Mosquitto (MQTT), Spring Integration MQTT |
| Database | PostgreSQL 16 + TimescaleDB |
| Frontend | Angular 22, Angular Material 3, Chart.js, Leaflet, Vitest |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers, JaCoCo |
| DevOps | Docker, Docker Compose, GitHub Actions |

## Quick start

Prerequisite: **Docker** (Docker Desktop on Windows/macOS). Java and Node are only needed to develop
outside containers.

```sh
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env
# edit .env: set your own passwords and a JWT signing key, for example
#   openssl rand -base64 48            (or PowerShell: [Convert]::ToBase64String((1..48|%{Get-Random -Max 256})))
docker compose up --build
```

**Sign in.** Every API route except login, health and the API documentation needs a JWT. The `dev`
profile creates these demo accounts, all with the password `APP_SEED_DEMO_PASSWORD` from `.env`:

| Username | Name | Role |
|---|---|---|
| `admin` | Youssef El Amrani | ADMIN: meters, users, rule settings |
| `operateur` | Salma Bennani | OPERATOR: supervision, alert handling and assignment |
| `technicien`, `technicien2` | Hamza Tazi, Nadia Alaoui | TECHNICIAN: map and their own alerts |

The simulator signs in with the technical account `simulateur` (`SIMULATOR_PASSWORD`). Get a token with
`POST /api/auth/login` (`{"username":"admin","password":"..."}`) and send it as
`Authorization: Bearer <token>`, or use the *Authorize* button of Swagger UI.

| Address | Service |
|---|---|
| <http://localhost:8080/swagger-ui.html> | API documentation (Swagger UI) |
| <http://localhost:8080/actuator/health> | Backend health |
| <http://localhost:8025> | Mailpit (e-mails sent by the platform) |
| `localhost:1883` | MQTT broker (authenticated) |
| `localhost:5432` | PostgreSQL / TimescaleDB |

The `dev` profile seeds 50 meters around Fès (`APP_SEED_METER_COUNT` in `.env`), and the simulator
publishes one reading per meter and per minute on `ceac/meters/{serial}/readings`. Watch them with:

```sh
docker compose exec mosquitto sh -c 'mosquitto_sub -u "$MQTT_USERNAME" -P "$MQTT_PASSWORD" -t "ceac/meters/+/readings" -v'
```

**Demo scenario** (compose defaults, see `.env.example`): the simulator first publishes 4 days of history,
then injects one anomaly per rule, started 25 hours ago, so every alert appears within minutes:
`CEAC-W-000004` zero consumption (A1), `CEAC-E-000012` spike (A2, raised then resolved), `CEAC-E-000011`
silent (A3), `CEAC-E-000005` decreasing index (A4), `CEAC-E-000008` voltage (A5). Alerts:
`GET /api/alerts` (signed in); HIGH and CRITICAL e-mails: <http://localhost:8025>.

The backend stores every reading (TimescaleDB hypertable `reading`). Try, with a meter id from
`/api/meters`: `/api/meters/{id}`, `/api/meters/{id}/consumption?step=hour` and `/api/meters/{id}/export.csv`.

Stop with `docker compose down` (add `-v` to delete the data volumes).

## Developing locally

Start the infrastructure only, then run the backend from your IDE or the command line:

```sh
docker compose up -d timescaledb mosquitto mailpit
cd backend
./mvnw spring-boot:run      # reads ../.env automatically
```

Tests:

```sh
./mvnw test      # unit tests (no Docker needed)
./mvnw verify    # unit + integration tests with Testcontainers (Docker required) + coverage report
```

Frontend (Node 24):

```sh
cd frontend
npm install
npm start        # http://localhost:4200, demo accounts admin / operateur / technicien, password demo1234
```

The frontend currently runs on a simulated API in the browser (`useMockApi` in
`frontend/src/environments/environment.ts`); see [frontend/README.md](frontend/README.md).

## Repository layout

```
backend/     Spring Boot application (package ma.ceac.telereleve)
simulator/   Smart meter simulator (standalone Java 21)
frontend/    Angular application
docker/      Infrastructure configuration (Mosquitto)
docs/        architecture.md, decisions.md, test-report.md, user-guide.md (FR)
```

## Documentation

- [Architecture](docs/architecture.md)
- [Technical decisions](docs/decisions.md)
- [Test report](docs/test-report.md)
- [Guide utilisateur (FR)](docs/user-guide.md)

## Roadmap

- [x] Phase 0 - Setup: monorepo, Docker Compose infrastructure, Flyway schema, CI
- [x] Phase 1 - Meters and simulator
- [x] Phase 2 - MQTT ingestion and consumption
- [x] Phase 3 - Anomaly detection and alerts
- [x] Phase 4 - Security (JWT, roles)
- [ ] Phase 5 - Angular frontend: all pages built on the simulated API; wiring to the real API remains
- [ ] Phase 6 - Performance, documentation, finalisation
