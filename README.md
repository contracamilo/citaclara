# CitaClara

[![CI](https://github.com/contracamilo/citaclara/actions/workflows/ci.yml/badge.svg)](https://github.com/contracamilo/citaclara/actions/workflows/ci.yml)

Panel de recepción para clínicas pequeñas (2–5 profesionales): agenda del día por profesional, alta de citas sin solapes ni en el pasado y registro del resultado de cada cita. Interfaz en español de España, pensada para el portátil de recepción y para el móvil.

El proyecto se desarrolla con [Spec Kit](https://github.com/github/spec-kit): cada comportamiento nace de una spec en [`specs/`](specs/MAPA.md) y se rige por la [constitución](.specify/memory/constitution.md).

| Capa | Tecnología |
|---|---|
| Backend | Java 25, Spring Boot 4, JPA/Hibernate 7, Flyway |
| Base de datos | PostgreSQL 18 (la regla «sin solapes» es una *exclusion constraint*) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4 |
| Entorno | Docker Compose (`db`, `api`, `web` con nginx y `semilla`) |
| Pruebas | JUnit + Testcontainers, Vitest + Testing Library, Playwright + axe |

## Arranque rápido (solo Docker)

Requisitos: Docker con Docker Compose v2.

```bash
git clone https://github.com/contracamilo/citaclara.git
cd citaclara
docker compose up -d --build        # PostgreSQL, API y web
docker compose run --rm semilla     # datos de demostración de Clínica Eleva
```

Abre **http://localhost:8080/eleva** y entra con la clave **`eleva-demo-2026`**. La analítica (solo lectura: ocupación, no asistencias, ingresos y evolución de 8 semanas) está en **http://localhost:8080/eleva/analitica** y en el enlace «Analítica» de la cabecera. Las cifras de su spec solo se reproducen cargando la semilla en lunes.

- `docker compose ps` debe mostrar `db` y `api` como *healthy*.
- La semilla se puede repetir cuando quieras: borra y recrea Clínica Eleva. Toma el día de carga como referencia: la historia ocupa los 40 días laborables anteriores y el futuro los 10 siguientes. Las cifras exactas están en [`datos-demostracion.md`](specs/001-nucleo-agenda/datos-demostracion.md).
- Para pararlo: `docker compose down` (añade `-v` para borrar también la base de datos).

### Puertos

| Variable | Por defecto | Uso |
|---|---|---|
| `PUERTO_WEB` | `8080` | Web y API (nginx sirve el frontend y redirige `/api`) |
| `PUERTO_DB` | `5433` | PostgreSQL, publicado solo en `127.0.0.1` para el desarrollo local |

Si el 8080 está ocupado: `PUERTO_WEB=8081 docker compose up -d --build` y abre `http://localhost:8081/eleva`.

## Desarrollo local

Requisitos adicionales: JDK 25 y Node 24 LTS. Docker tiene que estar en marcha, porque las pruebas del backend usan Testcontainers.

```bash
# 1. Base de datos (de Docker Compose)
docker compose up -d db

# 2. Backend en http://localhost:8080 (PUERTO=… para usar otro puerto)
cd backend
DB_URL=jdbc:postgresql://localhost:5433/citaclara ./mvnw spring-boot:run

# 3. Datos de demostración (otra terminal, desde la raíz del repositorio)
docker compose run --rm semilla

# 4. Frontend con recarga en caliente en http://localhost:5173/eleva
cd frontend
npm ci
npm run dev                         # URL_API=http://localhost:PUERTO si el backend no está en el 8080
```

Vite redirige `/api` al backend, así que la cookie de sesión funciona igual que detrás de nginx.

## Pruebas

```bash
cd backend && ./mvnw verify                        # unitarias e integración (PostgreSQL 18 real)
cd frontend && npm test                            # Vitest
cd frontend && npx playwright install chromium     # solo la primera vez
cd frontend && URL_BASE=http://localhost:8080 npm run e2e   # contra el entorno de Docker Compose
./scripts/trazabilidad.sh                          # requisitos de la spec sin test referenciado
```

- Las pruebas extremo a extremo recargan la semilla al empezar (`SIN_SEMILLA=1` lo evita). Se ejecutan en portátil y a 360 px, y comprueban la accesibilidad con axe.
- El nombre de cada test empieza por la referencia a la spec, p. ej. `[001][FR-012][US2-AC2]`. `scripts/trazabilidad.sh` falla si algún requisito o escenario de aceptación no tiene test.

### Integración continua

[`ci.yml`](.github/workflows/ci.yml) se ejecuta en cada PR y en cada push a `main`. Comprueba las puertas de merge de la constitución:

1. Trazabilidad spec ↔ tests.
2. Backend: `./mvnw verify`, con las pruebas de solape y de altas simultáneas sobre PostgreSQL real.
3. Frontend: comprobación de tipos, build y Vitest.
4. Extremo a extremo: Playwright contra `docker compose`, con la semilla cargada.

## Estructura

```text
backend/            API Spring Boot, organizada por capacidad (acceso, clinica, paciente, cita, agenda, analitica, semilla)
frontend/           SPA React (acceso, agenda, analitica, citas, ui, formato, api) y sus pruebas
specs/              specs de Spec Kit; MAPA.md lista cada spec y su propietario
scripts/            trazabilidad.sh
docker-compose.yml  entorno completo
CLAUDE.md           decisiones y convenciones transversales para nuevas features
```

## Cómo se añade una feature

1. Registrar la spec en `specs/MAPA.md` y redactarla con `/speckit-specify`, en su propia rama.
2. Recorrer `/speckit-clarify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-analyze` e `/speckit-implement`.
3. Abrir un PR. La CI tiene que estar en verde y la trazabilidad completa.

Las convenciones que ya existen (importes en céntimos, zona horaria, errores, aislamiento por clínica, etc.) están en [`CLAUDE.md`](CLAUDE.md).

## Producción

`docker-compose.yml` es solo para desarrollo (credenciales públicas, sin TLS). Para un entorno real usa `docker-compose.produccion.yml` con `DB_CLAVE` propia, TLS en un proxy delante y `COOKIE_SEGURA=true`; con el perfil `produccion` el arranque falla si quedan valores de desarrollo y la semilla se niega a ejecutarse. Nunca cargues los datos de demostración en un entorno real. Detalle: `specs/005-security-audit/informe-auditoria.md`.
