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
}
