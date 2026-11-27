import { useCallback, useEffect, useState } from 'react';
import { LogOut } from 'lucide-react';
import { api, ErrorApi, esErrorAbort } from '../api/cliente';
import type { Analitica, Contexto } from '../api/tipos';
import { formatearFecha } from '../formato/fechas';
import { rutaAgenda } from '../navegacion';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { Evolucion } from './Evolucion';
import { IngresosPorServicio } from './IngresosPorServicio';
import { ResumenProfesionales } from './ResumenProfesionales';

interface Props {
  contexto: Contexto;
  identificador: string;
  onSalir: () => void;
}

/** Solo si el fallo no trae mensaje del API. */
const MENSAJE_ERROR_CARGA = 'No se ha podido cargar la analítica. Inténtalo de nuevo.';

type Estado =
  | { tipo: 'cargando' }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'listo'; analitica: Analitica };

/** Analítica de la clínica, de solo lectura (specs/004-panel-analitica, contracts/ui.md). */
export function PantallaAnalitica({ contexto, identificador, onSalir }: Props) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' });
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    const control = new AbortController();
    setEstado({ tipo: 'cargando' });
    api
      .obtenerAnalitica(control.signal)
      .then((analitica) => setEstado({ tipo: 'listo', analitica }))
      .catch((e: unknown) => {
        if (esErrorAbort(e)) return;
        // Una sesión perdida la gestiona App (evento global): aquí no se muestra nada.
        if (e instanceof ErrorApi && e.esSesionPerdida) return;
        setEstado({ tipo: 'error', mensaje: e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_CARGA });
      });
    return () => control.abort();
  }, [intento]);

  const cerrarSesion = useCallback(() => {
    api
      .cerrarSesion()
      .catch(() => undefined)
      .finally(onSalir);
  }, [onSalir]);

  const periodo =
    estado.tipo === 'listo' && estado.analitica.evolucion.length > 0
      ? `Últimas 8 semanas: del ${formatearFecha(estado.analitica.evolucion[0]!.semana.inicio)} al ${formatearFecha(
          estado.analitica.evolucion[estado.analitica.evolucion.length - 1]!.semana.fin,
        )}`
      : null;

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-borde bg-superficie/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 pt-2">
          <p className="min-w-0 truncate text-base font-bold">{contexto.clinica.nombre}</p>
          <Boton variante="fantasma" icono={<LogOut aria-hidden="true" className="size-4" />} onClick={cerrarSesion}>
            Cerrar sesión
          </Boton>
        </div>
        <nav aria-label="Secciones del panel" className="mx-auto flex max-w-3xl gap-2 px-4 pt-1 pb-3">
          <a
            href={rutaAgenda(identificador)}
            className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl px-4 text-sm font-semibold hover:bg-superficie-2"
          >
            Agenda
          </a>
          <a
            href="#"
            aria-current="page"
            onClick={(e) => e.preventDefault()}
            className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl bg-acento-suave px-4 text-sm font-semibold"
          >
            Analítica
          </a>
        </nav>
      </header>

      <main className="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-4" aria-busy={estado.tipo === 'cargando'}>
        <div>
          <h1 className="text-xl font-bold">Analítica de la clínica</h1>
          {periodo && <p className="numeros text-sm text-texto-suave">{periodo}</p>}
        </div>

        {estado.tipo === 'cargando' && <p className="p-6 text-center text-texto-suave">Cargando la analítica…</p>}

        {estado.tipo === 'error' && (
          <div className="flex flex-col items-start gap-3">
            <Aviso tipo="error">{estado.mensaje}</Aviso>
            <Boton variante="primaria" onClick={() => setIntento((n) => n + 1)}>
              Reintentar
            </Boton>
          </div>
        )}

        {estado.tipo === 'listo' && estado.analitica.sinDatos && (
          <Aviso tipo="info">
            Aún no hay citas cerradas en las últimas 8 semanas. Cuando la clínica complete citas, aquí verás su evolución.
          </Aviso>
        )}

        {estado.tipo === 'listo' && !estado.analitica.sinDatos && (
          <>
            <ResumenProfesionales semana={estado.analitica.ultimaSemana.semana} profesionales={estado.analitica.ultimaSemana.profesionales} />
            <IngresosPorServicio ingresos={estado.analitica.ingresos} />
            <Evolucion
              evolucion={estado.analitica.evolucion}
              profesionales={estado.analitica.ultimaSemana.profesionales.map((p) => ({ profesionalId: p.profesionalId, nombre: p.nombre }))}
              totalIngresosCentimos={estado.analitica.ingresos.totalCentimos}
            />
          </>
        )}
      </main>
    </div>
  );
}
