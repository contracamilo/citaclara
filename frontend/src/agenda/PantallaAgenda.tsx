import { useCallback, useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { LogOut, Plus } from 'lucide-react';
import { AvisoCancelaciones } from '../cancelaciones/AvisoCancelaciones';
import { api, ErrorApi, esErrorAbort, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { Agenda, Cita, Contexto } from '../api/tipos';
import { DialogoAltaCita } from '../citas/DialogoAltaCita';
import { esFechaValida } from '../formato/fechas';
import { rutaAnalitica } from '../navegacion';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { LineaTiempo } from './LineaTiempo';
import { NavegadorDia } from './NavegadorDia';
import { SelectorProfesional } from './SelectorProfesional';

interface PropsPantallaAgenda {
  contexto: Contexto;
  /** Identificador de la clínica de la ruta; habilita el enlace «Analítica». */
  identificador?: string;
  onSalir: () => void;
}

interface Vista {
  profesionalId: number | null;
  /** `null` = hoy según el servidor (US1-AC3). */
  fecha: string | null;
}

/** Lee `?profesional={id}&fecha=AAAA-MM-DD`; si no indica un profesional válido, el primero. */
function leerVista(contexto: Contexto): Vista {
  const params = new URLSearchParams(window.location.search);
  const id = Number(params.get('profesional'));
  const fecha = params.get('fecha');
  const profesional = contexto.profesionales.find((p) => p.id === id) ?? contexto.profesionales[0];
  return { profesionalId: profesional?.id ?? null, fecha: fecha && esFechaValida(fecha) ? fecha : null };
}

function puedeAnimar(): boolean {
  return (
    typeof document.startViewTransition === 'function' &&
    !(typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  );
}

/** Agenda del día de un profesional (US1) con alta de citas (US2) y cambios de estado (US3). */
export function PantallaAgenda({ contexto, identificador, onSalir }: PropsPantallaAgenda) {
  const [vista, setVista] = useState<Vista>(() => leerVista(contexto));
  const [agenda, setAgenda] = useState<Agenda | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);
  const [alta, setAlta] = useState<{ hora: string | null } | null>(null);
  const [resaltadaId, setResaltadaId] = useState<number | null>(null);
  const [exito, setExito] = useState<string | null>(null);
  const ultimaClave = useRef<string | null>(null);

  // Botón atrás / adelante del navegador.
  useEffect(() => {
    const alVolver = () => setVista(leerVista(contexto));
    window.addEventListener('popstate', alVolver);
    return () => window.removeEventListener('popstate', alVolver);
  }, [contexto]);

  useEffect(() => {
    if (vista.profesionalId === null) return;
    const control = new AbortController();
    const clave = `${vista.profesionalId}|${vista.fecha ?? ''}`;
    setCargando(true);
    api
      .obtenerAgenda(vista.profesionalId, vista.fecha, control.signal)
      .then((nueva) => {
        const esNavegacion = ultimaClave.current !== null && ultimaClave.current !== clave;
        ultimaClave.current = clave;
        const aplicar = () => {
          setAgenda(nueva);
          setError(null);
          setCargando(false);
        };
        // Transición de día con View Transitions solo si existe y no se pide menos movimiento.
        if (esNavegacion && puedeAnimar()) document.startViewTransition(() => flushSync(aplicar));
        else aplicar();
      })
      .catch((e: unknown) => {
        if (esErrorAbort(e)) return;
        setCargando(false);
        if (e instanceof ErrorApi && e.esSesionPerdida) return;
        setError(e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO);
      });
    return () => control.abort();
  }, [vista, recarga]);

  useEffect(() => {
    if (resaltadaId === null) return;
    const t = setTimeout(() => setResaltadaId(null), 2500);
    return () => clearTimeout(t);
  }, [resaltadaId]);

  const fechaActual = vista.fecha ?? agenda?.fecha ?? contexto.hoy;
  const hoy = agenda?.hoy ?? contexto.hoy;
  const profesional = contexto.profesionales.find((p) => p.id === vista.profesionalId) ?? null;

  const navegar = useCallback((profesionalId: number, fecha: string) => {
    const params = new URLSearchParams(window.location.search);
    params.set('profesional', String(profesionalId));
    params.set('fecha', fecha);
    window.history.pushState(null, '', `${window.location.pathname}?${params}`);
    setExito(null);
    setVista({ profesionalId, fecha });
  }, []);

  function cerrarSesion() {
    api
      .cerrarSesion()
      .catch(() => undefined)
      .finally(onSalir);
  }

  function alReservar(cita: Cita) {
    setAlta(null);
    setResaltadaId(cita.id);
    setExito(`Cita reservada para ${cita.paciente.nombre} a las ${cita.horaInicio}.`);
    setRecarga((n) => n + 1);
  }

  function alCambiarEstado(cita?: Cita) {
    if (cita) {
      setAgenda((a) => (a ? { ...a, citas: a.citas.map((c) => (c.id === cita.id ? cita : c)) } : a));
    }
    setRecarga((n) => n + 1);
  }

  const agendaVigente = agenda;
  const primeraHoraLibre = agendaVigente?.huecos.find((h) => h.reservable)?.horaInicio ?? null;

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-borde bg-superficie/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 pt-2">
          <h1 className="min-w-0 truncate text-base font-bold">{contexto.clinica.nombre}</h1>
          <div className="flex shrink-0 items-center gap-1">
            {/* 002 (US4, SC-008): se vuelve a pedir al cambiar de profesional o de día. */}
            <AvisoCancelaciones clave={`${vista.profesionalId ?? ''}|${fechaActual}`} />
            <Boton variante="fantasma" icono={<LogOut aria-hidden="true" className="size-4" />} onClick={cerrarSesion}>
              Cerrar sesión
            </Boton>
          </div>
        </div>
        {identificador && (
          <nav aria-label="Secciones del panel" className="mx-auto flex max-w-3xl gap-2 px-4 pt-1">
            <a
              href="#"
              aria-current="page"
              onClick={(e) => e.preventDefault()}
              className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl bg-acento-suave px-4 text-sm font-semibold"
            >
              Agenda
            </a>
            <a
              href={rutaAnalitica(identificador)}
              className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl px-4 text-sm font-semibold hover:bg-superficie-2"
            >
              Analítica
            </a>
          </nav>
        )}
        <div className="mx-auto flex max-w-3xl flex-col gap-2 px-4 pt-1 pb-3">
          <SelectorProfesional
            profesionales={contexto.profesionales}
            seleccionadoId={vista.profesionalId}
            onSeleccionar={(id) => id !== vista.profesionalId && navegar(id, fechaActual)}
          />
          <NavegadorDia fecha={fechaActual} hoy={hoy} onCambiar={(f) => vista.profesionalId !== null && navegar(vista.profesionalId, f)} />
        </div>
      </header>

      <main className="mx-auto flex max-w-3xl flex-col gap-3 px-4 py-4" aria-busy={cargando}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">{profesional ? `Agenda de ${profesional.nombre}` : 'Agenda'}</h2>
          <Boton
            variante="primaria"
            icono={<Plus aria-hidden="true" className="size-4" />}
            disabled={!agendaVigente?.horario}
            onClick={() => setAlta({ hora: primeraHoraLibre })}
          >
            Nueva cita
          </Boton>
        </div>

        {exito && <Aviso tipo="exito">{exito}</Aviso>}
        {error && <Aviso tipo="error">{error}</Aviso>}
        {!agendaVigente && cargando && <p className="p-6 text-center text-texto-suave">Cargando la agenda…</p>}
        {agendaVigente && (
          <LineaTiempo
            agenda={agendaVigente}
            resaltadaId={resaltadaId}
            onReservar={(hora) => setAlta({ hora })}
            onCambioEstado={alCambiarEstado}
          />
        )}
      </main>

      {profesional && agendaVigente?.horario && (
        <DialogoAltaCita
          abierto={alta !== null}
          profesional={profesional}
          fecha={agendaVigente.fecha}
          horario={agendaVigente.horario}
          servicios={contexto.servicios}
          horaPropuesta={alta?.hora}
          onCerrar={() => setAlta(null)}
          onReservada={alReservar}
        />
      )}
    </div>
  );
}
