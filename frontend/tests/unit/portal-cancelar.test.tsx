import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PantallaMisCitas } from '../../src/portal/PantallaMisCitas';
import { cuerpoEnviado, json, problema, simularFetch } from './datos';
import { citaPortal, FUERA_DE_PLAZO, misCitas } from './portal-datos';

const deLaura = citaPortal({
  id: 21,
  version: 3,
  paciente: 'Laura Romero',
  fecha: '2026-12-02',
  horaInicio: '09:45',
  horaFin: '10:30',
  profesional: 'Jorge',
  servicio: 'Sesión fisioterapia',
});

function pintar(onRecargar = vi.fn(async () => undefined)) {
  render(
    <PantallaMisCitas
      datos={misCitas({ pacientes: ['Laura Romero'], proximas: [deLaura] })}
      onRecargar={onRecargar}
      onSalir={() => undefined}
    />,
  );
  return onRecargar;
}

async function abrirConfirmacion() {
  await userEvent.click(screen.getByRole('button', { name: 'Cancelar cita' }));
  return screen.findByRole('alertdialog');
}

describe('Cancelar desde el portal', () => {
  it('[002][US3-AC1] la confirmación muestra la fecha, la hora y el profesional', async () => {
    simularFetch({});
    pintar();
    const dialogo = await abrirConfirmacion();
    expect(dialogo).toHaveTextContent('¿Cancelar tu cita del miércoles, 02/12/2026 a las 09:45 con Jorge?');
  });

  it('[002][US3-AC3] «Volver» no cancela nada', async () => {
    const espia = simularFetch({});
    pintar();
    await abrirConfirmacion();
    await userEvent.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(espia).not.toHaveBeenCalled();
  });

  it('[002][US3-AC2] al confirmar envía la versión vista, avisa «Tu cita se ha cancelado.» y recarga del servidor', async () => {
    const espia = simularFetch({
      'POST /api/portal/citas/21/cancelacion': json({ ...deLaura, estadoPortal: 'CANCELADA', cancelable: false }),
    });
    const recargar = pintar();
    await abrirConfirmacion();
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }));

    expect(await screen.findByText('Tu cita se ha cancelado.')).toBeInTheDocument();
    expect(cuerpoEnviado(espia, 'POST', '/api/portal/citas/21/cancelacion')).toEqual([{ version: 3 }]);
    expect(recargar).toHaveBeenCalled();
  });

  it('[002][US3-AC5] si el plazo vence antes de confirmar, explica que hay que llamar', async () => {
    simularFetch({
      'POST /api/portal/citas/21/cancelacion': problema(422, { codigo: 'FUERA_DE_PLAZO', mensaje: FUERA_DE_PLAZO }),
    });
    pintar();
    await abrirConfirmacion();
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }));

    expect(await screen.findByText(FUERA_DE_PLAZO)).toBeInTheDocument();
  });

  it('[002][US3-AC6] si la cita acaba de cambiar, lo avisa y recarga el estado real', async () => {
    simularFetch({
      'POST /api/portal/citas/21/cancelacion': problema(409, {
        codigo: 'ESTADO_YA_CAMBIADO',
        mensaje: 'Esta cita acaba de cambiar. Revisa su estado.',
      }),
    });
    const recargar = pintar();
    await abrirConfirmacion();
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }));

    expect(await screen.findByText('Esta cita acaba de cambiar. Revisa su estado.')).toBeInTheDocument();
    expect(recargar).toHaveBeenCalled();
  });

  it('[002][US3-AC7] mientras espera la respuesta, «Sí, cancelar» queda desactivado', async () => {
    let responder: (r: Response) => void = () => undefined;
    simularFetch({ 'POST /api/portal/citas/21/cancelacion': () => new Promise<Response>((r) => (responder = r)) });
    pintar();
    await abrirConfirmacion();
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Sí, cancelar' })).toBeDisabled());
    responder(json({ ...deLaura, estadoPortal: 'CANCELADA', cancelable: false }));
    expect(await screen.findByText('Tu cita se ha cancelado.')).toBeInTheDocument();
  });
});
