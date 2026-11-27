import type { Cita } from '../api/tipos';
import { formatearEuros } from '../formato/euros';
import { formatearFranja } from '../formato/fechas';
import { DistintivoEstado } from '../ui/DistintivoEstado';
import { AccionesCita } from './AccionesCita';

interface PropsTarjetaCita {
  cita: Cita;
  resaltada?: boolean;
  /** Se llama tras un cambio de estado (con la cita actualizada si se conoce) para recargar la agenda. */
  onCambioEstado?: (cita?: Cita) => void;
}

/** Tarjeta de cita de la línea de tiempo (FR-006, US1-AC4, US1-AC5). */
export function TarjetaCita({ cita, resaltada = false, onCambioEstado }: PropsTarjetaCita) {
  const atenuada = cita.estado === 'CANCELADA' || cita.estado === 'NO_ASISTIDA';
  const cancelada = cita.estado === 'CANCELADA';
  return (
    <article
      data-atenuada={atenuada}
      aria-label={`Cita ${formatearFranja(cita.horaInicio, cita.horaFin)} · ${cita.paciente.nombre}`}
      className={`rounded-tarjeta border p-4 ${
        atenuada ? 'border-borde bg-superficie-2 text-texto-suave' : 'border-borde bg-superficie shadow-suave'
      } ${resaltada ? 'resaltada' : ''}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-2">
        <p className={`numeros text-base font-semibold ${cancelada ? 'line-through' : ''}`}>
          {formatearFranja(cita.horaInicio, cita.horaFin)}
        </p>
        <DistintivoEstado estado={cita.estado} />
      </div>
      <p className={`mt-1 text-base font-semibold break-words ${atenuada ? '' : 'text-texto'} ${cancelada ? 'line-through' : ''}`}>
        {cita.paciente.nombre}
      </p>
      <p className="mt-0.5 flex flex-wrap justify-between gap-x-3 text-sm text-texto-suave">
        <span className="break-words">{cita.servicio.nombre}</span>
        <span className="numeros font-medium">{formatearEuros(cita.importeCentimos)}</span>
      </p>
      <AccionesCita cita={cita} onCambiada={(c) => onCambioEstado?.(c)} />
    </article>
  );
}
