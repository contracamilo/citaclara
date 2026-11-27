import { expect, test } from '@playwright/test';
import { entrar } from './utilidades';

const RUTAS_ESTATICAS = ['/', '/eleva'];

test('[005][FR-005][FR-006] las rutas de la interfaz llevan las cabeceras de seguridad y no revelan la versión del servidor', async ({ request }) => {
  for (const ruta of RUTAS_ESTATICAS) {
    const r = await request.get(ruta);
    const h = r.headers();
    expect(h['content-security-policy'], ruta).toContain("default-src 'self'");
    expect(h['content-security-policy'], ruta).toContain("frame-ancestors 'none'");
    expect(h['x-content-type-options'], ruta).toBe('nosniff');
    expect(h['referrer-policy'], ruta).toBe('no-referrer');
    expect(h['permissions-policy'], ruta).toContain('camera=()');
    expect(h['server'] ?? '', ruta).not.toMatch(/\d/);
  }
});

test('[005][FR-005][FR-006] el API responde con Cache-Control no-store, nosniff y sin versión del servidor', async ({ request }) => {
  const r = await request.get('/api/sesion');
  const h = r.headers();
  expect(h['cache-control']).toContain('no-store');
  expect(h['x-content-type-options']).toBe('nosniff');
  expect(h['server'] ?? '').not.toMatch(/\d/);
});

test('[005][FR-005][FR-006] la política de contenido no se infringe al usar el panel y la cookie es HttpOnly y SameSite=Strict', async ({ page, context }) => {
  const violaciones: string[] = [];
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (e) => {
      (window as unknown as { __csp: string[] }).__csp = [...((window as unknown as { __csp?: string[] }).__csp ?? []), e.violatedDirective];
    });
  });
  await page.goto('/eleva');
  await entrar(page);
  const encontradas = await page.evaluate(() => (window as unknown as { __csp?: string[] }).__csp ?? []);
  violaciones.push(...encontradas);
  expect(violaciones).toEqual([]);

  const cookie = (await context.cookies()).find((c) => c.name === 'CITACLARA_SESION');
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.sameSite).toBe('Strict');
});

test('[005][FR-009] un cuerpo de más de 64 KB se rechaza con 413', async ({ request }) => {
  const grande = await request.post('/api/clinicas/eleva/sesion', {
    data: { clave: 'x'.repeat(70_000) },
    headers: { 'Content-Type': 'application/json' },
  });
  expect(grande.status()).toBe(413);
});
