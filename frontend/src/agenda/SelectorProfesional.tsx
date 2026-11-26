import { useRef, type KeyboardEvent } from 'react';
import type { Profesional } from '../api/tipos';

interface PropsSelectorProfesional {
  profesionales: Profesional[];
  seleccionadoId: number | null;
  onSeleccionar: (id: number) => void;
}

/**
 * Control segmentado accesible (`radiogroup`) con nombre y especialidad visible de cada profesional
 * (FR-005, FR-027). Se reparte en rejilla y se apila si no cabe: sin desplazamiento horizontal a 360 px.
 */
export function SelectorProfesional({ profesionales, seleccionadoId, onSeleccionar }: PropsSelectorProfesional) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  function alPulsarTecla(e: KeyboardEvent, indice: number) {
    const delta = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0;
    if (delta === 0) return;
    e.preventDefault();
    const siguiente = (indice + delta + profesionales.length) % profesionales.length;
    const p = profesionales[siguiente];
    if (!p) return;
    onSeleccionar(p.id);
    refs.current[siguiente]?.focus();
  }

  const algunoSeleccionado = profesionales.some((p) => p.id === seleccionadoId);

  return (
    <div
      role="radiogroup"
      aria-label="Profesional"
      className="grid grid-cols-[repeat(auto-fit,minmax(6.5rem,1fr))] gap-1 rounded-2xl bg-superficie-2 p-1"
    >
      {profesionales.map((p, i) => {
        const activo = p.id === seleccionadoId;
        return (
          <button
            key={p.id}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={activo}
            aria-label={`${p.nombre} · ${p.especialidadNombre}`}
            tabIndex={activo || (!algunoSeleccionado && i === 0) ? 0 : -1}
            onClick={() => onSeleccionar(p.id)}
            onKeyDown={(e) => alPulsarTecla(e, i)}
            className={`min-h-11 min-w-0 rounded-xl px-3 py-1.5 text-left transition-colors ${
              activo ? 'bg-superficie text-texto shadow-suave' : 'text-texto-suave hover:text-texto'
            }`}
          >
            <span className="block truncate text-sm font-semibold">{p.nombre}</span>
            <span className="block truncate text-xs">{p.especialidadNombre}</span>
          </button>
        );
      })}
    </div>
  );
}
