import { expect, test, type Page } from '@playwright/test';
import { ApiPrueba, entrar, pantallaAccesible } from './utilidades';

/**
 * Analítica (specs/004-panel-analitica). El entorno carga la semilla el día en que se ejecuta, así
 * que NO se comparan cifras literales con la spec (solo reproducibles con la carga en lunes, las
 * verifica AnaliticaSemillaTest, research D9): se comprueba que la pantalla muestra lo que
 * devuelve el API y que las sumas cuadran.
 */

interface Proporcion {
  parte: number;
  total: number;
  decimas: number | null;
}
interface Analitica {
  sinDatos: boolean;
  ultimaSemana: { semana: { inicio: string; fin: string }; profesionales: Array<{ nombre: string; tasaNoAsistencia: Proporcion; ocupacion: Proporcion }> };
  ingresos: { servicios: Array<{ nombre: string; citasCompletadas: number; importeCentimos: number; importeNoObtenidoCentimos: number }>; totalCentimos: number; totalNoObtenidoCentimos: number };
  evolucion: Array<{ semana: { inicio: string; fin: string } }>;
}

const euros = (centimos: number) => new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(centimos / 100);
const porcentaje = (p: Proporcion) => (p.decimas === null ? 'Sin datos' : `${(p.decimas / 10).toFixed(1).replace('.', ',')} %`);
const fecha = (iso: string) => iso.split('-').reverse().join('/');

async function datosApi(page: Page): Promise<Analitica> {
  const r = await page.request.get('/api/analitica');
  expect(r.status()).toBe(200);
  return r.json();
}

async function abrirAnalitica(page: Page) {
  await entrar(page, '/eleva/analitica');
  await expect(page.getByRole('heading', { name: 'Analítica de la clínica' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Última semana' })).toBeVisible();
}

test('[004][FR-001][US4-AC1] sin sesión /eleva/analitica pide la clave y tras entrar muestra la analítica', async ({ page }) => {
  await page.goto('/eleva/analitica');
  await expect(page.getByLabel('Clave de la clínica')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Analítica de la clínica' })).toHaveCount(0);
  await abrirAnalitica(page);
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByLabel('Clave de la clínica')).toBeVisible();
});

test('[004][FR-012][SC-001][US1-AC1] los valores mostrados son los que devuelve el API formateados', async ({ page }) => {
  await abrirAnalitica(page);
  const api = await datosApi(page);
  const ocupacion = page.getByRole('list', { name: 'Ocupación por profesional' });
  const tasa = page.getByRole('list', { name: 'Tasa de no asistencia por profesional' });
  for (const p of api.ultimaSemana.profesionales) {
    await expect(ocupacion).toContainText(p.nombre);
    await expect(ocupacion).toContainText(porcentaje(p.ocupacion));
    await expect(tasa).toContainText(p.nombre);
    await expect(tasa).toContainText(porcentaje(p.tasaNoAsistencia));
    if (p.tasaNoAsistencia.total > 0) await expect(tasa).toContainText(`${p.tasaNoAsistencia.parte} de ${p.tasaNoAsistencia.total} cita`);
  }
});

test('[004][FR-010][SC-002][US2-AC2] el Total de ingresos mostrado es la suma de las filas', async ({ page }) => {
  await abrirAnalitica(page);
  const api = await datosApi(page);
  const suma = api.ingresos.servicios.reduce((n, s) => n + s.importeCentimos, 0);
  const sumaNoObtenidos = api.ingresos.servicios.reduce((n, s) => n + s.importeNoObtenidoCentimos, 0);
  expect(suma).toBe(api.ingresos.totalCentimos);
  expect(sumaNoObtenidos).toBe(api.ingresos.totalNoObtenidoCentimos);

  const seccion = page.getByRole('region', { name: 'Ingresos por servicio' });
  const lista = seccion.getByRole('list', { name: 'Ingresos por servicio' });
  for (const s of api.ingresos.servicios) {
    await expect(lista).toContainText(s.nombre);
    await expect(lista).toContainText(euros(s.importeCentimos));
  }
  await expect(seccion).toContainText(euros(api.ingresos.totalCentimos));
  await expect(seccion).toContainText(euros(api.ingresos.totalNoObtenidoCentimos));
  await expect(seccion).not.toContainText(/ahorro/i);
});

test('[004][FR-014][US3-AC1] las 8 semanas se etiquetan con fechas dd/mm/aaaa, de la más antigua a la más reciente', async ({ page }) => {
  await abrirAnalitica(page);
  const api = await datosApi(page);
  expect(api.evolucion).toHaveLength(8);
  const tabla = page.getByRole('table');
  await expect(tabla.getByRole('rowheader')).toHaveCount(9); // 8 semanas + «Total de las 8 semanas»
  for (const [i, s] of api.evolucion.entries()) {
    await expect(tabla.getByRole('rowheader').nth(i)).toHaveText(`${fecha(s.semana.inicio)} – ${fecha(s.semana.fin)}`);
  }
});

test('[004][FR-013][SC-004] la analítica se usa sin desbordes, con objetivos táctiles ≥ 44 px y sin infracciones de accesibilidad en claro y oscuro', async ({ page }) => {
  await abrirAnalitica(page);
  await expect(page.getByRole('table')).toBeVisible();
  for (const esquema of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme: esquema });
    // Los botones animan el color al cambiar de esquema: axe debe medir con los colores ya asentados.
    await page.evaluate(() => Promise.all(document.getAnimations().map((a) => a.finished)));
    await pantallaAccesible(page);
  }
});

test('[004][SC-006] la página con todos sus gráficos es visible en menos de 3 s', async ({ page }) => {
  await entrar(page);
  const inicio = Date.now();
  await page.goto('/eleva/analitica');
  await expect(page.getByRole('region', { name: 'Ingresos por servicio' })).toBeVisible();
  await expect(page.getByRole('table')).toBeVisible();
  expect(Date.now() - inicio).toBeLessThan(3000);
});

test('[004][FR-003][SC-005] usar la analítica y volver a «Agenda» no cambia las citas', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { hoy } = await api.contexto();
  const jorge = await api.profesional('Jorge');
  const antes = await api.agenda(jorge.id, hoy);

  await page.getByRole('link', { name: 'Analítica' }).click();
  await expect(page.getByRole('heading', { name: 'Analítica de la clínica' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('region', { name: 'Última semana' })).toBeVisible();
  await page.getByRole('link', { name: 'Agenda' }).click();
  await expect(page).toHaveURL(/\/eleva$/);
  await expect(page.getByRole('button', { name: 'Nueva cita' })).toBeVisible();

  expect(await api.agenda(jorge.id, hoy)).toEqual(antes);
});
