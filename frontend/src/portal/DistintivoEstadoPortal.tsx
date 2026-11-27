import { CalendarClock, CircleCheck, CircleX, Hourglass, UserX } from 'lucide-react';
import type { EstadoPortal } from '../api/tipos';

/** Textos visibles de cada estado para el paciente (contracts/ui.md §3, FR-009). */
export const TEXTO_ESTADO_PORTAL: Record<EstadoPortal, string> = {
  RESERVADA: 'Reservada',
  PENDIENTE_CIERRE: 'Pendiente de cierre por la clínica',
  REALIZADA: 'Realizada',
  NO_ACUDISTE: 'No acudiste',
  CANCELADA: 'Cancelada',
};

const ESTILO: Record<EstadoPortal, { clases: string; Icono: typeof CircleCheck }> = {
  RESERVADA: { clases: 'bg-reservada-fondo text-reservada', Icono: CalendarClock },
  PENDIENTE_CIERRE: { clases: 'bg-info-suave text-info', Icono: Hourglass },
  REALIZADA: { clases: 'bg-completada-fondo text-completada', Icono: CircleCheck },
  NO_ACUDISTE: { clases: 'bg-no-asistida-fondo text-no-asistida', Icono: UserX },
  CANCELADA: { clases: 'bg-cancelada-fondo text-cancelada', Icono: CircleX },
};

/** Estado con color + icono + texto; nunca solo color (FR-024). */
export function DistintivoEstadoPortal({ estado }: { estado: EstadoPortal }) {
  const { clases, Icono } = ESTILO[estado];
  return (
    <span
      data-estado={estado}
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${clases}`}
    >
      <Icono aria-hidden="true" className="size-3.5 shrink-0" />
      {TEXTO_ESTADO_PORTAL[estado]}
    </span>
  );
}
