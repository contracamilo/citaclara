import type { Analitica, Proporcion } from '../api/tipos';
import { formatearEuros } from '../formato/euros';
import { formatearSemana } from '../formato/fechas';
import { formatearDecimas, textoCitas } from '../formato/porcentaje';

interface Props {
  evolucion: Analitica['evolucion'];
  /** Nombres de los profesionales, en el mismo orden que `ocupacion` de cada semana. */
  profesionales: Array<{ profesionalId: number; nombre: string }>;
  totalIngresosCentimos: number;
}

function Barra({ porcentaje }: { porcentaje: number }) {
  return (
    <div aria-hidden="true" className="mt-1 h-2 w-full overflow-hidden rounded-full bg-superficie-2">
      {porcentaje > 0 && <div className="h-full rounded-full bg-acento" style={{ width: `${Math.min(100, porcentaje)}%` }} />}
    </div>
  );
}

function Celda({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  // En pantallas estrechas las celdas se apilan y cada una lleva su etiqueta visible.
  return (
    <td className="flex items-start justify-between gap-3 px-3 py-1.5 md:table-cell md:align-top md:py-3">
      <span className="shrink-0 text-sm text-texto-suave md:hidden">{etiqueta}</span>
      <div className="min-w-0 flex-1 text-right md:text-left">{children}</div>
    </td>
  );
}

function Porcentaje({ proporcion, detalle }: { proporcion: Proporcion; detalle?: string }) {
  return (
    <>
      <span className={`numeros font-semibold ${proporcion.decimas === null ? 'text-texto-suave' : ''}`}>
        {formatearDecimas(proporcion.decimas)}
      </span>
      {detalle && proporcion.total > 0 && <span className="numeros block text-sm text-texto-suave">{detalle}</span>}
      {proporcion.decimas !== null && <Barra porcentaje={proporcion.decimas / 10} />}
    </>
  );
}

/** Evolución semana a semana de las últimas 8 semanas (US3, FR-007, FR-014). */
export function Evolucion({ evolucion, profesionales, totalIngresosCentimos }: Props) {
  const mayorIngreso = Math.max(0, ...evolucion.map((s) => s.ingresosCentimos));
  return (
    <section aria-labelledby="titulo-evolucion" className="flex flex-col gap-3 rounded-2xl border border-borde bg-superficie p-4 shadow-suave">
      <h2 id="titulo-evolucion" className="text-lg font-semibold">
        Evolución semana a semana
      </h2>
      <table className="w-full border-collapse text-left">
        <caption className="sr-only">Ingresos, no asistencia y ocupación de cada una de las últimas 8 semanas</caption>
        <thead className="sr-only md:table-header-group md:not-sr-only">
          <tr className="border-b border-borde text-sm text-texto-suave">
            <th scope="col" className="px-3 py-2 font-medium">Semana</th>
            <th scope="col" className="px-3 py-2 font-medium">Ingresos</th>
            <th scope="col" className="px-3 py-2 font-medium">No asistencia</th>
            {profesionales.map((p) => (
              <th key={p.profesionalId} scope="col" className="px-3 py-2 font-medium">
                Ocupación de {p.nombre}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="block md:table-row-group">
          {evolucion.map((s) => (
            <tr key={s.semana.inicio} className="mb-3 block rounded-xl border border-borde py-2 md:mb-0 md:table-row md:rounded-none md:border-0 md:border-b md:py-0">
              <th scope="row" className="block px-3 py-1.5 font-semibold md:table-cell md:py-3">
                <span className="numeros">{formatearSemana(s.semana)}</span>
              </th>
              <Celda etiqueta="Ingresos">
                <span className="numeros font-semibold">{formatearEuros(s.ingresosCentimos)}</span>
                <Barra porcentaje={mayorIngreso > 0 ? (s.ingresosCentimos / mayorIngreso) * 100 : 0} />
              </Celda>
              <Celda etiqueta="No asistencia">
                <Porcentaje
                  proporcion={s.tasaNoAsistencia}
                  detalle={`${s.noAsistidas} de ${textoCitas(s.tasaNoAsistencia.total)}`}
                />
              </Celda>
              {s.ocupacion.map((o) => {
                const nombre = profesionales.find((p) => p.profesionalId === o.profesionalId)?.nombre ?? '';
                return (
                  <Celda key={o.profesionalId} etiqueta={`Ocupación de ${nombre}`}>
                    <Porcentaje proporcion={o.ocupacion} />
                  </Celda>
                );
              })}
            </tr>
          ))}
        </tbody>
        <tfoot className="block md:table-footer-group">
          <tr className="block px-3 py-3 md:table-row md:px-0">
            <th scope="row" className="numeros flex items-baseline justify-between gap-3 font-bold md:table-cell md:px-3">
              <span>Total de las 8 semanas</span>
              <span className="md:hidden">{formatearEuros(totalIngresosCentimos)}</span>
            </th>
            <td className="numeros hidden px-3 font-bold md:table-cell">{formatearEuros(totalIngresosCentimos)}</td>
            <td className="hidden md:table-cell" colSpan={1 + profesionales.length} />
          </tr>
        </tfoot>
      </table>
    </section>
  );
}
