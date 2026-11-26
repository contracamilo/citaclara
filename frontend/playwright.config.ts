import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas extremo a extremo contra `docker compose` con la semilla cargada (quickstart.md).
 * URL_BASE permite apuntar a otro puerto (p. ej. PUERTO_WEB=8081 → URL_BASE=http://localhost:8081).
 * Proyectos: portátil de recepción y móvil de 360 px (SC-009). Los escenarios modifican la agenda,
 * así que se ejecutan en serie y cada prueba busca sus huecos libres en el momento.
 */
export default defineConfig({
  testDir: './tests/e2e',
  globalSetup: './tests/e2e/preparar.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: [['list']],
  use: {
    baseURL: process.env.URL_BASE ?? 'http://localhost:8080',
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'portatil', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 768 } } },
    {
      name: 'movil',
      use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 780 }, isMobile: true, hasTouch: true },
    },
  ],
});
