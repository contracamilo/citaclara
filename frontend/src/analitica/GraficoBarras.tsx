import { SIN_DATOS } from '../formato/porcentaje';

export interface FilaGrafico {
  clave: string | number;
  etiqueta: string;
  /** Valor numérico de la barra; `null` = sin datos (no se dibuja barra). */
  valor: number | null;
  /** Texto del valor, siempre visible («54,5 %», «17.040,00 €»). */
  texto: string;
  /** Texto de apoyo («1 de 39 citas»). */
  detalle?: string;
}

interface PropsGraficoBarras {
  filas: FilaGrafico[];
  /** Valor que ocupa el 100 % de la barra; por defecto, el mayor de las filas. */
  maximo?: number;
  /** Nombre accesible del gráfico. */
  nombre: string;
}

/**
 * Barras horizontales con nombre y valor SIEMPRE en texto (FR-012, FR-013): la información no
 * depende del color ni de pasar el ratón. La barra es decorativa (`aria-hidden`); sin datos solo
 * queda el texto «Sin datos» (FR-011).
 */
export function GraficoBarras({ filas, maximo, nombre }: PropsGraficoBarras) {
  const tope = maximo ?? Math.max(0, ...filas.map((f) => f.valor ?? 0));
  return (
    <ul aria-label={nombre} className="flex flex-col gap-3">
      {filas.map((fila) => {
        const porcentaje = fila.valor === null || tope <= 0 ? 0 : Math.min(100, (fila.valor / tope) * 100);
        return (
          <li key={fila.clave} className="flex flex-col gap-1">
            <div className="flex items-baseline justify-between gap-3">
              <span className="min-w-0 break-words font-medium">{fila.etiqueta}</span>
              <span className={`numeros shrink-0 text-right font-semibold ${fila.valor === null ? 'text-texto-suave' : ''}`}>
                {fila.valor === null ? SIN_DATOS : fila.texto}
              </span>
            </div>
            {fila.valor !== null && (
              <div aria-hidden="true" className="h-3 w-full overflow-hidden rounded-full bg-superficie-2">
                {porcentaje > 0 && <div className="h-full rounded-full bg-acento" style={{ width: `${porcentaje}%` }} />}
              </div>
            )}
            {fila.detalle && <span className="numeros text-sm text-texto-suave">{fila.detalle}</span>}
          </li>
        );
      })}
    </ul>
  );
}
