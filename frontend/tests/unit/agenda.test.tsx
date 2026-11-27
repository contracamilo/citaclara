import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LineaTiempo } from '../../src/agenda/LineaTiempo';
import { NavegadorDia } from '../../src/agenda/NavegadorDia';
import { PantallaAgenda } from '../../src/agenda/PantallaAgenda';
import { SelectorProfesional } from '../../src/agenda/SelectorProfesional';
import { TarjetaCita } from '../../src/agenda/TarjetaCita';
import { agenda, cita, contexto, jorge, json, lucia, maria, simularFetch } from './datos';

describe('TarjetaCita', () => {
  it('[001][FR-006][US1-AC4] muestra «10:00 – 10:45», paciente, servicio, «40,00 €» y el distintivo con texto', () => {
    render(<TarjetaCita cita={cita({ acciones: [] })} />);
    const tarjeta = screen.getByRole('article');
    expect(within(tarjeta).getByText('10:00 – 10:45')).toBeInTheDocument();
    expect(within(tarjeta).getByText('Ana Pérez')).toBeInTheDocument();
    expect(within(tarjeta).getByText('Sesión fisioterapia')).toBeInTheDocument();
    expect(within(tarjeta).getByText('40,00 €')).toBeInTheDocument();
    const distintivo = within(tarjeta).getByText('Reservada');
    expect(distintivo.querySelector('svg')).not.toBeNull(); // color + icono + texto
    expect(tarjeta).toHaveAttribute('data-atenuada', 'false');
  });

  it('[001][US1-AC5] las citas canceladas y no asistidas se muestran atenuadas con su estado', () => {
    render(
      <>
        <TarjetaCita cita={cita({ id: 1, estado: 'CANCELADA', acciones: [] })} />
        <TarjetaCita cita={cita({ id: 2, estado: 'NO_ASISTIDA', acciones: [] })} />
        <TarjetaCita cita={cita({ id: 3, estado: 'COMPLETADA', acciones: [] })} />
      </>,
    );
    const [cancelada, noAsistida, completada] = screen.getAllByRole('article');
    expect(cancelada).toHaveAttribute('data-atenuada', 'true');
    expect(within(cancelada!).getByText('Cancelada')).toBeInTheDocument();
    expect(within(cancelada!).getByText('10:00 – 10:45')).toHaveClass('line-through');
    expect(noAsistida).toHaveAttribute('data-atenuada', 'true');
    expect(within(noAsistida!).getByText('No asistida')).toBeInTheDocument();
    expect(completada).toHaveAttribute('data-atenuada', 'false');
    expect(within(completada!).getByText('Completada')).toBeInTheDocument();
  });
});

describe('LineaTiempo', () => {
  it('[001][FR-007][FR-008] intercala tarjetas y huecos en orden y solo los huecos reservables tienen «Reservar aquí»', async () => {
    const onReservar = vi.fn();
    render(
      <LineaTiempo
        agenda={agenda({
          citas: [
            cita({ id: 1, horaInicio: '10:00', horaFin: '10:45', acciones: [] }),
            cita({ id: 2, horaInicio: '12:00', horaFin: '12:45', paciente: { id: 51, nombre: 'Luis Gil', telefono: '+34611111111' }, acciones: [] }),
          ],
          huecos: [
            { horaInicio: '09:00', horaFin: '10:00', reservable: false },
            { horaInicio: '10:45', horaFin: '12:00', reservable: true },
            { horaInicio: '12:45', horaFin: '20:00', reservable: true },
          ],
        })}
        onReservar={onReservar}
      />,
    );
    const elementos = within(screen.getByRole('list', { name: 'Agenda del día' })).getAllByRole('listitem');
    expect(elementos.map((e) => e.textContent)).toEqual([
      expect.stringContaining('Libre · 09:00 – 10:00'),
      expect.stringContaining('10:00 – 10:45'),
      expect.stringContaining('Libre · 10:45 – 12:00'),
      expect.stringContaining('12:00 – 12:45'),
      expect.stringContaining('Libre · 12:45 – 20:00'),
    ]);
    expect(within(elementos[0]!).queryByRole('button', { name: 'Reservar aquí' })).toBeNull();
    expect(screen.getAllByRole('button', { name: 'Reservar aquí' })).toHaveLength(2);

    await userEvent.click(within(elementos[2]!).getByRole('button', { name: 'Reservar aquí' }));
    expect(onReservar).toHaveBeenCalledWith('10:45');
  });

  it('[001][US1-AC5] una cita cancelada no tapa el hueco libre de su franja', () => {
    render(
      <LineaTiempo
        agenda={agenda({
          citas: [cita({ estado: 'CANCELADA', acciones: [] })],
          huecos: [{ horaInicio: '09:00', horaFin: '20:00', reservable: true }],
        })}
      />,
    );
    expect(screen.getByText('Cancelada')).toBeInTheDocument();
    expect(screen.getByText('Libre · 09:00 – 20:00')).toBeInTheDocument();
  });

  it('[001][FR-015] un día sin horario muestra «La clínica no atiende este día.»', () => {
    render(<LineaTiempo agenda={agenda({ horario: null, citas: [], huecos: [] })} />);
    expect(screen.getByText('La clínica no atiende este día.')).toBeInTheDocument();
  });

  it('[001][US1-AC6] un día sin citas muestra un único hueco con todo el horario', () => {
    render(<LineaTiempo agenda={agenda({ citas: [], huecos: [{ horaInicio: '09:00', horaFin: '20:00', reservable: true }] })} onReservar={() => undefined} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText('Libre · 09:00 – 20:00')).toBeInTheDocument();
  });
});

describe('SelectorProfesional', () => {
  it('[001][FR-005][FR-027] muestra el nombre y la especialidad visible de cada profesional («Lucía · Nutrición», nunca «nutricion»)', async () => {
    const onSeleccionar = vi.fn();
    render(<SelectorProfesional profesionales={[maria, jorge, lucia]} seleccionadoId={maria.id} onSeleccionar={onSeleccionar} />);
    const grupo = screen.getByRole('radiogroup', { name: 'Profesional' });
    expect(within(grupo).getByRole('radio', { name: 'María · Fisioterapia' })).toBeChecked();
    expect(within(grupo).getByRole('radio', { name: 'Jorge · Fisioterapia' })).not.toBeChecked();
    expect(within(grupo).getByRole('radio', { name: 'Lucía · Nutrición' })).toBeInTheDocument();
    expect(grupo.textContent).not.toContain('nutricion');

    await userEvent.click(screen.getByRole('radio', { name: 'Lucía · Nutrición' }));
    expect(onSeleccionar).toHaveBeenCalledWith(lucia.id);
  });
});

describe('NavegadorDia', () => {
  it('[001][FR-005] ofrece «Día anterior», «Día siguiente», «Hoy» y fecha concreta con la fecha larga', async () => {
    const onCambiar = vi.fn();
    render(<NavegadorDia fecha="2026-11-27" hoy="2026-11-26" onCambiar={onCambiar} />);
    expect(screen.getByText('viernes, 27/11/2026')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Día anterior' }));
    expect(onCambiar).toHaveBeenLastCalledWith('2026-11-26');
    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }));
    expect(onCambiar).toHaveBeenLastCalledWith('2026-11-28');
    await userEvent.click(screen.getByRole('button', { name: 'Hoy' }));
    expect(onCambiar).toHaveBeenLastCalledWith('2026-11-26');

    const campo = screen.getByLabelText('Ir a una fecha');
    fireEvent.change(campo, { target: { value: '2026-12-03' } });
    expect(onCambiar).toHaveBeenLastCalledWith('2026-12-03');
  });
});

describe('PantallaAgenda', () => {
  it('[001][FR-005][US1-AC3] abre la agenda de hoy del primer profesional y al cambiar de profesional conserva la fecha en la query string', async () => {
    window.history.replaceState(null, '', '/eleva');
    const espia = simularFetch({
      'GET /api/agenda': (url) => {
        const p = new URL(url, 'http://localhost').searchParams;
        const prof = [maria, jorge, lucia].find((x) => String(x.id) === p.get('profesionalId'))!;
        return json(agenda({ profesional: prof, fecha: p.get('fecha') ?? '2026-11-26', citas: [] }));
      },
    });
    render(<PantallaAgenda contexto={contexto} onSalir={() => undefined} />);

    expect(await screen.findByRole('heading', { name: 'Agenda de María' })).toBeInTheDocument();
    expect(String(espia.mock.calls[0]![0])).toBe('/api/agenda?profesionalId=2'); // sin fecha: hoy del servidor
    expect(screen.getByText('jueves, 26/11/2026')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }));
    await waitFor(() => expect(window.location.search).toBe('?profesional=2&fecha=2026-11-27'));
    await userEvent.click(screen.getByRole('radio', { name: 'Lucía · Nutrición' }));
    await waitFor(() => expect(window.location.search).toBe('?profesional=3&fecha=2026-11-27'));
    expect(await screen.findByRole('heading', { name: 'Agenda de Lucía' })).toBeInTheDocument();
    expect(String(espia.mock.calls.at(-1)![0])).toBe('/api/agenda?profesionalId=3&fecha=2026-11-27');
    expect(screen.getByText('viernes, 27/11/2026')).toBeInTheDocument();
  });

  it('[001][FR-005] el enlace con ?profesional=&fecha= reabre la misma vista y el botón atrás vuelve a la anterior', async () => {
    window.history.replaceState(null, '', '/eleva?profesional=1&fecha=2026-11-30');
    const espia = simularFetch({
      'GET /api/agenda': (url) => {
        const p = new URL(url, 'http://localhost').searchParams;
        const prof = [maria, jorge, lucia].find((x) => String(x.id) === p.get('profesionalId'))!;
        return json(agenda({ profesional: prof, fecha: p.get('fecha')!, citas: [] }));
      },
    });
    render(<PantallaAgenda contexto={contexto} onSalir={() => undefined} />);
    expect(await screen.findByRole('heading', { name: 'Agenda de Jorge' })).toBeInTheDocument();
    expect(String(espia.mock.calls[0]![0])).toBe('/api/agenda?profesionalId=1&fecha=2026-11-30');
    expect(screen.getByText('lunes, 30/11/2026')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }));
    expect(await screen.findByText('martes, 01/12/2026')).toBeInTheDocument();

    window.history.back();
    expect(await screen.findByText('lunes, 30/11/2026')).toBeInTheDocument();
  });
});
