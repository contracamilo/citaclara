import { expect, test } from '@playwright/test';
import { ApiPrueba, abrirAgenda, diaLaborable, dialogoAlta, entrar, pantallaAccesible } from './utilidades';

test('[001][SC-009][US1-AC7] todas las pantallas y diálogos de recepción se usan a 360 px sin desplazamiento horizontal', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });

  await page.goto('/eleva');
  await pantallaAccesible(page);

  await entrar(page);
  await pantallaAccesible(page);

  const api = new ApiPrueba(page.request);
  const { hoy } = await api.contexto();
  const jorge = await api.profesional('Jorge');
  // Día con muchas citas (historia) y día futuro con huecos reservables.
  await abrirAgenda(page, jorge.id, diaLaborable(hoy, -1));
  await expect(page.getByRole('article').first()).toBeVisible();
  await pantallaAccesible(page);

  await abrirAgenda(page, jorge.id, diaLaborable(hoy, 1));
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  await expect(dialogoAlta(page)).toBeVisible();
  await pantallaAccesible(page);
  await dialogoAlta(page).getByRole('button', { name: 'Paciente nuevo' }).click();
  await pantallaAccesible(page);
  await dialogoAlta(page).getByRole('button', { name: 'Volver a la agenda' }).click();

  const cancelar = page.getByRole('button', { name: 'Cancelar cita' }).first();
  if (await cancelar.count()) {
    await cancelar.click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await pantallaAccesible(page);
    await page.getByRole('alertdialog').getByRole('button', { name: 'No, volver' }).click();
  }
});
