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
