import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App } from '../../src/App';
import { EVENTO_SESION_PORTAL_PERDIDA } from '../../src/api/cliente';
import { reiniciarEnlace } from '../../src/portal/enlace';
import { PantallaEntradaPortal } from '../../src/portal/PantallaEntradaPortal';
import { cuerpoEnviado, json, problema, simularFetch, sinContenido } from './datos';
import { AVISO_ENVIADO, misCitas } from './portal-datos';

const clinica = { 'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }) };
const sinSesion = problema(401, {
  codigo: 'SIN_SESION_PORTAL',
  mensaje: 'Entra con el enlace que te enviamos por email para ver tus citas.',
});

beforeEach(() => {
  reiniciarEnlace();
  window.history.replaceState(null, '', '/eleva/mis-citas');
});

afterEach(() => {
  window.history.replaceState(null, '', '/');
});

describe('PantallaEntradaPortal', () => {
  it('[002][US1-AC1][US1-AC3] tras pedir el enlace muestra siempre el mismo aviso neutro', async () => {
    const espia = simularFetch({ ...clinica, 'POST /api/portal/eleva/enlaces': new Response(null, { status: 202 }) });
    render(<PantallaEntradaPortal identificador="eleva" />);

    expect(await screen.findByRole('heading', { name: 'Tus citas en Clínica Eleva' })).toBeInTheDocument();
    expect(
      screen.getByText('Escribe el email que diste en la clínica y te enviaremos un enlace para entrar. No necesitas contraseña.'),
    ).toBeInTheDocument();
    const campo = screen.getByLabelText('Tu email');
    expect(campo).toHaveAttribute('type', 'email');
    expect(campo).toHaveAttribute('autocomplete', 'email');
    await userEvent.type(campo, 'nadie@ejemplo.es');
    await userEvent.click(screen.getByRole('button', { name: 'Enviarme el enlace' }));

    expect(await screen.findByText(AVISO_ENVIADO)).toBeInTheDocument();
    expect(cuerpoEnviado(espia, 'POST', '/api/portal/eleva/enlaces')).toEqual([{ email: 'nadie@ejemplo.es' }]);
    await userEvent.click(screen.getByRole('button', { name: 'Pedir otro enlace' }));
    expect(screen.getByLabelText('Tu email')).toBeInTheDocument();
  });

  it('[002][US1-AC7] si se piden demasiados enlaces lo explica sin jerga', async () => {
    simularFetch({
      ...clinica,
      'POST /api/portal/eleva/enlaces': problema(429, {
        codigo: 'ENLACES_AGOTADOS',
        mensaje: 'Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.',
      }),
    });
    render(<PantallaEntradaPortal identificador="eleva" />);
    await userEvent.type(await screen.findByLabelText('Tu email'), 'teresa.mendez@ejemplo.es');
    await userEvent.click(screen.getByRole('button', { name: 'Enviarme el enlace' }));

    expect(await screen.findByText('Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.')).toBeInTheDocument();
  });

  it('[002][FR-002] un email mal escrito se marca en el propio campo', async () => {
    simularFetch({
      ...clinica,
      'POST /api/portal/eleva/enlaces': problema(400, {
        codigo: 'EMAIL_NO_VALIDO',
        mensaje: 'Escribe un email válido, por ejemplo nombre@correo.es.',
      }),
    });
    render(<PantallaEntradaPortal identificador="eleva" />);
    await userEvent.type(await screen.findByLabelText('Tu email'), 'teresa');
    await userEvent.click(screen.getByRole('button', { name: 'Enviarme el enlace' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Escribe un email válido, por ejemplo nombre@correo.es.');
    expect(screen.getByLabelText('Tu email')).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('AppPortal', () => {
  it('[002][US1-AC2] con #enlace= borra el fragmento, abre la sesión y muestra mis citas', async () => {
    window.history.replaceState(null, '', '/eleva/mis-citas#enlace=abc');
    const espia = simularFetch({
      'POST /api/portal/eleva/sesion': sinContenido(),
      'GET /api/portal/citas': json(misCitas()),
    });
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Hola, Teresa' })).toBeInTheDocument();
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/eleva/mis-citas');
    expect(cuerpoEnviado(espia, 'POST', '/api/portal/eleva/sesion')).toEqual([{ enlace: 'abc' }]);
  });

  it('[002][US1-AC4] con un enlace usado o caducado vuelve a la entrada con el aviso', async () => {
    window.history.replaceState(null, '', '/eleva/mis-citas#enlace=usado');
    simularFetch({
      ...clinica,
      'POST /api/portal/eleva/sesion': problema(401, {
        codigo: 'ENLACE_NO_VALIDO',
        mensaje: 'Este enlace ya no es válido. Pide uno nuevo.',
      }),
    });
    render(<App />);

    expect(await screen.findByText('Este enlace ya no es válido. Pide uno nuevo.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tu email')).toBeInTheDocument();
  });

  it('[002][US1-AC5] si la sesión caduca durante el uso, vuelve a la entrada con «Tu acceso ha caducado…»', async () => {
    simularFetch({ ...clinica, 'GET /api/portal/citas': json(misCitas()) });
    render(<App />);
    await screen.findByRole('heading', { name: 'Hola, Teresa' });

    window.dispatchEvent(
      new CustomEvent(EVENTO_SESION_PORTAL_PERDIDA, {
        detail: { codigo: 'SESION_PORTAL_CADUCADA', mensaje: 'Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.' },
      }),
    );

    expect(await screen.findByText('Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tu email')).toBeInTheDocument();
  });

  it('[002][US1-AC6] «Salir» cierra la sesión del portal y vuelve a la entrada', async () => {
    const espia = simularFetch({
      ...clinica,
      'GET /api/portal/citas': json(misCitas()),
      'DELETE /api/portal/sesion': sinContenido(),
    });
    render(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Salir' }));

    expect(await screen.findByLabelText('Tu email')).toBeInTheDocument();
    await waitFor(() =>
      expect(espia.mock.calls.some(([u, i]) => String(u) === '/api/portal/sesion' && i?.method === 'DELETE')).toBe(true),
    );
  });

  it('[002][FR-029] sin sesión del portal muestra la entrada del portal, nunca la clave del panel', async () => {
    simularFetch({ ...clinica, 'GET /api/portal/citas': sinSesion });
    render(<App />);

    expect(await screen.findByLabelText('Tu email')).toBeInTheDocument();
    expect(screen.queryByLabelText('Clave de la clínica')).toBeNull();
  });
});
