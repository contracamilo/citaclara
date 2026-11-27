import { useState } from 'react';
import { CalendarHeart, LogOut } from 'lucide-react';
import { apiPortal } from '../api/cliente';
import type { CitaPortal, MisCitas } from '../api/tipos';
import { Aviso } from '../ui/Aviso';
import { Boton } from '../ui/Boton';
import { DialogoCancelarCita, type ResultadoCancelacion } from './DialogoCancelarCita';
import { TarjetaCitaPortal } from './TarjetaCitaPortal';

interface PropsMisCitas {
  datos: MisCitas;
  /** Vuelve a pedir las citas al servidor (la respuesta del servidor manda). */
  onRecargar: () => Promise<void>;
  onSalir: () => void;
}

/** «Hola, Teresa» con una ficha; «Hola» con varias (cada tarjeta dice de quién es). */
export function saludo(pacientes: string[]): string {
  if (pacientes.length !== 1) return 'Hola';
  return `Hola, ${pacientes[0]!.split(' ')[0]}`;
}

/** «Mis citas»: próximas y pasadas, cancelación y salida (US2, US3; contracts/ui.md §3). */
export function PantallaMisCitas({ datos, onRecargar, onSalir }: PropsMisCitas) {
  const [aCancelar, setACancelar] = useState<CitaPortal | null>(null);
  const [aviso, setAviso] = useState<{ tipo: 'exito' | 'error'; texto: string } | null>(null);
  const [saliendo, setSaliendo] = useState(false);
  const variasFichas = datos.pacientes.length > 1;

  async function alResultado(resultado: ResultadoCancelacion) {
    setACancelar(null);
    setAviso(
      resultado.tipo === 'cancelada'
        ? { tipo: 'exito', texto: 'Tu cita se ha cancelado.' }
        : { tipo: 'error', texto: resultado.mensaje },
    );
    await onRecargar();
  }

  function salir() {
    setSaliendo(true);
    apiPortal
      .cerrarSesion()
      .catch(() => undefined)
      .finally(onSalir);
  }

  return (
    <>
      <header className="sticky top-0 z-10 border-b border-borde bg-superficie/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-2xl items-center justify-between gap-3 px-4 py-2">
          <p className="flex min-w-0 items-center gap-2 truncate font-bold">
            <CalendarHeart aria-hidden="true" className="size-5 shrink-0 text-acento" />
            <span className="truncate">{datos.clinicaNombre}</span>
          </p>
          <Boton variante="fantasma" icono={<LogOut aria-hidden="true" className="size-4" />} ocupado={saliendo} onClick={salir}>
            Salir
          </Boton>
        </div>
      </header>

      <main className="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-6">
        <h1 className="text-2xl font-bold tracking-tight">{saludo(datos.pacientes)}</h1>

        {aviso && <Aviso tipo={aviso.tipo}>{aviso.texto}</Aviso>}

        <section aria-labelledby="titulo-proximas" className="flex flex-col gap-3">
          <h2 id="titulo-proximas" className="text-lg font-semibold">
            Próximas citas
          </h2>
          {datos.proximas.length === 0 ? (
            <p className="rounded-tarjeta border border-dashed border-borde-fuerte p-4 text-sm text-texto-suave">
              No tienes citas próximas. Si quieres pedir una, llama a la clínica.
            </p>
          ) : (
            datos.proximas.map((cita) => (
              <TarjetaCitaPortal key={cita.id} cita={cita} mostrarPaciente={variasFichas} onCancelar={setACancelar} />
            ))
          )}
        </section>

        {datos.pasadas.length > 0 && (
          <section aria-labelledby="titulo-pasadas" className="flex flex-col gap-3">
            <h2 id="titulo-pasadas" className="text-lg font-semibold">
              Citas pasadas
            </h2>
            {datos.pasadas.map((cita) => (
              <TarjetaCitaPortal key={cita.id} cita={cita} mostrarPaciente={variasFichas} />
            ))}
          </section>
        )}
      </main>

      <DialogoCancelarCita cita={aCancelar} onCerrar={() => setACancelar(null)} onResultado={(r) => void alResultado(r)} />
    </>
  );
}
