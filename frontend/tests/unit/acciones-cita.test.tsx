import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { AccionesCita } from '../../src/agenda/AccionesCita';
import { TarjetaCita } from '../../src/agenda/TarjetaCita';
import type { Cita } from '../../src/api/tipos';
import { cita, cuerpoEnviado, json, problema, simularFetch } from './datos';

const CONFIRMACION = '¿Cancelar la cita de Ana Pérez del 26/11/2026 a las 10:00? El hueco quedará libre.';

describe('AccionesCita', () => {
  it('[001][US3-AC4] una cita futura solo ofrece «Cancelar cita»', () => {
    render(<AccionesCita cita={cita({ acciones: ['CANCELAR'] })} onCambiada={() => undefined} />);
    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual(['Cancelar cita']);
  });

  it('[001][US3-AC5] una cita ya empezada solo ofrece «Completada» y «No asistió»', () => {
    render(<AccionesCita cita={cita({ acciones: ['COMPLETAR', 'MARCAR_NO_ASISTIDA'] })} onCambiada={() => undefined} />);
    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual(['Completada', 'No asistió']);
  });

  it('[001][US3-AC6] una cita cerrada no ofrece ninguna acción', () => {
    render(<AccionesCita cita={cita({ estado: 'COMPLETADA', acciones: [] })} onCambiada={() => undefined} />);
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('[001][FR-022][US3-AC1] «Cancelar cita» pide confirmación con el texto exacto y «No, volver» no envía nada', async () => {
    const espia = simularFetch({});
    const onCambiada = vi.fn();
    render(<AccionesCita cita={cita()} onCambiada={onCambiada} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar cita' }));

    const dialogo = screen.getByRole('alertdialog');
    expect(within(dialogo).getByText(CONFIRMACION)).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Sí, cancelar' })).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'No, volver' }));

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(espia).not.toHaveBeenCalled();
    expect(onCambiada).not.toHaveBeenCalled();
  });

  it('[001][FR-022][US3-AC1] «Sí, cancelar» envía CANCELAR con la versión de la tarjeta', async () => {
    const cancelada = cita({ estado: 'CANCELADA', acciones: [], version: 4 });
    const espia = simularFetch({ 'POST /api/citas/100/estado': json(cancelada) });
    const onCambiada = vi.fn();
    render(<AccionesCita cita={cita({ version: 3 })} onCambiada={onCambiada} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar cita' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }));

    await waitFor(() => expect(onCambiada).toHaveBeenCalledWith(cancelada));
    expect(cuerpoEnviado(espia, 'POST', '/api/citas/100/estado')).toEqual([{ accion: 'CANCELAR', version: 3 }]);
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('[001][FR-023][US3-AC2][US3-AC3] «Completada» y «No asistió» se envían sin confirmación', async () => {
    const espia = simularFetch({
      'POST /api/citas/100/estado': (_u, init) => {
        const { accion } = JSON.parse(String(init?.body));
        return json(cita({ estado: accion === 'COMPLETAR' ? 'COMPLETADA' : 'NO_ASISTIDA', acciones: [], version: 1 }));
      },
    });
    const onCambiada = vi.fn();
    const empezada = cita({ acciones: ['COMPLETAR', 'MARCAR_NO_ASISTIDA'] });
    const { unmount } = render(<AccionesCita cita={empezada} onCambiada={onCambiada} />);
    await userEvent.click(screen.getByRole('button', { name: 'Completada' }));
    await waitFor(() => expect(onCambiada).toHaveBeenLastCalledWith(expect.objectContaining({ estado: 'COMPLETADA' })));
    expect(screen.queryByRole('alertdialog')).toBeNull();
    unmount();

    render(<AccionesCita cita={empezada} onCambiada={onCambiada} />);
    await userEvent.click(screen.getByRole('button', { name: 'No asistió' }));
    await waitFor(() => expect(onCambiada).toHaveBeenLastCalledWith(expect.objectContaining({ estado: 'NO_ASISTIDA' })));
    expect(cuerpoEnviado(espia, 'POST', '/api/citas/100/estado')).toEqual([
      { accion: 'COMPLETAR', version: 0 },
      { accion: 'MARCAR_NO_ASISTIDA', version: 0 },
    ]);
  });

  it('[001][FR-025][US3-AC7] ante 409 ESTADO_YA_CAMBIADO muestra el mensaje y la tarjeta se actualiza con citaActual', async () => {
    const actual = cita({ estado: 'COMPLETADA', acciones: [], version: 1 });
    simularFetch({
      'POST /api/citas/100/estado': problema(409, {
        codigo: 'ESTADO_YA_CAMBIADO',
        mensaje: 'Otra persona acaba de cambiar esta cita. Ahora está: Completada.',
        citaActual: actual,
      }),
    });
    function Contenedor() {
      const [c, setC] = useState<Cita>(cita({ acciones: ['COMPLETAR', 'MARCAR_NO_ASISTIDA'] }));
      return <TarjetaCita cita={c} onCambioEstado={(n) => n && setC(n)} />;
    }
    render(<Contenedor />);
    await userEvent.click(screen.getByRole('button', { name: 'No asistió' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Otra persona acaba de cambiar esta cita. Ahora está: Completada.');
    expect(screen.getByText('Completada', { selector: '[data-estado]' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'No asistió' })).toBeNull();
    expect(document.body.textContent).not.toContain('ESTADO_YA_CAMBIADO');
  });
});
