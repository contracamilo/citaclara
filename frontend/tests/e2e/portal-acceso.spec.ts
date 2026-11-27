import { expect, test } from '@playwright/test';
import { hayCorreoPara, pedirEnlace, ultimoEnlace, vaciarCorreo } from './utilidades';

// Portal del paciente, acceso por enlace (002, US1; quickstart.md escenario 1). Los enlaces se leen de
// Mailpit: nunca sale correo real a las direcciones de la semilla (FR-031). Cada email se usa en pocas
// pruebas para no llegar al límite de 5 enlaces por hora (FR-004), ni con los dos proyectos.

test('[002][FR-031][US1-AC1][US1-AC2][US1-AC4][US1-AC6] Teresa entra con el enlace de su email, no puede reutilizarlo y sale', async ({ page }) => {
  const email = 'teresa.mendez@ejemplo.es';
  await vaciarCorreo(email);
  await pedirEnlace(page, email);
  await expect(
    page.getByText(
      'Si tu email está registrado en Clínica Eleva, te hemos enviado un enlace para entrar. Revisa tu correo; el enlace caduca en 10 minutos. Si no te llega, llama a la clínica.',
    ),
  ).toBeVisible();

  const enlace = await ultimoEnlace(email);
  expect(enlace).toMatch(/^\/eleva\/mis-citas#enlace=/);
  await page.goto(enlace);
  await expect(page.getByRole('heading', { name: 'Hola, Teresa' })).toBeVisible();
  await expect(page).toHaveURL(/\/eleva\/mis-citas$/); // el secreto se borra de la dirección

  await page.getByRole('button', { name: 'Salir' }).click();
  await expect(page.getByLabel('Tu email')).toBeVisible();
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Hola, Teresa' })).toHaveCount(0);

  await page.goto(enlace);
  await expect(page.getByText('Este enlace ya no es válido. Pide uno nuevo.')).toBeVisible();
  await expect(page.getByLabel('Tu email')).toBeVisible();
});

test('[002][US1-AC3] un email que no es de ningún paciente recibe el mismo aviso y no se envía nada', async ({ page }) => {
  const email = 'nadie@ejemplo.es';
  await vaciarCorreo(email);
  await pedirEnlace(page, email);
  await expect(page.getByText(/Si tu email está registrado en Clínica Eleva, te hemos enviado un enlace/)).toBeVisible();
  expect(await hayCorreoPara(email)).toBe(false);
});
