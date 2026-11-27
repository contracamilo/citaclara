import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GraficoBarras } from '../../src/analitica/GraficoBarras';

describe('GraficoBarras', () => {
  it('[004][FR-012] cada barra muestra su nombre y su valor como texto visible', () => {
    render(
      <GraficoBarras
        nombre="Ocupación por profesional"
        filas={[
          { clave: 1, etiqueta: 'María', valor: 545, texto: '54,5 %', detalle: '1800 de 3300 min' },
          { clave: 2, etiqueta: 'Jorge', valor: 482, texto: '48,2 %' },
        ]}
        maximo={1000}
      />,
    );
    const lista = screen.getByRole('list', { name: 'Ocupación por profesional' });
    const [maria, jorge] = within(lista).getAllByRole('listitem');
    expect(within(maria!).getByText('María')).toBeVisible();
    expect(within(maria!).getByText('54,5 %')).toBeVisible();
    expect(within(maria!).getByText('1800 de 3300 min')).toBeVisible();
    expect(within(jorge!).getByText('48,2 %')).toBeVisible();
  });

  it('[004][FR-013] la información no depende solo del color: la barra con «Sin datos» no se dibuja y su texto sigue visible', () => {
    const { container } = render(
      <GraficoBarras
        nombre="Tasa"
        filas={[
          { clave: 1, etiqueta: 'María', valor: 100, texto: '10,0 %' },
          { clave: 2, etiqueta: 'Lucía', valor: null, texto: 'x' },
        ]}
      />,
    );
    const items = screen.getAllByRole('listitem');
    expect(within(items[1]!).getByText('Sin datos')).toBeVisible();
    expect(items[1]!.querySelector('[aria-hidden="true"]')).toBeNull();
    // La barra de María sí existe y es decorativa (el valor ya está en texto).
    expect(items[0]!.querySelector('[aria-hidden="true"]')).not.toBeNull();
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(1);
  });

  it('[004][FR-011] un valor nulo nunca se muestra como 0 %', () => {
    render(<GraficoBarras nombre="Tasa" filas={[{ clave: 1, etiqueta: 'Lucía', valor: null, texto: '0,0 %' }]} />);
    expect(screen.queryByText(/0,0/)).toBeNull();
    expect(screen.getByText('Sin datos')).toBeInTheDocument();
  });
});
