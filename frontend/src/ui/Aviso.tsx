import type { ReactNode } from 'react';
import { CircleAlert, CircleCheck, Info } from 'lucide-react';

type TipoAviso = 'error' | 'exito' | 'info';

const ESTILO: Record<TipoAviso, { clases: string; Icono: typeof Info }> = {
  error: { clases: 'bg-peligro-suave text-peligro border-peligro/30', Icono: CircleAlert },
  exito: { clases: 'bg-exito-suave text-exito border-exito/30', Icono: CircleCheck },
  info: { clases: 'bg-info-suave text-info border-info/30', Icono: Info },
};

interface PropsAviso {
  tipo?: TipoAviso;
  children: ReactNode;
  className?: string;
  id?: string;
}

/** Aviso con icono y texto. Los errores se anuncian con `role="alert"`, el resto con `role="status"`. */
export function Aviso({ tipo = 'info', children, className = '', id }: PropsAviso) {
  const { clases, Icono } = ESTILO[tipo];
  return (
    <div
      id={id}
      role={tipo === 'error' ? 'alert' : 'status'}
      className={`flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm font-medium ${clases} ${className}`}
    >
      <Icono aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 break-words">{children}</div>
    </div>
  );
}
