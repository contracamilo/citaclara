import { execSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { ApiPrueba, abrirAgenda, entrar, pantallaAccesible, tarjeta } from './utilidades';

// 003-recordatorios-cita: recordatorio → enlace del correo → cancelación del paciente → agenda de recepción.
//
// El proceso diario recuerda las citas de [ahora + 24 h, mañana a la misma hora + 24 h). Para no depender de
// la hora real (de viernes por la noche a domingo no hay horario de atención en esa ventana), la prueba crea
// su cita por SQL directamente en la ventana: un recordatorio no necesita que el día sea laborable.

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const CARPETA_CORREO = resolve(RAIZ, 'datos', 'salida-correo');
const PROYECTO = process.env.PROYECTO_COMPOSE ? `-p ${process.env.PROYECTO_COMPOSE} ` : '';

function sql(consulta: string): string {
  return execSync(`docker compose ${PROYECTO}exec -T db psql -U citaclara -d citaclara -v ON_ERROR_STOP=1 -At -c "${consulta.replace(/"/g, '\\"')}"`, {
    cwd: RAIZ,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

/** Inserta una cita reservada de 45 min con un paciente nuevo dentro de la ventana; devuelve su identificador. */
function citaEnVentana(profesional: string, email: string, nombre: string): number {
  const telefono = `+3465${Math.floor(1_000_000 + Math.random() * 8_999_999)}`;
  for (let minutos = 30 * 60; minutos < 46 * 60; minutos += 15) {
    try {
      const salida = sql(`
        WITH p AS (
          INSERT INTO paciente (clinica_id, nombre, telefono, email)
          SELECT id, '${nombre}', '${telefono}', '${email}' FROM clinica WHERE identificador = 'eleva' RETURNING id, clinica_id)
        INSERT INTO cita (clinica_id, profesional_id, servicio_id, paciente_id, inicio, fin, importe_centimos, estado)
        SELECT p.clinica_id, pr.id, s.id, p.id, now() + interval '${minutos} minutes', now() + interval '${minutos + 45} minutes', s.precio_centimos, 'RESERVADA'
        FROM p, profesional pr, servicio s
        WHERE pr.clinica_id = p.clinica_id AND pr.nombre = '${profesional}' AND s.clinica_id = p.clinica_id AND s.nombre = 'Sesión fisioterapia'
        RETURNING id`);
      return Number(salida.split('\n')[0]);
    } catch {
      // franja ocupada por otra cita de la semilla (restricción de solape): se prueba la siguiente
    }
  }
  throw new Error(`Sin franja libre de ${profesional} en la ventana de recordatorios`);
}

function correoDe(email: string): string {
  const fichero = readdirSync(CARPETA_CORREO)
    .filter((n) => n.endsWith('.eml'))
    .find((n) => readFileSync(resolve(CARPETA_CORREO, n), 'utf8').includes(email));
  if (!fichero) throw new Error(`Ningún .eml para ${email} en ${CARPETA_CORREO}`);
  // El .eml va en quoted-printable: se quitan los saltos de línea blandos («=» al final de línea).
  return readFileSync(resolve(CARPETA_CORREO, fichero), 'utf8').replace(/=\r?\n/g, '');
}

test('[003][FR-012][FR-016][FR-017][SC-002][US2-AC1][US2-AC2][US2-AC8] el paciente cancela desde el correo y recepción ve «Cancelada por el paciente»', async ({ page }) => {
  await entrar(page);
  const api = new ApiPrueba(page.request);
  const maria = await api.profesional('María');
  const email = `e2e-${Date.now()}@ejemplo.es`;
  const nombre = `Paciente Correo ${Date.now() % 100000}`;
  const citaId = citaEnVentana('María', email, nombre);

  // Proceso diario (comando manual) y lectura del correo simulado.
  execSync(`docker compose ${PROYECTO}run --rm recordatorios`, { cwd: RAIZ, stdio: 'ignore' });
  const eml = correoDe(email);
  const enlace = /\/cancelar\/[A-Za-z0-9_-]{43}/.exec(eml)?.[0];
  expect(enlace, 'el correo contiene el enlace de cancelación').toBeTruthy();

  // El paciente abre el enlace sin sesión (otro contexto, sin la cookie del panel).
  const contextoPaciente = await page.context().browser()!.newContext({
    ...test.info().project.use, baseURL: test.info().project.use.baseURL ?? process.env.URL_BASE ?? 'http://localhost:8080',
    storageState: undefined,
  });
  const paciente = await contextoPaciente.newPage();
  const inicio = Date.now();
  await paciente.goto(enlace!);
  await expect(paciente.getByRole('heading', { name: '¿Cancelamos tu cita?' })).toBeVisible();
  await expect(paciente.getByText('Sesión fisioterapia')).toBeVisible();
  await pantallaAccesible(paciente);

  // Abrir no cancela (US2-AC3): la cita sigue reservada.
  expect(sql(`SELECT estado FROM cita WHERE id = ${citaId}`)).toBe('RESERVADA');

  // Tres pasos como máximo: abrir, revisar, confirmar (SC-002).
  await paciente.getByRole('button', { name: 'Sí, cancelar mi cita' }).click();
  await expect(paciente.getByRole('heading', { name: 'Tu cita está cancelada' })).toBeVisible();
  expect(Date.now() - inicio, 'cancelación en menos de 30 s').toBeLessThan(30_000);
  await pantallaAccesible(paciente);
  await paciente.reload();
  await expect(paciente.getByRole('heading', { name: 'Tu cita está cancelada' })).toBeVisible();
  await contextoPaciente.close();

  // Recepción: la cita queda cancelada y el API de la agenda indica quién canceló (FR-015, FR-016).
  expect(sql(`SELECT estado || '/' || cancelada_por FROM cita WHERE id = ${citaId}`)).toBe('CANCELADA/PACIENTE');
  const fecha = sql(`SELECT to_char(inicio AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD') FROM cita WHERE id = ${citaId}`);
  const cita = (await api.agenda(maria.id, fecha)).citas.find((c) => c.id === citaId);
  expect(cita).toMatchObject({ estado: 'CANCELADA', canceladaPor: 'PACIENTE' });
  // Si ese día hay horario de atención, la tarjeta también lo muestra en pantalla.
  await abrirAgenda(page, maria.id, fecha);
  const t = tarjeta(page, cita!.horaInicio, cita!.horaFin, nombre);
  if (await t.count()) await expect(t).toContainText('Cancelada por el paciente');
});

test('[003][FR-018][US2-AC6] un enlace inventado muestra el mensaje genérico sin datos de ninguna clínica', async ({ page }) => {
  await page.goto(`/cancelar/${'A'.repeat(43)}`);
  await expect(page.getByRole('heading', { name: 'No hemos encontrado lo que buscas.' })).toBeVisible();
  await expect(page.getByText('Eleva')).toHaveCount(0);
  await pantallaAccesible(page);
});
