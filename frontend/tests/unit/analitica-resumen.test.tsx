import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PantallaAnalitica } from '../../src/analitica/PantallaAnalitica';
import { analiticaEleva, contexto, json, proporcion, simularFetch } from './datos';

function filaDe(lista: HTMLElement, nombre: string) {
  return within(lista).getAllByRole('listitem').find((li) => within(li).queryByText(nombre))!;
}

describe('Resumen por profesional (US1)', () => {
  it('[004][FR-005][FR-012][US1-AC1] la página muestra los tres profesionales con su ocupación y su tasa, con valor y «x de y citas» visibles', async () => {
    simularFetch({ 'GET /api/analitica': json(analiticaEleva) });
    render(<PantallaAnalitica contexto={contexto} identificador="eleva" onSalir={() => undefined} />);

    const seccion = await screen.findByRole('region', { name: 'Última semana' });
    const ocupacion = within(seccion).getByRole('list', { name: 'Ocupación por profesional' });
    expect(within(filaDe(ocupacion, 'María')).getByText('54,5 %')).toBeVisible();
    expect(within(filaDe(ocupacion, 'Jorge')).getByText('48,2 %')).toBeVisible();
    expect(within(filaDe(ocupacion, 'Lucía')).getByText('48,6 %')).toBeVisible();

    const tasa = within(seccion).getByRole('list', { name: 'Tasa de no asistencia por profesional' });
    const maria = filaDe(tasa, 'María');
    expect(within(maria).getByText('2,6 %')).toBeVisible();
    expect(within(maria).getByText('1 de 39 citas')).toBeVisible();
    expect(within(filaDe(tasa, 'Jorge')).getByText('12,2 %')).toBeVisible();
    expect(within(filaDe(tasa, 'Jorge')).getByText('5 de 41 citas')).toBeVisible();
    expect(within(filaDe(tasa, 'Lucía')).getByText('7,3 %')).toBeVisible();
    expect(within(filaDe(tasa, 'Lucía')).getByText('4 de 55 citas')).toBeVisible();
    // Sin jerga técnica ni identificadores internos en pantalla.
    expect(document.body.textContent).not.toMatch(/decimas|profesionalId|undefined|null/);
  });

  it('[004][FR-011][US1-AC4] un profesional sin citas cerradas muestra «Sin datos» y no 0 %', async () => {
    const vacia = structuredClone(analiticaEleva);
    vacia.ultimaSemana.profesionales[1]!.tasaNoAsistencia = proporcion(0, 0);
    simularFetch({ 'GET /api/analitica': json(vacia) });
    render(<PantallaAnalitica contexto={contexto} identificador="eleva" onSalir={() => undefined} />);

    const tasa = await screen.findByRole('list', { name: 'Tasa de no asistencia por profesional' });
    const lucia = filaDe(tasa, 'Lucía');
    expect(within(lucia).getByText('Sin datos')).toBeVisible();
    expect(within(lucia).queryByText(/0,0/)).toBeNull();
    expect(within(lucia).queryByText(/de \d+ citas/)).toBeNull();
  });

  it('[004][FR-014][US1-AC1] el subtítulo muestra la semana con fechas dd/mm/aaaa y el periodo de 8 semanas', async () => {
    simularFetch({ 'GET /api/analitica': json(analiticaEleva) });
    render(<PantallaAnalitica contexto={contexto} identificador="eleva" onSalir={() => undefined} />);
    expect(await screen.findByText('Semana del 23/11/2026 al 29/11/2026')).toBeInTheDocument();
    expect(screen.getByText('Últimas 8 semanas: del 05/10/2026 al 29/11/2026')).toBeInTheDocument();
  });
});
