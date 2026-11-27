import { expect, test } from '@playwright/test';
import { citaFuturaPara, entrar, paginaAislada, tarjetaPortal, pantallaAccesible, pedirEnlace, recepcionAparte, ultimoEnlace, vaciarCorreo } from './utilidades';

// FR-024 / SC-006: cada pantalla del portal y el aviso de recepción a 360 px (y en portátil) sin
// desplazamiento horizontal, sin texto desbordado, con objetivos táctiles ≥ 44 px y sin infracciones axe.
// Pide un solo enlace por proyecto (límite de 5 por hora, FR-004).

test('[002][FR-024][SC-006] entrada, aviso neutro, mis citas, confirmación y aviso de recepción son accesibles', async ({ page: paciente, browser, playwright }, testInfo) => {
  const email = 'gonzalo.cruz@ejemplo.es';
  const { request } = await recepcionAparte(playwright);
  const { cita, fecha } = await citaFuturaPara(request, 'María', 'Gonzalo Cruz');
  await request.dispose();

  await paciente.goto('/eleva/mis-citas');
  await expect(paciente.getByLabel('Tu email')).toBeVisible();
  await pantallaAccesible(paciente);

  await vaciarCorreo(email);
  await pedirEnlace(paciente, email);
  await pantallaAccesible(paciente);

  await paciente.goto(await ultimoEnlace(email));
  await expect(paciente.getByRole('heading', { name: 'Próximas citas' })).toBeVisible();
  await pantallaAccesible(paciente);

  const tarjeta = tarjetaPortal(paciente, fecha, cita.horaInicio, cita.horaFin).filter({ hasText: 'con María' });
  await tarjeta.getByRole('button', { name: 'Cancelar cita' }).click();
  await expect(paciente.getByRole('alertdialog')).toBeVisible();
  await pantallaAccesible(paciente);
  await paciente.getByRole('alertdialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(paciente.getByText('Tu cita se ha cancelado.')).toBeVisible();
  await pantallaAccesible(paciente);

  const recepcion = await paginaAislada(browser, testInfo);
  await entrar(recepcion);
  const aviso = recepcion.getByRole('button', { name: /de pacientes por revisar/ });
  await expect(aviso).toBeVisible();
  await pantallaAccesible(recepcion);
  await aviso.click();
  await expect(recepcion.getByRole('dialog', { name: 'Cancelaciones de pacientes' })).toBeVisible();
  await pantallaAccesible(recepcion);
  await recepcion.context().close();
});
