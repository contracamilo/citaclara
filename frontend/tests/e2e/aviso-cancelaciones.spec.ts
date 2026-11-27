import { expect, test } from '@playwright/test';
import { citaFuturaPara, entrar, entrarEnPortal, fechaLarga, paginaAislada, recepcionAparte, tarjetaPortal } from './utilidades';

// Aviso de cancelaciones del portal en recepción (002, US4; quickstart.md escenario 3). Usa sus
// propias citas (Ana Pérez) para poder repetirse sobre la misma semilla.

test('[002][US4-AC1][US4-AC3] recepción ve la cancelación del portal en el aviso y la marca como revisada', async ({ page: paciente, browser, playwright }, testInfo) => {
  const { request } = await recepcionAparte(playwright);
  const { cita, fecha } = await citaFuturaPara(request, 'Jorge', 'Ana Pérez');
  await request.dispose();

  await entrarEnPortal(paciente, 'ana.perez@ejemplo.es');
  const tarjeta = tarjetaPortal(paciente, fecha, cita.horaInicio, cita.horaFin).filter({ hasText: 'con Jorge' });
  await tarjeta.getByRole('button', { name: 'Cancelar cita' }).click();
  await paciente.getByRole('alertdialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(paciente.getByText('Tu cita se ha cancelado.')).toBeVisible();

  const recepcion = await paginaAislada(browser, testInfo);
  await entrar(recepcion);
  const aviso = recepcion.getByRole('button', { name: /cancelaci(ón|ones) de pacientes por revisar/ });
  await expect(aviso).toBeVisible();
  await aviso.click();
  const dialogo = recepcion.getByRole('dialog', { name: 'Cancelaciones de pacientes' });
  const elemento = dialogo
    .getByRole('listitem')
    .filter({ hasText: 'Ana Pérez · Sesión fisioterapia' })
    .filter({ hasText: `Jorge · ${fechaLarga(fecha)} · ${cita.horaInicio}–${cita.horaFin}` });
  await expect(elemento).toContainText('Jorge ·');
  await expect(elemento).toContainText(/Cancelada el \d{2}\/\d{2}\/\d{4} a las \d{2}:\d{2}/);
  await elemento.getByRole('button', { name: 'Marcar como revisada' }).click();
  await expect(elemento).toHaveCount(0);
  await recepcion.context().close();
});
