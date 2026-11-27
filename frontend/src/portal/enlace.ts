import { apiPortal } from '../api/cliente';

/**
 * Secreto del enlace de acceso (research R1): va en el fragmento `#enlace=…`, se lee una sola vez por
 * carga de página y se borra del historial nada más leerlo. El consumo se memoriza por secreto porque
 * el enlace es de un solo uso y React (StrictMode) puede ejecutar dos veces el mismo efecto.
 */
let leido = false;
let pendiente: string | null = null;
const consumos = new Map<string, Promise<void>>();

export function tomarEnlace(): string | null {
  if (!leido) {
    leido = true;
    const coincidencia = /(?:^#|&)enlace=([^&]+)/.exec(window.location.hash);
    if (coincidencia) {
      pendiente = decodeURIComponent(coincidencia[1]!);
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
    }
  }
  return pendiente;
}

export function consumirEnlace(identificador: string, secreto: string): Promise<void> {
  let consumo = consumos.get(secreto);
  if (!consumo) {
    consumo = apiPortal.abrirSesion(identificador, secreto);
    consumos.set(secreto, consumo);
  }
  return consumo;
}

/** El enlace ya se ha usado (bien o mal): no se vuelve a intentar en esta carga. */
export function olvidarEnlace() {
  pendiente = null;
}

/** Solo para pruebas: vuelve a leer el fragmento como en una carga nueva. */
export function reiniciarEnlace() {
  leido = false;
  pendiente = null;
  consumos.clear();
}
