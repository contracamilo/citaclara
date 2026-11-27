import { useEffect, useId, useState, type FormEvent } from 'react';
import { CalendarHeart } from 'lucide-react';
import { api, apiPortal, ErrorApi, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';

interface PropsEntrada {
  identificador: string;
  /** Aviso con el que se llega (acceso caducado, enlace no válido). */
  aviso?: { tipo: 'info' | 'error'; texto: string } | null;
}

/** Texto único tras pedir el enlace, exista o no el email (FR-003, contracts/ui.md §1). */
export const avisoEnlaceEnviado = (clinica: string) =>
  `Si tu email está registrado en ${clinica}, te hemos enviado un enlace para entrar. Revisa tu correo; el enlace caduca en 10 minutos. Si no te llega, llama a la clínica.`;

/** Entrada del portal: el paciente pide un enlace a su email, sin cuenta ni contraseña (US1). */
export function PantallaEntradaPortal({ identificador, aviso = null }: PropsEntrada) {
  const [clinica, setClinica] = useState<string | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [errorEmail, setErrorEmail] = useState<string | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviado, setEnviado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const idCampo = useId();
  const idError = useId();

  useEffect(() => {
    let vigente = true;
    api
      .obtenerClinica(identificador)
      .then((c) => vigente && setClinica(c.nombre))
      .catch((e: unknown) => vigente && setErrorCarga(e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO));
    return () => {
      vigente = false;
    };
  }, [identificador]);

  async function pedir(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setErrorEmail(null);
    setErrorGeneral(null);
    try {
      await apiPortal.pedirEnlace(identificador, email);
      setEnviado(true);
    } catch (error) {
      const mensaje = error instanceof ErrorApi ? error.mensaje : MENSAJE_ERROR_GENERICO;
      if (error instanceof ErrorApi && error.codigo === 'EMAIL_NO_VALIDO') setErrorEmail(mensaje);
      else setErrorGeneral(mensaje);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-dvh items-start justify-center px-4 py-10 sm:items-center">
      <div className="w-full max-w-sm rounded-tarjeta border border-borde bg-superficie p-6 shadow-suave sm:p-8">
        <p className="mb-6 flex items-center gap-2 text-sm font-semibold text-acento">
          <CalendarHeart aria-hidden="true" className="size-5" />
          {clinica ?? ' '}
        </p>
        <h1 className="mb-2 text-2xl font-bold tracking-tight">Tus citas en {clinica ?? 'tu clínica'}</h1>
        <p className="mb-6 text-sm text-texto-suave">
          Escribe el email que diste en la clínica y te enviaremos un enlace para entrar. No necesitas contraseña.
        </p>

        {aviso && (
          <Aviso tipo={aviso.tipo} className="mb-4">
            {aviso.texto}
          </Aviso>
        )}
        {errorCarga && (
          <Aviso tipo="error" className="mb-4">
            {errorCarga}
          </Aviso>
        )}

        {enviado ? (
          <div className="flex flex-col gap-4">
            <Aviso tipo="exito">{avisoEnlaceEnviado(clinica ?? 'la clínica')}</Aviso>
            <Boton
              variante="secundaria"
              onClick={() => {
                setEnviado(false);
              }}
            >
              Pedir otro enlace
            </Boton>
          </div>
        ) : (
          <form onSubmit={pedir} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={idCampo} className="text-sm font-semibold">
                Tu email
              </label>
              <input
                id={idCampo}
                name="email"
                type="email"
                inputMode="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                aria-invalid={errorEmail ? true : undefined}
                aria-describedby={errorEmail ? idError : undefined}
                className="min-h-11 w-full min-w-0 rounded-xl border border-borde-fuerte bg-superficie px-3 text-base"
              />
              {errorEmail && (
                <p id={idError} role="alert" className="text-sm font-medium text-peligro">
                  {errorEmail}
                </p>
              )}
            </div>
            {errorGeneral && <Aviso tipo="error">{errorGeneral}</Aviso>}
            <Boton type="submit" variante="primaria" ocupado={enviando} disabled={email.trim().length === 0}>
              Enviarme el enlace
            </Boton>
          </form>
        )}
      </div>
    </main>
  );
}
