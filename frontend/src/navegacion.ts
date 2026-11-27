/** Rutas del panel sin enrutador (specs/004-panel-analitica, research D8). */

/** `/eleva?profesional=1` o `/eleva/analitica` → «eleva». */
export function identificadorDeRuta(ruta: string): string {
  const primero = ruta.split('/').filter(Boolean)[0] ?? '';
  try {
    return decodeURIComponent(primero);
  } catch {
    return primero;
  }
}

export type Vista = 'agenda' | 'analitica';

/** `/eleva/analitica` → analítica; cualquier otra ruta de la clínica → agenda. */
export function vistaDeRuta(ruta: string): Vista {
  const segmentos = ruta.split('/').filter(Boolean);
  return segmentos[1] === 'analitica' ? 'analitica' : 'agenda';
}

export function rutaAgenda(identificador: string): string {
  return `/${encodeURIComponent(identificador)}`;
}

export function rutaAnalitica(identificador: string): string {
  return `/${encodeURIComponent(identificador)}/analitica`;
}
