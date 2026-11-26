import { Plus } from 'lucide-react';
import type { Hueco } from '../api/tipos';
import { formatearFranja } from '../formato/fechas';
import { Boton } from '../ui/Boton';

interface PropsHuecoLibre {
  hueco: Hueco;
  onReservar?: (horaInicio: string) => void;
}

/** Hueco libre del horario como bloque punteado (FR-007); «Reservar aquí» solo si es reservable (FR-008). */
export function HuecoLibre({ hueco, onReservar }: PropsHuecoLibre) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-tarjeta border-2 border-dashed border-borde-fuerte px-4 py-3">
      <p className="numeros text-sm font-medium text-texto-suave">
        Libre · {formatearFranja(hueco.horaInicio, hueco.horaFin)}
      </p>
      {hueco.reservable && onReservar && (
        <Boton variante="secundaria" icono={<Plus aria-hidden="true" className="size-4" />} onClick={() => onReservar(hueco.horaInicio)}>
          Reservar aquí
        </Boton>
      )}
    </div>
  );
}
