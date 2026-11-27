# CitaClara

Panel de recepción para clínicas pequeñas. Proceso: Spec Kit (`specs/`, `specs/MAPA.md`) bajo la constitución `.specify/memory/constitution.md`, que prevalece sobre todo lo demás.

## Comandos

- Entorno: `docker compose up -d --build` (web en `http://localhost:8080`; `PUERTO_WEB=8081` si el 8080 está ocupado) y `docker compose run --rm semilla` para cargar Clínica Eleva (clave `eleva-demo-2026`).
- Backend (JDK 25, Docker en marcha para Testcontainers): `cd backend && ./mvnw verify`.
- Desarrollo local: `docker compose up -d db` (PostgreSQL en `127.0.0.1:5433`, `PUERTO_DB`), backend con `DB_URL=jdbc:postgresql://localhost:5433/citaclara ./mvnw spring-boot:run` (`PUERTO`) y `npm run dev` (`URL_API` si el backend no está en el 8080). Detalle en `README.md`.
- Frontend: `cd frontend && npm ci && npm test`; e2e contra el entorno levantado: `URL_BASE=http://localhost:8080 npm run e2e`.
- CI: `.github/workflows/ci.yml` (trazabilidad, backend, frontend y e2e con `docker compose`) en cada PR.
- Trazabilidad spec ↔ tests: `./scripts/trazabilidad.sh [specs/NNN-…]` (debe salir vacía).

## Decisiones y convenciones transversales

- [001] RN1 (sin solapes) la garantiza la *exclusion constraint* `cita_sin_solape` de PostgreSQL (`btree_gist`, `tstzrange '[)'`, solo RESERVADA/COMPLETADA); ningún camino de escritura queda exento, ni la semilla. Toda feature que escriba en la agenda incluye pruebas de solape y de altas simultáneas contra PostgreSQL real.
- [001] Las altas de cita bloquean la fila del profesional (`RepositorioProfesional.bloquear`, `FOR UPDATE`) al empezar la transacción para evitar tormentas de interbloqueos (`40P01`) en la restricción; ante un interbloqueo residual se reintenta. Cualquier nuevo camino que inserte citas en concurrencia debe hacer lo mismo.
- [001] Importes siempre en céntimos enteros (`int`/`integer`, JSON entero); el único formateador visible es `frontend/src/formato/euros.ts` (CLDR `es-ES`: «1234,50 €», «12.345,50 €»).
- [001] Instantes en `timestamptz`/`Instant`; lógica en `Europe/Madrid` mediante `comun/ZonaClinica` y un `Clock` inyectado (nunca `now()` directo). El API intercambia fecha `YYYY-MM-DD` y hora `HH:mm` locales y el frontend no hace aritmética de zonas. No configurar `hibernate.jdbc.time_zone` (desplaza las columnas `time`).
- [001] Errores del API en Problem Details con `codigo` (para el programa, nunca visible) y `mensaje` en español de España listo para mostrarse; se lanzan como `ErrorNegocio` y los traduce `ManejadorErrores`.
- [001] Aislamiento por clínica: toda tabla de negocio lleva `clinica_id` (con FK compuestas en `cita`), los repositorios reciben el `clinicaId` de la sesión (`@RequestAttribute(SesionClinica.CLINICA)`) y un recurso ajeno responde igual que uno inexistente (404).
- [001] Sesión: clave compartida por clínica con BCrypt, caducidad absoluta de 8 h (`InterceptorSesion`), cookie `HttpOnly` + `SameSite=Strict`, mismo origen vía nginx y todo POST solo en JSON (415 `TIPO_NO_ADMITIDO`) como defensa CSRF; sin Spring Security completo.
- [001] Las especialidades se guardan como valor interno (`nutricion`) y se muestran solo con la etiqueta de `clinica/Especialidades` (`especialidadNombre` en el API).
- [001] Trazabilidad: el nombre visible de cada test empieza por `[NNN][FR-xxx][USn-ACm]` (`@DisplayName` en JUnit, título en Vitest/Playwright); `scripts/trazabilidad.sh` falla si un FR, SC o escenario no tiene test.
- [001] Datos de demostración: generador puro con `Random` de semilla fija y posiciones relativas en días laborables respecto al día de carga; las cifras citables están en `specs/001-nucleo-agenda/datos-demostracion.md` y las verifica `DatosDemostracionDocumentadosTest` (regenerar con `-DactualizarDatosDemostracion=true` tras un cambio deliberado).
- [001] Backend organizado por capacidad de dominio (`acceso`, `clinica`, `paciente`, `cita`, `agenda`, `semilla`, `comun`); una feature nueva añade su propio paquete. Esquema solo por migraciones Flyway en SQL (`ddl-auto=validate`).
- [001] Stack: Spring Boot 4 (starters modulares, p. ej. `spring-boot-starter-webmvc` y `spring-boot-starter-flyway`; Jackson 3 en `tools.jackson.*`), Testcontainers 2 con `postgres:18.7-alpine`; React 19 sin enrutador ni gestor de estado, Tailwind v4 con tokens OKLCH.
- [001] Pruebas de integración: heredan de `soporte/PruebaIntegracionBase` (aplicación real en puerto aleatorio, `ClienteApi` con cookie propia por «pestaña», `RelojControlable`, `DatosPrueba`); al mover el reloj más de 8 h hay que volver a iniciar sesión.
- [001] e2e: cada prueba busca sus huecos libres en el momento (`ApiPrueba.huecoLibre`) para poder repetirse sobre la misma semilla, y comprueba en cada pantalla 360 px, texto no desbordado, objetivos táctiles ≥ 44 px y axe (`pantallaAccesible`).
- [005] Informe y requisitos de seguridad en `specs/005-security-audit/` (`informe-auditoria.md`, `requisitos-pagos.md`); los valida `es.citaclara.seguridad` (formato, fichas, inventario). Todo hallazgo corregido cita su prueba `[005]…`; los que tocan otra spec se piden en `specs/MAPA.md` («Solicitudes entre specs») y no se editan.
- [005] Los registros nunca llevan datos personales: Hibernate registra el SQL con valores, así que `org.hibernate.orm.jdbc.error` y `SqlExceptionHelper` están en `OFF` y `ManejadorErrores` registra solo clases y `SQLState` (`resumenSinDatos`). Comprobar con `soporte/CapturaRegistros`.
- [005] CI: trabajo `seguridad` (OSV-Scanner 2.7.1 y gitleaks 8.30.1 con `.gitleaks.toml`), acciones fijadas por SHA de 40 caracteres con la etiqueta en comentario y `.github/dependabot.yml`; `ControlesCiTest` lo exige. OSV-Scanner no cubre Maven con versiones del BOM.
- [005] `scripts/trazabilidad.sh` lee las exclusiones de la línea `**Excluidos de la trazabilidad**:` de cada spec (por defecto `SC-004 SC-005 SC-006`); `TrazabilidadScriptTest` lo ejecuta dentro de `./mvnw verify`.
- [005] Cobros con Stripe: la tarjeta nunca toca CitaClara; cualquier integración futura cumple `RP-001…RP-012` de `requisitos-pagos.md`.
- [005] nginx añade las cabeceras de `frontend/seguridad.conf` (se incluye en cada `location`, porque `add_header` no se hereda), `limit_req` (API 20/s; acceso 60/min) y `client_max_body_size 64k`; el 429 y el 413 los traduce `api/cliente.ts`. Con el perfil `produccion`, `ValidacionProduccion` falla ante `DB_CLAVE` por defecto o cookie sin `Secure`.
- [005] La búsqueda de pacientes limita `q` a 80 caracteres y escapa `%`, `_` y `\` (`ESCAPE '\'`); toda consulta `LIKE` nueva con texto de la persona usuaria debe hacer lo mismo.
