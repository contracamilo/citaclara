/**
 * Único módulo de formato de fechas y horas (FR-026, research D4).
 *
 * Las fechas llegan del API como cadenas `YYYY-MM-DD` (fecha local de Madrid) y las horas
 * como `HH:mm` (24 h). Este módulo NUNCA crea un `Date` con la zona del dispositivo:
 * cuando necesita calendario (día de la semana, sumar días) trabaja en UTC puro, donde
 * no hay cambios de hora. Las horas se muestran tal cual llegan, sin aritmética de zonas.
 */

const PATRON_FECHA = /^(\d{4})-(\d{2})-(\d{2})$/;
const PATRON_HORA = /^([01]\d|2[0-3]):([0-5]\d)$/;

const DIAS_SEMANA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'] as const;

function partes(fecha: string): [number, number, number] {
  const m = PATRON_FECHA.exec(fecha);
  if (!m) throw new Error(`Fecha no válida: ${fecha}`);
  return [Number(m[1]), Number(m[2]), Number(m[3])];
}

function aUtc(fecha: string): Date {
  const [a, m, d] = partes(fecha);
  return new Date(Date.UTC(a, m - 1, d));
}

function desdeUtc(f: Date): string {
  const a = String(f.getUTCFullYear()).padStart(4, '0');
  const m = String(f.getUTCMonth() + 1).padStart(2, '0');
  const d = String(f.getUTCDate()).padStart(2, '0');
  return `${a}-${m}-${d}`;
}

/** ¿Es `YYYY-MM-DD` una fecha de calendario real? */
export function esFechaValida(fecha: string): boolean {
  const m = PATRON_FECHA.exec(fecha);
  if (!m) return false;
  return desdeUtc(aUtc(fecha)) === fecha;
}

/** «2026-11-26» → «26/11/2026». */
export function formatearFecha(fecha: string): string {
  const [a, m, d] = partes(fecha);
  return `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${String(a).padStart(4, '0')}`;
}

/** Semana lunes–domingo → «23/11/2026 – 29/11/2026» (FR-014). */
export function formatearSemana(semana: { inicio: string; fin: string }): string {
  return `${formatearFecha(semana.inicio)} – ${formatearFecha(semana.fin)}`;
}

/** «2026-11-26» → «jueves, 26/11/2026». */
export function formatearFechaLarga(fecha: string): string {
  return `${DIAS_SEMANA[aUtc(fecha).getUTCDay()]}, ${formatearFecha(fecha)}`;
}

/** Suma (o resta) días de calendario a una fecha `YYYY-MM-DD`. */
export function sumarDias(fecha: string, dias: number): string {
  const f = aUtc(fecha);
  f.setUTCDate(f.getUTCDate() + dias);
  return desdeUtc(f);
}

/** Día de la semana ISO (1 = lunes … 7 = domingo). */
export function diaSemanaIso(fecha: string): number {
  const d = aUtc(fecha).getUTCDay();
  return d === 0 ? 7 : d;
}

/** ¿Es una hora `HH:mm` de 24 h? */
export function esHoraValida(hora: string): boolean {
  return PATRON_HORA.test(hora);
}

/** Las horas se muestran tal cual llegan del API («HH:mm», 24 h). */
export function formatearHora(hora: string): string {
  return hora;
}

/** «10:00» y «10:45» → «10:00 – 10:45». */
export function formatearFranja(inicio: string, fin: string): string {
  return `${inicio} – ${fin}`;
}

/** «10:45» → 645. */
export function minutosDelDia(hora: string): number {
  const m = PATRON_HORA.exec(hora);
  if (!m) throw new Error(`Hora no válida: ${hora}`);
  return Number(m[1]) * 60 + Number(m[2]);
}

/** 645 → «10:45». */
export function horaDesdeMinutos(minutos: number): string {
  const h = Math.floor(minutos / 60);
  const m = minutos % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/**
 * Hora de reloj de pared + minutos, para la vista previa «Termina a las HH:mm» del alta.
 * Es solo orientativa: la hora de fin real la calcula el servidor sobre el instante.
 */
export function sumarMinutos(hora: string, minutos: number): string {
  return horaDesdeMinutos((minutosDelDia(hora) + minutos) % (24 * 60));
}

/** Horas en pasos de `paso` minutos desde `desde` (incluida) hasta `hasta` (excluida). */
export function horasEnPasos(desde: string, hasta: string, paso = 5): string[] {
  const resultado: string[] = [];
  const inicio = Math.ceil(minutosDelDia(desde) / paso) * paso;
  for (let m = inicio; m < minutosDelDia(hasta); m += paso) resultado.push(horaDesdeMinutos(m));
  return resultado;
}
