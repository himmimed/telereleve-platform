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

## Simulated API

Until the backend phases are done, `src/environments/environment.ts` has `useMockApi: true`: an HTTP
interceptor (`src/app/core/mock`) answers every `/api` call in the browser with simulated data that follows
the REST contract of the specification (see `docs/decisions.md`, D12 and D13). A "Données simulées" badge
is shown in the top bar.

Demo accounts (password `demo1234`, mock mode only):

| Account | Role |
|---|---|
| `admin` | Administrateur |
| `operateur` | Opérateur |
| `technicien` | Technicien (Hamza Tazi) |

With `useMockApi: false`, `ng serve` proxies `/api` to the Spring Boot backend on http://localhost:8080
(`proxy.conf.json`).

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
