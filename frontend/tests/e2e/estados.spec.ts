import { expect, test } from '@playwright/test';
import { ApiPrueba, abrirAgenda, diaLaborable, entrar, pantallaAccesible, tarjeta, type Cita } from './utilidades';

// Escenarios 17–19 de quickstart.md y la confirmación de cancelación.
//
// El escenario 16 (cita de hoy ya empezada: solo «Completada» y «No asistió», US3-AC2, US3-AC3,
// US3-AC5) depende de la hora real: la semilla no pone citas el día de carga y en extremo a extremo
// no se controla el reloj del servidor. Lo cubren CambioEstadoTest y MaquinaEstadosCitaTest (backend,
// reloj controlado) y acciones-cita.test.tsx (componente), y se valida a mano según quickstart.md.

async function citaFuturaReservada(api: ApiPrueba, nombre: string): Promise<{ fecha: string; cita: Cita; profesionalId: number }> {
  const profesional = await api.profesional(nombre);
  const { hoy } = await api.contexto();
  for (let posicion = 1; posicion <= 10; posicion++) {
    const fecha = diaLaborable(hoy, posicion);
    const citas = (await api.agenda(profesional.id, fecha)).citas;
    // Solo citas cuya franja y paciente son únicos ese día, para que su tarjeta no sea ambigua.
    const unica = (c: Cita) =>
      citas.filter((o) => o.horaInicio === c.horaInicio && o.paciente.nombre === c.paciente.nombre).length === 1;
    const cita = citas.find((c) => c.acciones.includes('CANCELAR') && unica(c));
    if (cita) return { fecha, cita, profesionalId: profesional.id };
  }
  throw new Error(`Sin citas futuras reservadas de ${nombre}`);
}

test('[001][FR-022][US3-AC4] una cita futura solo ofrece «Cancelar cita»', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { fecha, cita, profesionalId } = await citaFuturaReservada(api, 'Lucía');
  await abrirAgenda(page, profesionalId, fecha);
  const t = tarjeta(page, cita.horaInicio, cita.horaFin, cita.paciente.nombre);
  await expect(t.getByRole('button', { name: 'Cancelar cita' })).toBeVisible();
  await expect(t.getByRole('button', { name: 'Completada' })).toHaveCount(0);
  await expect(t.getByRole('button', { name: 'No asistió' })).toHaveCount(0);
});

test('[001][FR-021][US3-AC6] una cita cerrada no ofrece ninguna acción', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  await abrirAgenda(page, maria.id, diaLaborable((await api.contexto()).hoy, -1));
  await expect(page.getByRole('article').first()).toBeVisible();
  for (const nombre of ['Cancelar cita', 'Completada', 'No asistió']) {
    await expect(page.getByRole('button', { name: nombre })).toHaveCount(0);
  }
});

test('[001][FR-022][US3-AC1] cancelar pide confirmación y deja la franja libre', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { fecha, cita, profesionalId } = await citaFuturaReservada(api, 'Jorge');
  await abrirAgenda(page, profesionalId, fecha);
  const t = tarjeta(page, cita.horaInicio, cita.horaFin, cita.paciente.nombre);
  const [anio, mes, dia] = fecha.split('-');

  await t.getByRole('button', { name: 'Cancelar cita' }).click();
  const confirmacion = page.getByRole('alertdialog', { name: 'Cancelar cita' });
  await expect(confirmacion).toContainText(
    `¿Cancelar la cita de ${cita.paciente.nombre} del ${dia}/${mes}/${anio} a las ${cita.horaInicio}? El hueco quedará libre.`);
  await pantallaAccesible(page);
  await confirmacion.getByRole('button', { name: 'No, volver' }).click();
  await expect(t).toContainText('Reservada');

  await t.getByRole('button', { name: 'Cancelar cita' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(t).toContainText('Cancelada');
  await expect(t).toHaveAttribute('data-atenuada', 'true');
  const agenda = await api.agenda(profesionalId, fecha);
  expect(agenda.huecos.some((h) => h.horaInicio <= cita.horaInicio && h.horaFin >= cita.horaFin)).toBe(true);
});

test('[001][FR-025][US3-AC7] si otra pestaña cambia la cita antes, se ve el estado real con un aviso claro', async ({ page, context }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { fecha, cita, profesionalId } = await citaFuturaReservada(api, 'María');
  const otra = await context.newPage();
  await abrirAgenda(page, profesionalId, fecha);
  await abrirAgenda(otra, profesionalId, fecha);

  const enPrimera = tarjeta(page, cita.horaInicio, cita.horaFin, cita.paciente.nombre);
  await enPrimera.getByRole('button', { name: 'Cancelar cita' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(enPrimera).toContainText('Cancelada');

  const enSegunda = tarjeta(otra, cita.horaInicio, cita.horaFin, cita.paciente.nombre);
  await enSegunda.getByRole('button', { name: 'Cancelar cita' }).click();
  await otra.getByRole('alertdialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(otra.getByText('Otra persona acaba de cambiar esta cita. Ahora está: Cancelada.')).toBeVisible();
  await expect(enSegunda).toContainText('Cancelada');
  await expect(enSegunda.getByRole('button', { name: 'Cancelar cita' })).toHaveCount(0);
});
