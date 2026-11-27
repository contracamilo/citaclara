import { useEffect, useId, useState, type FormEvent } from 'react';
import { CalendarHeart, Eye, EyeOff } from 'lucide-react';
import { api, ErrorApi, MENSAJE_ERROR_GENERICO, MENSAJE_SESION_CADUCADA } from '../api/cliente';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';

interface PropsPantallaAcceso {
  identificador: string;
  /** La pantalla se muestra porque la sesión ha caducado (FR-002). */
  sesionCaducada?: boolean;
  onEntrado: () => void;
}

/** Acceso al panel con la clave de la clínica (FR-001, US1-AC1, US1-AC2). */
export function PantallaAcceso({ identificador, sesionCaducada = false, onEntrado }: PropsPantallaAcceso) {
  const [nombreClinica, setNombreClinica] = useState<string | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [clave, setClave] = useState('');
  const [mostrarClave, setMostrarClave] = useState(false);
  const [errorClave, setErrorClave] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const idCampo = useId();
  const idError = useId();

  useEffect(() => {
    let vigente = true;
    api
      .obtenerClinica(identificador)
      .then((c) => vigente && setNombreClinica(c.nombre))
      .catch((e: unknown) => vigente && setErrorCarga(e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO));
    return () => {
      vigente = false;
    };
  }, [identificador]);

  async function entrar(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setErrorClave(null);
    try {
      await api.iniciarSesion(identificador, clave);
      onEntrado();
    } catch (error) {
      setErrorClave(error instanceof ErrorApi ? error.mensaje : MENSAJE_ERROR_GENERICO);
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm rounded-tarjeta border border-borde bg-superficie p-6 shadow-suave sm:p-8">
        <p className="mb-6 flex items-center gap-2 text-sm font-semibold text-acento">
          <CalendarHeart aria-hidden="true" className="size-5" />
          CitaClara
        </p>
        <h1 className="mb-1 text-2xl font-bold tracking-tight">{nombreClinica ?? ' '}</h1>
        <p className="mb-6 text-sm text-texto-suave">Panel de recepción</p>

        {sesionCaducada && (
          <Aviso tipo="info" className="mb-4">
            {MENSAJE_SESION_CADUCADA}
          </Aviso>
        )}
        {errorCarga && (
          <Aviso tipo="error" className="mb-4">
            {errorCarga}
          </Aviso>
        )}

        <form onSubmit={entrar} noValidate className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor={idCampo} className="text-sm font-semibold">
              Clave de la clínica
            </label>
            <div className="flex gap-2">
              <input
                id={idCampo}
                name="clave"
                type={mostrarClave ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={clave}
                onChange={(e) => setClave(e.target.value)}
                aria-invalid={errorClave ? true : undefined}
                aria-describedby={errorClave ? idError : undefined}
                className="min-h-11 w-full min-w-0 rounded-xl border border-borde-fuerte bg-superficie px-3 text-base"
              />
              <button
                type="button"
                onClick={() => setMostrarClave((v) => !v)}
                aria-label={mostrarClave ? 'Ocultar clave' : 'Mostrar clave'}
                aria-pressed={mostrarClave}
                className="inline-flex size-11 shrink-0 items-center justify-center rounded-xl border border-borde text-texto-suave hover:bg-superficie-2"
              >
                {mostrarClave ? <EyeOff aria-hidden="true" className="size-5" /> : <Eye aria-hidden="true" className="size-5" />}
              </button>
            </div>
            {errorClave && (
              <p id={idError} role="alert" className="text-sm font-medium text-peligro">
                {errorClave}
              </p>
            )}
          </div>
          <Boton type="submit" variante="primaria" ocupado={enviando} disabled={clave.length === 0}>
            Entrar
          </Boton>
        </form>
      </div>
    </main>
  );
}
