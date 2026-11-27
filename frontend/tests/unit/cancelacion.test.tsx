import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App, enlaceDeCancelacion } from '../../src/App';
import { PantallaCancelacion } from '../../src/cancelacion/PantallaCancelacion';
import { TarjetaCita } from '../../src/agenda/TarjetaCita';
import type { Cancelacion } from '../../src/api/tipos';
import { cita, json, problema, simularFetch } from './datos';

const ENLACE = 'A'.repeat(43);
const RUTA = `/api/cancelacion/${ENLACE}`;
const TELEFONO = '+34 910 000 000';

function cancelacion(parcial: Partial<Cancelacion> = {}): Cancelacion {
  return {
    situacion: 'ACTIVA',
    canceladaPorPaciente: false,
    clinica: { nombre: 'Clínica Eleva', telefono: TELEFONO },
    cita: { fecha: '2026-11-30', horaInicio: '10:00', profesional: 'María', servicio: 'Sesión fisioterapia' },
    ...parcial,
  };
}

const cancelada = () => cancelacion({ situacion: 'YA_CERRADA', canceladaPorPaciente: true });

describe('PantallaCancelacion (003)', () => {
  it('[003][FR-017][US2-AC1] una cita activa se muestra con los dos botones y abrirla no llama al POST', async () => {
    const espia = simularFetch({ [`GET ${RUTA}`]: json(cancelacion()) });
    render(<PantallaCancelacion enlace={ENLACE} />);

    expect(await screen.findByRole('heading', { name: '¿Cancelamos tu cita?' })).toBeInTheDocument();
    expect(screen.getByText('Sesión fisioterapia')).toBeInTheDocument();
    expect(screen.getByText('con María')).toBeInTheDocument();
    expect(screen.getByText('lunes 30/11/2026 · 10:00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sí, cancelar mi cita' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'No, mantener mi cita' })).toBeInTheDocument();
    expect(espia.mock.calls.filter(([, i]) => (i?.method ?? 'GET') === 'POST')).toHaveLength(0);
  });

  it('[003][FR-012][US2-AC3] «No, mantener mi cita» no envía nada', async () => {
    const espia = simularFetch({ [`GET ${RUTA}`]: json(cancelacion()) });
    render(<PantallaCancelacion enlace={ENLACE} />);
    await userEvent.click(await screen.findByRole('button', { name: 'No, mantener mi cita' }));

    expect(screen.getByText('Tu cita se mantiene. ¡Te esperamos!')).toBeInTheDocument();
    expect(espia).toHaveBeenCalledTimes(1);
  });

  it('[003][FR-017][US2-AC2] confirmar envía el POST y muestra «Tu cita está cancelada» sin recargar', async () => {
    const espia = simularFetch({ [`GET ${RUTA}`]: json(cancelacion()), [`POST ${RUTA}`]: json(cancelada()) });
    render(<PantallaCancelacion enlace={ENLACE} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, cancelar mi cita' }));

    expect(await screen.findByRole('heading', { name: 'Tu cita está cancelada' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sí, cancelar mi cita' })).toBeNull();
    expect(espia.mock.calls.filter(([, i]) => i?.method === 'POST')).toHaveLength(1);
  });

  it('[003][FR-017][US2-AC2] mientras se envía, el botón queda desactivado y no admite doble envío', async () => {
    let liberar: (r: Response) => void = () => undefined;
    const espia = simularFetch({
      [`GET ${RUTA}`]: json(cancelacion()),
      [`POST ${RUTA}`]: () => new Promise<Response>((resolver) => (liberar = resolver)),
    });
    render(<PantallaCancelacion enlace={ENLACE} />);
    const boton = await screen.findByRole('button', { name: 'Sí, cancelar mi cita' });
    await userEvent.click(boton);
    await userEvent.click(boton);

    expect(boton).toBeDisabled();
    expect(espia.mock.calls.filter(([, i]) => i?.method === 'POST')).toHaveLength(1);
    liberar(json(cancelada()));
    expect(await screen.findByRole('heading', { name: 'Tu cita está cancelada' })).toBeInTheDocument();
  });

  it('[003][FR-017][US2-AC5] fuera de plazo: teléfono como enlace tel: y sin botón de cancelar', async () => {
    simularFetch({ [`GET ${RUTA}`]: json(cancelacion({ situacion: 'FUERA_DE_PLAZO' })) });
    render(<PantallaCancelacion enlace={ENLACE} />);

    expect(await screen.findByRole('heading', { name: 'Ya no puedes cancelar desde aquí.' })).toBeInTheDocument();
    expect(screen.getByText(/Faltan menos de 24 horas/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: TELEFONO })).toHaveAttribute('href', 'tel:+34910000000');
    expect(screen.queryByRole('button', { name: 'Sí, cancelar mi cita' })).toBeNull();
  });

  it('[003][FR-017][US2-AC5] si el POST responde fuera de plazo (422) se muestra la situación real', async () => {
    simularFetch({
      [`GET ${RUTA}`]: json(cancelacion()),
      [`POST ${RUTA}`]: problema(422, {
        codigo: 'FUERA_DE_PLAZO',
        mensaje: `Ya no puedes cancelar desde aquí. Llama a la clínica al ${TELEFONO}.`,
        cancelacion: cancelacion({ situacion: 'FUERA_DE_PLAZO' }),
      }),
    });
    render(<PantallaCancelacion enlace={ENLACE} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, cancelar mi cita' }));

    expect(await screen.findByRole('heading', { name: 'Ya no puedes cancelar desde aquí.' })).toBeInTheDocument();
  });

  it('[003][FR-017][US2-AC4] una cita ya no activa se explica sin ofrecer cancelar', async () => {
    simularFetch({ [`GET ${RUTA}`]: json(cancelacion({ situacion: 'YA_CERRADA' })) });
    render(<PantallaCancelacion enlace={ENLACE} />);

    expect(await screen.findByRole('heading', { name: 'Esta cita ya no está activa.' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: TELEFONO })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sí, cancelar mi cita' })).toBeNull();
  });

  it('[003][FR-017][US2-AC4] si otra persona cambió la cita (409) se muestra el estado real', async () => {
    simularFetch({
      [`GET ${RUTA}`]: json(cancelacion()),
      [`POST ${RUTA}`]: problema(409, {
        codigo: 'ESTADO_YA_CAMBIADO',
        mensaje: 'Esta cita ya no está activa. Si tienes dudas, llama a la clínica.',
        cancelacion: cancelacion({ situacion: 'YA_CERRADA' }),
      }),
    });
    render(<PantallaCancelacion enlace={ENLACE} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, cancelar mi cita' }));

    expect(await screen.findByRole('heading', { name: 'Esta cita ya no está activa.' })).toBeInTheDocument();
  });

  it('[003][FR-018][US2-AC6] un enlace no válido no revela nada de ninguna clínica', async () => {
    simularFetch({
      [`GET ${RUTA}`]: problema(404, { codigo: 'NO_ENCONTRADO', mensaje: 'No hemos encontrado lo que buscas.' }),
    });
    render(<PantallaCancelacion enlace={ENLACE} />);

    expect(await screen.findByRole('heading', { name: 'No hemos encontrado lo que buscas.' })).toBeInTheDocument();
    expect(screen.getByText('Revisa que has abierto el enlace completo del correo.')).toBeInTheDocument();
    expect(screen.queryByText(/Eleva/)).toBeNull();
  });

  it('[003][FR-017][US2-AC1] un error del servidor ofrece «Reintentar» con el mensaje genérico', async () => {
    let intentos = 0;
    simularFetch({
      [`GET ${RUTA}`]: () => (++intentos === 1 ? problema(500, { codigo: 'ERROR_INTERNO', mensaje: 'Algo ha fallado. Inténtalo de nuevo en unos segundos.' }) : json(cancelacion())),
    });
    render(<PantallaCancelacion enlace={ENLACE} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Algo ha fallado. Inténtalo de nuevo en unos segundos.');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: '¿Cancelamos tu cita?' })).toBeInTheDocument());
  });
});

describe('Ruta pública del enlace (003)', () => {
  it('[003][FR-012][US2-AC1] /cancelar/{enlace} abre la pantalla del paciente sin consultar la sesión de recepción', async () => {
    window.history.replaceState(null, '', `/cancelar/${ENLACE}`);
    const espia = simularFetch({ [`GET ${RUTA}`]: json(cancelacion()) });
    render(<App />);

    expect(await screen.findByRole('heading', { name: '¿Cancelamos tu cita?' })).toBeInTheDocument();
    expect(espia.mock.calls.map(([u]) => String(u))).toEqual([RUTA]);
  });

  it('[003][FR-012][US2-AC1] solo «/cancelar/{enlace}» es una ruta de cancelación; «cancelar» sin enlace no lo es', () => {
    expect(enlaceDeCancelacion(`/cancelar/${ENLACE}`)).toBe(ENLACE);
    expect(enlaceDeCancelacion('/cancelar')).toBeNull();
    expect(enlaceDeCancelacion('/eleva')).toBeNull();
  });
});

describe('TarjetaCita y quién canceló (003)', () => {
  it('[003][FR-016][US2-AC8] una cita cancelada por el paciente lo indica; la cancelada por la clínica no', () => {
    const { rerender } = render(<TarjetaCita cita={cita({ estado: 'CANCELADA', acciones: [], canceladaPor: 'PACIENTE' })} />);
    expect(screen.getByText('Cancelada por el paciente')).toBeInTheDocument();

    rerender(<TarjetaCita cita={cita({ estado: 'CANCELADA', acciones: [], canceladaPor: null })} />);
    expect(screen.queryByText('Cancelada por el paciente')).toBeNull();
  });
});
