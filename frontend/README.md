# Frontend

Angular 22 console of the Télérelève platform. User interface in French.

- Angular Material 3, customised (teal accent, Geist / Geist Mono, light and dark themes)
- Chart.js through ng2-charts, Leaflet with OpenStreetMap tiles, Phosphor icons
- Standalone components, signals, zoneless change detection, lazy-loaded pages
- Unit tests with Vitest

## Run

```sh
npm install
npm start            # http://localhost:4200
npm test -- --watch=false
npm run build        # production build in dist/frontend
```

## Backend API

`ng serve` proxies `/api` to the Spring Boot backend on http://localhost:8080 (`proxy.conf.json`). Start it
with `docker compose up` from the repository root (or the backend alone, see the root README), then sign
in with a demo account of the `dev` profile: `admin`, `operateur`, `technicien` or `technicien2`, password
`APP_SEED_DEMO_PASSWORD` from `.env`.

Dates are shown and periods computed on the platform calendar, a fixed UTC+1 like the backend, whatever
the browser's time zone (`src/app/shared/calendar.ts`, `docs/decisions.md` D33).

## Simulated API (optional)

With `useMockApi: true` in `src/environments/environment.ts`, an HTTP interceptor (`src/app/core/mock`)
answers every `/api` call in the browser with simulated data that follows the same REST contract
(`docs/decisions.md`, D12 and D13), so the screens can be worked on without Docker. A "Données simulées"
badge is shown in the top bar and the login page offers the demo accounts (password `demo1234`). The unit
tests keep the mock aligned with the contract.

The session token is kept in memory only: reloading the page signs you out (`docs/decisions.md`, D16).

## Structure

```
src/app/
  core/       models (API DTOs), API services, auth store / guards / interceptors, French labels,
              display rules, theme, mock API
  shared/     badges, skeletons, empty states, chart, formatting pipes, dialogs
  layout/     application shell (sidebar, top bar)
  features/   login, dashboard, map, meters (list, detail, form), alerts, admin
```
