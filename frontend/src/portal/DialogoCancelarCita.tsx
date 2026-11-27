import { useState } from 'react';
import { apiPortal, ErrorApi, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { CitaPortal } from '../api/tipos';
import { formatearFechaLarga } from '../formato/fechas';
import { Boton } from '../ui/Boton';
import { Dialogo } from '../ui/Dialogo';

export type ResultadoCancelacion =
  | { tipo: 'cancelada'; cita: CitaPortal }
  | { tipo: 'no-cancelada'; mensaje: string };

interface PropsDialogo {
  cita: CitaPortal | null;
  onCerrar: () => void;
  onResultado: (resultado: ResultadoCancelacion) => void;
}

/** Confirmación de la cancelación (US3-AC1–AC3, AC5–AC7; contracts/ui.md §3). */
export function DialogoCancelarCita({ cita, onCerrar, onResultado }: PropsDialogo) {
  const [enviando, setEnviando] = useState(false);

  async function confirmar() {
    if (!cita || enviando) return;
    setEnviando(true);
    try {
      const cancelada = await apiPortal.cancelarCita(cita.id, cita.version);
      onResultado({ tipo: 'cancelada', cita: cancelada });
    } catch (e) {
      if (e instanceof ErrorApi && e.esSesionPortalPerdida) return; // AppPortal ya lleva a la entrada
      onResultado({ tipo: 'no-cancelada', mensaje: e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialogo
      abierto={cita !== null}
      titulo="Cancelar cita"
      rol="alertdialog"
      onCerrar={() => !enviando && onCerrar()}
      pie={
        <>
          <Boton variante="secundaria" onClick={onCerrar} disabled={enviando} data-foco-inicial>
            Volver
          </Boton>
          <Boton variante="peligro" ocupado={enviando} onClick={() => void confirmar()}>
            Sí, cancelar
          </Boton>
        </>
      }
    >
      {cita && (
        <p>
          ¿Cancelar tu cita del {formatearFechaLarga(cita.fecha)} a las {cita.horaInicio} con {cita.profesional}?
        </p>
      )}
    </Dialogo>
  );
}
