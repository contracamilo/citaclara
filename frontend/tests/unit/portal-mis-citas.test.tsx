import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PantallaMisCitas } from '../../src/portal/PantallaMisCitas';
import { normalizarEspacios } from './datos';
import { citaPortal, FUERA_DE_PLAZO, misCitas } from './portal-datos';

const pintar = (datos = misCitas()) =>
  render(<PantallaMisCitas datos={datos} onRecargar={async () => undefined} onSalir={() => undefined} />);

describe('PantallaMisCitas', () => {
  it('[002][FR-009][FR-026][US2-AC1] la tarjeta muestra fecha larga, franja, profesional, servicio, precio y estado', () => {
    pintar();
    const tarjeta = screen.getByRole('article');

    expect(within(tarjeta).getByText('martes, 01/12/2026')).toBeInTheDocument();
    expect(within(tarjeta).getByText('10:15–11:15')).toBeInTheDocument();
    expect(within(tarjeta).getByText('con María')).toBeInTheDocument();
    expect(within(tarjeta).getByText('Primera visita fisioterapia')).toBeInTheDocument();
    expect(normalizarEspacios(tarjeta.textContent ?? '')).toContain('50,00 €');
    expect(within(tarjeta).getByText('Reservada')).toBeInTheDocument();
    expect(within(tarjeta).getByRole('button', { name: 'Cancelar cita' })).toBeInTheDocument();
  });

  it('[002][FR-027] la cabecera muestra el nombre de la clínica y el saludo usa el nombre del paciente', () => {
    pintar();
    expect(screen.getByRole('banner')).toHaveTextContent('Clínica Eleva');
    expect(screen.getByRole('heading', { level: 1, name: 'Hola, Teresa' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument();
  });

  it('[002][US2-AC3][US2-AC4][US2-AC6] las pasadas muestran su estado en lenguaje del paciente, con icono y sin botones', () => {
    pintar(
      misCitas({
        proximas: [],
        pasadas: [
          citaPortal({ id: 1, estadoPortal: 'PENDIENTE_CIERRE', cancelable: false }),
          citaPortal({ id: 2, estadoPortal: 'REALIZADA', cancelable: false }),
          citaPortal({ id: 3, estadoPortal: 'NO_ACUDISTE', cancelable: false }),
          citaPortal({ id: 4, estadoPortal: 'CANCELADA', cancelable: false }),
        ],
      }),
    );
    const pasadas = screen.getByRole('region', { name: 'Citas pasadas' });

    for (const texto of ['Pendiente de cierre por la clínica', 'Realizada', 'No acudiste', 'Cancelada']) {
      const distintivo = within(pasadas).getByText(texto);
      expect(distintivo.querySelector('svg')).not.toBeNull();
    }
    expect(within(pasadas).queryByRole('button')).toBeNull();
  });

  it('[002][US2-AC7] sin próximas citas invita a llamar a la clínica', () => {
    pintar(misCitas({ proximas: [] }));
    expect(screen.getByText('No tienes citas próximas. Si quieres pedir una, llama a la clínica.')).toBeInTheDocument();
  });

  it('[002][US3-AC4] una cita fuera de plazo no ofrece cancelar y explica qué hacer', () => {
    pintar(misCitas({ proximas: [citaPortal({ cancelable: false, motivoNoCancelable: FUERA_DE_PLAZO })] }));
    expect(screen.queryByRole('button', { name: 'Cancelar cita' })).toBeNull();
    expect(screen.getByText(FUERA_DE_PLAZO)).toBeInTheDocument();
  });

  it('[002][FR-028] con varias fichas saluda sin nombre y cada tarjeta dice de quién es', () => {
    pintar(
      misCitas({
        pacientes: ['Hijo de Laura', 'Laura Romero'],
        proximas: [citaPortal({ id: 1, paciente: 'Laura Romero' }), citaPortal({ id: 2, paciente: 'Hijo de Laura' })],
      }),
    );
    expect(screen.getByRole('heading', { level: 1, name: 'Hola' })).toBeInTheDocument();
    expect(screen.getByText('Laura Romero')).toBeInTheDocument();
    expect(screen.getByText('Hijo de Laura')).toBeInTheDocument();
  });
});
