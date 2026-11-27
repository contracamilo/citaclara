import type { CancelacionPortal, CitaPortal, MisCitas } from '../../src/api/tipos';

/** Datos de prueba del portal (002). */
export function citaPortal(parcial: Partial<CitaPortal> = {}): CitaPortal {
  return {
    id: 11,
    version: 0,
    paciente: 'Teresa Méndez',
    fecha: '2026-12-01',
    horaInicio: '10:15',
    horaFin: '11:15',
    profesional: 'María',
    servicio: 'Primera visita fisioterapia',
    importeCentimos: 5000,
    estadoPortal: 'RESERVADA',
    cancelable: true,
    motivoNoCancelable: null,
    ...parcial,
  };
}

export function misCitas(parcial: Partial<MisCitas> = {}): MisCitas {
  return {
    clinicaNombre: 'Clínica Eleva',
    pacientes: ['Teresa Méndez'],
    proximas: [citaPortal()],
    pasadas: [],
    ...parcial,
  };
}

export function cancelacionPortal(parcial: Partial<CancelacionPortal> = {}): CancelacionPortal {
  return {
    citaId: 21,
    paciente: 'Laura Romero',
    profesional: 'Jorge',
    fecha: '2026-12-02',
    horaInicio: '09:45',
    horaFin: '10:30',
    servicio: 'Sesión fisioterapia',
    canceladaFecha: '2026-12-01',
    canceladaHora: '09:00',
    huecoPasado: false,
    ...parcial,
  };
}

export const FUERA_DE_PLAZO = 'Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.';
export const AVISO_ENVIADO =
  'Si tu email está registrado en Clínica Eleva, te hemos enviado un enlace para entrar. Revisa tu correo; el enlace caduca en 10 minutos. Si no te llega, llama a la clínica.';
