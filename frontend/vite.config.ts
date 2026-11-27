import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/** Variables de entorno de Node sin depender de @types/node. */
const entorno = (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env ?? {};

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Backend arrancado fuera de Docker (./mvnw spring-boot:run); URL_API si no escucha en el 8080.
    proxy: {
      '/api': { target: entorno.URL_API ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/unit/**/*.test.ts?(x)'],
    setupFiles: ['tests/unit/preparar.ts'],
    css: false,
  },
});
