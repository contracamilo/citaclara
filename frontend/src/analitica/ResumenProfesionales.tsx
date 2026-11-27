import type { ProfesionalAnalitica, Semana } from '../api/tipos';
import { formatearFecha } from '../formato/fechas';
import { formatearDecimas, textoCitas } from '../formato/porcentaje';
import { GraficoBarras } from './GraficoBarras';

interface Props {
  semana: Semana;
  profesionales: ProfesionalAnalitica[];
}

/** Ocupación y tasa de no asistencia de la última semana completa, por profesional (US1, FR-004, FR-005). */
export function ResumenProfesionales({ semana, profesionales }: Props) {
  return (
    <section aria-labelledby="titulo-ultima-semana" className="flex flex-col gap-4 rounded-2xl border border-borde bg-superficie p-4 shadow-suave">
      <div>
        <h2 id="titulo-ultima-semana" className="text-lg font-semibold">
          Última semana
        </h2>
        <p className="numeros text-sm text-texto-suave">Semana del {formatearFecha(semana.inicio)} al {formatearFecha(semana.fin)}</p>
      </div>

      <div className="flex flex-col gap-2">
        <h3 className="font-semibold">Ocupación</h3>
        <p className="text-sm text-texto-suave">Parte del horario de atención en que ha atendido pacientes.</p>
        <GraficoBarras
          nombre="Ocupación por profesional"
          maximo={1000}
          filas={profesionales.map((p) => ({
            clave: p.profesionalId,
            etiqueta: p.nombre,
            valor: p.ocupacion.decimas,
            texto: formatearDecimas(p.ocupacion.decimas),
            detalle: p.ocupacion.total > 0 ? `${p.ocupacion.parte} de ${p.ocupacion.total} min de horario` : undefined,
          }))}
        />
      </div>

      <div className="flex flex-col gap-2">
        <h3 className="font-semibold">Tasa de no asistencia</h3>
        <p className="text-sm text-texto-suave">
          Citas a las que el paciente no acudió, sobre todas las citas cerradas de la semana (incluidas las canceladas).
        </p>
        <GraficoBarras
          nombre="Tasa de no asistencia por profesional"
          filas={profesionales.map((p) => ({
            clave: p.profesionalId,
            etiqueta: p.nombre,
            valor: p.tasaNoAsistencia.decimas,
            texto: formatearDecimas(p.tasaNoAsistencia.decimas),
            detalle: p.tasaNoAsistencia.total > 0 ? `${p.tasaNoAsistencia.parte} de ${textoCitas(p.tasaNoAsistencia.total)}` : undefined,
          }))}
        />
      </div>
    </section>
  );
}
