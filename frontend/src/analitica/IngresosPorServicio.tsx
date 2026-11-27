import type { Analitica } from '../api/tipos';
import { formatearEuros } from '../formato/euros';
import { textoCitas } from '../formato/porcentaje';
import { GraficoBarras } from './GraficoBarras';

type Ingresos = Analitica['ingresos'];

/** Ingresos por servicio (completadas) e ingresos no obtenidos por no asistencias (US2, FR-006, FR-016). */
export function IngresosPorServicio({ ingresos }: { ingresos: Ingresos }) {
  return (
    <section aria-labelledby="titulo-ingresos" className="flex flex-col gap-5 rounded-2xl border border-borde bg-superficie p-4 shadow-suave">
      <div className="flex flex-col gap-2">
        <h2 id="titulo-ingresos" className="text-lg font-semibold">
          Ingresos por servicio
        </h2>
        <p className="text-sm text-texto-suave">Citas completadas de las últimas 8 semanas</p>
        <GraficoBarras
          nombre="Ingresos por servicio"
          filas={ingresos.servicios.map((s) => ({
            clave: s.servicioId,
            etiqueta: s.nombre,
            valor: s.importeCentimos,
            texto: formatearEuros(s.importeCentimos),
            detalle: textoCitas(s.citasCompletadas),
          }))}
        />
        <p className="numeros mt-1 flex items-baseline justify-between gap-3 border-t border-borde pt-3 text-lg font-bold">
          <span>Total</span>
          <span>{formatearEuros(ingresos.totalCentimos)}</span>
        </p>
      </div>

      <div className="flex flex-col gap-2">
        <h3 className="text-lg font-semibold">Ingresos no obtenidos por no asistencias</h3>
        <p className="text-sm text-texto-suave">Importe de las citas a las que el paciente no acudió</p>
        <GraficoBarras
          nombre="Ingresos no obtenidos por no asistencias"
          filas={ingresos.servicios.map((s) => ({
            clave: s.servicioId,
            etiqueta: s.nombre,
            valor: s.importeNoObtenidoCentimos,
            texto: formatearEuros(s.importeNoObtenidoCentimos),
            detalle: textoCitas(s.citasNoAsistidas),
          }))}
        />
        <p className="numeros mt-1 flex items-baseline justify-between gap-3 border-t border-borde pt-3 text-lg font-bold">
          <span>Total</span>
          <span>{formatearEuros(ingresos.totalNoObtenidoCentimos)}</span>
        </p>
      </div>
    </section>
  );
}
