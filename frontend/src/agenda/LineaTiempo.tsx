import type { Agenda, Cita, Hueco } from '../api/tipos';
import { HuecoLibre } from './HuecoLibre';
import { TarjetaCita } from './TarjetaCita';

interface PropsLineaTiempo {
  agenda: Agenda;
  resaltadaId?: number | null;
  onReservar?: (horaInicio: string) => void;
  onCambioEstado?: (cita?: Cita) => void;
}

type Elemento = { tipo: 'cita'; cita: Cita } | { tipo: 'hueco'; hueco: Hueco };

/** Línea de tiempo vertical que intercala citas y huecos libres en orden cronológico (FR-007). */
export function LineaTiempo({ agenda, resaltadaId, onReservar, onCambioEstado }: PropsLineaTiempo) {
  if (!agenda.horario) {
    return (
      <p className="rounded-tarjeta border border-borde bg-superficie p-6 text-center text-texto-suave">
        La clínica no atiende este día.
      </p>
    );
  }

  const elementos: Elemento[] = [
    ...agenda.citas.map((cita) => ({ tipo: 'cita' as const, cita })),
    ...agenda.huecos.map((hueco) => ({ tipo: 'hueco' as const, hueco })),
  ];
  const inicio = (e: Elemento) => (e.tipo === 'cita' ? e.cita.horaInicio : e.hueco.horaInicio);
  // Las horas «HH:mm» se ordenan bien como texto; a igual hora, la cita va antes que el hueco.
  elementos.sort((a, b) => inicio(a).localeCompare(inicio(b)) || (a.tipo === 'cita' ? -1 : 1) - (b.tipo === 'cita' ? -1 : 1));

  if (elementos.length === 0) {
    return <p className="p-6 text-center text-texto-suave">No hay citas ni huecos libres este día.</p>;
  }

  return (
    <ol className="flex flex-col gap-3" aria-label="Agenda del día">
      {elementos.map((e) =>
        e.tipo === 'cita' ? (
          <li key={`c-${e.cita.id}`}>
            <TarjetaCita cita={e.cita} resaltada={e.cita.id === resaltadaId} onCambioEstado={onCambioEstado} />
          </li>
        ) : (
          <li key={`h-${e.hueco.horaInicio}`}>
            <HuecoLibre hueco={e.hueco} onReservar={onReservar} />
          </li>
        ),
      )}
    </ol>
  );
}
