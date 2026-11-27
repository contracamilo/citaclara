import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { AvisoCancelaciones } from '../../src/cancelaciones/AvisoCancelaciones';
import { PantallaAgenda } from '../../src/agenda/PantallaAgenda';
import { agenda, contexto, json, simularFetch, sinContenido } from './datos';
import { cancelacionPortal } from './portal-datos';

const conPendientes = (cancelaciones = [cancelacionPortal()]) =>
  json({ pendientes: cancelaciones.length, cancelaciones });

describe('AvisoCancelaciones', () => {
  it('[002][FR-019] sin pendientes no se muestra; con 1 o 3 dice cuántas hay', async () => {
    const espia = simularFetch({ 'GET /api/cancelaciones-portal': conPendientes([]) });
    const { rerender } = render(<AvisoCancelaciones clave="a" />);
    await waitFor(() => expect(espia).toHaveBeenCalled());
    expect(screen.queryByRole('button')).toBeNull();

    simularFetch({ 'GET /api/cancelaciones-portal': conPendientes() });
    rerender(<AvisoCancelaciones clave="b" />);
    expect(await screen.findByRole('button', { name: '1 cancelación de pacientes por revisar' })).toBeInTheDocument();

    simularFetch({
      'GET /api/cancelaciones-portal': conPendientes([
        cancelacionPortal({ citaId: 1 }),
        cancelacionPortal({ citaId: 2 }),
        cancelacionPortal({ citaId: 3 }),
      ]),
    });
    rerender(<AvisoCancelaciones clave="c" />);
    expect(await screen.findByRole('button', { name: '3 cancelaciones de pacientes por revisar' })).toBeInTheDocument();
  });

  it('[002][FR-020][US4-AC2][US4-AC6] el diálogo lista paciente, servicio, profesional, hueco y momento de la cancelación', async () => {
    simularFetch({
      'GET /api/cancelaciones-portal': conPendientes([cancelacionPortal(), cancelacionPortal({ citaId: 22, huecoPasado: true })]),
    });
    render(<AvisoCancelaciones clave="a" />);
    await userEvent.click(await screen.findByRole('button', { name: '2 cancelaciones de pacientes por revisar' }));
    const dialogo = screen.getByRole('dialog', { name: 'Cancelaciones de pacientes' });

    const elementos = within(dialogo).getAllByRole('listitem');
    expect(elementos[0]).toHaveTextContent('Laura Romero · Sesión fisioterapia');
    expect(elementos[0]).toHaveTextContent('Jorge · miércoles, 02/12/2026 · 09:45–10:30');
    expect(elementos[0]).toHaveTextContent('Cancelada el 01/12/2026 a las 09:00');
    expect(elementos[0]).not.toHaveTextContent('Hueco ya pasado');
    expect(elementos[1]).toHaveTextContent('Hueco ya pasado');
  });

  it('[002][US4-AC3] «Marcar como revisada» la quita; sin pendientes, el diálogo lo dice y el aviso desaparece', async () => {
    simularFetch({
      'GET /api/cancelaciones-portal': conPendientes(),
      'POST /api/cancelaciones-portal/21/revision': sinContenido(),
    });
    render(<AvisoCancelaciones clave="a" />);
    await userEvent.click(await screen.findByRole('button', { name: '1 cancelación de pacientes por revisar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como revisada' }));

    expect(await screen.findByText('No hay cancelaciones pendientes.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /por revisar/ })).toBeNull();
  });

  it('[002][SC-008] la agenda vuelve a pedir el aviso al cambiar de profesional', async () => {
    const espia = simularFetch({
      'GET /api/cancelaciones-portal': conPendientes([]),
      'GET /api/agenda': json(agenda()),
    });
    window.history.replaceState(null, '', '/eleva');
    render(<PantallaAgenda contexto={contexto} onSalir={() => undefined} />);
    const avisos = () => espia.mock.calls.filter(([u]) => String(u) === '/api/cancelaciones-portal').length;
    await waitFor(() => expect(avisos()).toBe(1));

    await userEvent.click(screen.getByRole('radio', { name: /Lucía/ }));

    await waitFor(() => expect(avisos()).toBe(2));
    window.history.replaceState(null, '', '/');
  });
});
