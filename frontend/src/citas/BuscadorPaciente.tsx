import { useEffect, useId, useState, type KeyboardEvent } from 'react';
import { Search, UserPlus, UserRound } from 'lucide-react';
import { api, ErrorApi, esErrorAbort, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { Paciente } from '../api/tipos';
import { formatearTelefono } from '../formato/telefono';
import { Boton } from '../ui/Boton';

export const ESPERA_BUSQUEDA_MS = 250;
const MINIMO_CARACTERES = 2;

interface PropsBuscadorPaciente {
  seleccionado: Paciente | null;
  onSeleccionar: (paciente: Paciente | null) => void;
  onPacienteNuevo: () => void;
}

/** Buscador de pacientes por nombre o teléfono (FR-018) con lista accesible `listbox`. */
export function BuscadorPaciente({ seleccionado, onSeleccionar, onPacienteNuevo }: PropsBuscadorPaciente) {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState<Paciente[] | null>(null);
  const [activo, setActivo] = useState(-1);
  const [error, setError] = useState<string | null>(null);
  const idCampo = useId();
  const idLista = useId();

  const consulta = texto.trim();

  useEffect(() => {
    if (consulta.length < MINIMO_CARACTERES) {
      setResultados(null);
      setError(null);
      return;
    }
    const control = new AbortController();
    const temporizador = setTimeout(() => {
      api
        .buscarPacientes(consulta, control.signal)
        .then((r) => {
          setResultados(r);
          setActivo(r.length > 0 ? 0 : -1);
          setError(null);
        })
        .catch((e: unknown) => {
          if (esErrorAbort(e)) return;
          if (e instanceof ErrorApi && e.esSesionPerdida) return;
          setError(e instanceof ErrorApi ? e.mensaje : MENSAJE_ERROR_GENERICO);
        });
    }, ESPERA_BUSQUEDA_MS);
    return () => {
      clearTimeout(temporizador);
      control.abort();
    };
  }, [consulta]);

  if (seleccionado) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-borde bg-superficie-2 px-3 py-2">
        <p className="flex min-w-0 items-center gap-2">
          <UserRound aria-hidden="true" className="size-4 shrink-0 text-texto-suave" />
          <span className="min-w-0 break-words">
            <span className="font-semibold">{seleccionado.nombre}</span>
            <span className="numeros text-texto-suave"> · {formatearTelefono(seleccionado.telefono)}</span>
          </span>
        </p>
        <Boton variante="fantasma" onClick={() => onSeleccionar(null)}>
          Cambiar paciente
        </Boton>
      </div>
    );
  }

  const opciones = resultados ?? [];
  const expandido = opciones.length > 0;

  function elegir(p: Paciente) {
    onSeleccionar(p);
    setTexto('');
    setResultados(null);
  }

  function alPulsarTecla(e: KeyboardEvent<HTMLInputElement>) {
    if (!expandido) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActivo((a) => Math.min(a + 1, opciones.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActivo((a) => Math.max(a - 1, 0));
    } else if (e.key === 'Enter') {
      const p = opciones[activo];
      if (p) {
        e.preventDefault();
        elegir(p);
      }
    } else if (e.key === 'Escape') {
      e.stopPropagation();
      setResultados(null);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={idCampo} className="text-sm font-semibold">
        Buscar paciente por nombre o teléfono
      </label>
      <div className="relative">
        <Search aria-hidden="true" className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-texto-suave" />
        <input
          id={idCampo}
          type="search"
          role="combobox"
          autoComplete="off"
          aria-autocomplete="list"
          aria-expanded={expandido}
          aria-controls={idLista}
          aria-activedescendant={expandido && activo >= 0 ? `${idLista}-${activo}` : undefined}
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={alPulsarTecla}
          className="min-h-11 w-full rounded-xl border border-borde-fuerte bg-superficie pr-3 pl-9 text-base"
        />
      </div>
      <ul id={idLista} role="listbox" aria-label="Pacientes encontrados" className={expandido ? 'flex flex-col gap-1' : 'hidden'}>
        {opciones.map((p, i) => (
          <li
            key={p.id}
            id={`${idLista}-${i}`}
            role="option"
            aria-selected={i === activo}
            onClick={() => elegir(p)}
            onMouseEnter={() => setActivo(i)}
            className={`flex min-h-11 cursor-pointer flex-wrap items-center justify-between gap-x-3 rounded-xl px-3 py-2 ${
              i === activo ? 'bg-acento-suave' : 'hover:bg-superficie-2'
            }`}
          >
            <span className="font-medium break-words">{p.nombre}</span>
            <span className="numeros text-sm text-texto-suave">{formatearTelefono(p.telefono)}</span>
          </li>
        ))}
      </ul>
      {resultados && resultados.length === 0 && (
        <p role="status" className="text-sm text-texto-suave">
          No hay pacientes que coincidan con la búsqueda.
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-peligro">
          {error}
        </p>
      )}
      <div>
        <Boton variante="fantasma" className="px-2 text-acento" icono={<UserPlus aria-hidden="true" className="size-4" />} onClick={onPacienteNuevo}>
          Paciente nuevo
        </Boton>
      </div>
    </div>
  );
}
