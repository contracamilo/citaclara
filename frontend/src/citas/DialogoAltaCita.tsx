import { useId, useMemo, useState, type FormEvent } from 'react';
import { api, ErrorApi, MENSAJE_ERROR_GENERICO } from '../api/cliente';
import type { Cita, HorarioDia, NuevaCita, Paciente, Profesional, Servicio } from '../api/tipos';
import { formatearEuros } from '../formato/euros';
import { formatearFechaLarga, horasEnPasos, sumarMinutos } from '../formato/fechas';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { Dialogo } from '../ui/Dialogo';
import { BuscadorPaciente } from './BuscadorPaciente';
import {
  FormularioPacienteNuevo,
  type Duplicado,
  type ErroresPacienteNuevo,
  type ValoresPacienteNuevo,
} from './FormularioPacienteNuevo';

export const PASO_MINUTOS = 5;

interface PropsDialogoAltaCita {
  abierto: boolean;
  profesional: Profesional;
  fecha: string;
  horario: HorarioDia;
  /** Catálogo completo de la clínica; se filtra por la especialidad del profesional (FR-010). */
  servicios: Servicio[];
  /** Hora propuesta (la del hueco desde «Reservar aquí», FR-008). */
  horaPropuesta?: string | null;
  onCerrar: () => void;
  onReservada: (cita: Cita) => void;
}

export function DialogoAltaCita(props: PropsDialogoAltaCita) {
  // Se monta de nuevo en cada apertura: el estado vive mientras el diálogo está abierto.
  if (!props.abierto) return null;
  return <FormularioAlta {...props} />;
}

const VACIO: ValoresPacienteNuevo = { nombre: '', telefono: '', email: '' };

function FormularioAlta({ profesional, fecha, horario, servicios, horaPropuesta, onCerrar, onReservada }: PropsDialogoAltaCita) {
  const serviciosProfesional = useMemo(
    () => servicios.filter((s) => s.especialidad === profesional.especialidad),
    [servicios, profesional.especialidad],
  );
  const horas = useMemo(() => horasEnPasos(horario.apertura, horario.cierre, PASO_MINUTOS), [horario]);

  const [servicioId, setServicioId] = useState<number | null>(serviciosProfesional[0]?.id ?? null);
  const [hora, setHora] = useState<string>(horaPropuesta && horas.includes(horaPropuesta) ? horaPropuesta : (horas[0] ?? ''));
  const [modo, setModo] = useState<'buscar' | 'nuevo'>('buscar');
  const [paciente, setPaciente] = useState<Paciente | null>(null);
  const [nuevo, setNuevo] = useState<ValoresPacienteNuevo>(VACIO);
  const [errores, setErrores] = useState<ErroresPacienteNuevo>({});
  const [duplicado, setDuplicado] = useState<Duplicado | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const id = useId();

  const servicio = serviciosProfesional.find((s) => s.id === servicioId) ?? null;

  async function reservar(e: FormEvent) {
    e.preventDefault();
    if (!servicio || !hora) return;
    if (modo === 'buscar' && !paciente) {
      setError('Elige un paciente o da de alta uno nuevo.');
      return;
    }
    const base = { profesionalId: profesional.id, servicioId: servicio.id, fecha, horaInicio: hora };
    const peticion: NuevaCita =
      modo === 'buscar' && paciente
        ? { ...base, pacienteId: paciente.id }
        : {
            ...base,
            nuevoPaciente: {
              nombre: nuevo.nombre.trim(),
              telefono: nuevo.telefono.trim(),
              email: nuevo.email.trim() === '' ? null : nuevo.email.trim(),
            },
          };

    setEnviando(true);
    setError(null);
    setErrores({});
    setDuplicado(null);
    try {
      const cita = await api.crearCita(peticion);
      onReservada(cita);
    } catch (e) {
      setEnviando(false);
      if (!(e instanceof ErrorApi)) {
        setError(MENSAJE_ERROR_GENERICO);
        return;
      }
      // 401: App vuelve al acceso con el aviso de caducidad; la cita no se ha registrado.
      if (e.esSesionPerdida) return;
      if (e.codigo === 'PACIENTE_DUPLICADO' && e.pacienteExistente) {
        setDuplicado({ paciente: e.pacienteExistente, mensaje: e.mensaje });
        return;
      }
      // FR-016: se muestra el mensaje del API arriba y se conserva todo lo introducido.
      setError(e.mensaje);
      const porCampo: ErroresPacienteNuevo = {};
      for (const { campo, mensaje } of e.errores ?? []) {
        const nombre = campo.replace(/^nuevoPaciente\./, '');
        if (nombre === 'nombre' || nombre === 'telefono' || nombre === 'email') porCampo[nombre] = mensaje;
      }
      setErrores(porCampo);
    }
  }

  function usarFicha(p: Paciente) {
    setPaciente(p);
    setModo('buscar');
    setDuplicado(null);
    setError(null);
  }

  return (
    <Dialogo
      abierto
      titulo="Nueva cita"
      onCerrar={onCerrar}
      pie={
        <>
          <Boton variante="secundaria" onClick={onCerrar}>
            Volver a la agenda
          </Boton>
          <Boton type="submit" form={`${id}-form`} variante="primaria" ocupado={enviando}>
            Reservar cita
          </Boton>
        </>
      }
    >
      <form id={`${id}-form`} onSubmit={reservar} noValidate className="flex flex-col gap-4">
        {error && <Aviso tipo="error">{error}</Aviso>}

        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          <dt className="text-texto-suave">Profesional</dt>
          <dd className="font-semibold">
            {profesional.nombre} · {profesional.especialidadNombre}
          </dd>
          <dt className="text-texto-suave">Día</dt>
          <dd className="numeros font-semibold">{formatearFechaLarga(fecha)}</dd>
        </dl>

        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-servicio`} className="text-sm font-semibold">
            Servicio
          </label>
          <select
            id={`${id}-servicio`}
            value={servicioId ?? ''}
            onChange={(e) => setServicioId(Number(e.target.value))}
            className="min-h-11 w-full min-w-0 rounded-xl border border-borde-fuerte bg-superficie px-3 text-base"
          >
            {serviciosProfesional.map((s) => (
              <option key={s.id} value={s.id}>
                {`${s.nombre} · ${s.duracionMinutos} min · ${formatearEuros(s.precioCentimos)}`}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-hora`} className="text-sm font-semibold">
            Hora de inicio
          </label>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <select
              id={`${id}-hora`}
              value={hora}
              onChange={(e) => setHora(e.target.value)}
              className="numeros min-h-11 rounded-xl border border-borde-fuerte bg-superficie px-3 text-base"
            >
              {horas.map((h) => (
                <option key={h} value={h}>
                  {h}
                </option>
              ))}
            </select>
            {servicio && hora && (
              <p className="numeros text-sm text-texto-suave" aria-live="polite">
                Termina a las {sumarMinutos(hora, servicio.duracionMinutos)}
              </p>
            )}
          </div>
        </div>

        <div className="border-t border-borde pt-4">
          {modo === 'buscar' ? (
            <BuscadorPaciente
              seleccionado={paciente}
              onSeleccionar={(p) => {
                setPaciente(p);
                if (p) setError(null);
              }}
              onPacienteNuevo={() => {
                setModo('nuevo');
                setError(null);
              }}
            />
          ) : (
            <FormularioPacienteNuevo
              valores={nuevo}
              onCambiar={setNuevo}
              errores={errores}
              duplicado={duplicado}
              onUsarFicha={usarFicha}
              onBuscarExistente={() => {
                setModo('buscar');
                setDuplicado(null);
                setError(null);
              }}
            />
          )}
        </div>
      </form>
    </Dialogo>
  );
}
