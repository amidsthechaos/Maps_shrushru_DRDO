// localhost only — no external services.
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080', // Spring Boot backend
  // ?v= busts browser cache after basemap regenerations (was caching solid-yellow tiles).
  tileUrl: '/api/tiles/{z}/{x}/{y}.png?v=roads1', // proxied through ng serve → :8080
};
