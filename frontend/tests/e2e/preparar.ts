import { execSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Carga de nuevo los datos de demostración antes de la batería (quickstart.md, paso 1).
 * SIN_SEMILLA=1 lo omite (por ejemplo, si el entorno lo prepara otro proceso).
 */
export default function preparar() {
  if (process.env.SIN_SEMILLA === '1') return;
  execSync('docker compose run --rm semilla', { cwd: resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..'), stdio: 'ignore' });
}
