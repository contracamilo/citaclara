import { useId } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { esFechaValida, formatearFechaLarga, sumarDias } from '../formato/fechas';
import { Boton } from '../ui/Boton';

interface PropsNavegadorDia {
  fecha: string;
  /** «Hoy» según el servidor (US1-AC3), nunca según el dispositivo. */
  hoy: string;
  onCambiar: (fecha: string) => void;
}

/** Navegación de día: anterior, siguiente, hoy y fecha concreta (FR-005). */
export function NavegadorDia({ fecha, hoy, onCambiar }: PropsNavegadorDia) {
  const idFecha = useId();
  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* En móvil, la fecha ocupa su propia fila para no encogerse hasta 0 px y montarse sobre los botones. */}
      <div className="flex w-full min-w-0 items-center gap-1 sm:w-auto sm:flex-1">
        <Boton variante="fantasma" className="px-2" onClick={() => onCambiar(sumarDias(fecha, -1))}>
          <ChevronLeft aria-hidden="true" className="size-5" />
          <span className="sr-only sm:not-sr-only">Día anterior</span>
        </Boton>
        <p className="numeros min-w-0 flex-1 text-center text-base font-semibold" aria-live="polite">
          {formatearFechaLarga(fecha)}
        </p>
        <Boton variante="fantasma" className="px-2" onClick={() => onCambiar(sumarDias(fecha, 1))}>
          <span className="sr-only sm:not-sr-only">Día siguiente</span>
          <ChevronRight aria-hidden="true" className="size-5" />
        </Boton>
      </div>
      <div className="flex items-center gap-2">
        <Boton variante="secundaria" onClick={() => onCambiar(hoy)} disabled={fecha === hoy}>
          Hoy
        </Boton>
        <label htmlFor={idFecha} className="sr-only">
          Ir a una fecha
        </label>
        <input
          id={idFecha}
          type="date"
          value={fecha}
          onChange={(e) => esFechaValida(e.target.value) && onCambiar(e.target.value)}
          className="numeros min-h-11 rounded-xl border border-borde bg-superficie px-2 text-sm"
        />
      </div>
    </div>
  );
}
