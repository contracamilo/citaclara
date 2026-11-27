import { useState } from 'react';
import { CircleCheck, CircleX, UserX } from 'lucide-react';
import { api, ErrorApi, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { Accion, Cita } from '../api/tipos';
import { formatearFecha } from '../formato/fechas';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { Dialogo } from '../ui/Dialogo';

interface PropsAccionesCita {
  cita: Cita;
  /**
   * Tras un cambio (o un conflicto) se llama con la cita actualizada (`citaActual` en un 409),
   * o sin argumento si no se conoce, para que la agenda se recargue (huecos liberados).
   */
  onCambiada: (cita?: Cita) => void;
}

const ORDEN: Accion[] = ['COMPLETAR', 'MARCAR_NO_ASISTIDA', 'CANCELAR'];

/** Botones de cambio de estado según las `acciones` que devuelve el API (US3, FR-021–FR-025). */
export function AccionesCita({ cita, onCambiada }: PropsAccionesCita) {
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState<Accion | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  async function aplicar(accion: Accion) {
    setEnviando(accion);
    setAviso(null);
    try {
      const actualizada = await api.cambiarEstadoCita(cita.id, accion, cita.version);
      setConfirmando(false);
      onCambiada(actualizada);
    } catch (e) {
      setConfirmando(false);
      if (e instanceof ErrorApi) {
        if (e.esSesionPerdida) return; // App vuelve al acceso; no se ha aplicado nada.
        setAviso(e.mensaje);
        onCambiada(e.citaActual);
      } else {
        setAviso(MENSAJE_ERROR_GENERICO);
      }
    } finally {
      setEnviando(null);
    }
  }

  const disponibles = ORDEN.filter((a) => cita.acciones.includes(a));

  return (
    <>
      {aviso && (
        <Aviso tipo="error" className="mt-3">
          {aviso}
        </Aviso>
      )}
      {disponibles.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {disponibles.map((accion) => {
            switch (accion) {
              case 'COMPLETAR':
                return (
                  <Boton
                    key={accion}
                    variante="secundaria"
                    ocupado={enviando === accion}
                    disabled={enviando !== null}
                    icono={<CircleCheck aria-hidden="true" className="size-4" />}
                    onClick={() => aplicar('COMPLETAR')}
                  >
                    Completada
                  </Boton>
                );
              case 'MARCAR_NO_ASISTIDA':
                return (
                  <Boton
                    key={accion}
                    variante="secundaria"
                    ocupado={enviando === accion}
                    disabled={enviando !== null}
                    icono={<UserX aria-hidden="true" className="size-4" />}
                    onClick={() => aplicar('MARCAR_NO_ASISTIDA')}
                  >
                    No asistió
                  </Boton>
                );
              case 'CANCELAR':
                return (
                  <Boton
                    key={accion}
                    variante="secundaria"
                    disabled={enviando !== null}
                    icono={<CircleX aria-hidden="true" className="size-4" />}
                    onClick={() => setConfirmando(true)}
                  >
                    Cancelar cita
                  </Boton>
                );
            }
          })}
        </div>
      )}
      <Dialogo
        abierto={confirmando}
        rol="alertdialog"
        titulo="Cancelar cita"
        onCerrar={() => setConfirmando(false)}
        pie={
          <>
            <Boton variante="secundaria" data-foco-inicial onClick={() => setConfirmando(false)} disabled={enviando !== null}>
              No, volver
            </Boton>
            <Boton variante="peligro" ocupado={enviando === 'CANCELAR'} onClick={() => aplicar('CANCELAR')}>
              Sí, cancelar
            </Boton>
          </>
        }
      >
        <p>
          ¿Cancelar la cita de {cita.paciente.nombre} del {formatearFecha(cita.fecha)} a las {cita.horaInicio}? El hueco
          quedará libre.
        </p>
      </Dialogo>
    </>
  );
}
