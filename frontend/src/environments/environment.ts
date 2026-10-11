export const environment = {
  apiBaseUrl: '/api',
  /**
   * While the backend API is being built (phases 1-4), HTTP calls are answered in the browser by
   * MockBackend with simulated data that follows the same REST contract. Set to false to call the
   * real Spring Boot API (proxied to http://localhost:8080 by `ng serve`).
   */
  useMockApi: true,
};
