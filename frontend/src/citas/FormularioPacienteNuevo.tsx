import { useId } from 'react';
import { Search } from 'lucide-react';
import type { NuevoPaciente, Paciente } from '../api/tipos';
import { Boton } from '../ui/Boton';

export interface ValoresPacienteNuevo {
  nombre: string;
  telefono: string;
  email: string;
}

export type ErroresPacienteNuevo = Partial<Record<keyof NuevoPaciente, string>>;

export interface Duplicado {
  paciente: Paciente;
  mensaje: string;
}

interface PropsFormularioPacienteNuevo {
  valores: ValoresPacienteNuevo;
  onCambiar: (valores: ValoresPacienteNuevo) => void;
  errores: ErroresPacienteNuevo;
  duplicado: Duplicado | null;
  onUsarFicha: (paciente: Paciente) => void;
  onBuscarExistente: () => void;
}

/** Ficha de paciente nuevo dentro del alta (FR-019, FR-020). */
export function FormularioPacienteNuevo({
  valores,
  onCambiar,
  errores,
  duplicado,
  onUsarFicha,
  onBuscarExistente,
}: PropsFormularioPacienteNuevo) {
  const id = useId();
  const campo = (
    nombre: keyof ValoresPacienteNuevo,
    etiqueta: string,
    obligatorio: boolean,
    extra: { type?: string; inputMode?: 'tel' | 'email'; autoComplete?: string },
  ) => {
    const idCampo = `${id}-${nombre}`;
    const error = errores[nombre];
    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={idCampo} className="text-sm font-semibold">
          {etiqueta}
          {obligatorio ? (
            <span aria-hidden="true" className="text-peligro">
              {' '}
              *
            </span>
          ) : (
            <span className="font-normal text-texto-suave"> (opcional)</span>
          )}
        </label>
        <input
          id={idCampo}
          name={nombre}
          type={extra.type ?? 'text'}
          inputMode={extra.inputMode}
          autoComplete={extra.autoComplete}
          required={obligatorio}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${idCampo}-error` : undefined}
          value={valores[nombre]}
          onChange={(e) => onCambiar({ ...valores, [nombre]: e.target.value })}
          className="min-h-11 w-full min-w-0 rounded-xl border border-borde-fuerte bg-superficie px-3 text-base"
        />
        {error && (
          <p id={`${idCampo}-error`} className="text-sm font-medium text-peligro">
            {error}
          </p>
        )}
      </div>
    );
  };

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-2 text-sm font-semibold">Paciente nuevo</legend>
      {campo('nombre', 'Nombre', true, { autoComplete: 'off' })}
      {campo('telefono', 'Teléfono', true, { type: 'tel', inputMode: 'tel', autoComplete: 'off' })}
      {duplicado && (
        <div role="alert" className="flex flex-col gap-2 rounded-xl border border-info/30 bg-info-suave p-3 text-sm text-info">
          <p className="font-medium">{duplicado.mensaje}</p>
          <div>
            <Boton variante="primaria" onClick={() => onUsarFicha(duplicado.paciente)}>
              Usar esta ficha
            </Boton>
          </div>
        </div>
      )}
      {campo('email', 'Email', false, { type: 'email', inputMode: 'email', autoComplete: 'off' })}
      <div>
        <Boton variante="fantasma" className="px-2 text-acento" icono={<Search aria-hidden="true" className="size-4" />} onClick={onBuscarExistente}>
          Buscar un paciente existente
        </Boton>
      </div>
    </fieldset>
  );
}
