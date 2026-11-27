import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PantallaAnalitica } from '../../src/analitica/PantallaAnalitica';
import { analiticaEleva, contexto, json, proporcion, simularFetch } from './datos';

async function tabla(datos = analiticaEleva) {
  simularFetch({ 'GET /api/analitica': json(datos) });
  render(<PantallaAnalitica contexto={contexto} identificador="eleva" onSalir={() => undefined} />);
  return screen.findByRole('table');
}

describe('Evolución de 8 semanas (US3)', () => {
  it('[004][FR-007][US3-AC1] la tabla muestra 8 semanas en orden con ingresos, tasa y una celda de ocupación por profesional', async () => {
    const t = await tabla();
    const filas = within(t).getAllByRole('row');
    // cabecera + 8 semanas + total
    expect(filas).toHaveLength(10);
    const primera = filas[1]!;
    expect(within(primera).getByText('4380,00 €')).toBeVisible();
    expect(within(primera).getByText('8,1 %')).toBeVisible();
    expect(within(primera).getByText('11 de 135 citas')).toBeVisible();
    // Ocupación de Jorge, Lucía y María en la semana 1: 50,9 · 41,8 · 46,8 %.
    expect(within(primera).getByText('50,9 %')).toBeVisible();
    expect(within(primera).getByText('41,8 %')).toBeVisible();
    expect(within(primera).getByText('46,8 %')).toBeVisible();
    expect(within(t).getAllByRole('columnheader').map((c) => c.textContent)).toEqual([
      'Semana', 'Ingresos', 'No asistencia', 'Ocupación de Jorge', 'Ocupación de Lucía', 'Ocupación de María',
    ]);
    const ultima = filas[8]!;
    expect(within(ultima).getByText('4750,00 €')).toBeVisible();
    expect(within(ultima).getByText('7,4 %')).toBeVisible();
  });

  it('[004][FR-009][US3-AC1] la ocupación semanal por profesional reproduce la tabla de la semilla (semana 6: Jorge 35,5 %)', async () => {
    const t = await tabla();
    const semana6 = within(t).getAllByRole('row')[6]!;
    expect(within(semana6).getByText('35,5 %')).toBeVisible(); // Jorge
    expect(within(semana6).getByText('44,5 %')).toBeVisible(); // Lucía
    expect(within(semana6).getByText('47,7 %')).toBeVisible(); // María
    const minutosTotales = analiticaEleva.evolucion.map((s) => s.ocupacion.map((o) => o.ocupacion.parte));
    expect([0, 1, 2].map((p) => minutosTotales.reduce((n, s) => n + s[p]!, 0))).toEqual([12150, 11595, 12840]);
  });

  it('[004][FR-014] cada semana se etiqueta con fecha de inicio y fin dd/mm/aaaa', async () => {
    const t = await tabla();
    expect(within(t).getByText('05/10/2026 – 11/10/2026')).toBeVisible();
    expect(within(t).getByText('23/11/2026 – 29/11/2026')).toBeVisible();
    expect(within(t).getAllByRole('rowheader').map((c) => c.textContent).slice(0, 8)).toHaveLength(8);
  });

  it('[004][FR-011][US3-AC2] una semana sin citas muestra «0,00 €» y «Sin datos» y no se omite', async () => {
    const datos = structuredClone(analiticaEleva);
    const s = datos.evolucion[3]!;
    Object.assign(s, { completadas: 0, noAsistidas: 0, canceladas: 0, ingresosCentimos: 0, tasaNoAsistencia: proporcion(0, 0) });
    s.ocupacion = s.ocupacion.map((o) => ({ ...o, ocupacion: proporcion(0, 3300) }));
    datos.ingresos.totalCentimos -= 399000;
    const t = await tabla(datos);
    const fila = within(t).getAllByRole('row')[4]!;
    expect(within(fila).getByText('26/10/2026 – 01/11/2026')).toBeVisible();
    expect(within(fila).getByText('0,00 €')).toBeVisible();
    expect(within(fila).getByText('Sin datos')).toBeVisible();
    expect(within(t).getAllByRole('row')).toHaveLength(10);
  });

  it('[004][FR-010] la fila «Total de las 8 semanas» coincide con la suma de las semanas mostradas', async () => {
    const t = await tabla();
    const total = within(t).getAllByRole('row')[9]!;
    expect(within(total).getAllByText('34.870,00 €').length).toBeGreaterThan(0);
    const suma = analiticaEleva.evolucion.reduce((n, s) => n + s.ingresosCentimos, 0);
    expect(suma).toBe(analiticaEleva.ingresos.totalCentimos);
  });
});
