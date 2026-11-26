import { CalendarClock, CircleCheck, CircleX, UserX } from 'lucide-react';
import type { Estado } from '../api/tipos';

export const TEXTO_ESTADO: Record<Estado, string> = {
  RESERVADA: 'Reservada',
  COMPLETADA: 'Completada',
  CANCELADA: 'Cancelada',
  NO_ASISTIDA: 'No asistida',
};

const ESTILO: Record<Estado, { clases: string; Icono: typeof CircleCheck }> = {
  RESERVADA: { clases: 'bg-reservada-fondo text-reservada', Icono: CalendarClock },
  COMPLETADA: { clases: 'bg-completada-fondo text-completada', Icono: CircleCheck },
  CANCELADA: { clases: 'bg-cancelada-fondo text-cancelada', Icono: CircleX },
  NO_ASISTIDA: { clases: 'bg-no-asistida-fondo text-no-asistida', Icono: UserX },
};

/** Estado de una cita con color + icono + texto; nunca solo color (FR-006, FR-028). */
export function DistintivoEstado({ estado }: { estado: Estado }) {
  const { clases, Icono } = ESTILO[estado];
  return (
    <span
      data-estado={estado}
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold whitespace-nowrap ${clases}`}
    >
      <Icono aria-hidden="true" className="size-3.5" />
      {TEXTO_ESTADO[estado]}
    </span>
  );
}
