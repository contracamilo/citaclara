import { useEffect, useState } from 'react';
import { Bell } from 'lucide-react';
import { apiCancelaciones, esErrorAbort } from '../api/cliente';
import type { CancelacionPortal } from '../api/tipos';
import { DialogoCancelacionesPortal } from './DialogoCancelacionesPortal';

/** «1 cancelación de pacientes por revisar» / «3 cancelaciones de pacientes por revisar». */
export function textoAviso(pendientes: number): string {
  return pendientes === 1
    ? '1 cancelación de pacientes por revisar'
    : `${pendientes} cancelaciones de pacientes por revisar`;
}

/**
 * Aviso en la cabecera del panel con las cancelaciones del portal pendientes (US4, FR-019). Se vuelve
 * a pedir cada vez que cambia `clave` (profesional o día, SC-008); no hay tiempo real.
 */
export function AvisoCancelaciones({ clave }: { clave: string }) {
  const [cancelaciones, setCancelaciones] = useState<CancelacionPortal[]>([]);
  const [abierto, setAbierto] = useState(false);

  useEffect(() => {
    const control = new AbortController();
    apiCancelaciones
      .obtener(control.signal)
      .then((r) => setCancelaciones(r.cancelaciones))
      .catch((e: unknown) => {
        if (!esErrorAbort(e)) setCancelaciones([]); // el aviso nunca bloquea la agenda
      });
    return () => control.abort();
  }, [clave]);

  const pendientes = cancelaciones.length;
  return (
    <>
      {pendientes > 0 && (
        <button
          type="button"
          onClick={() => setAbierto(true)}
          aria-label={textoAviso(pendientes)}
          className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1.5 rounded-xl bg-acento-suave px-3 text-sm font-semibold text-acento hover:bg-superficie-2"
        >
          <Bell aria-hidden="true" className="size-4 shrink-0" />
          <span aria-hidden="true" className="sm:hidden">
            {pendientes}
          </span>
          <span aria-hidden="true" className="hidden sm:inline">
            {textoAviso(pendientes)}
          </span>
        </button>
      )}
      <DialogoCancelacionesPortal
        abierto={abierto}
        cancelaciones={cancelaciones}
        onCerrar={() => setAbierto(false)}
        onRevisada={(citaId) => setCancelaciones((actuales) => actuales.filter((c) => c.citaId !== citaId))}
      />
    </>
  );
}
