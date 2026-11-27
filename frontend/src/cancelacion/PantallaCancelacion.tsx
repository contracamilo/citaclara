import { useCallback, useEffect, useState } from 'react';
import { CalendarCheck, CalendarX, Phone } from 'lucide-react';
import { api, ErrorApi, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { Cancelacion } from '../api/tipos';
import { formatearFechaLarga } from '../formato/fechas';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';

type Estado =
  | { tipo: 'cargando' }
  | { tipo: 'cita'; cancelacion: Cancelacion }
  | { tipo: 'no-valido' }
  | { tipo: 'error'; mensaje: string };

interface PropsPantallaCancelacion {
  enlace: string;
}

/** «lunes 30/11/2026 · 10:00» (sin la coma del formato largo de la agenda). */
function cuando(c: Cancelacion): string {
  return `${formatearFechaLarga(c.cita.fecha).replace(',', '')} · ${c.cita.horaInicio}`;
}

function Telefono({ telefono }: { telefono: string }) {
  return (
    <a
      href={`tel:${telefono.replace(/\s/g, '')}`}
      className="inline-flex min-h-11 min-w-11 items-center gap-2 rounded-xl px-2 font-semibold text-acento underline"
    >
      <Phone aria-hidden="true" className="size-4" />
      {telefono}
    </a>
  );
}

function Resumen({ cancelacion }: { cancelacion: Cancelacion }) {
  return (
    <dl className="mt-4 space-y-1 rounded-tarjeta border border-borde bg-superficie p-4 text-base">
      <div>
        <dt className="sr-only">Servicio</dt>
        <dd className="font-semibold break-words">{cancelacion.cita.servicio}</dd>
      </div>
      <div>
        <dt className="sr-only">Profesional</dt>
        <dd className="break-words text-texto-suave">con {cancelacion.cita.profesional}</dd>
      </div>
      <div>
        <dt className="sr-only">Día y hora</dt>
        <dd className="numeros font-medium">{cuando(cancelacion)}</dd>
      </div>
    </dl>
  );
}

/**
 * Pantalla pública del enlace del recordatorio (contracts/ui.md, 003). Sin sesión: abrirla solo
 * consulta (GET); únicamente «Sí, cancelar mi cita» llama al POST (FR-012).
 */
export function PantallaCancelacion({ enlace }: PropsPantallaCancelacion) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' });
  const [enviando, setEnviando] = useState(false);
  const [mantenida, setMantenida] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null);

  const cargar = useCallback(async () => {
    setEstado({ tipo: 'cargando' });
    try {
      setEstado({ tipo: 'cita', cancelacion: await api.consultarCancelacion(enlace) });
    } catch (e) {
      setEstado(aEstadoError(e));
    }
  }, [enlace]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  async function confirmar() {
    setEnviando(true);
    setErrorEnvio(null);
    try {
      setEstado({ tipo: 'cita', cancelacion: await api.confirmarCancelacion(enlace) });
    } catch (e) {
      if (e instanceof ErrorApi && e.cancelacion) {
        // 409 (otra persona cambió la cita) y 422 (fuera de plazo): se muestra la situación real.
        setEstado({ tipo: 'cita', cancelacion: e.cancelacion });
      } else if (e instanceof ErrorApi && e.estadoHttp === 404) {
        setEstado({ tipo: 'no-valido' });
      } else {
        setErrorEnvio(e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO);
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col justify-center gap-2 px-4 py-8">
      {estado.tipo === 'cargando' && (
        <p role="status" aria-busy="true" className="text-center text-texto-suave">
          Estamos buscando tu cita…
        </p>
      )}

      {estado.tipo === 'no-valido' && (
        <>
          <h1 className="text-2xl font-bold">No hemos encontrado lo que buscas.</h1>
          <p className="text-texto-suave">Revisa que has abierto el enlace completo del correo.</p>
        </>
      )}

      {estado.tipo === 'error' && (
        <>
          <Aviso tipo="error">{estado.mensaje}</Aviso>
          <Boton variante="primaria" onClick={() => void cargar()}>
            Reintentar
          </Boton>
        </>
      )}

      {estado.tipo === 'cita' && vistaCita(estado.cancelacion)}
    </main>
  );

  /** Función (no componente anidado): así no se remonta en cada render y conserva su estado. */
  function vistaCita(cancelacion: Cancelacion) {
    const { situacion, canceladaPorPaciente, clinica } = cancelacion;
    const cabecera = <p className="text-sm font-semibold text-texto-suave">{clinica.nombre}</p>;

    if (situacion === 'YA_CERRADA' && canceladaPorPaciente) {
      return (
        <>
          {cabecera}
          <h1 className="flex items-center gap-2 text-2xl font-bold">
            <CalendarCheck aria-hidden="true" className="size-6 shrink-0 text-exito" />
            Tu cita está cancelada
          </h1>
          <Resumen cancelacion={cancelacion} />
          <p className="mt-2 text-texto-suave">Gracias por avisarnos. Si quieres otra cita, llama a la clínica al</p>
          <Telefono telefono={clinica.telefono} />
        </>
      );
    }

    if (situacion === 'YA_CERRADA') {
      return (
        <>
          {cabecera}
          <h1 className="text-2xl font-bold">Esta cita ya no está activa.</h1>
          <p className="text-texto-suave">Si tienes dudas, llama a la clínica al</p>
          <Telefono telefono={clinica.telefono} />
        </>
      );
    }

    if (situacion === 'FUERA_DE_PLAZO') {
      return (
        <>
          {cabecera}
          <h1 className="flex items-center gap-2 text-2xl font-bold">
            <CalendarX aria-hidden="true" className="size-6 shrink-0 text-peligro" />
            Ya no puedes cancelar desde aquí.
          </h1>
          <Resumen cancelacion={cancelacion} />
          <p className="mt-2 text-texto-suave">Faltan menos de 24 horas para tu cita. Llama a la clínica al</p>
          <Telefono telefono={clinica.telefono} />
        </>
      );
    }

    // ACTIVA
    return (
      <>
        {cabecera}
        <h1 className="text-2xl font-bold">¿Cancelamos tu cita?</h1>
        <Resumen cancelacion={cancelacion} />
        {mantenida ? (
          <Aviso tipo="exito" className="mt-3">
            Tu cita se mantiene. ¡Te esperamos!
          </Aviso>
        ) : (
          <>
            <p className="mt-2 text-texto-suave">Al cancelar dejamos el hueco libre para otra persona.</p>
            {errorEnvio && <Aviso tipo="error">{errorEnvio}</Aviso>}
            <div className="mt-3 flex flex-col gap-2">
              <Boton variante="peligro" ocupado={enviando} onClick={() => void confirmar()}>
                Sí, cancelar mi cita
              </Boton>
              <Boton variante="secundaria" disabled={enviando} onClick={() => setMantenida(true)}>
                No, mantener mi cita
              </Boton>
            </div>
          </>
        )}
      </>
    );
  }
}

function aEstadoError(e: unknown): Estado {
  if (e instanceof ErrorApi && e.estadoHttp === 404) return { tipo: 'no-valido' };
  return { tipo: 'error', mensaje: e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO };
}
