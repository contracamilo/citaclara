import { useCallback, useEffect, useState } from 'react';
import { PantallaAcceso } from './acceso/PantallaAcceso';
import { PantallaAgenda } from './agenda/PantallaAgenda';
import { PantallaAnalitica } from './analitica/PantallaAnalitica';
import { api, ErrorApi, EVENTO_SESION_PERDIDA, MENSAJE_ERROR_GENERICO } from './api/cliente';
import type { Contexto } from './api/tipos';
import { identificadorDeRuta, vistaDeRuta } from './navegacion';
import { Aviso } from './ui/Aviso';
import { Boton } from './ui/Boton';

type Estado =
  | { tipo: 'cargando' }
  | { tipo: 'acceso'; sesionCaducada: boolean }
  | { tipo: 'agenda'; contexto: Contexto }
  | { tipo: 'error'; mensaje: string };

export { identificadorDeRuta, vistaDeRuta };

/**
 * Raíz de la aplicación, sin enrutador ni estado global (research D15): la vista depende de la
 * sesión; el profesional y el día viven en la query string (PantallaAgenda).
 */
export function App() {
  const identificador = identificadorDeRuta(window.location.pathname);
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
