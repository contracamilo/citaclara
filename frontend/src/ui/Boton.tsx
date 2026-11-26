import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { LoaderCircle } from 'lucide-react';

type Variante = 'primaria' | 'secundaria' | 'peligro' | 'fantasma';

const CLASES: Record<Variante, string> = {
  primaria: 'bg-acento text-sobre-acento hover:bg-acento-hover shadow-suave',
  secundaria: 'bg-superficie text-texto border border-borde hover:bg-superficie-2',
  peligro: 'bg-peligro text-sobre-peligro hover:bg-peligro-hover shadow-suave',
  fantasma: 'bg-transparent text-texto hover:bg-superficie-2',
};

export interface PropsBoton extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante;
  /** Muestra un indicador y desactiva el botón mientras se envía. */
  ocupado?: boolean;
  icono?: ReactNode;
}

/** Botón base con objetivo táctil de al menos 44 px (FR-028). */
export function Boton({
  variante = 'secundaria',
  ocupado = false,
  icono,
  className = '',
  children,
  disabled,
  type = 'button',
  ...resto
}: PropsBoton) {
  return (
    <button
      type={type}
      disabled={disabled || ocupado}
      aria-busy={ocupado || undefined}
      className={`inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${CLASES[variante]} ${className}`}
      {...resto}
    >
      {ocupado ? <LoaderCircle aria-hidden="true" className="size-4 animate-spin" /> : icono}
      {children}
    </button>
  );
}
