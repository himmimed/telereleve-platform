export const environment = {
  apiBaseUrl: '/api',
  /**
   * false: calls the real Spring Boot API (proxied to http://localhost:8080 by `ng serve`).
   * true: HTTP calls are answered in the browser by MockBackend with simulated data that follows the
   * same REST contract. It served while the backend was being built (phases 1-4) and remains useful to
   * work on the screens without Docker.
   */
  useMockApi: false,
};
