import { execSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Carga de nuevo los datos de demostración antes de la batería (quickstart.md, paso 1).
 * SIN_SEMILLA=1 lo omite (por ejemplo, si el entorno lo prepara otro proceso).
 * PROYECTO_COMPOSE=nombre usa otro proyecto de Docker Compose (varias copias del repositorio a la vez).
 */
export default function preparar() {
  if (process.env.SIN_SEMILLA === '1') return;
  execSync(`docker compose ${process.env.PROYECTO_COMPOSE ? `-p ${process.env.PROYECTO_COMPOSE} ` : ''}run --rm semilla`, { cwd: resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..'), stdio: 'ignore' });
}
