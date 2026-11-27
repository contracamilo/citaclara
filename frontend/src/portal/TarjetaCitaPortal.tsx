import type { CitaPortal } from '../api/tipos';
import { formatearEuros } from '../formato/euros';
import { formatearFechaLarga } from '../formato/fechas';
import { Boton } from '../ui/Boton';
import { DistintivoEstadoPortal } from './DistintivoEstadoPortal';

interface PropsTarjeta {
  cita: CitaPortal;
  /** Con varias fichas con el mismo email, cada tarjeta dice de quién es la cita. */
  mostrarPaciente: boolean;
  onCancelar?: (cita: CitaPortal) => void;
}

/** «10:15–11:15» (contracts/ui.md §3). */
export const franjaPortal = (cita: Pick<CitaPortal, 'horaInicio' | 'horaFin'>) => `${cita.horaInicio}–${cita.horaFin}`;

/** Cita del paciente a todo el ancho (FR-009, FR-024). */
export function TarjetaCitaPortal({ cita, mostrarPaciente, onCancelar }: PropsTarjeta) {
  return (
    <article
      data-cita={cita.id}
      aria-label={`Cita del ${formatearFechaLarga(cita.fecha)} a las ${cita.horaInicio}`}
      className="flex flex-col gap-2 rounded-tarjeta border border-borde bg-superficie p-4 shadow-suave"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold">{formatearFechaLarga(cita.fecha)}</p>
          <p className="text-lg font-bold tabular-nums">{franjaPortal(cita)}</p>
        </div>
        <DistintivoEstadoPortal estado={cita.estadoPortal} />
      </div>
      {mostrarPaciente && <p className="text-sm font-semibold text-acento">{cita.paciente}</p>}
      <p className="text-sm">
        <span className="font-medium">{cita.servicio}</span> <span className="text-texto-suave">con {cita.profesional}</span>
      </p>
      <p className="text-sm tabular-nums text-texto-suave">{formatearEuros(cita.importeCentimos)}</p>
      {cita.motivoNoCancelable && <p className="text-sm text-texto-suave">{cita.motivoNoCancelable}</p>}
      {cita.cancelable && onCancelar && (
        <Boton variante="secundaria" className="self-start" onClick={() => onCancelar(cita)}>
          Cancelar cita
        </Boton>
      )}
    </article>
  );
}
