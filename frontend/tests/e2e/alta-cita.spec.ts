import { expect, test, type Page } from '@playwright/test';
import {
  ApiPrueba,
  aHora,
  aMinutos,
  abrirAgenda,
  diaLaborable,
  dialogoAlta,
  elegirPaciente,
  entrar,
  pantallaAccesible,
  tarjeta,
} from './utilidades';

// Escenarios 6–15 de quickstart.md. Cada prueba busca en el momento un hueco libre en los días
// laborables futuros, así que pueden repetirse sobre la misma semilla.

async function reservarDesdeHueco(page: Page, inicioHueco: string) {
  const hueco = page.locator('div', { has: page.getByText(new RegExp(`^Libre · ${inicioHueco} – `)) }).last();
  await hueco.getByRole('button', { name: 'Reservar aquí' }).click();
  await expect(dialogoAlta(page)).toBeVisible();
}

const SESION_FISIO = 'Sesión fisioterapia · 45 min · 40,00\u00a0€';

async function elegirHora(page: Page, hora: string) {
  await dialogoAlta(page).getByLabel('Hora de inicio').selectOption(hora);
}

/** Sesión de 45 min y hora de inicio (el diálogo propone por defecto el primer servicio de la lista). */
async function elegirSesionYHora(page: Page, hora: string) {
  await dialogoAlta(page).getByLabel('Servicio').selectOption({ label: SESION_FISIO });
  await elegirHora(page, hora);
}

async function enviar(page: Page) {
  await dialogoAlta(page).getByRole('button', { name: 'Reservar cita' }).click();
}

test('[001][FR-008][FR-009][FR-017][US2-AC1] desde un hueco libre se reserva una sesión de 45 min por 40,00 €', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const jorge = await api.profesional('Jorge');
  const { fecha, hueco, inicio } = await api.huecoLibre(jorge, 45);
  await abrirAgenda(page, jorge.id, fecha);

  await reservarDesdeHueco(page, hueco.horaInicio);
  const dialogo = dialogoAlta(page);
  await expect(dialogo).toContainText('Jorge · Fisioterapia');
  await expect(dialogo.getByLabel('Hora de inicio')).toHaveValue(hueco.horaInicio);
  await dialogo.getByLabel('Servicio').selectOption({ label: 'Sesión fisioterapia · 45 min · 40,00 €' });
  await elegirHora(page, inicio);
  await expect(dialogo).toContainText(`Termina a las ${aHora(aMinutos(inicio) + 45)}`);
  await elegirPaciente(dialogo, 'Ana P', 'Ana Pérez');
  await pantallaAccesible(page);
  await enviar(page);

  await expect(page.getByText(`Cita reservada para Ana Pérez a las ${inicio}.`)).toBeVisible();
  // Puede haber una cita cancelada anterior con la misma franja y paciente: se busca la reservada.
  const nueva = tarjeta(page, inicio, aHora(aMinutos(inicio) + 45), 'Ana Pérez').filter({ hasText: 'Reservada' });
  await expect(nueva).toContainText('40,00');
});

test('[001][FR-012][FR-016][US2-AC2][US2-AC3][US2-AC4] solape rechazado con datos conservados, contigua aceptada y hueco reutilizable tras cancelar', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const jorge = await api.profesional('Jorge');
  const { fecha, hueco, inicio } = await api.huecoLibre(jorge, 90);
  const fin = aHora(aMinutos(inicio) + 45);
  await abrirAgenda(page, jorge.id, fecha);

  // Primera cita.
  await reservarDesdeHueco(page, hueco.horaInicio);
  await elegirSesionYHora(page, inicio);
  await elegirPaciente(dialogoAlta(page), 'Ana P', 'Ana Pérez');
  await enviar(page);
  await expect(tarjeta(page, inicio, fin, 'Ana Pérez').filter({ hasText: 'Reservada' })).toBeVisible();

  // Solape: 30 min después → rechazo con la cita que lo provoca; los datos se conservan.
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  const solapada = aHora(aMinutos(inicio) + 30);
  await elegirSesionYHora(page, solapada);
  await elegirPaciente(dialogoAlta(page), 'Carlos', 'Carlos Gómez');
  await enviar(page);
  await expect(dialogoAlta(page).getByText(`Jorge ya tiene una cita de ${inicio} a ${fin}. Elige otro hueco.`)).toBeVisible();
  await expect(dialogoAlta(page).getByLabel('Hora de inicio')).toHaveValue(solapada);
  await expect(dialogoAlta(page)).toContainText('Carlos Gómez');

  // Contigua: empieza justo al terminar la primera.
  await elegirSesionYHora(page, fin);
  await enviar(page);
  await expect(page.getByText(`Cita reservada para Carlos Gómez a las ${fin}.`)).toBeVisible();

  // Cancelar la primera y volver a reservar a la misma hora.
  const primera = tarjeta(page, inicio, fin, 'Ana Pérez').filter({ hasText: 'Reservada' });
  await primera.getByRole('button', { name: 'Cancelar cita' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(primera).toHaveCount(0);
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  await elegirSesionYHora(page, inicio);
  await elegirPaciente(dialogoAlta(page), 'Elena', 'Elena Ruiz');
  await enviar(page);
  await expect(tarjeta(page, inicio, fin, 'Elena Ruiz').filter({ hasText: 'Reservada' })).toBeVisible();
});

test('[001][FR-012][US2-AC6] a la misma hora que Jorge se puede reservar con María', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const jorge = await api.profesional('Jorge');
  const maria = await api.profesional('María');
  const { fecha, inicio } = await api.huecoLibre(jorge, 45, maria);
  const fin = aHora(aMinutos(inicio) + 45);

  for (const [profesional, paciente, busqueda] of [[jorge, 'Pablo Díaz', 'Pablo'], [maria, 'Marta Sánchez', 'Marta']] as const) {
    await abrirAgenda(page, profesional.id, fecha);
    await page.getByRole('button', { name: 'Nueva cita' }).click();
    await elegirSesionYHora(page, inicio);
    await elegirPaciente(dialogoAlta(page), busqueda, paciente);
    await enviar(page);
    await expect(tarjeta(page, inicio, fin, paciente).filter({ hasText: 'Reservada' })).toBeVisible();
  }
});

test('[001][FR-014][US2-AC7] no se pueden crear citas en el pasado', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const jorge = await api.profesional('Jorge');
  await abrirAgenda(page, jorge.id, diaLaborable((await api.contexto()).hoy, -1));
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  await elegirHora(page, '09:00');
  await elegirPaciente(dialogoAlta(page), 'Ana P', 'Ana Pérez');
  await enviar(page);
  await expect(dialogoAlta(page).getByText('No se pueden crear citas en el pasado. Elige una hora posterior.')).toBeVisible();
});

test('[001][FR-019][FR-020][US2-AC8] paciente nuevo: teléfono repetido ofrece la ficha existente y uno nuevo crea la ficha', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  const { fecha, inicio } = await api.huecoLibre(maria, 45);
  await abrirAgenda(page, maria.id, fecha);

  // Teléfono de Ana Pérez escrito con espacios → aviso y «Usar esta ficha».
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  const dialogo = dialogoAlta(page);
  await elegirSesionYHora(page, inicio);
  await dialogo.getByRole('button', { name: 'Paciente nuevo' }).click();
  await dialogo.getByLabel(/^Nombre/).fill('Ana Repetida');
  await dialogo.getByLabel(/^Teléfono/).fill('600 000 101');
  await enviar(page);
  await expect(dialogo.getByText('Ya hay un paciente con ese teléfono: Ana Pérez. ¿Quieres usar su ficha?')).toBeVisible();
  await pantallaAccesible(page);
  await dialogo.getByRole('button', { name: 'Usar esta ficha' }).click();
  await expect(dialogo.getByLabel('Hora de inicio')).toHaveValue(inicio);
  await enviar(page);
  await expect(page.getByText(`Cita reservada para Ana Pérez a las ${inicio}.`)).toBeVisible();

  // Paciente nuevo válido (teléfono único en cada ejecución).
  const siguiente = await api.huecoLibre(maria, 45);
  await abrirAgenda(page, maria.id, siguiente.fecha);
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  await elegirSesionYHora(page, siguiente.inicio);
  await dialogo.getByRole('button', { name: 'Paciente nuevo' }).click();
  const telefono = `699 ${String(Date.now()).slice(-6, -3)} ${String(Date.now()).slice(-3)}`;
  await dialogo.getByLabel(/^Nombre/).fill('Carmen Prueba');
  await dialogo.getByLabel(/^Teléfono/).fill(telefono);
  await dialogo.getByLabel(/^Email/).fill('carmen.prueba@ejemplo.es');
  await enviar(page);
  await expect(page.getByText(`Cita reservada para Carmen Prueba a las ${siguiente.inicio}.`)).toBeVisible();
});

test('[001][FR-019] los datos de un paciente nuevo no válidos se marcan campo a campo', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  const { fecha, inicio } = await api.huecoLibre(maria, 45);
  await abrirAgenda(page, maria.id, fecha);
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  const dialogo = dialogoAlta(page);
  await elegirSesionYHora(page, inicio);
  await dialogo.getByRole('button', { name: 'Paciente nuevo' }).click();
  await dialogo.getByLabel(/^Nombre/).fill('Teléfono Malo');
  await dialogo.getByLabel(/^Teléfono/).fill('12345');
  await enviar(page);
  await expect(dialogo.getByText('Escribe un teléfono válido, por ejemplo 612 345 678.')).toBeVisible();
  await expect(dialogo.getByLabel(/^Nombre/)).toHaveValue('Teléfono Malo');
});

test('[001][FR-010][US2-AC9] con Lucía solo se ofrecen servicios de nutrición', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const lucia = await api.profesional('Lucía');
  await abrirAgenda(page, lucia.id, diaLaborable((await api.contexto()).hoy, 1));
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  const opciones = await dialogoAlta(page).getByLabel('Servicio').locator('option').allTextContents();
  expect(opciones.map((o) => o.replace(/ /g, ' '))).toEqual([
    'Consulta nutrición · 30 min · 35,00 €',
    'Primera visita nutrición · 45 min · 45,00 €',
  ]);
});

test('[001][FR-015][US2-AC10] una sesión de 45 min a las 19:30 se rechaza indicando el horario', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const jorge = await api.profesional('Jorge');
  await abrirAgenda(page, jorge.id, diaLaborable((await api.contexto()).hoy, 3));
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  await dialogoAlta(page).getByLabel('Servicio').selectOption({ label: 'Sesión fisioterapia · 45 min · 40,00 €' });
  await elegirHora(page, '19:30');
  await elegirPaciente(dialogoAlta(page), 'Ana P', 'Ana Pérez');
  await enviar(page);
  await expect(dialogoAlta(page).getByText(
    'La cita debe empezar y terminar dentro del horario de atención (09:00 a 20:00).')).toBeVisible();
  await expect(dialogoAlta(page).getByLabel('Hora de inicio')).toHaveValue('19:30');
});
