import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PantallaAnalitica } from '../../src/analitica/PantallaAnalitica';
import { analiticaEleva, contexto, json, simularFetch } from './datos';

async function pantalla(datos = analiticaEleva) {
  simularFetch({ 'GET /api/analitica': json(datos) });
  render(<PantallaAnalitica contexto={contexto} identificador="eleva" onSalir={() => undefined} />);
  return screen.findByRole('region', { name: 'Ingresos por servicio' });
}

const fila = (lista: HTMLElement, nombre: string) => within(lista).getAllByRole('listitem').find((li) => within(li).queryByText(nombre))!;

describe('Ingresos por servicio (US2)', () => {
  it('[004][FR-006][US2-AC1] cada servicio muestra su importe formateado y su número de citas, y el Total', async () => {
    const seccion = await pantalla();
    const lista = within(seccion).getByRole('list', { name: 'Ingresos por servicio' });
    expect(within(fila(lista, 'Sesión fisioterapia')).getByText('17.040,00 €')).toBeVisible();
    expect(within(fila(lista, 'Sesión fisioterapia')).getByText('426 citas')).toBeVisible();
    expect(within(fila(lista, 'Primera visita fisioterapia')).getByText('4850,00 €')).toBeVisible();
    expect(within(fila(lista, 'Consulta nutrición')).getByText('9695,00 €')).toBeVisible();
    expect(within(fila(lista, 'Primera visita nutrición')).getByText('3285,00 €')).toBeVisible();
    expect(within(seccion).getAllByText('34.870,00 €').length).toBeGreaterThan(0);
  });

  it('[004][FR-010][US2-AC2] el Total mostrado es la suma de las filas mostradas, al céntimo', async () => {
    const datos = structuredClone(analiticaEleva);
    datos.ingresos.servicios = [
      { servicioId: 1, nombre: 'A', citasCompletadas: 1, importeCentimos: 3333, citasNoAsistidas: 0, importeNoObtenidoCentimos: 0 },
      { servicioId: 2, nombre: 'B', citasCompletadas: 1, importeCentimos: 5001, citasNoAsistidas: 0, importeNoObtenidoCentimos: 0 },
      { servicioId: 3, nombre: 'C', citasCompletadas: 1, importeCentimos: 1, citasNoAsistidas: 0, importeNoObtenidoCentimos: 0 },
    ];
    datos.ingresos.totalCentimos = 8335;
    const seccion = await pantalla(datos);
    const lista = within(seccion).getByRole('list', { name: 'Ingresos por servicio' });
    expect(within(fila(lista, 'A')).getByText('33,33 €')).toBeVisible();
    expect(within(fila(lista, 'B')).getByText('50,01 €')).toBeVisible();
    expect(within(fila(lista, 'C')).getByText('0,01 €')).toBeVisible();
    expect(within(seccion).getAllByText('83,35 €').length).toBeGreaterThan(0); // 33,33 + 50,01 + 0,01
  });

  it('[004][FR-006][US2-AC4] un servicio con 0 citas muestra «0,00 €» y «0 citas» sin barra', async () => {
    const datos = structuredClone(analiticaEleva);
    datos.ingresos.servicios[3] = { servicioId: 13, nombre: 'Primera visita nutrición', citasCompletadas: 0, importeCentimos: 0, citasNoAsistidas: 0, importeNoObtenidoCentimos: 0 };
    const seccion = await pantalla(datos);
    const lista = within(seccion).getByRole('list', { name: 'Ingresos por servicio' });
    const vacia = fila(lista, 'Primera visita nutrición');
    expect(within(vacia).getByText('0,00 €')).toBeVisible();
    expect(within(vacia).getByText('0 citas')).toBeVisible();
    expect(vacia.querySelector('[aria-hidden="true"] > div')).toBeNull();
  });

  it('[004][FR-016][US2-AC5] el bloque «Ingresos no obtenidos por no asistencias» muestra sus importes y nunca la palabra «ahorro»', async () => {
    const seccion = await pantalla();
    expect(within(seccion).getByRole('heading', { name: 'Ingresos no obtenidos por no asistencias' })).toBeInTheDocument();
    const lista = within(seccion).getByRole('list', { name: 'Ingresos no obtenidos por no asistencias' });
    expect(within(fila(lista, 'Sesión fisioterapia')).getByText('2240,00 €')).toBeVisible();
    expect(within(fila(lista, 'Sesión fisioterapia')).getByText('56 citas')).toBeVisible();
    expect(within(fila(lista, 'Consulta nutrición')).getByText('945,00 €')).toBeVisible();
    expect(within(seccion).getByText('4275,00 €')).toBeVisible();
    expect(document.body.textContent?.toLowerCase()).not.toContain('ahorro');
  });
});
