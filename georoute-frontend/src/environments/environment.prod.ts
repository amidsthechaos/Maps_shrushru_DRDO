// localhost only — no external services.
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080', // Spring Boot backend
  tileUrl: '/api/tiles/{z}/{x}/{y}.png?v=roads1',
};
