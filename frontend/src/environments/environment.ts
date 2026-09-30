export const environment = {
  production: true,
  // Same-origin in Docker (nginx proxies /api to backend). Dev uses proxy.conf.json.
  apiBaseUrl: '/api/v1'
};
