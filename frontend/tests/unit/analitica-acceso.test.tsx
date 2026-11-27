import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from '../../src/App';
import { analiticaEleva, contexto, json, problema, simularFetch } from './datos';

const CADUCADA = 'Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.';

function abrir(ruta: string) {
  window.history.replaceState(null, '', ruta);
  return render(<App />);
}

describe('Acceso y estados de la analítica (US4)', () => {
  it('[004][FR-001][US4-AC1] en /eleva/analitica sin sesión se muestra la pantalla de acceso y ningún dato', async () => {
    simularFetch({
      'GET /api/sesion': problema(401, { codigo: 'SIN_SESION', mensaje: 'Introduce la clave de la clínica.' }),
      'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }),
    });
    abrir('/eleva/analitica');
    expect(await screen.findByLabelText('Clave de la clínica')).toBeInTheDocument();
    expect(screen.queryByText('Analítica de la clínica')).toBeNull();
    expect(screen.queryByText(/Ingresos por servicio/)).toBeNull();
  });

  it('[004][FR-001][US4-AC1] una sesión caducada muestra «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.»', async () => {
    simularFetch({
      'GET /api/sesion': json(contexto),
      'GET /api/analitica': problema(401, { codigo: 'SESION_CADUCADA', mensaje: CADUCADA }),
      'GET /api/clinicas/eleva': json({ nombre: 'Clínica Eleva' }),
    });
    abrir('/eleva/analitica');
    expect(await screen.findByText(CADUCADA)).toBeInTheDocument();
    expect(screen.queryByText(/Ingresos por servicio/)).toBeNull();
  });

  it('[004][FR-001][US4-AC1] con sesión, /eleva/analitica muestra la analítica con los enlaces «Agenda» y «Analítica»', async () => {
    simularFetch({ 'GET /api/sesion': json(contexto), 'GET /api/analitica': json(analiticaEleva) });
    abrir('/eleva/analitica');
    expect(await screen.findByRole('heading', { name: 'Analítica de la clínica' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Analítica' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Agenda' })).toHaveAttribute('href', '/eleva');
  });

  it('[004][FR-012][US4-AC4] sinDatos muestra el estado vacío y ningún gráfico', async () => {
    simularFetch({ 'GET /api/sesion': json(contexto), 'GET /api/analitica': json({ ...analiticaEleva, sinDatos: true }) });
    abrir('/eleva/analitica');
    expect(
      await screen.findByText('Aún no hay citas cerradas en las últimas 8 semanas. Cuando la clínica complete citas, aquí verás su evolución.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('list')).toBeNull();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('[004][FR-012] un error de carga muestra el aviso y «Reintentar» vuelve a cargar', async () => {
    let llamadas = 0;
    simularFetch({
      'GET /api/sesion': json(contexto),
      'GET /api/analitica': () => {
        llamadas += 1;
        return llamadas === 1
          ? problema(500, { codigo: 'ERROR_INTERNO', mensaje: 'Algo ha fallado. Inténtalo de nuevo en unos segundos.' })
          : json(analiticaEleva);
      },
    });
    abrir('/eleva/analitica');
    expect(await screen.findByText('Algo ha fallado. Inténtalo de nuevo en unos segundos.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(screen.getByRole('region', { name: 'Última semana' })).toBeInTheDocument());
    expect(llamadas).toBe(2);
  });

  it('[004][FR-001] la agenda ofrece un enlace «Analítica» hacia /eleva/analitica', async () => {
    simularFetch({
      'GET /api/sesion': json(contexto),
      'GET /api/agenda': json({ fecha: '2026-11-26', hoy: '2026-11-26', profesional: contexto.profesionales[0], horario: { apertura: '09:00', cierre: '20:00' }, citas: [], huecos: [] }),
    });
    abrir('/eleva');
    expect(await screen.findByRole('link', { name: 'Analítica' })).toHaveAttribute('href', '/eleva/analitica');
  });
});
