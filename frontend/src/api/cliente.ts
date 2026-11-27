import type { Accion, Agenda, Cita, Contexto, NuevaCita, Paciente, Problema } from './tipos';

/** Evento global que se emite cuando cualquier llamada recibe 401 SIN_SESION o SESION_CADUCADA. */
export const EVENTO_SESION_PERDIDA = 'sesion-perdida';

export const MENSAJE_SESION_CADUCADA = 'Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.';
export const MENSAJE_SIN_CONEXION = 'No hay conexión con el servidor. Inténtalo de nuevo.';
export const MENSAJE_ERROR_GENERICO = 'Algo ha fallado. Inténtalo de nuevo en unos segundos.';

export const MENSAJE_DEMASIADAS_PETICIONES = 'Has probado demasiadas veces. Espera unos minutos e inténtalo de nuevo.';
export const MENSAJE_DEMASIADO_GRANDE = 'Lo que intentas enviar es demasiado grande. Acórtalo e inténtalo de nuevo.';

const CODIGOS_SESION_PERDIDA = new Set(['SIN_SESION', 'SESION_CADUCADA']);

export interface DetalleSesionPerdida {
  codigo: string;
  mensaje: string;
}

/**
 * Error del API. `codigo` es solo para el programa: en pantalla se muestra siempre `mensaje` (FR-027).
 */
export class ErrorApi extends Error implements Problema {
  readonly estadoHttp: number;
  readonly codigo: string;
  readonly mensaje: string;
  readonly errores?: Problema['errores'];
  readonly citaConflictiva?: Problema['citaConflictiva'];
  readonly citaActual?: Problema['citaActual'];
  readonly pacienteExistente?: Problema['pacienteExistente'];

  constructor(estadoHttp: number, problema: Problema) {
    super(problema.mensaje);
    this.name = 'ErrorApi';
    this.estadoHttp = estadoHttp;
    this.codigo = problema.codigo;
    this.mensaje = problema.mensaje;
    this.errores = problema.errores;
    this.citaConflictiva = problema.citaConflictiva;
    this.citaActual = problema.citaActual;
    this.pacienteExistente = problema.pacienteExistente;
  }

  get esSesionPerdida(): boolean {
    return this.estadoHttp === 401 && CODIGOS_SESION_PERDIDA.has(this.codigo);
  }
}

interface OpcionesPeticion {
  cuerpo?: unknown;
  senal?: AbortSignal;
  /** Si es falso, un 401 de sesión no emite `sesion-perdida` (carga inicial del contexto). */
  avisarSesionPerdida?: boolean;
}

function esAbort(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError';
}

async function leerProblema(respuesta: Response): Promise<Problema> {
  const tipo = respuesta.headers.get('Content-Type') ?? '';
  if (tipo.includes('json')) {
    try {
      const datos = (await respuesta.json()) as Partial<Problema>;
      if (typeof datos.mensaje === 'string' && typeof datos.codigo === 'string') return datos as Problema;
    } catch {
      // cuerpo ilegible: se usa el mensaje genérico
    }
  }
  if (respuesta.status === 429) return { codigo: 'DEMASIADAS_PETICIONES', mensaje: MENSAJE_DEMASIADAS_PETICIONES };
  if (respuesta.status === 413) return { codigo: 'CUERPO_DEMASIADO_GRANDE', mensaje: MENSAJE_DEMASIADO_GRANDE };
  return { codigo: 'ERROR_INTERNO', mensaje: MENSAJE_ERROR_GENERICO };
}

async function peticion<T>(metodo: string, ruta: string, opciones: OpcionesPeticion = {}): Promise<T> {
  const { cuerpo, senal, avisarSesionPerdida = true } = opciones;
  const cabeceras: Record<string, string> = { Accept: 'application/json, application/problem+json' };
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';

  let respuesta: Response;
  try {
    respuesta = await fetch(ruta, {
      method: metodo,
      credentials: 'same-origin',
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: senal,
    });
  } catch (e) {
    if (esAbort(e)) throw e;
    throw new ErrorApi(0, { codigo: 'SIN_CONEXION', mensaje: MENSAJE_SIN_CONEXION });
  }

  if (!respuesta.ok) {
    const error = new ErrorApi(respuesta.status, await leerProblema(respuesta));
    if (error.esSesionPerdida && avisarSesionPerdida) {
      window.dispatchEvent(
        new CustomEvent<DetalleSesionPerdida>(EVENTO_SESION_PERDIDA, {
          detail: { codigo: error.codigo, mensaje: MENSAJE_SESION_CADUCADA },
        }),
      );
    }
    throw error;
  }

  if (respuesta.status === 204) return undefined as T;
  const texto = await respuesta.text();
  return (texto ? JSON.parse(texto) : undefined) as T;
}

const ruta = (identificador: string) => `/api/clinicas/${encodeURIComponent(identificador)}`;

export const api = {
  obtenerClinica: (identificador: string) => peticion<{ nombre: string }>('GET', ruta(identificador)),

  iniciarSesion: (identificador: string, clave: string) =>
    peticion<void>('POST', `${ruta(identificador)}/sesion`, { cuerpo: { clave } }),

  cerrarSesion: () => peticion<void>('DELETE', '/api/sesion'),

  obtenerContexto: (opciones: { avisarSesionPerdida?: boolean } = {}) =>
    peticion<Contexto>('GET', '/api/sesion', opciones),

  obtenerAgenda: (profesionalId: number, fecha?: string | null, senal?: AbortSignal) => {
    const params = new URLSearchParams({ profesionalId: String(profesionalId) });
    if (fecha) params.set('fecha', fecha);
    return peticion<Agenda>('GET', `/api/agenda?${params}`, { senal });
  },

  buscarPacientes: (q: string, senal?: AbortSignal) =>
    peticion<Paciente[]>('GET', `/api/pacientes?${new URLSearchParams({ q })}`, { senal }),

  crearCita: (cita: NuevaCita) => peticion<Cita>('POST', '/api/citas', { cuerpo: cita }),

  cambiarEstadoCita: (citaId: number, accion: Accion, version: number) =>
    peticion<Cita>('POST', `/api/citas/${citaId}/estado`, { cuerpo: { accion, version } }),
};

export function esErrorAbort(e: unknown): boolean {
  return esAbort(e);
}
