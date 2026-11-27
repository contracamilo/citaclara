import { expect, test } from '@playwright/test';
import { citaFuturaPara, entrarEnPortal, recepcionAparte, tarjetaPortal } from './utilidades';

// Cancelar desde el portal (002, US3; quickstart.md escenario 3). La prueba crea su propia cita a más
// de 24 h para Laura Romero: no gasta citas de la semilla y se puede repetir (research R11).

test('[002][US3-AC1][US3-AC2] Laura cancela su cita desde el portal y el hueco queda libre en la agenda de Jorge', async ({ page, playwright }) => {
  const { request, api } = await recepcionAparte(playwright);
  const { fecha, profesional, cita } = await citaFuturaPara(request, 'Jorge', 'Laura Romero');

  await entrarEnPortal(page, 'laura.romero@ejemplo.es');
  const tarjeta = tarjetaPortal(page, fecha, cita.horaInicio, cita.horaFin).filter({ hasText: 'con Jorge' });
  await tarjeta.getByRole('button', { name: 'Cancelar cita' }).click();
  const dialogo = page.getByRole('alertdialog');
  await expect(dialogo).toContainText(`a las ${cita.horaInicio} con Jorge?`);
  await dialogo.getByRole('button', { name: 'Sí, cancelar' }).click();

  await expect(page.getByText('Tu cita se ha cancelado.')).toBeVisible();
  await expect(
    tarjetaPortal(page.getByRole('region', { name: 'Citas pasadas' }), fecha, cita.horaInicio, cita.horaFin),
  ).toContainText('Cancelada');

  const agenda = await api.agenda(profesional.id, fecha);
  expect(agenda.citas.find((c) => c.id === cita.id)?.estado).toBe('CANCELADA');
  const minutos = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3));
  expect(
    agenda.huecos.some((h) => minutos(h.horaInicio) <= minutos(cita.horaInicio) && minutos(h.horaFin) >= minutos(cita.horaFin)),
  ).toBe(true);
  await request.dispose();
});
