import AxeBuilder from '@axe-core/playwright';
import { expect, type APIRequestContext, type Browser, type Locator, type Page, type TestInfo } from '@playwright/test';

export const CLAVE = 'eleva-demo-2026';

export interface Hueco {
  horaInicio: string;
  horaFin: string;
  reservable: boolean;
}
export interface Cita {
  id: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  paciente: { id: number; nombre: string; telefono: string };
  servicio: { nombre: string };
  estado: string;
  canceladaPor?: string | null;
  acciones: string[];
  version: number;
}
export interface Agenda {
  fecha: string;
  hoy: string;
  horario: { apertura: string; cierre: string } | null;
  citas: Cita[];
  huecos: Hueco[];
}
export interface Profesional {
  id: number;
  nombre: string;
}

/** Entra en el panel de Clínica Eleva con la clave de demostración. */
export async function entrar(page: Page, ruta = '/eleva') {
  await page.goto(ruta);
  await page.getByLabel('Clave de la clínica').fill(CLAVE);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
}

/** SC-009 / US1-AC7: sin desplazamiento horizontal. */
export async function sinDesplazamientoHorizontal(page: Page) {
  const { ancho, visible } = await page.evaluate(() => ({
    ancho: document.documentElement.scrollWidth,
    visible: document.documentElement.clientWidth,
  }));
  expect(ancho, 'desplazamiento horizontal').toBeLessThanOrEqual(visible);
}

/** FR-028: sin infracciones de accesibilidad serias o críticas (WCAG 2.2 AA). */
export async function sinInfraccionesAxe(page: Page) {
  const resultado = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
  const graves = resultado.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(graves.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}

/** FR-028 / contracts/ui.md: objetivos táctiles de al menos 44 px en botones y campos visibles. */
export async function objetivosTactiles(page: Page) {
  const pequenos = await page.evaluate(() => {
    const elementos = Array.from(document.querySelectorAll<HTMLElement>('button, a[href], input:not([type=hidden]), select'));
    return elementos
      .filter((el) => {
        const r = el.getBoundingClientRect();
        const estilo = getComputedStyle(el);
        return r.width > 0 && r.height > 0 && estilo.visibility !== 'hidden' && !el.closest('.sr-only');
      })
      .filter((el) => {
        const r = el.getBoundingClientRect();
        return r.height < 43.5 || r.width < 43.5;
      })
      .map((el) => `${el.tagName.toLowerCase()} «${(el.getAttribute('aria-label') ?? el.textContent ?? '').trim()}» ${Math.round(el.getBoundingClientRect().width)}×${Math.round(el.getBoundingClientRect().height)}`);
  });
  expect(pequenos, 'objetivos táctiles menores de 44 px').toEqual([]);
}

/** Ningún texto visible queda aplastado o desbordado sobre otros elementos (p. ej. a 360 px). */
export async function sinTextoDesbordado(page: Page) {
  const desbordados = await page.evaluate(() =>
    Array.from(document.querySelectorAll<HTMLElement>('p, h1, h2, h3, button, label, dt, dd, span'))
      .filter((el) => {
        if (el.closest('.sr-only, [aria-hidden=true]')) return false;
        const propio = Array.from(el.childNodes).some((n) => n.nodeType === Node.TEXT_NODE && n.textContent?.trim());
        if (!propio) return false;
        const r = el.getBoundingClientRect();
        if (r.height === 0 || getComputedStyle(el).visibility === 'hidden') return false;
        return r.width < 1 || el.scrollWidth > el.clientWidth + 2;
      })
      .map((el) => `${el.tagName.toLowerCase()} «${el.textContent?.trim()}»`),
  );
  expect(desbordados, 'textos desbordados').toEqual([]);
}

/** Comprobaciones transversales de cada pantalla: 360 px, texto sin desbordar, axe y objetivos táctiles. */
export async function pantallaAccesible(page: Page) {
  await sinDesplazamientoHorizontal(page);
  await sinTextoDesbordado(page);
  await objetivosTactiles(page);
  await sinInfraccionesAxe(page);
}

/** Día laborable relativo (lunes a viernes) respecto a una fecha YYYY-MM-DD (datos-demostracion.md). */
export function diaLaborable(desde: string, posicion: number): string {
  const fecha = new Date(`${desde}T12:00:00Z`);
  const paso = posicion > 0 ? 1 : -1;
  for (let restantes = Math.abs(posicion); restantes > 0; ) {
    fecha.setUTCDate(fecha.getUTCDate() + paso);
    const dia = fecha.getUTCDay();
    if (dia !== 0 && dia !== 6) restantes--;
  }
  return fecha.toISOString().slice(0, 10);
}

export function proximoSabado(desde: string): string {
  const fecha = new Date(`${desde}T12:00:00Z`);
  do fecha.setUTCDate(fecha.getUTCDate() + 1);
  while (fecha.getUTCDay() !== 6);
  return fecha.toISOString().slice(0, 10);
}

export function aMinutos(hora: string): number {
  const [h, m] = hora.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

export function aHora(minutos: number): string {
  return `${String(Math.floor(minutos / 60)).padStart(2, '0')}:${String(minutos % 60).padStart(2, '0')}`;
}

/** Consultas al API con la cookie de la página (misma sesión que la interfaz). */
export class ApiPrueba {
  constructor(private readonly request: APIRequestContext) {}

  async contexto(): Promise<{ hoy: string; profesionales: Profesional[] }> {
    const r = await this.request.get('/api/sesion');
    expect(r.status()).toBe(200);
    return r.json();
  }

  async profesional(nombre: string): Promise<Profesional> {
    const p = (await this.contexto()).profesionales.find((x) => x.nombre === nombre);
    if (!p) throw new Error(`No existe ${nombre}`);
    return p;
  }

  async agenda(profesionalId: number, fecha: string): Promise<Agenda> {
    const r = await this.request.get(`/api/agenda?profesionalId=${profesionalId}&fecha=${fecha}`);
    expect(r.status()).toBe(200);
    return r.json();
  }

  /**
   * Primer hueco reservable de al menos `minutos` en los días laborables futuros (a partir de +1)
   * en el que, si se indica, también esté libre `otro` profesional a la misma hora.
   */
  async huecoLibre(profesional: Profesional, minutos: number, otro?: Profesional, desdePosicion = 1) {
    const { hoy } = await this.contexto();
    for (let posicion = desdePosicion; posicion <= 10; posicion++) {
      const fecha = diaLaborable(hoy, posicion);
      const agenda = await this.agenda(profesional.id, fecha);
      const agendaOtro = otro ? await this.agenda(otro.id, fecha) : null;
      for (const hueco of agenda.huecos) {
        // Empezar en un múltiplo de 15 dentro del hueco.
        const inicio = Math.ceil(aMinutos(hueco.horaInicio) / 15) * 15;
        if (!hueco.reservable || aMinutos(hueco.horaFin) - inicio < minutos) continue;
        const cabeEnOtro =
          !agendaOtro ||
          agendaOtro.huecos.some((h) => aMinutos(h.horaInicio) <= inicio && aMinutos(h.horaFin) >= inicio + 45);
        if (cabeEnOtro) return { fecha, hueco, inicio: aHora(inicio) };
      }
    }
    throw new Error(`Sin hueco libre de ${minutos} min para ${profesional.nombre}`);
  }
}

/** Abre la agenda de un profesional y día por enlace directo (contracts/ui.md, «Direcciones»). */
export async function abrirAgenda(page: Page, profesionalId: number, fecha: string) {
  await page.goto(`/eleva?profesional=${profesionalId}&fecha=${fecha}`);
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
  await expect(page.getByText('Cargando la agenda…')).toHaveCount(0);
}

/** Tarjeta de cita por franja y paciente. */
export function tarjeta(page: Page, horaInicio: string, horaFin: string, paciente: string): Locator {
  return page.getByRole('article', { name: `Cita ${horaInicio} – ${horaFin} · ${paciente}` });
}

/** Diálogo de alta abierto. */
export function dialogoAlta(page: Page): Locator {
  return page.getByRole('dialog', { name: 'Nueva cita' });
}

/** Elige un paciente existente en el buscador del diálogo de alta. */
export async function elegirPaciente(dialogo: Locator, busqueda: string, nombre: string) {
  await dialogo.getByLabel('Buscar paciente por nombre o teléfono').fill(busqueda);
  await dialogo.getByRole('option', { name: new RegExp(nombre) }).first().click();
}

/** Texto visible normalizado (el formateador de euros usa espacio no separable antes de «€»). */
export async function textoVisible(page: Page): Promise<string> {
  return (await page.locator('body').innerText()).replace(/ /g, ' ');
}

// ── Portal del paciente (002) ──

const URL_CORREO = process.env.URL_CORREO ?? 'http://localhost:8025';
const URL_BASE = process.env.URL_BASE ?? 'http://localhost:8080';

interface MensajeMailpit {
  ID: string;
  Created: string;
}

/** Borra de Mailpit los correos de `email` (cada prueba lee solo el suyo). */
export async function vaciarCorreo(email: string) {
  const r = await fetch(`${URL_CORREO}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`, { method: 'DELETE' });
  expect(r.ok, 'Mailpit no responde: ¿está levantado y URL_CORREO apunta a él?').toBe(true);
}

/**
 * Ruta del último enlace de acceso enviado a `email` (`/eleva/mis-citas#enlace=…`), leída del API de
 * Mailpit (research R2, FR-031). Espera hasta 10 s a que llegue el correo, que sale tras el commit.
 */
export async function ultimoEnlace(email: string): Promise<string> {
  for (let intento = 0; intento < 40; intento++) {
    const r = await fetch(`${URL_CORREO}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`);
    const { messages } = (await r.json()) as { messages: MensajeMailpit[] };
    if (messages.length > 0) {
      const ultimo = [...messages].sort((a, b) => b.Created.localeCompare(a.Created))[0]!;
      const mensaje = (await (await fetch(`${URL_CORREO}/api/v1/message/${ultimo.ID}`)).json()) as { Text: string };
      const url = /https?:\/\/\S+#enlace=[A-Za-z0-9_-]+/.exec(mensaje.Text)?.[0];
      if (!url) throw new Error('El correo no contiene un enlace de acceso');
      return url.replace(/^https?:\/\/[^/]+/, '');
    }
    await new Promise((resuelve) => setTimeout(resuelve, 250));
  }
  throw new Error(`No ha llegado ningún enlace a ${email}`);
}

/** ¿Ha llegado algún correo a `email`? (espera breve: el envío es asíncrono). */
export async function hayCorreoPara(email: string): Promise<boolean> {
  await new Promise((resuelve) => setTimeout(resuelve, 1500));
  const r = await fetch(`${URL_CORREO}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`);
  return ((await r.json()) as { messages: unknown[] }).messages.length > 0;
}

/** Pide el enlace desde la pantalla de entrada del portal (contracts/ui.md §1). */
export async function pedirEnlace(page: Page, email: string) {
  await page.goto('/eleva/mis-citas');
  await page.getByLabel('Tu email').fill(email);
  await page.getByRole('button', { name: 'Enviarme el enlace' }).click();
  await expect(page.getByText(/te hemos enviado un enlace para entrar/)).toBeVisible();
}

/** Entra en el portal como el paciente de `email` con el enlace recibido en Mailpit. */
export async function entrarEnPortal(page: Page, email: string) {
  await vaciarCorreo(email);
  await pedirEnlace(page, email);
  await page.goto(await ultimoEnlace(email));
  await expect(page.getByRole('heading', { name: 'Próximas citas' })).toBeVisible();
}

/**
 * Recepción en un contexto HTTP aparte: la sesión del panel y la del portal no conviven en el mismo
 * navegador (research R4), así que las pruebas del portal preparan sus citas desde aquí.
 */
export async function recepcionAparte(playwright: { request: { newContext: (o: { baseURL: string }) => Promise<APIRequestContext> } }) {
  const request = await playwright.request.newContext({ baseURL: URL_BASE });
  const r = await request.post('/api/clinicas/eleva/sesion', { data: { clave: CLAVE } });
  expect(r.status()).toBe(204);
  return { request, api: new ApiPrueba(request) };
}

/** Crea una cita de `paciente` con `profesional` en un hueco libre a más de 24 h (desde el día laborable +2). */
export async function citaFuturaPara(request: APIRequestContext, profesionalNombre: string, paciente: string) {
  const api = new ApiPrueba(request);
  const contexto = (await (await request.get('/api/sesion')).json()) as {
    profesionales: Profesional[];
    servicios: Array<{ id: number; nombre: string }>;
  };
  const profesional = await api.profesional(profesionalNombre);
  const servicio = contexto.servicios.find((s) => s.nombre === 'Sesión fisioterapia')!;
  const pacientes = (await (await request.get(`/api/pacientes?q=${encodeURIComponent(paciente)}`)).json()) as Array<{
    id: number;
    nombre: string;
  }>;
  const ficha = pacientes.find((p) => p.nombre === paciente)!;
  const { fecha, inicio } = await api.huecoLibre(profesional, 45, undefined, 2);
  const r = await request.post('/api/citas', {
    data: { profesionalId: profesional.id, servicioId: servicio.id, pacienteId: ficha.id, fecha, horaInicio: inicio },
  });
  expect(r.status(), await r.text()).toBe(201);
  const cita = (await r.json()) as Cita;
  return { fecha, profesional, cita };
}

/**
 * Página en un contexto de navegador aparte (otra «persona»: p. ej. recepción mientras el paciente usa
 * `page`), con el viewport, la zona y la URL base del proyecto en curso.
 */
export async function paginaAislada(browser: Browser, testInfo: TestInfo): Promise<Page> {
  const { viewport, isMobile, hasTouch, locale, timezoneId, userAgent, deviceScaleFactor } = testInfo.project.use;
  const contexto = await browser.newContext({ viewport, isMobile, hasTouch, locale, timezoneId, userAgent, deviceScaleFactor, baseURL: URL_BASE });
  return contexto.newPage();
}

/** Fecha larga «martes, 01/12/2026» como la del formateador único (formato/fechas.ts). */
export function fechaLarga(fecha: string): string {
  const dias = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
  const [a, m, d] = fecha.split('-');
  return `${dias[new Date(`${fecha}T12:00:00Z`).getUTCDay()]}, ${d}/${m}/${a}`;
}

/** Tarjeta de una cita en el portal por fecha y franja (únicas para un mismo paciente). */
export function tarjetaPortal(dentro: Page | Locator, fecha: string, horaInicio: string, horaFin: string): Locator {
  return dentro.getByRole('article').filter({ hasText: fechaLarga(fecha) }).filter({ hasText: `${horaInicio}–${horaFin}` });
}
