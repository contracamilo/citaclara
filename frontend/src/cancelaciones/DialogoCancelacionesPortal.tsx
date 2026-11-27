import { useState } from 'react';
import { apiCancelaciones, ErrorApi, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { CancelacionPortal } from '../api/tipos';
import { formatearFecha, formatearFechaLarga } from '../formato/fechas';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { Dialogo } from '../ui/Dialogo';

interface PropsDialogo {
  abierto: boolean;
  cancelaciones: CancelacionPortal[];
  onCerrar: () => void;
  onRevisada: (citaId: number) => void;
}

/** Lista de cancelaciones del portal pendientes de revisar (US4-AC2, AC3, AC6; contracts/ui.md §5). */
export function DialogoCancelacionesPortal({ abierto, cancelaciones, onCerrar, onRevisada }: PropsDialogo) {
  const [revisando, setRevisando] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function revisar(citaId: number) {
    setRevisando(citaId);
    setError(null);
    try {
      await apiCancelaciones.marcarRevisada(citaId);
      onRevisada(citaId);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO);
    } finally {
      setRevisando(null);
    }
  }

  return (
    <Dialogo abierto={abierto} titulo="Cancelaciones de pacientes" onCerrar={onCerrar}>
      <div className="flex flex-col gap-3">
        {error && <Aviso tipo="error">{error}</Aviso>}
        {cancelaciones.length === 0 ? (
          <p className="text-sm text-texto-suave">No hay cancelaciones pendientes.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {cancelaciones.map((c) => (
              <li key={c.citaId} className="flex flex-col gap-1.5 rounded-xl border border-borde p-3">
                <p className="font-semibold">
                  {c.paciente} · {c.servicio}
                </p>
                <p className="text-sm tabular-nums">
                  {c.profesional} · {formatearFechaLarga(c.fecha)} · {c.horaInicio}–{c.horaFin}
                </p>
                <p className="text-sm text-texto-suave">
                  Cancelada el {formatearFecha(c.canceladaFecha)} a las {c.canceladaHora}
                </p>
                {c.huecoPasado && (
                  <span className="self-start rounded-full bg-cancelada-fondo px-2.5 py-1 text-xs font-semibold text-cancelada">
                    Hueco ya pasado
                  </span>
                )}
                <Boton
                  variante="secundaria"
                  className="self-start"
                  ocupado={revisando === c.citaId}
                  onClick={() => void revisar(c.citaId)}
                >
                  Marcar como revisada
                </Boton>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Dialogo>
  );
}
