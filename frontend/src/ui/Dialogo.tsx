import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface PropsDialogo {
  abierto: boolean;
  titulo: string;
  onCerrar: () => void;
  children: ReactNode;
  /** Botones del pie. */
  pie?: ReactNode;
  /** `alertdialog` para confirmaciones. */
  rol?: 'dialog' | 'alertdialog';
}

/**
 * Envoltorio del `<dialog>` nativo (research D15): modal, cierre con Escape, foco inicial en el
 * elemento con `data-foco-inicial` (o el primer campo) y hoja inferior por debajo de 640 px.
 */
export function Dialogo({ abierto, titulo, onCerrar, children, pie, rol = 'dialog' }: PropsDialogo) {
  if (!abierto) return null;
  return (
    <DialogoAbierto titulo={titulo} onCerrar={onCerrar} pie={pie} rol={rol}>
      {children}
    </DialogoAbierto>
  );
}

function DialogoAbierto({ titulo, onCerrar, children, pie, rol }: Omit<PropsDialogo, 'abierto'>) {
  const ref = useRef<HTMLDialogElement>(null);
  const idTitulo = useId();
  const onCerrarRef = useRef(onCerrar);
  onCerrarRef.current = onCerrar;

  useEffect(() => {
    const dialogo = ref.current;
    if (!dialogo) return;
    if (!dialogo.open) {
      if (typeof dialogo.showModal === 'function') dialogo.showModal();
      else dialogo.setAttribute('open', '');
    }
    // Foco inicial: el elemento marcado con `data-foco-inicial` o, si no hay, el primer campo del cuerpo.
    const enfocable =
      dialogo.querySelector<HTMLElement>('[data-foco-inicial]') ??
      dialogo.querySelector<HTMLElement>(
        ['input', 'select', 'textarea', 'button'].map((t) => `.cuerpo-dialogo ${t}:not([disabled])`).join(', '),
      );
    enfocable?.focus();
    const alCancelar = (e: Event) => {
      e.preventDefault();
      onCerrarRef.current();
    };
    dialogo.addEventListener('cancel', alCancelar);
    return () => {
      dialogo.removeEventListener('cancel', alCancelar);
      if (dialogo.open && typeof dialogo.close === 'function') dialogo.close();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      className="dialogo"
      aria-labelledby={idTitulo}
      role={rol === 'alertdialog' ? 'alertdialog' : undefined}
      aria-modal="true"
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.preventDefault();
          onCerrar();
        }
      }}
    >
      <div className="flex items-start justify-between gap-3 border-b border-borde px-5 pt-5 pb-3">
        <h2 id={idTitulo} className="text-lg font-semibold leading-snug">
          {titulo}
        </h2>
        <button
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar"
          className="-mt-2 -mr-2 inline-flex size-11 shrink-0 items-center justify-center rounded-xl text-texto-suave hover:bg-superficie-2"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
      </div>
      <div className="cuerpo-dialogo px-5 py-4">{children}</div>
      {pie && (
        <div className="flex flex-col-reverse gap-2 border-t border-borde px-5 py-4 sm:flex-row sm:justify-end">
          {pie}
        </div>
      )}
    </dialog>
  );
}
