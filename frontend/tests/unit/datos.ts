import { vi } from 'vitest';
import type { Agenda, Cita, Contexto, Problema } from '../../src/api/tipos';

/** Datos de prueba que siguen la forma exacta de contracts/api.yaml. */

export const jorge = { id: 1, nombre: 'Jorge', especialidad: 'fisioterapia', especialidadNombre: 'Fisioterapia' };
export const maria = { id: 2, nombre: 'María', especialidad: 'fisioterapia', especialidadNombre: 'Fisioterapia' };
export const lucia = { id: 3, nombre: 'Lucía', especialidad: 'nutricion', especialidadNombre: 'Nutrición' };

export const contexto: Contexto = {
  clinica: { nombre: 'Clínica Eleva' },
  hoy: '2026-11-26',
  sesionCaducaA: '2026-11-26T17:00:00Z',
  profesionales: [maria, jorge, lucia],
  servicios: [
    { id: 10, nombre: 'Sesión fisioterapia', especialidad: 'fisioterapia', especialidadNombre: 'Fisioterapia', duracionMinutos: 45, precioCentimos: 4000 },
    { id: 11, nombre: 'Primera visita fisioterapia', especialidad: 'fisioterapia', especialidadNombre: 'Fisioterapia', duracionMinutos: 60, precioCentimos: 5000 },
    { id: 12, nombre: 'Consulta nutrición', especialidad: 'nutricion', especialidadNombre: 'Nutrición', duracionMinutos: 30, precioCentimos: 3500 },
    { id: 13, nombre: 'Primera visita nutrición', especialidad: 'nutricion', especialidadNombre: 'Nutrición', duracionMinutos: 45, precioCentimos: 4500 },
  ],
  horario: [1, 2, 3, 4, 5].map((diaSemana) => ({ diaSemana, apertura: '09:00', cierre: '20:00' })),
};

export function cita(parcial: Partial<Cita> = {}): Cita {
  return {
    id: 100,
    fecha: '2026-11-26',
    horaInicio: '10:00',
    horaFin: '10:45',
    profesional: { id: jorge.id, nombre: jorge.nombre },
    paciente: { id: 50, nombre: 'Ana Pérez', telefono: '+34612345678' },
    servicio: { id: 10, nombre: 'Sesión fisioterapia' },
    importeCentimos: 4000,
    estado: 'RESERVADA',
    acciones: ['CANCELAR'],
    version: 0,
    ...parcial,
  };
}

export function agenda(parcial: Partial<Agenda> = {}): Agenda {
  return {
    fecha: '2026-11-26',
    hoy: '2026-11-26',
    profesional: jorge,
    horario: { apertura: '09:00', cierre: '20:00' },
    citas: [cita()],
    huecos: [
      { horaInicio: '09:00', horaFin: '10:00', reservable: true },
      { horaInicio: '10:45', horaFin: '20:00', reservable: true },
    ],
    ...parcial,
  };
}

export function json(cuerpo: unknown, estado = 200): Response {
  return new Response(JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } });
}

export function sinContenido(): Response {
  return new Response(null, { status: 204 });
}

export function problema(estado: number, p: Problema): Response {
  return new Response(JSON.stringify({ title: 'x', status: estado, ...p }), {
    status: estado,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

type Manejador = (url: string, init: RequestInit | undefined) => Response | Promise<Response>;

/** Sustituye `fetch` por un enrutador de respuestas: `'GET /api/sesion'` → respuesta. */
export function simularFetch(rutas: Record<string, Manejador | Response>) {
  const espia = vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const url = String(entrada);
    const metodo = (init?.method ?? 'GET').toUpperCase();
    const ruta = url.split('?')[0];
    const clave = Object.keys(rutas).find((k) => k === `${metodo} ${url}` || k === `${metodo} ${ruta}`);
    if (!clave) throw new Error(`Petición no prevista en la prueba: ${metodo} ${url}`);
    const r = rutas[clave]!;
    return typeof r === 'function' ? r(url, init) : r.clone();
  });
  vi.stubGlobal('fetch', espia);
  return espia;
}

/** Cuerpo JSON enviado en la n-ésima llamada a `fetch` que coincide con método y ruta. */
export function cuerpoEnviado(espia: ReturnType<typeof simularFetch>, metodo: string, ruta: string): unknown[] {
  return espia.mock.calls
    .filter(([u, i]) => String(u).split('?')[0] === ruta && (i?.method ?? 'GET').toUpperCase() === metodo)
    .map(([, i]) => JSON.parse(String(i?.body)));
}

/** «40,00 €» con espacio no separable → con espacio normal (ver src/formato/euros.ts). */
export const normalizarEspacios = (s: string) => s.replace(/[  ]/g, ' ');

/* ------------------------------------------------------------------------------------------
 * Analítica (specs/004-panel-analitica): cifras de la semilla de Clínica Eleva cargada en lunes.
 * Orden del API: Jorge, Lucía, María.
 * ---------------------------------------------------------------------------------------- */
import type { Analitica, Proporcion } from '../../src/api/tipos';

export function proporcion(parte: number, total: number): Proporcion {
  return { parte, total, decimas: total === 0 ? null : Math.floor((2000 * parte + total) / (2 * total)) };
}

const lunes = ['2026-10-05', '2026-10-12', '2026-10-19', '2026-10-26', '2026-11-02', '2026-11-09', '2026-11-16', '2026-11-23'];
const domingos = ['2026-10-11', '2026-10-18', '2026-10-25', '2026-11-01', '2026-11-08', '2026-11-15', '2026-11-22', '2026-11-29'];
const completadas = [110, 115, 105, 100, 114, 102, 109, 118];
const noAsistidas = [11, 10, 18, 12, 10, 19, 16, 10];
const canceladas = [14, 11, 9, 13, 10, 12, 9, 7];
const ingresosSemana = [438000, 461000, 421000, 399000, 455000, 405000, 433000, 475000];
// Minutos completados por semana: Jorge, Lucía, María.
const minutos = [
  [1680, 1740, 1425, 1410, 1470, 1170, 1665, 1590], // Jorge: 12.150 min en total
  [1380, 1545, 1335, 1335, 1575, 1470, 1350, 1605], // Lucía: 11.595
  [1545, 1560, 1680, 1440, 1710, 1575, 1530, 1800], // María: 12.840
];

export const analiticaEleva: Analitica = {
  sinDatos: false,
  ultimaSemana: {
    semana: { inicio: '2026-11-23', fin: '2026-11-29' },
    profesionales: [
      { profesionalId: 1, nombre: 'Jorge', especialidadNombre: 'Fisioterapia', tasaNoAsistencia: proporcion(5, 41), ocupacion: proporcion(1590, 3300) },
      { profesionalId: 3, nombre: 'Lucía', especialidadNombre: 'Nutrición', tasaNoAsistencia: proporcion(4, 55), ocupacion: proporcion(1605, 3300) },
      { profesionalId: 2, nombre: 'María', especialidadNombre: 'Fisioterapia', tasaNoAsistencia: proporcion(1, 39), ocupacion: proporcion(1800, 3300) },
    ],
  },
  ingresos: {
    servicios: [
      { servicioId: 11, nombre: 'Primera visita fisioterapia', citasCompletadas: 97, importeCentimos: 485000, citasNoAsistidas: 11, importeNoObtenidoCentimos: 55000 },
      { servicioId: 10, nombre: 'Sesión fisioterapia', citasCompletadas: 426, importeCentimos: 1704000, citasNoAsistidas: 56, importeNoObtenidoCentimos: 224000 },
      { servicioId: 12, nombre: 'Consulta nutrición', citasCompletadas: 277, importeCentimos: 969500, citasNoAsistidas: 27, importeNoObtenidoCentimos: 94500 },
      { servicioId: 13, nombre: 'Primera visita nutrición', citasCompletadas: 73, importeCentimos: 328500, citasNoAsistidas: 12, importeNoObtenidoCentimos: 54000 },
    ],
    totalCentimos: 3487000,
    totalNoObtenidoCentimos: 427500,
  },
  evolucion: lunes.map((inicio, i) => ({
    semana: { inicio, fin: domingos[i]! },
    completadas: completadas[i]!,
    noAsistidas: noAsistidas[i]!,
    canceladas: canceladas[i]!,
    tasaNoAsistencia: proporcion(noAsistidas[i]!, completadas[i]! + noAsistidas[i]! + canceladas[i]!),
    ingresosCentimos: ingresosSemana[i]!,
    ocupacion: [1, 3, 2].map((profesionalId, p) => ({ profesionalId, ocupacion: proporcion(minutos[p]![i]!, 3300) })),
  })),
};
