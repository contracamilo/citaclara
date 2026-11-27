import { useCallback, useEffect, useState } from 'react';
import { apiPortal, ErrorApi, EVENTO_SESION_PORTAL_PERDIDA, MENSAJE_ERROR_GENERICO, type DetalleSesionPerdida } from '../api/cliente';
import type { MisCitas } from '../api/tipos';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { consumirEnlace, olvidarEnlace, tomarEnlace } from './enlace';
import { PantallaEntradaPortal } from './PantallaEntradaPortal';
import { PantallaMisCitas } from './PantallaMisCitas';

type Estado =
  | { tipo: 'cargando'; texto: string }
  | { tipo: 'entrada'; aviso: { tipo: 'info' | 'error'; texto: string } | null }
  | { tipo: 'mis-citas'; datos: MisCitas }
  | { tipo: 'error'; mensaje: string };

const ENTRANDO = 'Entrando en tus citas…';

/**
 * Portal del paciente en `/{identificador}/mis-citas` (002, research R10). Sin enrutador: la vista
 * depende de si hay sesión del portal. Un enlace `#enlace=…` abre la sesión (US1-AC2).
 */
export function AppPortal({ identificador }: { identificador: string }) {
  const [estado, setEstado] = useState<Estado>(() => ({ tipo: 'cargando', texto: tomarEnlace() ? ENTRANDO : 'Cargando…' }));

  const cargarCitas = useCallback(async () => {
    try {
      const datos = await apiPortal.obtenerMisCitas({ avisarSesionPerdida: false });
      setEstado({ tipo: 'mis-citas', datos });
    } catch (e) {
      if (e instanceof ErrorApi && e.esSesionPortalPerdida) {
        setEstado({
          tipo: 'entrada',
          aviso: e.codigo === 'SESION_PORTAL_CADUCADA' ? { tipo: 'info', texto: e.mensaje } : null,
        });
      } else {
        setEstado({ tipo: 'error', mensaje: e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO });
      }
    }
  }, []);

  useEffect(() => {
    let vigente = true;
    const secreto = tomarEnlace();
    if (!secreto) {
      void cargarCitas();
      return;
    }
    consumirEnlace(identificador, secreto)
      .then(() => {
        olvidarEnlace();
        if (vigente) void cargarCitas();
      })
      .catch((e: unknown) => {
        olvidarEnlace();
        if (!vigente) return;
        setEstado({ tipo: 'entrada', aviso: { tipo: 'error', texto: e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO } });
      });
    return () => {
      vigente = false;
    };
  }, [identificador, cargarCitas]);

  // Un enlace pegado en la misma pestaña solo cambia el fragmento (sin recargar): se recarga para leerlo.
  useEffect(() => {
    const alCambiarFragmento = () => {
      if (/(?:^#|&)enlace=/.test(window.location.hash)) window.location.reload();
    };
    window.addEventListener('hashchange', alCambiarFragmento);
    return () => window.removeEventListener('hashchange', alCambiarFragmento);
  }, []);

  // US1-AC5: un 401 de la sesión del portal en cualquier acción lleva a la entrada con el aviso.
  useEffect(() => {
    const alPerder = (e: Event) => {
      const detalle = (e as CustomEvent<DetalleSesionPerdida>).detail;
      setEstado({
        tipo: 'entrada',
        aviso: detalle.codigo === 'SESION_PORTAL_CADUCADA' ? { tipo: 'info', texto: detalle.mensaje } : null,
      });
    };
    window.addEventListener(EVENTO_SESION_PORTAL_PERDIDA, alPerder);
    return () => window.removeEventListener(EVENTO_SESION_PORTAL_PERDIDA, alPerder);
  }, []);

  switch (estado.tipo) {
    case 'cargando':
      return (
        <main className="flex min-h-dvh items-center justify-center px-4" aria-busy="true">
          <p className="text-texto-suave">{estado.texto}</p>
        </main>
      );
    case 'error':
      return (
        <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-4">
          <Aviso tipo="error">{estado.mensaje}</Aviso>
          <Boton
            variante="primaria"
            onClick={() => {
              setEstado({ tipo: 'cargando', texto: 'Cargando…' });
              void cargarCitas();
            }}
          >
            Reintentar
          </Boton>
        </main>
      );
    case 'entrada':
      return <PantallaEntradaPortal identificador={identificador} aviso={estado.aviso} />;
    case 'mis-citas':
      return (
        <PantallaMisCitas
          datos={estado.datos}
          onRecargar={cargarCitas}
          onSalir={() => setEstado({ tipo: 'entrada', aviso: null })}
        />
      );
  }
}
