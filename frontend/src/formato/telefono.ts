/** Presentación de teléfonos guardados normalizados (`+34612345678` → «612 345 678»). */
export function formatearTelefono(telefono: string): string {
  const m = /^\+34(\d{3})(\d{3})(\d{3})$/.exec(telefono);
  return m ? `${m[1]} ${m[2]} ${m[3]}` : telefono;
}
