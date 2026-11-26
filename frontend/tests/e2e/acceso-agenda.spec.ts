import { expect, test } from '@playwright/test';
import {
  ApiPrueba,
  abrirAgenda,
  diaLaborable,
  entrar,
  pantallaAccesible,
  proximoSabado,
  sinDesplazamientoHorizontal,
  textoVisible,
} from './utilidades';

// Escenarios 1–5, 20 y 21 de quickstart.md.

test('[001][FR-001][US1-AC1] sin sesión solo se ve la pantalla de acceso', async ({ page }) => {
  await page.goto('/eleva');
  await expect(page.getByRole('heading', { name: 'Clínica Eleva' })).toBeVisible();
  await expect(page.getByLabel('Clave de la clínica')).toHaveAttribute('type', 'password');
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toHaveCount(0);
  await expect(page.getByText('María')).toHaveCount(0);
  await page.getByRole('button', { name: 'Mostrar clave' }).click();
  await expect(page.getByLabel('Clave de la clínica')).toHaveAttribute('type', 'text');
  await pantallaAccesible(page);
});

test('[001][US1-AC2] con una clave incorrecta se ve un mensaje claro y sigue sin acceso', async ({ page }) => {
  await page.goto('/eleva');
  await page.getByLabel('Clave de la clínica').fill('no-es-la-clave');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByText('La clave no es correcta. Revísala e inténtalo de nuevo.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toHaveCount(0);
  await sinDesplazamientoHorizontal(page);
});

test('[001][FR-005][US1-AC3] al entrar se ve la agenda de hoy con los tres profesionales', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { hoy } = await api.contexto();
  const [anio, mes, dia] = hoy.split('-');
  await expect(page.getByText(new RegExp(`${dia}/${mes}/${anio}`)).filter({ visible: true }).first()).toBeVisible();
  const selector = page.getByRole('radiogroup', { name: 'Profesional' });
  for (const nombre of ['María · Fisioterapia', 'Jorge · Fisioterapia', 'Lucía · Nutrición']) {
    await expect(selector.getByRole('radio', { name: nombre })).toBeVisible();
  }
  await expect(page.getByText('nutricion')).toHaveCount(0);
  await pantallaAccesible(page);
});

test('[001][FR-006][FR-007][US1-AC4] el día laborable −1 de María muestra sus citas con hora, paciente, servicio, importe y estado, y sus huecos', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  const fecha = diaLaborable((await api.contexto()).hoy, -1);
  const agenda = await api.agenda(maria.id, fecha);
  await abrirAgenda(page, maria.id, fecha);

  const primera = agenda.citas[0]!;
  const tarjeta = page.getByRole('article', { name: `Cita ${primera.horaInicio} – ${primera.horaFin} · ${primera.paciente.nombre}` });
  await expect(tarjeta).toBeVisible();
  await expect(tarjeta).toContainText(primera.servicio.nombre);
  await expect(tarjeta).toContainText(/\d+,\d{2}\s€/);
  await expect(tarjeta).toContainText(/Completada|No asistida|Cancelada/);
  await expect(page.getByRole('article')).toHaveCount(agenda.citas.length);
  await expect(page.getByText(/^Libre · \d{2}:\d{2} – \d{2}:\d{2}$/)).toHaveCount(agenda.huecos.length);
  // Un día pasado no ofrece «Reservar aquí».
  await expect(page.getByRole('button', { name: 'Reservar aquí' })).toHaveCount(0);
  await pantallaAccesible(page);
});

test('[001][FR-024][US1-AC5] una cita cancelada o no asistida sigue visible, atenuada y con su franja libre', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  const { hoy } = await api.contexto();
  for (let posicion = -1; posicion >= -40; posicion--) {
    const fecha = diaLaborable(hoy, posicion);
    const agenda = await api.agenda(maria.id, fecha);
    const cerrada = agenda.citas.find((c) => c.estado === 'CANCELADA' || c.estado === 'NO_ASISTIDA');
    if (!cerrada) continue;
    await abrirAgenda(page, maria.id, fecha);
    const tarjeta = page.getByRole('article', { name: `Cita ${cerrada.horaInicio} – ${cerrada.horaFin} · ${cerrada.paciente.nombre}` });
    await expect(tarjeta).toHaveAttribute('data-atenuada', 'true');
    await expect(tarjeta).toContainText(cerrada.estado === 'CANCELADA' ? 'Cancelada' : 'No asistida');
    expect(agenda.huecos.some((h) => h.horaInicio <= cerrada.horaInicio && h.horaFin >= cerrada.horaFin)).toBe(true);
    return;
  }
  throw new Error('La semilla no tiene ninguna cita cancelada o no asistida de María');
});

test('[001][FR-015] un sábado la clínica no atiende', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  await abrirAgenda(page, maria.id, proximoSabado((await api.contexto()).hoy));
  await expect(page.getByText('La clínica no atiende este día.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nueva cita' })).toBeDisabled();
});

test('[001][FR-005][SC-007] cambiar de profesional conserva el día, la navegación responde en menos de 2 s y el enlace es compartible', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const { hoy } = await api.contexto();
  const fecha = diaLaborable(hoy, 2);
  const jorge = await api.profesional('Jorge');
  await abrirAgenda(page, jorge.id, fecha);

  const inicio = Date.now();
  await page.getByRole('radio', { name: 'Lucía · Nutrición' }).click();
  await expect(page.getByRole('heading', { name: 'Agenda de Lucía' })).toBeVisible();
  await expect(page.getByText(/^Libre · /).first()).toBeVisible();
  expect(Date.now() - inicio).toBeLessThan(2000);
  expect(new URL(page.url()).searchParams.get('fecha')).toBe(fecha);

  const antes = Date.now();
  await page.getByRole('button', { name: 'Día siguiente' }).click();
  const siguiente = new Date(`${fecha}T12:00:00Z`);
  siguiente.setUTCDate(siguiente.getUTCDate() + 1);
  await expect(page).toHaveURL(new RegExp(`fecha=${siguiente.toISOString().slice(0, 10)}`));
  await expect(page.getByText('Cargando la agenda…')).toHaveCount(0);
  expect(Date.now() - antes).toBeLessThan(2000);

  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Agenda de Lucía' })).toBeVisible();
  expect(new URL(page.url()).searchParams.get('fecha')).toBe(fecha);

  await page.getByRole('button', { name: 'Hoy' }).click();
  expect(new URL(page.url()).searchParams.get('fecha')).toBe(hoy);
  await expect(page.getByLabel('Ir a una fecha')).toBeVisible();
});

test('[001][FR-028] la agenda es accesible también en modo oscuro', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  await abrirAgenda(page, maria.id, diaLaborable((await api.contexto()).hoy, 1));
  await pantallaAccesible(page);
  await page.goto('/eleva');
  await pantallaAccesible(page);
});

test('[001][FR-002] «Cerrar sesión» vuelve a la pantalla de acceso', async ({ page }) => {
  await entrar(page);
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByLabel('Clave de la clínica')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Clave de la clínica')).toBeVisible();
});

test('[001][FR-027] la raíz pide abrir la dirección del panel de la clínica', async ({ page }) => {
  await page.goto('/');
  expect(await textoVisible(page)).toContain('Abre la dirección del panel de tu clínica para entrar.');
  await sinDesplazamientoHorizontal(page);
});
