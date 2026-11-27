import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PantallaAcceso } from '../../src/acceso/PantallaAcceso';
import { App } from '../../src/App';
import { api, EVENTO_SESION_PERDIDA, ErrorApi } from '../../src/api/cliente';
import { agenda, contexto, cuerpoEnviado, json, maria, problema, simularFetch, sinContenido } from './datos';

const CADUCADA = 'Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.';
const caducada = () =>
  problema(401, { codigo: 'SESION_CADUCADA', mensaje: CADUCADA });

describe('PantallaAcceso', () => {
  it('[001][FR-001][US1-AC1] muestra el nombre de la clínica, «Clave de la clínica» (contraseña que se puede mostrar) y «Entrar», sin datos de agenda', async () => {
    simularFetch({ 'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }) });
    render(<PantallaAcceso identificador="eleva" onEntrado={() => undefined} />);

    expect(await screen.findByRole('heading', { name: 'Clínica Eleva' })).toBeInTheDocument();
    const campo = screen.getByLabelText('Clave de la clínica');
    expect(campo).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar clave' }));
    expect(campo).toHaveAttribute('type', 'text');
    await userEvent.click(screen.getByRole('button', { name: 'Ocultar clave' }));
    expect(campo).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.queryByRole('radiogroup')).toBeNull();
    expect(screen.queryByText(/Libre ·/)).toBeNull();
  });

  it('[001][FR-001] con la clave correcta envía la clave en JSON y entra', async () => {
    const espia = simularFetch({
      'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }),
      'POST /api/clinicas/eleva/sesion': sinContenido(),
    });
    const onEntrado = vi.fn();
    render(<PantallaAcceso identificador="eleva" onEntrado={onEntrado} />);
    await userEvent.type(screen.getByLabelText('Clave de la clínica'), 'eleva-demo-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(onEntrado).toHaveBeenCalled());
    expect(cuerpoEnviado(espia, 'POST', '/api/clinicas/eleva/sesion')).toEqual([{ clave: 'eleva-demo-2026' }]);
    const init = espia.mock.calls.find(([u]) => u === '/api/clinicas/eleva/sesion')![1]!;
    expect(init.credentials).toBe('same-origin');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('[001][US1-AC2] ante una clave incorrecta muestra bajo el campo el mensaje del API, sin el código', async () => {
    simularFetch({
      'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }),
      'POST /api/clinicas/eleva/sesion': problema(401, {
        codigo: 'CLAVE_INCORRECTA',
        mensaje: 'La clave no es correcta. Revísala e inténtalo de nuevo.',
      }),
    });
    const onEntrado = vi.fn();
    render(<PantallaAcceso identificador="eleva" onEntrado={onEntrado} />);
    const campo = screen.getByLabelText('Clave de la clínica');
    await userEvent.type(campo, 'mala');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    const error = await screen.findByText('La clave no es correcta. Revísala e inténtalo de nuevo.');
    expect(campo).toHaveAccessibleDescription('La clave no es correcta. Revísala e inténtalo de nuevo.');
    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(error).toBeVisible();
    expect(document.body.textContent).not.toContain('CLAVE_INCORRECTA');
    expect(document.body.textContent).not.toContain('401');
    expect(onEntrado).not.toHaveBeenCalled();
  });

  it('[001][FR-002] al llegar por caducidad muestra «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.»', async () => {
    simularFetch({ 'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }) });
    render(<PantallaAcceso identificador="eleva" sesionCaducada onEntrado={() => undefined} />);
    expect(await screen.findByText(CADUCADA)).toBeInTheDocument();
  });
});

describe('cliente del API y App', () => {
  it('[001][FR-002] un 401 SESION_CADUCADA en cualquier llamada emite «sesion-perdida»', async () => {
    simularFetch({ 'GET /api/agenda': caducada(), 'POST /api/citas': caducada() });
    const oyente = vi.fn();
    window.addEventListener(EVENTO_SESION_PERDIDA, oyente);
    try {
      await expect(api.obtenerAgenda(1, '2026-11-26')).rejects.toBeInstanceOf(ErrorApi);
      await expect(
        api.crearCita({ profesionalId: 1, servicioId: 10, fecha: '2026-11-27', horaInicio: '10:00', pacienteId: 50 }),
      ).rejects.toMatchObject({ codigo: 'SESION_CADUCADA', mensaje: CADUCADA });
      expect(oyente).toHaveBeenCalledTimes(2);
    } finally {
      window.removeEventListener(EVENTO_SESION_PERDIDA, oyente);
    }
  });

  it('[001][FR-027] un fallo de red se traduce a «No hay conexión con el servidor. Inténtalo de nuevo.»', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
    await expect(api.obtenerAgenda(1)).rejects.toMatchObject({
      mensaje: 'No hay conexión con el servidor. Inténtalo de nuevo.',
    });
  });

  it('[001][FR-002] si la sesión caduca mientras se usa la agenda, App vuelve al acceso con el aviso de caducidad', async () => {
    window.history.replaceState(null, '', '/eleva');
    let agendaCaducada = false;
    simularFetch({
      'GET /api/sesion': json(contexto),
      'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }),
      'GET /api/agenda': () => (agendaCaducada ? caducada() : json(agenda({ profesional: maria, citas: [] }))),
    });
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Agenda de María' })).toBeInTheDocument();

    agendaCaducada = true;
    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }));

    expect(await screen.findByText(CADUCADA)).toBeInTheDocument();
    expect(screen.getByLabelText('Clave de la clínica')).toBeInTheDocument();
    expect(screen.queryByRole('radiogroup')).toBeNull();
  });

  it('[001][FR-001][US1-AC1] sin sesión en la carga inicial App muestra el acceso sin aviso de caducidad', async () => {
    window.history.replaceState(null, '', '/eleva');
    simularFetch({
      'GET /api/sesion': problema(401, { codigo: 'SIN_SESION', mensaje: 'Introduce la clave de la clínica.' }),
      'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }),
    });
    render(<App />);
    expect(await screen.findByLabelText('Clave de la clínica')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Clínica Eleva' })).toBeInTheDocument();
    expect(screen.queryByText(CADUCADA)).toBeNull();
  });

  it('[001][FR-001] la raíz pide abrir la dirección del panel de la clínica', () => {
    window.history.replaceState(null, '', '/');
    render(<App />);
    expect(screen.getByText('Abre la dirección del panel de tu clínica para entrar.')).toBeInTheDocument();
  });
});
