import { useCallback, useEffect, useState } from 'react';
import { PantallaAcceso } from './acceso/PantallaAcceso';
import { PantallaAgenda } from './agenda/PantallaAgenda';
import { PantallaAnalitica } from './analitica/PantallaAnalitica';
import { PantallaCancelacion } from './cancelacion/PantallaCancelacion';
import { api, ErrorApi, EVENTO_SESION_PERDIDA, MENSAJE_ERROR_GENERICO } from './api/cliente';
import type { Contexto } from './api/tipos';
import { identificadorDeRuta, vistaDeRuta } from './navegacion';
import { Aviso } from './ui/Aviso';
import { Boton } from './ui/Boton';
import { AppPortal } from './portal/AppPortal';

type Estado =
  | { tipo: 'cargando' }
  | { tipo: 'acceso'; sesionCaducada: boolean }
  | { tipo: 'agenda'; contexto: Contexto }
  | { tipo: 'error'; mensaje: string };

export { identificadorDeRuta, vistaDeRuta };

/** `/eleva/mis-citas` → portal del paciente (002, research R10). */
export function esRutaPortal(ruta: string): boolean {
  return ruta.split('/').filter(Boolean)[1] === 'mis-citas';
}

/** `/cancelar/{enlace}` → el enlace del recordatorio (003); en otro caso, nulo. */
export function enlaceDeCancelacion(ruta: string): string | null {
  const [primero, enlace] = ruta.split('/').filter(Boolean);
  if (primero !== 'cancelar' || !enlace) return null;
  try {
    return decodeURIComponent(enlace);
  } catch {
    return enlace;
  }
}

/**
 * Raíz de la aplicación, sin enrutador ni estado global (research D15): la vista depende de la
 * sesión; el profesional y el día viven en la query string (PantallaAgenda). La dirección
 * `/{identificador}/mis-citas` monta el portal del paciente (002), con su propia sesión.
 */
export function App() {
  // Pantalla pública del paciente (003): no usa la sesión de recepción ni el contexto de clínica.
  const enlace = enlaceDeCancelacion(window.location.pathname);
  return enlace ? <PantallaCancelacion enlace={enlace} /> : <AppRecepcion />;
}

function AppRecepcion() {
  const identificador = identificadorDeRuta(window.location.pathname);
  if (identificador && esRutaPortal(window.location.pathname)) {
    return <AppPortal identificador={identificador} />;
  }
  return <Panel identificador={identificador} />;
}

function Panel({ identificador }: { identificador: string }) {
  const vista = vistaDeRuta(window.location.pathname);
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' });

  const cargarContexto = useCallback(async () => {
    try {
      // En la carga inicial un SIN_SESION lleva al acceso sin aviso; un SESION_CADUCADA, con aviso.
      const contexto = await api.obtenerContexto({ avisarSesionPerdida: false });
      setEstado({ tipo: 'agenda', contexto });
    } catch (e) {
      if (e instanceof ErrorApi && e.esSesionPerdida) {
        setEstado({ tipo: 'acceso', sesionCaducada: e.codigo === 'SESION_CADUCADA' });
      } else {
        setEstado({ tipo: 'error', mensaje: e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO });
      }
    }
  }, []);

  useEffect(() => {
    if (identificador) void cargarContexto();
  }, [identificador, cargarContexto]);

  // FR-002: un 401 de sesión en cualquier llamada devuelve a la pantalla de acceso con el aviso.
  useEffect(() => {
    const alPerderSesion = () => setEstado({ tipo: 'acceso', sesionCaducada: true });
    window.addEventListener(EVENTO_SESION_PERDIDA, alPerderSesion);
    return () => window.removeEventListener(EVENTO_SESION_PERDIDA, alPerderSesion);
  }, []);

  if (!identificador) {
    return (
      <main className="flex min-h-dvh items-center justify-center px-4">
        <p className="max-w-sm text-center text-lg">Abre la dirección del panel de tu clínica para entrar.</p>
      </main>
    );
  }

  switch (estado.tipo) {
    case 'cargando':
      return (
        <main className="flex min-h-dvh items-center justify-center px-4" aria-busy="true">
          <p className="text-texto-suave">Cargando…</p>
        </main>
      );
    case 'error':
      return (
        <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-4">
          <Aviso tipo="error">{estado.mensaje}</Aviso>
          <Boton
            variante="primaria"
            onClick={() => {
              setEstado({ tipo: 'cargando' });
              void cargarContexto();
            }}
          >
            Reintentar
          </Boton>
        </main>
      );
    case 'acceso':
      return (
        <PantallaAcceso
          identificador={identificador}
          sesionCaducada={estado.sesionCaducada}
          onEntrado={() => {
            setEstado({ tipo: 'cargando' });
            void cargarContexto();
          }}
        />
      );
    case 'agenda':
      return vista === 'analitica' ? (
        <PantallaAnalitica
          contexto={estado.contexto}
          identificador={identificador}
          onSalir={() => setEstado({ tipo: 'acceso', sesionCaducada: false })}
        />
      ) : (
        <PantallaAgenda
          contexto={estado.contexto}
          identificador={identificador}
          onSalir={() => setEstado({ tipo: 'acceso', sesionCaducada: false })}
        />
      );
  }
}
