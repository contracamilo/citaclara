import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PantallaAgenda } from '../../src/agenda/PantallaAgenda';
import { DialogoAltaCita } from '../../src/citas/DialogoAltaCita';
import { agenda, cita, contexto, cuerpoEnviado, jorge, json, lucia, problema, simularFetch } from './datos';

const ana = { id: 50, nombre: 'Ana Pérez', telefono: '+34612345678', email: null };
const CODIGOS = /SOLAPE|HUECO_RECIEN_OCUPADO|PACIENTE_DUPLICADO|HORA_PASADA|FUERA_DE_HORARIO|DATOS_NO_VALIDOS|\b(400|409|422)\b/;

function abrir(props: Partial<Parameters<typeof DialogoAltaCita>[0]> = {}) {
  const onReservada = vi.fn();
  render(
    <DialogoAltaCita
      abierto
      profesional={jorge}
      fecha="2026-11-27"
      horario={{ apertura: '09:00', cierre: '20:00' }}
      servicios={contexto.servicios}
      horaPropuesta="10:00"
      onCerrar={() => undefined}
      onReservada={onReservada}
      {...props}
    />,
  );
  return { onReservada, dialogo: screen.getByRole('dialog', { name: 'Nueva cita' }) };
}

async function elegirAna() {
  await userEvent.type(screen.getByRole('combobox', { name: 'Buscar paciente por nombre o teléfono' }), 'ana');
  await userEvent.click(await screen.findByRole('option', { name: /Ana Pérez/ }));
}

describe('DialogoAltaCita', () => {
  it('[001][FR-010] solo ofrece los servicios de la especialidad del profesional, con «Nombre · N min · 40,00 €»', () => {
    abrir();
    const opciones = within(screen.getByRole('combobox', { name: 'Servicio' })).getAllByRole('option');
    expect(opciones.map((o) => o.textContent?.replace(/ /g, ' '))).toEqual([
      'Sesión fisioterapia · 45 min · 40,00 €',
      'Primera visita fisioterapia · 60 min · 50,00 €',
    ]);
  });

  it('[001][FR-010] con Lucía solo ofrece servicios de nutrición y muestra su especialidad visible', () => {
    abrir({ profesional: lucia });
    const opciones = within(screen.getByRole('combobox', { name: 'Servicio' })).getAllByRole('option');
    expect(opciones.map((o) => o.textContent)).toEqual([
      expect.stringContaining('Consulta nutrición · 30 min'),
      expect.stringContaining('Primera visita nutrición · 45 min'),
    ]);
    expect(screen.getByText('Lucía · Nutrición')).toBeInTheDocument();
  });

  it('[001][FR-009] la hora avanza de 5 en 5 min dentro del horario y «Termina a las 10:45» se calcula y no es editable', async () => {
    abrir();
    const hora = screen.getByRole('combobox', { name: 'Hora de inicio' });
    const valores = within(hora).getAllByRole('option').map((o) => o.textContent);
    expect(valores[0]).toBe('09:00');
    expect(valores[1]).toBe('09:05');
    expect(valores.at(-1)).toBe('19:55');
    expect(valores).toHaveLength(132);
    expect(screen.getByText('Termina a las 10:45')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /Termina/ })).toBeNull();

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Servicio' }), '11');
    expect(screen.getByText('Termina a las 11:00')).toBeInTheDocument();
    await userEvent.selectOptions(hora, '12:35');
    expect(screen.getByText('Termina a las 13:35')).toBeInTheDocument();
  });

  it('[001][FR-008] abierto desde «Reservar aquí» llega con profesional, día y hora del hueco', async () => {
    window.history.replaceState(null, '', '/eleva?profesional=1&fecha=2026-11-27');
    simularFetch({ 'GET /api/agenda': json(agenda({ fecha: '2026-11-27', profesional: jorge })) });
    render(<PantallaAgenda contexto={contexto} onSalir={() => undefined} />);
    const botones = await screen.findAllByRole('button', { name: 'Reservar aquí' });
    await userEvent.click(botones[1]!); // hueco 10:45 – 20:00

    const dialogo = screen.getByRole('dialog', { name: 'Nueva cita' });
    expect(within(dialogo).getByText('Jorge · Fisioterapia')).toBeInTheDocument();
    expect(within(dialogo).getByText('viernes, 27/11/2026')).toBeInTheDocument();
    expect(within(dialogo).getByRole('combobox', { name: 'Hora de inicio' })).toHaveValue('10:45');
  });

  it('[001][FR-016] ante un 409 muestra el mensaje del API arriba y conserva servicio, hora y paciente elegido', async () => {
    const espia = simularFetch({
      'GET /api/pacientes': json([ana]),
      'POST /api/citas': problema(409, {
        codigo: 'SOLAPE',
        mensaje: 'Jorge ya tiene una cita de 10:00 a 10:45. Elige otro hueco.',
        citaConflictiva: cita(),
      }),
    });
    const { onReservada, dialogo } = abrir();
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Servicio' }), '11');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Hora de inicio' }), '10:30');
    await elegirAna();
    await userEvent.click(screen.getByRole('button', { name: 'Reservar cita' }));

    const aviso = await within(dialogo).findByRole('alert');
    expect(aviso).toHaveTextContent('Jorge ya tiene una cita de 10:00 a 10:45. Elige otro hueco.');
    const cuerpo = dialogo.querySelector('.cuerpo-dialogo')!;
    expect(cuerpo.firstElementChild!.contains(aviso)).toBe(true); // en la parte superior
    expect(screen.getByRole('combobox', { name: 'Servicio' })).toHaveValue('11');
    expect(screen.getByRole('combobox', { name: 'Hora de inicio' })).toHaveValue('10:30');
    expect(screen.getByText('Ana Pérez')).toBeInTheDocument();
    expect(cuerpoEnviado(espia, 'POST', '/api/citas')).toEqual([
      { profesionalId: 1, servicioId: 11, fecha: '2026-11-27', horaInicio: '10:30', pacienteId: 50 },
    ]);
    expect(onReservada).not.toHaveBeenCalled();
    expect(document.body.textContent).not.toMatch(CODIGOS);
  });

  it('[001][FR-016] ante un 422 conserva los datos del paciente nuevo y muestra los errores por campo', async () => {
    simularFetch({
      'POST /api/citas': problema(422, {
        codigo: 'FUERA_DE_HORARIO',
        mensaje: 'La cita debe empezar y terminar dentro del horario de atención (09:00 a 20:00).',
      }),
    });
    const { dialogo } = abrir({ horaPropuesta: '19:30' });
    await userEvent.click(screen.getByRole('button', { name: 'Paciente nuevo' }));
    await userEvent.type(screen.getByRole('textbox', { name: /Nombre/ }), 'Pedro Ruiz');
    await userEvent.type(screen.getByRole('textbox', { name: /Teléfono/ }), '611 222 333');
    await userEvent.type(screen.getByRole('textbox', { name: /Email/ }), 'pedro@ejemplo.es');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar cita' }));

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'La cita debe empezar y terminar dentro del horario de atención (09:00 a 20:00).',
    );
    expect(screen.getByRole('textbox', { name: /Nombre/ })).toHaveValue('Pedro Ruiz');
    expect(screen.getByRole('textbox', { name: /Teléfono/ })).toHaveValue('611 222 333');
    expect(screen.getByRole('textbox', { name: /Email/ })).toHaveValue('pedro@ejemplo.es');
    expect(screen.getByRole('combobox', { name: 'Hora de inicio' })).toHaveValue('19:30');
    expect(document.body.textContent).not.toMatch(CODIGOS);
  });

  it('[001][FR-019] muestra junto a cada campo los errores por campo del API', async () => {
    simularFetch({
      'POST /api/citas': problema(400, {
        codigo: 'DATOS_NO_VALIDOS',
        mensaje: 'Revisa los datos marcados.',
        errores: [{ campo: 'nuevoPaciente.telefono', mensaje: 'Escribe un teléfono válido, por ejemplo 612 345 678.' }],
      }),
    });
    abrir();
    await userEvent.click(screen.getByRole('button', { name: 'Paciente nuevo' }));
    await userEvent.type(screen.getByRole('textbox', { name: /Nombre/ }), 'Pedro Ruiz');
    await userEvent.type(screen.getByRole('textbox', { name: /Teléfono/ }), '123');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar cita' }));

    const telefono = screen.getByRole('textbox', { name: /Teléfono/ });
    await waitFor(() => expect(telefono).toHaveAccessibleDescription('Escribe un teléfono válido, por ejemplo 612 345 678.'));
    expect(telefono).toHaveAttribute('aria-invalid', 'true');
  });

  it('[001][FR-020] ante PACIENTE_DUPLICADO ofrece «Usar esta ficha», que selecciona la ficha existente sin perder el resto', async () => {
    const espia = simularFetch({
      'POST /api/citas': (_u, init) => {
        const cuerpo = JSON.parse(String(init?.body));
        return cuerpo.nuevoPaciente
          ? problema(409, {
              codigo: 'PACIENTE_DUPLICADO',
              mensaje: 'Ya hay un paciente con ese teléfono: Ana Pérez. ¿Quieres usar su ficha?',
              pacienteExistente: ana,
            })
          : json(cita({ id: 300, fecha: '2026-11-27', horaInicio: '11:00', horaFin: '11:45' }), 201);
      },
    });
    const { onReservada } = abrir({ horaPropuesta: '11:00' });
    await userEvent.click(screen.getByRole('button', { name: 'Paciente nuevo' }));
    await userEvent.type(screen.getByRole('textbox', { name: /Nombre/ }), 'Ana P.');
    await userEvent.type(screen.getByRole('textbox', { name: /Teléfono/ }), '612-345-678');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar cita' }));

    expect(await screen.findByText('Ya hay un paciente con ese teléfono: Ana Pérez. ¿Quieres usar su ficha?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Usar esta ficha' }));
    expect(screen.getByText('Ana Pérez')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Hora de inicio' })).toHaveValue('11:00');
    expect(screen.getByRole('combobox', { name: 'Servicio' })).toHaveValue('10');

    await userEvent.click(screen.getByRole('button', { name: 'Reservar cita' }));
    await waitFor(() => expect(onReservada).toHaveBeenCalled());
    expect(cuerpoEnviado(espia, 'POST', '/api/citas')[1]).toEqual({
      profesionalId: 1,
      servicioId: 10,
      fecha: '2026-11-27',
      horaInicio: '11:00',
      pacienteId: 50,
    });
    expect(document.body.textContent).not.toMatch(CODIGOS);
  });

  it('[001][FR-019] Nombre y Teléfono son obligatorios y Email es opcional', async () => {
    abrir();
    await userEvent.click(screen.getByRole('button', { name: 'Paciente nuevo' }));
    expect(screen.getByRole('textbox', { name: /Nombre/ })).toBeRequired();
    expect(screen.getByRole('textbox', { name: /Teléfono/ })).toBeRequired();
    expect(screen.getByRole('textbox', { name: /Teléfono/ })).toHaveAttribute('inputmode', 'tel');
    const email = screen.getByRole('textbox', { name: /Email/ });
    expect(email).not.toBeRequired();
    expect(screen.getByText('(opcional)')).toBeInTheDocument();
  });

  it('[001][FR-018] el buscador encuentra pacientes por nombre o teléfono a partir de 2 caracteres', async () => {
    const espia = simularFetch({ 'GET /api/pacientes': json([ana]) });
    abrir();
    const buscador = screen.getByRole('combobox', { name: 'Buscar paciente por nombre o teléfono' });
    await userEvent.type(buscador, 'a');
    await new Promise((r) => setTimeout(r, 400));
    expect(espia).not.toHaveBeenCalled();
    await userEvent.type(buscador, 'n');
    const opcion = await screen.findByRole('option', { name: /Ana Pérez/ });
    expect(opcion).toHaveTextContent('612 345 678');
    expect(String(espia.mock.calls.at(-1)![0])).toBe('/api/pacientes?q=an');
    await userEvent.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: 'Cambiar paciente' })).toBeInTheDocument();
  });

  it('[001][US2-AC1] al reservar cierra el diálogo, recarga la agenda y muestra «Cita reservada para Ana Pérez a las 10:45.»', async () => {
    window.history.replaceState(null, '', '/eleva?profesional=1&fecha=2026-11-27');
    const nueva = cita({ id: 301, fecha: '2026-11-27', horaInicio: '10:45', horaFin: '11:30' });
    let reservada = false;
    simularFetch({
      'GET /api/agenda': () =>
        json(agenda({ fecha: '2026-11-27', profesional: jorge, citas: reservada ? [cita(), nueva] : [cita()] })),
      'GET /api/pacientes': json([ana]),
      'POST /api/citas': () => {
        reservada = true;
        return json(nueva, 201);
      },
    });
    render(<PantallaAgenda contexto={contexto} onSalir={() => undefined} />);
    await userEvent.click((await screen.findAllByRole('button', { name: 'Reservar aquí' }))[1]!);
    await elegirAna();
    await userEvent.click(screen.getByRole('button', { name: 'Reservar cita' }));

    expect(await screen.findByText('Cita reservada para Ana Pérez a las 10:45.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
    const tarjeta = await screen.findByRole('article', { name: 'Cita 10:45 – 11:30 · Ana Pérez' });
    expect(tarjeta).toHaveClass('resaltada');
  });
});
