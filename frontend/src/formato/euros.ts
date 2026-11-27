/**
 * Único formateador de importes de la aplicación (research D5, FR-017).
 *
 * Los importes viajan como céntimos enteros (4000 = 40,00 €). Se formatean con
 * `Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })`, que sigue CLDR/RAE:
 * no agrupa los miles en números de 4 cifras («1000,00 €») y sí a partir de 5 («10.000,00 €»).
 *
 * Nota sobre espacios: `Intl` separa la cifra del símbolo con un espacio no separable
 * (U+00A0). Se conserva a propósito para que «40,00 €» nunca se parta en dos líneas.
 * Las pruebas normalizan los espacios antes de comparar (ver tests/unit/formato.test.ts);
 * Testing Library y Playwright ya tratan U+00A0 como espacio al buscar por texto.
 */
const formateador = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });

export function formatearEuros(centimos: number): string {
  return formateador.format(centimos / 100);
}
