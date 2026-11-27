import { expect, test, type Page } from '@playwright/test';
import { ApiPrueba, abrirAgenda, diaLaborable, dialogoAlta, elegirPaciente, entrar, textoVisible } from './utilidades';

// FR-027 / FR-026: recorrido por acceso, agenda, alta con un rechazo y acciones, comprobando que
// ningún texto visible contiene códigos, identificadores ni jerga, y que los formatos son españoles.

const PROHIBIDO = /\b(SOLAPE|HUECO_RECIEN_OCUPADO|NO_ENCONTRADO|DATOS_NO_VALIDOS|SESION_CADUCADA|SIN_SESION|CLAVE_INCORRECTA|HORA_PASADA|FUERA_DE_HORARIO|SERVICIO_NO_DISPONIBLE|PACIENTE_DUPLICADO|ESTADO_YA_CAMBIADO|ACCION_NO_DISPONIBLE|RESERVADA|COMPLETADA|CANCELADA|NO_ASISTIDA|MARCAR_NO_ASISTIDA|nutricion|null|undefined|NaN|Error|error|Loading|Cancel|Submit|409|422|500)\b/;

async function sinJerga(page: Page) {
  const texto = await textoVisible(page);
  expect(texto.match(PROHIBIDO)?.[0], `texto visible: ${texto.slice(0, 200)}`).toBeUndefined();
  // FR-026: ninguna fecha en formato ISO ni horas con segundos o AM/PM.
  expect(texto).not.toMatch(/\b\d{4}-\d{2}-\d{2}\b|\b\d{1,2}:\d{2}:\d{2}\b|\b(AM|PM)\b/);
  // FR-017: importes con coma decimal y símbolo detrás.
  expect(texto).not.toMatch(/€\s?\d|\d\.\d{2}\s€/);
}

test('[001][FR-027][FR-026][FR-017] ningún texto visible contiene códigos ni jerga y los formatos son españoles', async ({ page }) => {
  await page.goto('/eleva');
  await page.getByLabel('Clave de la clínica').fill('mala');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByText('La clave no es correcta. Revísala e inténtalo de nuevo.')).toBeVisible();
  await sinJerga(page);

  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { hoy } = await api.contexto();
  const jorge = await api.profesional('Jorge');

  // Agenda pasada (estados cerrados) y futura (acciones).
  await abrirAgenda(page, jorge.id, diaLaborable(hoy, -2));
  await expect(page.getByText(/\d{2}\/\d{2}\/\d{4}/).filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByText(/\d+,\d{2}\s€/).first()).toBeVisible();
  await sinJerga(page);

  const fecha = diaLaborable(hoy, 1);
  const agenda = await api.agenda(jorge.id, fecha);
  const ocupada = agenda.citas.find((c) => c.estado === 'RESERVADA');
  await abrirAgenda(page, jorge.id, fecha);
  await sinJerga(page);

  // Alta rechazada por solape: el mensaje se muestra tal cual, sin código.
  if (ocupada) {
    await page.getByRole('button', { name: 'Nueva cita' }).click();
    await dialogoAlta(page).getByLabel('Hora de inicio').selectOption(ocupada.horaInicio);
    await elegirPaciente(dialogoAlta(page), 'Ana P', 'Ana Pérez');
    await dialogoAlta(page).getByRole('button', { name: 'Reservar cita' }).click();
    await expect(dialogoAlta(page).getByText(/ya tiene una cita de .* Elige otro hueco\./)).toBeVisible();
    await sinJerga(page);
    await dialogoAlta(page).getByRole('button', { name: 'Volver a la agenda' }).click();
  }

  // Hora pasada.
  await abrirAgenda(page, jorge.id, diaLaborable(hoy, -1));
  await page.getByRole('button', { name: 'Nueva cita' }).click();
  await elegirPaciente(dialogoAlta(page), 'Ana P', 'Ana Pérez');
  await dialogoAlta(page).getByRole('button', { name: 'Reservar cita' }).click();
  await expect(dialogoAlta(page).getByText('No se pueden crear citas en el pasado. Elige una hora posterior.')).toBeVisible();
  await sinJerga(page);
});
