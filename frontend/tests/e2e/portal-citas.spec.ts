import { expect, test } from '@playwright/test';
import { diaLaborable, entrarEnPortal, tarjetaPortal } from './utilidades';

// Mis citas con los datos de demostración (002, US2; specs/002-portal-paciente/datos-demostracion.md).
// Que la cita del día laborable +1 es la PRIMERA de Teresa lo garantiza DatosPortalDocumentadosTest
// sobre la semilla; aquí no se afirma el orden porque otras pruebas e2e (p. ej. las de 001) pueden
// haber cancelado citas de la semilla antes en la misma ejecución.

function hoyEnMadrid(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date());
}

test('[002][US2-AC1] Teresa Méndez ve su cita de la semilla del día laborable +1 con María, 10:15–11:15, Primera visita fisioterapia, 50,00 €', async ({ page }) => {
  await entrarEnPortal(page, 'teresa.mendez@ejemplo.es');
  const cita = tarjetaPortal(page, diaLaborable(hoyEnMadrid(), 1), '10:15', '11:15');

  await expect(cita).toContainText('con María');
  await expect(cita).toContainText('Primera visita fisioterapia');
  await expect(cita).toContainText(/50,00\s€/);
  await expect(cita).toContainText(/Reservada|Cancelada/);
});

test('[002][US2-AC3] Rocío Garrido ve en pasadas su cita del día laborable −3 con María como «Realizada», sin botones', async ({ page }) => {
  await entrarEnPortal(page, 'rocio.garrido@ejemplo.es');
  const pasadas = page.getByRole('region', { name: 'Citas pasadas' });
  const cita = tarjetaPortal(page, diaLaborable(hoyEnMadrid(), -3), '10:00', '10:45');

  await expect(cita).toContainText('con María');
  await expect(cita).toContainText(/40,00\s€/);
  await expect(cita).toContainText('Realizada');
  await expect(pasadas.getByRole('button')).toHaveCount(0);
});
