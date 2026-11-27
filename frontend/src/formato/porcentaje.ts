/**
 * Único formateador de porcentajes de la aplicación (specs/004-panel-analitica, research D4).
 *
 * El servidor decide el valor: llega como «décimas de porcentaje» enteras (93 = 9,3 %), ya
 * redondeadas una sola vez con aritmética entera. Aquí solo se da formato (CLDR `es-ES`: «9,3 %»,
 * con espacio no separable). Sin base de cálculo (`null`) se muestra «Sin datos», nunca «0 %».
 */
const formateador = new Intl.NumberFormat('es-ES', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export const SIN_DATOS = 'Sin datos';

export function formatearDecimas(decimas: number | null): string {
  return decimas === null ? SIN_DATOS : formateador.format(decimas / 1000);
}

/** «1 cita», «39 citas». */
export function textoCitas(n: number): string {
  return `${n} ${n === 1 ? 'cita' : 'citas'}`;
}
