/** Tipos que reflejan exactamente los esquemas de specs/001-nucleo-agenda/contracts/api.yaml. */

/** Hora local de Madrid `HH:mm` (24 h). */
export type HoraLocal = string;
/** Fecha local de Madrid `YYYY-MM-DD`. */
export type FechaLocal = string;

export type Estado = 'RESERVADA' | 'COMPLETADA' | 'CANCELADA' | 'NO_ASISTIDA';
export type Accion = 'CANCELAR' | 'COMPLETAR' | 'MARCAR_NO_ASISTIDA';

export interface Profesional {
  id: number;
  nombre: string;
  /** Valor interno; nunca se muestra (FR-027). */
  especialidad: string;
  /** Etiqueta visible en español de España. */
  especialidadNombre: string;
}

export interface Servicio {
  id: number;
  nombre: string;
  /** Valor interno; nunca se muestra (FR-027). */
  especialidad: string;
  especialidadNombre: string;
  duracionMinutos: number;
  precioCentimos: number;
}

export interface Paciente {
  id: number;
  nombre: string;
  telefono: string;
  email?: string | null;
}

export interface NuevoPaciente {
  nombre: string;
  telefono: string;
  email?: string | null;
}

/** Exactamente uno de `pacienteId` o `nuevoPaciente`. */
export type NuevaCita = {
  profesionalId: number;
  servicioId: number;
  fecha: FechaLocal;
  horaInicio: HoraLocal;
} & ({ pacienteId: number; nuevoPaciente?: never } | { nuevoPaciente: NuevoPaciente; pacienteId?: never });

export interface Cita {
  id: number;
  fecha: FechaLocal;
  horaInicio: HoraLocal;
  horaFin: HoraLocal;
  profesional: { id: number; nombre: string };
  paciente: { id: number; nombre: string; telefono: string };
  servicio: { id: number; nombre: string };
  importeCentimos: number;
  estado: Estado;
  acciones: Accion[];
  version: number;
  /** Solo «PACIENTE» cuando canceló el propio paciente desde el recordatorio (003, FR-016). */
  canceladaPor?: 'PACIENTE' | null;
}

export interface Hueco {
  horaInicio: HoraLocal;
  horaFin: HoraLocal;
  reservable?: boolean;
}

export interface HorarioDia {
  apertura: HoraLocal;
  cierre: HoraLocal;
}

export interface Agenda {
  fecha: FechaLocal;
  hoy: FechaLocal;
  profesional: Profesional;
  horario?: HorarioDia | null;
  citas: Cita[];
  huecos: Hueco[];
}

export interface Contexto {
  clinica: { nombre: string };
  hoy: FechaLocal;
  /** Instante de caducidad absoluta (accesoEn + 8 h). */
  sesionCaducaA: string;
  profesionales: Profesional[];
  servicios: Servicio[];
  /** Horario semanal; días ausentes = cerrado. */
  horario: Array<{ diaSemana: number; apertura: HoraLocal; cierre: HoraLocal }>;
}

export interface ErrorCampo {
  campo: string;
  mensaje: string;
}

export interface Problema {
  /** Código estable para el programa. Nunca se muestra al usuario. */
  codigo: string;
  /** Texto en español de España listo para mostrarse. */
  mensaje: string;
  errores?: ErrorCampo[];
  citaConflictiva?: Cita;
  citaActual?: Cita;
  pacienteExistente?: Paciente;
  /** Situación real de la cita en las respuestas 409 y 422 de la cancelación por el paciente (003). */
  cancelacion?: Cancelacion;
}

/** Esquema `Cancelacion` de specs/003-recordatorios-cita/contracts/api.yaml. */
export type SituacionCancelacion = 'ACTIVA' | 'FUERA_DE_PLAZO' | 'YA_CERRADA';

export interface Cancelacion {
  situacion: SituacionCancelacion;
  canceladaPorPaciente: boolean;
  clinica: { nombre: string; telefono: string };
  cita: { fecha: FechaLocal; horaInicio: HoraLocal; profesional: string; servicio: string };
}

/* ---------------------------------------------------------------------------------------------
 * Analítica (specs/004-panel-analitica/contracts/api.yaml). El cliente NO recalcula porcentajes.
 * ------------------------------------------------------------------------------------------- */

/** Cociente exacto; `decimas` es el porcentaje por diez (93 = 9,3 %) o `null` si `total` es 0. */
export interface Proporcion {
  parte: number;
  total: number;
  decimas: number | null;
}

/** Lunes y domingo, fechas locales `YYYY-MM-DD`. */
export interface Semana {
  inicio: FechaLocal;
  fin: FechaLocal;
}

export interface ProfesionalAnalitica {
  profesionalId: number;
  nombre: string;
  especialidadNombre: string;
  tasaNoAsistencia: Proporcion;
  ocupacion: Proporcion;
}

export interface ServicioIngresos {
  servicioId: number;
  nombre: string;
  citasCompletadas: number;
  importeCentimos: number;
  citasNoAsistidas: number;
  importeNoObtenidoCentimos: number;
}

export interface SemanaEvolucion {
  semana: Semana;
  completadas: number;
  noAsistidas: number;
  canceladas: number;
  tasaNoAsistencia: Proporcion;
  ingresosCentimos: number;
  ocupacion: Array<{ profesionalId: number; ocupacion: Proporcion }>;
}

export interface Analitica {
  sinDatos: boolean;
  ultimaSemana: { semana: Semana; profesionales: ProfesionalAnalitica[] };
  ingresos: { servicios: ServicioIngresos[]; totalCentimos: number; totalNoObtenidoCentimos: number };
  evolucion: SemanaEvolucion[];
}

// ── Portal del paciente y aviso de cancelaciones (002, contracts/api.yaml) ──

/** Estado de una cita tal como la ve el paciente (data-model «Vista de cita en el portal»). */
export type EstadoPortal = 'RESERVADA' | 'PENDIENTE_CIERRE' | 'REALIZADA' | 'NO_ACUDISTE' | 'CANCELADA';

export interface CitaPortal {
  id: number;
  version: number;
  paciente: string;
  fecha: FechaLocal;
  horaInicio: HoraLocal;
  horaFin: HoraLocal;
  profesional: string;
  servicio: string;
  importeCentimos: number;
  estadoPortal: EstadoPortal;
  cancelable: boolean;
  /** Mensaje listo para mostrar si está reservada pero no se puede cancelar. */
  motivoNoCancelable?: string | null;
}

export interface MisCitas {
  clinicaNombre: string;
  pacientes: string[];
  proximas: CitaPortal[];
  pasadas: CitaPortal[];
}

export interface CancelacionPortal {
  citaId: number;
  paciente: string;
  profesional: string;
  fecha: FechaLocal;
  horaInicio: HoraLocal;
  horaFin: HoraLocal;
  servicio: string;
  canceladaFecha: FechaLocal;
  canceladaHora: HoraLocal;
  huecoPasado: boolean;
}

export interface CancelacionesPortal {
  pendientes: number;
  cancelaciones: CancelacionPortal[];
}
