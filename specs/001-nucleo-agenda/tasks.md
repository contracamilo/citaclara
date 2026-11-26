---

description: "Lista de tareas de implementación de la feature 001-nucleo-agenda"
---

# Tareas: Núcleo de agenda de CitaClara

**Entrada**: documentos de diseño de `specs/001-nucleo-agenda/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.yaml](contracts/api.yaml), [contracts/ui.md](contracts/ui.md), [quickstart.md](quickstart.md)

**Tests**: OBLIGATORIOS. La constitución (Principio VI y puertas de merge 2–4) exige que cada regla y criterio de aceptación tenga un test que lo referencie, y el Principio III exige las pruebas de solape. Convención (research D14): el nombre visible de cada test empieza por `[001][FR-xxx][USn-ACm]` (JUnit con `@DisplayName`; Vitest y Playwright en el título). Los tests de cada historia se escriben primero y DEBEN fallar antes de implementar.

**Organización**: por historia de usuario, para implementar y probar cada una de forma independiente.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: paralelizable (ficheros distintos, sin dependencias de tareas incompletas).
- **[Historia]**: US1, US2, US3, US4 (solo en las fases de historia).
- Cada descripción incluye la ruta exacta del fichero.

## Convenciones de rutas

- Backend: `backend/src/main/java/es/citaclara/<capacidad>/`, tests en `backend/src/test/java/es/citaclara/<capacidad>/`, recursos en `backend/src/main/resources/`.
- Frontend: `frontend/src/<capacidad>/`, tests unitarios en `frontend/tests/unit/`, extremo a extremo en `frontend/tests/e2e/`.
- Orquestación y scripts en la raíz: `docker-compose.yml`, `scripts/`.
- Reglas transversales que toda tarea respeta: importes en **céntimos enteros** (D5); instantes `timestamptz`/`Instant`, lógica en `Europe/Madrid` con `Clock` inyectado, API con fecha `YYYY-MM-DD` y hora `HH:mm` locales (D4); errores Problem Details con `codigo` + `mensaje` en español de España (contracts/api.yaml); todo acceso a datos filtrado por el `clinicaId` de la sesión (D7); nombres de dominio en español.

---

## Fase 1: Andamiaje (infraestructura compartida)

**Propósito**: proyectos, contenedores, esquema completo de base de datos y base de pruebas.

- [ ] T001 Crear el proyecto Maven del backend en `backend/pom.xml` con Maven Wrapper (`backend/mvnw`, `backend/.mvn/wrapper/`): Java 25, Spring Boot 4 (versión de parche exacta), dependencias `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `flyway-core` + `flyway-database-postgresql`, driver `org.postgresql:postgresql`, `spring-security-crypto` (solo este módulo de seguridad); test: `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. Sin ninguna otra dependencia (Principio IV). Crear `backend/src/main/java/es/citaclara/CitaClaraApplication.java`.
- [ ] T002 [P] Configurar `backend/src/main/resources/application.yml`: datasource por variables de entorno (`DB_URL`, `DB_USUARIO`, `DB_CLAVE`); `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; Flyway activado con `classpath:db/migration`; `server.servlet.session.timeout=8h` (D6: la inactividad no debe caducar antes de las 8 h); cookie de sesión `http-only: true`, `same-site: strict`, `secure` por variable `COOKIE_SEGURA` (falso en local); actuator exponiendo solo `health`; `spring.jackson.time-zone=UTC`; perfil `semilla` con `spring.main.web-application-type=none`.
- [ ] T003 [P] Crear el proyecto del frontend: `frontend/package.json` con versiones exactas (React 19, React DOM 19, TypeScript 5, Vite, `@vitejs/plugin-react`, `tailwindcss` v4 + `@tailwindcss/vite`, `@fontsource-variable/inter`, `lucide-react`; dev: `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `@playwright/test`, `@axe-core/playwright`) y scripts `dev`, `build`, `test` (vitest run), `e2e` (playwright test); `frontend/vite.config.ts` con plugin de React y Tailwind, proxy de desarrollo `/api` → `http://localhost:8080` (backend arrancado fuera de Docker) y configuración de Vitest (`environment: 'jsdom'`, `include: ['tests/unit/**/*.test.ts?(x)']`); `frontend/tsconfig.json` estricto; `frontend/index.html` con `lang="es"` y `<meta name="viewport" content="width=device-width, initial-scale=1">`; `frontend/src/main.tsx` que monta `App`.
- [ ] T004 [P] Crear `backend/Dockerfile` multietapa: etapa de construcción con Maven + JDK 25 (imagen con versión fijada) que ejecuta `./mvnw -B package -DskipTests`; etapa final con JRE 25 (versión fijada), usuario no root, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/citaclara.jar"]`.
- [ ] T005 [P] Crear `frontend/Dockerfile` (Node 24 LTS fijado → `npm ci && npm run build` → `nginx` fijado sirviendo `dist/`) y `frontend/nginx.conf`: `location /api/` con `proxy_pass http://api:8080` conservando cabeceras y cookies; cualquier otra ruta (`/`, `/{identificador}`) cae en `index.html` (`try_files $uri /index.html`); estáticos con caché larga salvo `index.html`.
- [ ] T006 Crear `docker-compose.yml` en la raíz (depende de T004, T005): servicio `db` (`postgres:18.x` fijado, volumen `datos-db`, *health check* `pg_isready`); `api` (build `backend/`, variables `DB_URL=jdbc:postgresql://db:5432/citaclara`, etc., `depends_on: db: condition: service_healthy`, *health check* sobre `/actuator/health`); `web` (build `frontend/`, puerto `8080:80`, `depends_on: api: condition: service_healthy`); `semilla` (misma imagen que `api`, `profiles: ["herramientas"]`, `SPRING_PROFILES_ACTIVE=semilla`, `depends_on: db: condition: service_healthy`). Sin secretos salvo la clave de demostración pública.
- [ ] T007 Crear la migración `backend/src/main/resources/db/migration/V1__esquema_inicial.sql` exactamente según [data-model.md](data-model.md): `CREATE EXTENSION btree_gist; CREATE EXTENSION unaccent;` y una función `IMMUTABLE` envoltorio de `unaccent` para poder indexarla; tablas `clinica` (`identificador varchar(40)` único con `CHECK (identificador ~ '^[a-z0-9-]+$')`, `nombre varchar(120) NOT NULL`, `clave_hash varchar(100) NOT NULL`), `horario_atencion` (PK `(clinica_id, dia_semana)`, `dia_semana smallint CHECK (dia_semana BETWEEN 1 AND 7)`, `apertura time`, `cierre time`, `CHECK (cierre > apertura)`), `profesional` (`nombre varchar(120) NOT NULL`, `especialidad varchar(40) NOT NULL`), `servicio` (`nombre varchar(120) NOT NULL`, `especialidad varchar(40) NOT NULL`, `duracion_minutos integer CHECK (duracion_minutos > 0 AND duracion_minutos % 5 = 0)`, `precio_centimos integer CHECK (precio_centimos >= 0)`), `paciente` (`nombre varchar(120) NOT NULL`, `telefono varchar(20) NOT NULL`, `email varchar(254)` nulo, `UNIQUE (clinica_id, telefono)`, índice sobre `lower(<unaccent inmutable>(nombre))`), `cita` (`inicio timestamptz NOT NULL`, `fin timestamptz NOT NULL`, `CHECK (fin > inicio)`, `importe_centimos integer NOT NULL CHECK (importe_centimos >= 0)`, `estado varchar(12) NOT NULL CHECK (estado IN ('RESERVADA','COMPLETADA','CANCELADA','NO_ASISTIDA'))`, `version integer NOT NULL DEFAULT 0`, `creada_en timestamptz NOT NULL DEFAULT now()`, FKs a clínica, profesional, servicio y paciente, índice `(profesional_id, inicio)`) y la restricción de no solape `EXCLUDE USING gist (profesional_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado IN ('RESERVADA','COMPLETADA'))` con nombre `cita_sin_solape`. Todas las PK `bigint GENERATED ALWAYS AS IDENTITY` (salvo `horario_atencion`).
- [ ] T008 [P] Crear la base de pruebas en `backend/src/test/java/es/citaclara/soporte/`: `PruebaIntegracionBase.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, contenedor `postgres:18.x` fijado compartido con `@ServiceConnection`, limpieza de tablas entre tests con `TRUNCATE … RESTART IDENTITY CASCADE`), `RelojControlable.java` (`Clock` mutable con `fijar(ZonedDateTime)` y `avanzar(Duration)`, publicado como `@Primary` en una `@TestConfiguration`) y `DatosPrueba.java` (constructor de datos: clínicas con clave en BCrypt, horario, profesionales, servicios, pacientes y citas insertadas por repositorio o `JdbcTemplate`; cliente HTTP con cookie de sesión para iniciar sesión con una clínica).
- [ ] T009 [P] Crear `scripts/trazabilidad.sh` (ejecutable, POSIX + `grep`): extrae de `specs/001-nucleo-agenda/spec.md` todos los identificadores `FR-0xx`, `SC-0xx` y `USn-ACm` (estos últimos a partir de la numeración de escenarios de cada historia), busca `[001][…]` en `backend/src/test/` y `frontend/tests/`, lista los que no tienen ningún test y sale con código 1 si la lista no está vacía. Excluir explícitamente, con comentario justificado, los criterios no automatizables (SC-004, SC-005, SC-006: medidos con usuarias) y FR-004 (deuda documentada).
- [ ] T010 Escribir `backend/src/test/java/es/citaclara/cita/RestriccionSolapeEsquemaTest.java` (depende de T007, T008), con inserciones directas por `JdbcTemplate` (sin pasar por el servicio): `[001][FR-012] la base de datos rechaza un solape parcial insertado directamente` (SQLSTATE `23P01`), contención y coincidencia exacta rechazadas, `[001][FR-012][US2-AC3]` citas contiguas aceptadas, `[001][FR-024][US2-AC4]` una cancelada o no asistida no bloquea, `[001][FR-012][US2-AC6]` mismo horario con otro profesional aceptado, y `CHECK (fin > inicio)` activo.

**Punto de control**: `docker compose up -d --build` levanta `db`, `api` y `web` en estado *healthy*; `./mvnw verify` ejecuta T010 en verde.

---

## Fase 2: Fundamentos (prerrequisitos bloqueantes)

**Propósito**: piezas que todas las historias necesitan: zona y reloj, errores, entidades, acceso con clave y sesión de 8 h, mapeo de citas, formateadores y base visual.

**⚠️ CRÍTICO**: ninguna historia empieza hasta terminar esta fase.

### Tests de fundamentos (escribir primero; deben fallar)

- [ ] T011 [P] Test unitario `backend/src/test/java/es/citaclara/comun/TelefonoTest.java`: `[001][FR-019]` normaliza «612 345 678», «612-345-678», «+34 612.345.678» a `+34612345678`; acepta móviles y fijos españoles de 9 cifras que empiezan por 6, 7, 8 o 9 y números internacionales `+` con 8 a 15 cifras; rechaza «512345678», «61234567», letras y cadena vacía (research D11).
- [ ] T012 [P] Test unitario `backend/src/test/java/es/citaclara/comun/ZonaClinicaTest.java`: `[001][FR-026]` conversión fecha+hora local de Madrid ↔ `Instant` en invierno y verano; en el día de cambio de marzo (28/03/2027) la hora local inexistente 02:30 se detecta como no válida; en el de octubre (25/10/2026) la hora ambigua 02:30 se detecta como no válida; `fin = inicio + duración` sobre el instante conserva la duración real.
- [ ] T013 [P] Test unitario `backend/src/test/java/es/citaclara/cita/MaquinaEstadosCitaTest.java`: `[001][FR-021]` solo `RESERVADA` admite transiciones y los demás estados son finales; `[001][FR-022][US3-AC1]` `CANCELAR` disponible solo si `ahora < inicio`; `[001][FR-023][US3-AC4]` `COMPLETAR` y `MARCAR_NO_ASISTIDA` no disponibles antes del inicio; `[001][FR-023][US3-AC2][US3-AC3][US3-AC5]` desde `ahora == inicio` solo `COMPLETAR` y `MARCAR_NO_ASISTIDA`; `[001][US3-AC6]` lista vacía en `COMPLETADA`, `CANCELADA` y `NO_ASISTIDA`.
- [ ] T014 [P] Test de integración `backend/src/test/java/es/citaclara/acceso/AccesoTest.java`: `[001][FR-001][US1-AC1]` `GET /api/sesion` y `GET /api/agenda` sin cookie → 401 `SIN_SESION`; `[001][US1-AC2]` clave incorrecta → 401 `CLAVE_INCORRECTA` con `mensaje` exacto «La clave no es correcta. Revísala e inténtalo de nuevo.» y el mismo resultado para un identificador de clínica inexistente; clave correcta → 204 con cookie `HttpOnly` y `SameSite=Strict`; `[001][FR-002]` con `RelojControlable` avanzado 7 h 59 min la sesión sigue válida y avanzado 8 h → 401 `SESION_CADUCADA` con «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.» aunque haya habido actividad entre medias; `DELETE /api/sesion` → 204 y después 401; `GET /api/clinicas/eleva` devuelve solo `nombre`; ninguna respuesta contiene `clave_hash`.
- [ ] T015 [P] Test de integración `backend/src/test/java/es/citaclara/acceso/ContextoSesionTest.java`: `[001][FR-003]` `GET /api/sesion` con la sesión de la clínica A devuelve `clinica.nombre`, `hoy` (fecha de Madrid según el `Clock`), `sesionCaducaA = accesoEn + 8 h`, sus profesionales, servicios (con `duracionMinutos` y `precioCentimos` enteros) y horario semanal, y nunca datos de la clínica B.
- [ ] T016 [P] Test unitario `frontend/tests/unit/formato.test.ts`: `[001][FR-017]` `formatearEuros(4000)` → «40,00 €», `formatearEuros(5)` → «0,05 €», `formatearEuros(1000000)` → «10.000,00 €» (documentar que «1000,00 €» sin separador es lo correcto según CLDR, research D5); `[001][FR-026]` `formatearFecha('2026-11-26')` → «26/11/2026», `formatearFechaLarga('2026-11-26')` → «jueves, 26/11/2026», horas «HH:mm» de 24 h tal cual llegan del API, sin aritmética de zonas.

### Implementación de fundamentos

- [ ] T017 [P] Crear `backend/src/main/java/es/citaclara/comun/ZonaClinica.java` (constante `ZONA = ZoneId.of("Europe/Madrid")`; utilidades `aInstante(LocalDate, LocalTime)` que lanza `HoraNoValida` si la hora local no existe o es ambigua, `fechaLocal(Instant)`, `horaLocal(Instant)`, `hoy(Clock)`) y `backend/src/main/java/es/citaclara/comun/ConfiguracionReloj.java` (bean `Clock.systemUTC()`). Hace pasar T012.
- [ ] T018 [P] Crear `backend/src/main/java/es/citaclara/comun/Telefono.java` (normalización y validación según research D11, formato guardado `+34XXXXXXXXX` / `+XXXXXXXX…`) y la anotación de Jakarta Validation `TelefonoValido` con su validador, mensaje «Escribe un teléfono válido, por ejemplo 612 345 678.». Hace pasar T011.
- [ ] T019 [P] Crear el formato de errores en `backend/src/main/java/es/citaclara/comun/errores/`: `ErrorNegocio.java` (excepción con `HttpStatus`, `codigo`, `mensaje` en español y propiedades extra opcionales `citaConflictiva`, `citaActual`, `pacienteExistente`) y `ManejadorErrores.java` (`@RestControllerAdvice` que devuelve `ProblemDetail` `application/problem+json` con propiedades `codigo` y `mensaje`; `MethodArgumentNotValidException` → 400 `DATOS_NO_VALIDOS` con `errores[{campo, mensaje}]` en español; JSON ilegible → 400 `DATOS_NO_VALIDOS`; recurso inexistente → 404 `NO_ENCONTRADO` «No hemos encontrado lo que buscas.»; cualquier otra excepción → 500 `ERROR_INTERNO` «Algo ha fallado. Inténtalo de nuevo en unos segundos.» sin trazas ni identificadores, FR-027).
- [ ] T020 [P] Crear en `backend/src/main/java/es/citaclara/clinica/` las entidades JPA y repositorios `Clinica.java` + `RepositorioClinica.java` (`findByIdentificador`), `HorarioAtencion.java` (id compuesto `clinicaId` + `diaSemana`, `apertura`/`cierre` como `LocalTime`) + `RepositorioHorario.java` (`findByClinicaIdAndDiaSemana`, `findByClinicaIdOrderByDiaSemana`), `Profesional.java` + `RepositorioProfesional.java` (`findByIdAndClinicaId`, `findByClinicaIdOrderByNombre`) y `Servicio.java` + `RepositorioServicio.java` (`findByIdAndClinicaId`, `findByClinicaIdOrderByEspecialidadAscNombreAsc`), mapeando exactamente columnas y tipos de V1 (`precioCentimos` como `int`).
- [ ] T021 [P] Crear `backend/src/main/java/es/citaclara/paciente/Paciente.java` (`nombre` obligatorio y recortado, máx. 120; `telefono` normalizado, máx. 20; `email` opcional, máx. 254) y `RepositorioPaciente.java` (`findByIdAndClinicaId`, `findByClinicaIdAndTelefono`, consulta nativa de búsqueda que se completa en US2).
- [ ] T022 [P] Crear `backend/src/main/java/es/citaclara/cita/EstadoCita.java` (`RESERVADA`, `COMPLETADA`, `CANCELADA`, `NO_ASISTIDA`, guardado como texto), `Accion.java` (`CANCELAR`, `COMPLETAR`, `MARCAR_NO_ASISTIDA`), `Cita.java` (`inicio`/`fin` como `Instant`, `importeCentimos` `int`, `estado` con `@Enumerated(STRING)`, `@Version int version`, `creadaEn`; relaciones `ManyToOne` perezosas a profesional, servicio y paciente; `clinicaId`) y `RepositorioCita.java` (`findByIdAndClinicaId`; citas de un profesional cuyo `inicio` está en `[inicioDiaLocal, inicioDiaSiguienteLocal)` ordenadas por inicio, con `JOIN FETCH` de paciente y servicio; primera cita `RESERVADA`/`COMPLETADA` del profesional que solape `[inicio, fin)`).
- [ ] T023 Crear `backend/src/main/java/es/citaclara/cita/MaquinaEstadosCita.java` (depende de T022): `accionesDisponibles(Cita, Instant ahora)` y `aplicar(Cita, Accion, Instant ahora)` según la máquina de [data-model.md](data-model.md); al rechazar lanza `ErrorNegocio` 422 `ACCION_NO_DISPONIBLE` con mensaje en español (p. ej. «Esta cita ya ha empezado: no se puede cancelar. Márcala como completada o no asistida.», «Esta cita aún no ha empezado: solo se puede cancelar.», «Esta cita ya está cerrada y no admite cambios.»). Hace pasar T013.
- [ ] T024 Crear `backend/src/main/java/es/citaclara/cita/CitaRespuesta.java` (record con la forma exacta del esquema `Cita` de contracts/api.yaml: `id`, `fecha`, `horaInicio`, `horaFin` en `HH:mm` de Madrid, `profesional{id,nombre}`, `paciente{id,nombre,telefono}`, `servicio{id,nombre}`, `importeCentimos`, `estado`, `acciones`, `version`) y su método de fábrica `desde(Cita, Clock)` que usa `MaquinaEstadosCita` (depende de T017, T023).
- [ ] T025 Crear el acceso en `backend/src/main/java/es/citaclara/acceso/`: `SesionClinica.java` (atributos de sesión `clinicaId` y `accesoEn`; `clinicaActual(HttpServletRequest)`), `InterceptorSesion.java` (sin sesión → 401 `SIN_SESION`; `ahora >= accesoEn + 8 h` según el `Clock` → invalida la sesión y 401 `SESION_CADUCADA`, sin aplicar la acción; FR-002), `ConfiguracionWeb.java` (registra el interceptor en `/api/**` salvo `/api/clinicas/**` y `DELETE /api/sesion`), `ServicioAcceso.java` (verifica la clave con `BCryptPasswordEncoder`; clave incorrecta o clínica inexistente → misma respuesta 401 `CLAVE_INCORRECTA` «La clave no es correcta. Revísala e inténtalo de nuevo.», FR-004) y `ControladorAcceso.java` (`GET /api/clinicas/{identificador}` → `{nombre}` o 404; `POST /api/clinicas/{identificador}/sesion` → invalida la sesión previa, crea una nueva y responde 204; `DELETE /api/sesion` → 204 siempre). Depende de T019, T020. Hace pasar T014.
- [ ] T026 Crear `backend/src/main/java/es/citaclara/clinica/ControladorContexto.java` con `GET /api/sesion` según el esquema `Contexto` (clínica, `hoy`, `sesionCaducaA`, profesionales, servicios, horario), leyendo siempre por el `clinicaId` de la sesión (FR-003). Depende de T025. Hace pasar T015.
- [ ] T027 [P] Crear `frontend/src/formato/euros.ts` (único formateador de importes: `Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })` sobre céntimos enteros / 100) y `frontend/src/formato/fechas.ts` (`formatearFecha`, `formatearFechaLarga`, `sumarDias`, `esFechaValida` sobre cadenas `YYYY-MM-DD` sin crear `Date` con zona local del dispositivo). Ningún otro módulo formatea importes ni fechas. Hace pasar T016.
- [ ] T028 [P] Crear `frontend/src/api/tipos.ts` (tipos TypeScript que reflejan exactamente los esquemas de contracts/api.yaml: `Problema`, `Contexto`, `Profesional`, `Servicio`, `Paciente`, `NuevoPaciente`, `NuevaCita`, `Estado`, `Accion`, `Cita`, `Hueco`, `Agenda`) y `frontend/src/api/cliente.ts` (envoltorio de `fetch` con `credentials: 'same-origin'` y `Content-Type: application/json`; respuestas `application/problem+json` → `ErrorApi` con `codigo`, `mensaje` y extras; 401 `SESION_CADUCADA`/`SIN_SESION` → emite un evento global `sesion-perdida` con el mensaje; errores de red → «No hay conexión con el servidor. Inténtalo de nuevo.»). Los `codigo` nunca se muestran (FR-027).
- [ ] T029 [P] Crear `frontend/src/estilos.css`: `@import "tailwindcss"`, `@import "@fontsource-variable/inter"`, tokens `@theme` en OKLCH para superficie, texto, borde, acento y los cuatro estados (Reservada índigo, Completada verde, Cancelada gris, No asistida ámbar) en claro y oscuro con `prefers-color-scheme`, contraste WCAG 2.2 AA, radios 16–20 px, sombras sutiles, `font-variant-numeric: tabular-nums` para horas e importes, `prefers-reduced-motion` que desactiva transiciones, y `overflow-x: clip` en `body` como red de seguridad (el diseño no debe depender de ello).
- [ ] T030 [P] Crear los componentes base en `frontend/src/ui/`: `Boton.tsx` (variantes primaria/secundaria/peligro, objetivo táctil ≥ 44 px, estado ocupado), `Dialogo.tsx` (envoltorio de `<dialog>` nativo con título, foco inicial y cierre con Escape; hoja inferior por debajo de 640 px), `Aviso.tsx` (`role="alert"` / `role="status"`, variantes error/éxito/información con icono) y `DistintivoEstado.tsx` (color + icono `lucide-react` + texto «Reservada», «Completada», «Cancelada», «No asistida»; nunca solo color, FR-006, FR-028).
- [ ] T031 Crear `frontend/src/App.tsx` (depende de T028): `/` muestra «Abre la dirección del panel de tu clínica para entrar.»; `/{identificador}` consulta `GET /api/sesion` y muestra la pantalla de acceso o la agenda (las pantallas llegan en US1; dejar marcadores); escucha `sesion-perdida` para volver al acceso conservando el aviso de caducidad; sin enrutador ni gestor de estado global (research D15).

**Punto de control**: T011–T016 en verde; se puede entrar con una clave de prueba vía API y obtener el contexto.

---

## Fase 3: Historia de usuario 1 — Consultar la agenda del día de un profesional (Prioridad: P1) 🎯 MVP

**Objetivo**: entrar con la clave de la clínica y ver, para un día y un profesional, sus citas con horario, paciente, servicio, importe y estado, y los huecos libres del horario de atención.

**Prueba independiente**: con datos de prueba (o la semilla), entrar con la clave, abrir la agenda de María en un día con citas y comprobar que se ven todas sus citas con su estado y los huecos libres entre ellas; un día sin citas muestra todo el horario libre; a 360 px no hay desplazamiento horizontal.

### Tests de US1 (escribir primero; deben fallar)

- [ ] T032 [P] [US1] Test unitario `backend/src/test/java/es/citaclara/agenda/CalculadoraHuecosTest.java`: `[001][FR-007][US1-AC6]` sin citas → un único hueco con todo el horario; `[001][FR-007][US1-AC4]` huecos como complemento de la unión de intervalos `RESERVADA`/`COMPLETADA` (incluidas citas contiguas, que no dejan hueco de 0 min); `[001][FR-024][US1-AC5]` citas `CANCELADA`/`NO_ASISTIDA` no restan hueco; `reservable = false` si el hueco ha terminado entero antes de `ahora` y `true` si aún queda algún minuto futuro; día sin horario → lista vacía.
- [ ] T033 [P] [US1] Test de integración `backend/src/test/java/es/citaclara/agenda/AgendaApiTest.java`: `[001][FR-005][US1-AC3]` sin `fecha` devuelve la agenda de `hoy` según el `RelojControlable` y `hoy` coincide; con `fecha` explícita devuelve ese día; `[001][FR-006][US1-AC4]` cada cita trae `horaInicio`/`horaFin` en 24 h, paciente, servicio, `importeCentimos` y `estado`, ordenadas por inicio; `[001][US1-AC5]` una cancelada y una no asistida aparecen en `citas` y su franja figura en `huecos`; `[001][US1-AC6]` día sin citas → un hueco con todo el horario; `[001][FR-015]` un sábado → `horario: null` y `huecos` vacía; `acciones` de cada cita coinciden con `MaquinaEstadosCita`; `[001][FR-003]` un `profesionalId` de otra clínica → 404 `NO_ENCONTRADO`; `[001][SC-007]` la respuesta de un día con 20 citas tarda menos de 500 ms en el entorno de pruebas.
- [ ] T034 [P] [US1] Test de integración `backend/src/test/java/es/citaclara/agenda/AgendaCambioHorarioTest.java` con una clínica de prueba que atiende en domingo: `[001][FR-026]` el 25/10/2026 (cambio de octubre) y el 28/03/2027 (cambio de marzo), una cita de 10:00 a 10:45 se devuelve como «10:00»–«10:45», aparece una sola vez y en ese día, y los huecos del día suman la duración local correcta del horario.
- [ ] T035 [P] [US1] Tests de componente `frontend/tests/unit/agenda.test.tsx`: `[001][FR-006][US1-AC4]` `TarjetaCita` muestra «10:00 – 10:45», paciente, servicio, «40,00 €» y el distintivo con texto; `[001][US1-AC5]` cancelada y no asistida se muestran atenuadas con su estado; `[001][FR-007][FR-008]` `LineaTiempo` intercala tarjetas y huecos «Libre · 10:45 – 12:00» en orden y solo los huecos `reservable` tienen «Reservar aquí»; día sin horario → «La clínica no atiende este día.»; `[001][FR-005]` `SelectorProfesional` conserva la fecha al cambiar de profesional y `NavegadorDia` ofrece «Día anterior», «Día siguiente», «Hoy» y fecha concreta.
- [ ] T036 [P] [US1] Test de componente `frontend/tests/unit/acceso.test.tsx`: `[001][FR-001][US1-AC1]` la pantalla de acceso muestra el nombre de la clínica, «Clave de la clínica» (tipo contraseña, con botón para mostrarla) y «Entrar», y no muestra datos de agenda; `[001][US1-AC2]` ante 401 `CLAVE_INCORRECTA` muestra bajo el campo «La clave no es correcta. Revísala e inténtalo de nuevo.»; llegada por caducidad muestra «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.».

### Implementación de US1

- [ ] T037 [P] [US1] Crear `backend/src/main/java/es/citaclara/agenda/CalculadoraHuecos.java` (función pura: horario del día como intervalo de instantes, citas ocupantes, `ahora` → lista de huecos `{inicio, fin, reservable}`), usando `ZonaClinica` para pasar de horas locales a instantes. Hace pasar T032.
- [ ] T038 [US1] Crear `backend/src/main/java/es/citaclara/agenda/ServicioAgenda.java` y `backend/src/main/java/es/citaclara/agenda/AgendaRespuesta.java` (forma exacta del esquema `Agenda`: `fecha`, `hoy`, `profesional`, `horario` o `null`, `citas` como `CitaRespuesta`, `huecos` con `horaInicio`, `horaFin`, `reservable`): valida que el profesional sea de la clínica de la sesión (si no, 404), lee el horario del `dia_semana` ISO, las citas del día local y calcula huecos (depende de T024, T037).
- [ ] T039 [US1] Crear `backend/src/main/java/es/citaclara/agenda/ControladorAgenda.java` con `GET /api/agenda?profesionalId=&fecha=` (fecha opcional, por defecto hoy en Madrid según el `Clock`; fecha mal formada → 400 `DATOS_NO_VALIDOS`). Hace pasar T033 y T034.
- [ ] T040 [P] [US1] Crear `frontend/src/acceso/PantallaAcceso.tsx`: carga el nombre con `GET /api/clinicas/{identificador}`, formulario con «Clave de la clínica» (tipo contraseña, botón mostrar/ocultar con `aria-label`), botón «Entrar», `POST /api/clinicas/{identificador}/sesion`; muestra el `mensaje` del API bajo el campo y el aviso de caducidad si llega por `sesion-perdida`. Hace pasar T036.
- [ ] T041 [P] [US1] Crear `frontend/src/agenda/TarjetaCita.tsx` y `frontend/src/agenda/HuecoLibre.tsx`: tarjeta con horario «HH:mm – HH:mm», paciente, servicio, importe con `formatearEuros`, `DistintivoEstado`, atenuada si cancelada o no asistida (cancelada con tachado) y una ranura para acciones (se rellena en US3); hueco como bloque punteado «Libre · HH:mm – HH:mm» con botón «Reservar aquí» solo si `reservable` que invoca un `onReservar(horaInicio)` (FR-008).
- [ ] T042 [P] [US1] Crear `frontend/src/agenda/SelectorProfesional.tsx` (control segmentado accesible `role="radiogroup"` con nombre y especialidad de cada profesional; sin desplazamiento horizontal a 360 px, apilándose si no cabe) y `frontend/src/agenda/NavegadorDia.tsx` («Día anterior», fecha larga «jueves, 26/11/2026», «Día siguiente», «Hoy» —usa `hoy` del servidor— y `<input type="date">` para fecha concreta).
- [ ] T043 [US1] Crear `frontend/src/agenda/LineaTiempo.tsx` (lista vertical cronológica que intercala `TarjetaCita` y `HuecoLibre` según `horaInicio`; «La clínica no atiende este día.» si `horario` es `null`) y `frontend/src/agenda/PantallaAgenda.tsx` (cabecera fija translúcida con nombre de la clínica y «Cerrar sesión» → `DELETE /api/sesion` y vuelta al acceso; barra de control fija con `SelectorProfesional` y `NavegadorDia`; estado `profesional` y `fecha` sincronizado con la query string `?profesional={id}&fecha=AAAA-MM-DD` mediante `URLSearchParams` y `history.pushState`/`popstate`; carga `GET /api/agenda` en cada cambio; transición de día con View Transitions API si está disponible; botón «Nueva cita» como marcador para US2). Depende de T041, T042. Hace pasar T035.
- [ ] T044 [US1] Conectar en `frontend/src/App.tsx` `PantallaAcceso` y `PantallaAgenda` según la sesión (tras entrar, cargar el contexto y abrir la agenda de hoy con el primer profesional si la query string no indica otro).

**Punto de control**: US1 funcional y probada de forma independiente (T032–T036 en verde); agenda navegable en portátil y en móvil.

---

## Fase 4: Historia de usuario 2 — Dar de alta una cita sin solapes (Prioridad: P1) 🎯 MVP

**Objetivo**: crear citas reservadas para pacientes existentes o nuevos, solo en huecos libres, futuros y dentro del horario, con exactamente una reserva ganadora ante altas simultáneas.

**Prueba independiente**: crear una cita en un hueco libre futuro y verla en la agenda; intentar otra que la pise y ver el rechazo con la cita conflictiva; lanzar 10 altas simultáneas del mismo hueco y comprobar que exactamente una queda registrada.

### Tests de US2 (escribir primero; deben fallar) — puerta de merge 3 (Principio III)

- [ ] T045 [P] [US2] Test de integración `backend/src/test/java/es/citaclara/cita/AltaCitaTest.java` (reloj fijado a un día laborable a las 12:00, Jorge y María de fisioterapia, Lucía de nutrición, horario 09:00–20:00): `[001][FR-009][FR-011][FR-017][US2-AC1]` «Sesión fisioterapia» a las 10:00 de un día futuro → 201, `RESERVADA`, `horaFin` «10:45», `importeCentimos` 4000, y aparece en `GET /api/agenda`; el importe sigue siendo 4000 aunque después se cambie `precio_centimos` del servicio en BD; `[001][FR-012][FR-016][US2-AC2]` otra de Jorge a las 10:30 → 409 `SOLAPE` con `mensaje` exacto «Jorge ya tiene una cita de 10:00 a 10:45. Elige otro hueco.» y `citaConflictiva`, sin registrar nada; solape por contención (09:45–11:00 sobre 10:00–10:45) y coincidencia exacta → 409 `SOLAPE`; `[001][US2-AC3]` a las 10:45 → 201; `[001][FR-024][US2-AC4]` sobre una cancelada de 10:00 a 10:45 → 201, y también sobre una no asistida; `[001][US2-AC6]` María a las 10:00 → 201; `[001][FR-014][SC-003][US2-AC7]` 11:30 de hoy, 12:00 de hoy (inicio igual a «ahora») y cualquier hora de ayer → 422 `HORA_PASADA` «No se pueden crear citas en el pasado. Elige una hora posterior.»; `[001][FR-010][US2-AC9]` servicio de nutrición con Jorge → 422 `SERVICIO_NO_DISPONIBLE` «Jorge no ofrece ese servicio. Elige uno de fisioterapia.»; `[001][FR-015][US2-AC10]` 19:30 con 45 min → 422 `FUERA_DE_HORARIO` «La cita debe empezar y terminar dentro del horario de atención (09:00 a 20:00).», 08:55 → igual, sábado → «La clínica no atiende ese día.»; hora no múltiplo de 5 («10:03») → 400 `HORA_NO_VALIDA`; `[001][FR-003]` profesional, servicio o paciente de otra clínica → 404 `NO_ENCONTRADO` sin registrar nada.
- [ ] T046 [P] [US2] Test de concurrencia `backend/src/test/java/es/citaclara/cita/AltaConcurrenteTest.java` contra el API real: `[001][FR-013][SC-002][US2-AC5]` 10 hilos sincronizados con `CountDownLatch` envían `POST /api/citas` para el mismo hueco de Jorge (mismo inicio y también variantes con solape parcial) → exactamente 1 respuesta 201 y 9 respuestas 409 con `codigo` `HUECO_RECIEN_OCUPADO` («Ese hueco se acaba de ocupar. Elige otro hueco.») o `SOLAPE`; repetido 20 veces con limpieza entre repeticiones; `[001][SC-001]` al terminar, una consulta SQL de solapes entre citas `RESERVADA`/`COMPLETADA` del mismo profesional devuelve 0 filas.
- [ ] T047 [P] [US2] Test de integración `backend/src/test/java/es/citaclara/paciente/PacientesTest.java`: `[001][FR-018]` `GET /api/pacientes?q=lucia` encuentra «Lucía Gómez» (sin tildes ni mayúsculas), `q=612 345` encuentra por teléfono normalizado, máximo 20 resultados ordenados por nombre, `q` de menos de 2 caracteres → 400 `DATOS_NO_VALIDOS`, nunca devuelve pacientes de otra clínica (FR-003); `[001][FR-019][US2-AC8]` alta de cita con `nuevoPaciente` válido crea la ficha (teléfono guardado normalizado) y la cita asociada; teléfono o email inválidos → 400 `DATOS_NO_VALIDOS` con `errores` por campo en español; nombre vacío → 400; `[001][FR-020]` `nuevoPaciente` con un teléfono existente (escrito con otro formato) → 409 `PACIENTE_DUPLICADO` «Ya hay un paciente con ese teléfono: Ana Pérez. ¿Quieres usar su ficha?» con `pacienteExistente`, y el mismo teléfono en otra clínica sí se permite; `[001][FR-016]` atomicidad (research D12): `nuevoPaciente` válido con una hora que provoca `SOLAPE`, `HORA_PASADA` o `FUERA_DE_HORARIO` → no se crea ni la cita ni la ficha; enviar a la vez `pacienteId` y `nuevoPaciente`, o ninguno → 400 `DATOS_NO_VALIDOS`.
- [ ] T048 [P] [US2] Test de integración `backend/src/test/java/es/citaclara/acceso/SesionCaducadaAltaTest.java`: `[001][FR-002]` con la sesión iniciada y el reloj avanzado 8 h, `POST /api/citas` → 401 `SESION_CADUCADA` y la cita no se registra (ni la ficha de un paciente nuevo).
- [ ] T049 [P] [US2] Tests de componente `frontend/tests/unit/alta-cita.test.tsx`: `[001][FR-010]` el diálogo solo ofrece los servicios de la especialidad del profesional, con texto «Sesión fisioterapia · 45 min · 40,00 €»; `[001][FR-009]` el selector de hora avanza de 5 en 5 min dentro del horario y muestra «Termina a las 10:45» calculado y no editable; `[001][FR-008]` abierto desde «Reservar aquí» llega con profesional, día y hora propuestos; `[001][FR-016]` ante un 409/422 muestra el `mensaje` del API en la parte superior y conserva todos los datos introducidos (servicio, hora, paciente elegido o datos del paciente nuevo); `[001][FR-020]` ante `PACIENTE_DUPLICADO` ofrece «Usar esta ficha», que selecciona al paciente existente sin perder el resto; `[001][FR-019]` Nombre y Teléfono marcados como obligatorios y Email opcional; ningún texto visible contiene el `codigo` (FR-027).

### Implementación de US2

- [ ] T050 [P] [US2] Completar en `backend/src/main/java/es/citaclara/paciente/RepositorioPaciente.java` la búsqueda nativa `WHERE clinica_id = :clinicaId AND (lower(<unaccent inmutable>(nombre)) LIKE '%' || lower(<unaccent inmutable>(:q)) || '%' OR telefono LIKE '%' || :digitos || '%') ORDER BY nombre LIMIT 20`, y crear `backend/src/main/java/es/citaclara/paciente/ServicioPacientes.java` (`buscar(clinicaId, q)`; `crear(clinicaId, NuevoPaciente)` que normaliza el teléfono y, si ya existe en la clínica, lanza 409 `PACIENTE_DUPLICADO` con `pacienteExistente`) y `backend/src/main/java/es/citaclara/paciente/ControladorPacientes.java` (`GET /api/pacientes?q=`, `q` con mínimo 2 caracteres).
- [ ] T051 [P] [US2] Crear `backend/src/main/java/es/citaclara/cita/NuevaCitaPeticion.java` (record validado con Jakarta Validation: `profesionalId`, `servicioId`, `fecha` obligatorios; `horaInicio` con patrón `^([01]\d|2[0-3]):[0-5]\d$`; exactamente uno de `pacienteId` o `nuevoPaciente`; `nuevoPaciente.nombre` 1–120 caracteres tras recortar, `telefono` con `@TelefonoValido`, `email` opcional con `@Email` y máx. 254) con mensajes por campo en español.
- [ ] T052 [US2] Crear `backend/src/main/java/es/citaclara/cita/ServicioAltaCita.java` (`@Transactional`, depende de T017, T022, T024, T050, T051) que aplica las reglas en el orden exacto de [data-model.md](data-model.md): 1) recursos de la clínica de la sesión (si no, 404); 2) paciente existente o creación con `ServicioPacientes`; 3) servicio de la especialidad del profesional (`SERVICIO_NO_DISPONIBLE`, nombrando la especialidad); 4) hora válida, múltiplo de 5 y existente en Madrid (`HORA_NO_VALIDA`); 5) `inicio > ahora` con el `Clock` (`HORA_PASADA`); 6) `[inicio, fin)` dentro del horario del día (`FUERA_DE_HORARIO`, con el horario «HH:mm a HH:mm» o «La clínica no atiende ese día.»); 7) comprobación previa de solape con mensaje «{Profesional} ya tiene una cita de HH:mm a HH:mm. Elige otro hueco.» (`SOLAPE` + `citaConflictiva`); 8) `fin = inicio + duracionMinutos` sobre el instante, `importeCentimos = precioCentimos`, estado `RESERVADA`, `saveAndFlush`. Toda la operación es una única transacción (paciente nuevo incluido).
- [ ] T053 [US2] Traducir las violaciones de restricción en `backend/src/main/java/es/citaclara/comun/errores/ManejadorErrores.java`: `DataIntegrityViolationException` cuya causa SQL tenga SQLSTATE `23P01` y restricción `cita_sin_solape` → 409 `HUECO_RECIEN_OCUPADO` «Ese hueco se acaba de ocupar. Elige otro hueco.»; SQLSTATE `23505` sobre la unicidad `(clinica_id, telefono)` → 409 `PACIENTE_DUPLICADO` (releyendo la ficha existente para `pacienteExistente`). Asegurar que la transacción entera se deshace (ni cita ni paciente).
- [ ] T054 [US2] Crear `backend/src/main/java/es/citaclara/cita/ControladorCitas.java` con `POST /api/citas` → 201 con `CitaRespuesta` (depende de T052, T053). Hace pasar T045, T046, T047 y T048.
- [ ] T055 [P] [US2] Crear `frontend/src/citas/BuscadorPaciente.tsx` (campo de búsqueda con espera de 250 ms, mínimo 2 caracteres, lista accesible `role="listbox"` con nombre y teléfono, selección por teclado y toque; enlace «Paciente nuevo») y `frontend/src/citas/FormularioPacienteNuevo.tsx` (Nombre*, Teléfono* con `inputmode="tel"`, Email; muestra los `errores` por campo del API junto a cada campo; ante `PACIENTE_DUPLICADO` muestra «Ya hay un paciente con ese teléfono: {nombre}. ¿Quieres usar su ficha?» con botón «Usar esta ficha»).
- [ ] T056 [US2] Crear `frontend/src/citas/DialogoAltaCita.tsx` (depende de T030, T055): `Dialogo` con profesional y día prefijados, selector de servicio filtrado por especialidad («Nombre · N min · 40,00 €»), selector de hora en pasos de 5 min dentro del horario del día con «Termina a las HH:mm» calculado, paciente (buscador o nuevo) y botón «Reservar cita»; envía `POST /api/citas`; ante error muestra `Aviso` con el `mensaje` arriba y conserva todo el estado del formulario; ante 401 delega en `sesion-perdida` (la cita no se ha registrado). Hace pasar T049.
- [ ] T057 [US2] Conectar el alta en `frontend/src/agenda/PantallaAgenda.tsx` y `frontend/src/agenda/HuecoLibre.tsx`: «Reservar aquí» abre `DialogoAltaCita` con la hora del hueco; «Nueva cita» lo abre con la primera hora reservable; al reservar, cierra el diálogo, recarga la agenda, resalta brevemente la nueva tarjeta y muestra «Cita reservada para {paciente} a las {HH:mm}.».

**Punto de control**: US1 + US2 forman el MVP; T045–T049 en verde, incluidas las 20 repeticiones de las 10 altas simultáneas.

---

## Fase 5: Historia de usuario 3 — Marcar el resultado de una cita (Prioridad: P2)

**Objetivo**: desde la agenda, marcar una cita reservada como completada, cancelada o no asistida según la hora, con protección ante cambios simultáneos.

**Prueba independiente**: cancelar una cita reservada futura y ver su hueco libre; marcar una ya iniciada como completada y otra como no asistida; comprobar que las cerradas no ofrecen acciones y que dos cambios simultáneos dejan solo el primero.

### Tests de US3 (escribir primero; deben fallar)

- [ ] T058 [P] [US3] Test de integración `backend/src/test/java/es/citaclara/cita/CambioEstadoTest.java` (reloj controlable): `[001][FR-022][US3-AC1]` cancelar una reservada futura → 200 `CANCELADA` y su franja aparece en `huecos` de la agenda; `[001][FR-023][US3-AC2]` completar desde la hora de inicio → `COMPLETADA` y sigue ocupando (un alta en esa franja da `SOLAPE`); `[001][US3-AC3]` marcar no asistida desde el inicio → `NO_ASISTIDA`; `[001][US3-AC4]` completar o marcar no asistida antes del inicio → 422 `ACCION_NO_DISPONIBLE`; `[001][US3-AC5]` cancelar desde el inicio → 422 `ACCION_NO_DISPONIBLE` «Esta cita ya ha empezado: no se puede cancelar. Márcala como completada o no asistida.»; `[001][FR-021][US3-AC6]` cualquier acción sobre una cita cerrada → 422 y la agenda devuelve `acciones` vacía; `[001][FR-003]` cita de otra clínica → 404; sesión caducada → 401 y el estado no cambia (FR-002).
- [ ] T059 [P] [US3] Test de concurrencia `backend/src/test/java/es/citaclara/cita/CambioEstadoConcurrenteTest.java`: `[001][FR-025][US3-AC7]` dos hilos sincronizados envían `COMPLETAR` y `MARCAR_NO_ASISTIDA` sobre la misma cita con la misma `version` → exactamente uno 200 y el otro 409 `ESTADO_YA_CAMBIADO` con `citaActual` y mensaje «Otra persona acaba de cambiar esta cita. Ahora está: Completada.» (o «No asistida»); petición tardía con `version` antigua → 409 igual; repetido 20 veces.
- [ ] T060 [P] [US3] Tests de componente `frontend/tests/unit/acciones-cita.test.tsx`: `[001][US3-AC4][US3-AC5][US3-AC6]` `AccionesCita` muestra solo los botones de las `acciones` recibidas («Cancelar cita», «Completada», «No asistió») y ninguno si la lista está vacía; `[001][FR-022][US3-AC1]` «Cancelar cita» pide confirmación con «¿Cancelar la cita de Ana Pérez del 26/11/2026 a las 10:00? El hueco quedará libre.» y botones «Sí, cancelar» / «No, volver», y «No, volver» no envía nada; «Completada» y «No asistió» se envían sin confirmación; `[001][US3-AC7]` ante 409 `ESTADO_YA_CAMBIADO` muestra el `mensaje` y actualiza la tarjeta con `citaActual`.

### Implementación de US3

- [ ] T061 [US3] Crear `backend/src/main/java/es/citaclara/cita/ServicioEstadoCita.java` (`@Transactional`): busca la cita por `id` y `clinicaId` (404 si no), si `version` recibida ≠ actual → 409 `ESTADO_YA_CAMBIADO` con `citaActual`; aplica `MaquinaEstadosCita.aplicar` con el `Clock`; `saveAndFlush`; traduce `ObjectOptimisticLockingFailureException` a 409 `ESTADO_YA_CAMBIADO` releyendo la cita en una transacción nueva para `citaActual` y nombrando el estado en español («Completada», «Cancelada», «No asistida», «Reservada»).
- [ ] T062 [US3] Añadir a `backend/src/main/java/es/citaclara/cita/ControladorCitas.java` `POST /api/citas/{citaId}/estado` con cuerpo `{accion, version}` validado → 200 `CitaRespuesta` (depende de T061). Hace pasar T058 y T059.
- [ ] T063 [US3] Crear `frontend/src/agenda/AccionesCita.tsx` (botones según `acciones`: «Cancelar cita» con `Dialogo` de confirmación exacto de contracts/ui.md, «Completada» y «No asistió» directos; envía `POST /api/citas/{id}/estado` con la `version` de la tarjeta; ante 409 muestra `Aviso` con el `mensaje` y pasa `citaActual` al padre) y montarlo en la ranura de `frontend/src/agenda/TarjetaCita.tsx`; tras cualquier cambio, `PantallaAgenda` recarga la agenda para reflejar huecos liberados. Hace pasar T060.

**Punto de control**: US1, US2 y US3 funcionan de forma independiente y conjunta.

---

## Fase 6: Historia de usuario 4 — Datos de demostración deterministas (Prioridad: P2)

**Objetivo**: cargar Clínica Eleva con una historia de 40 días laborables y un futuro de 10, idéntica en cada carga salvo el desplazamiento de fechas, con recuentos documentados.

**Prueba independiente**: cargar la semilla dos veces en entornos limpios (con relojes en días de la semana distintos) y comprobar que el resultado es idéntico por posición relativa y cumple recuentos y proporciones.

**Nota**: puede desarrollarse en paralelo con las fases 3–5 en cuanto termine la fase 2 (solo depende de entidades, `ZonaClinica` y la migración).

### Tests de US4 (escribir primero; deben fallar)

- [ ] T064 [P] [US4] Test unitario `backend/src/test/java/es/citaclara/semilla/DiasLaborablesTest.java`: `[001][FR-029]` el «día laborable −1» y «+1» respecto a un miércoles son martes y jueves; respecto a un lunes, viernes previo y martes; respecto a un sábado o domingo, viernes previo y lunes siguiente; el día de carga nunca es ni −1 ni +1; 40 posiciones hacia atrás y 10 hacia delante, sin descontar festivos.
- [ ] T065 [P] [US4] Test de integración `backend/src/test/java/es/citaclara/semilla/SemillaTest.java`: `[001][FR-030][US4-AC1]` existe Clínica Eleva (`eleva`) con la clave `eleva-demo-2026` verificable con BCrypt, horario lunes a viernes 09:00–20:00, María y Jorge (fisioterapia), Lucía (nutrición), los 4 servicios exactos (Sesión fisioterapia 45 min 4000; Primera visita fisioterapia 60 min 5000; Consulta nutrición 30 min 3500; Primera visita nutrición 45 min 4500) y 40 pacientes con nombre, teléfono válido y único y email; `[001][FR-031][SC-008][US4-AC2]` todas las citas de la historia están cerradas, no asistidas entre el 9 % y el 11 % y canceladas entre el 7 % y el 9 % del total de la historia; `[001][US4-AC3]` los 10 días laborables futuros tienen citas `RESERVADA` de los tres profesionales y huecos libres; `[001][FR-032][SC-001][US4-AC4]` 0 solapes entre citas `RESERVADA`/`COMPLETADA` de un mismo profesional y todas dentro del horario; el día de carga no tiene citas; ninguna cita en sábado ni domingo; cada importe coincide con el precio de su servicio.
- [ ] T066 [P] [US4] Test de integración `backend/src/test/java/es/citaclara/semilla/SemillaDeterminismoTest.java`: `[001][FR-029][SC-008][US4-AC5]` dos cargas con el reloj en el mismo día producen datos idénticos; dos cargas con el reloj en un lunes y en un miércoles producen la misma lista de (día laborable relativo, profesional, hora local de inicio y fin, paciente, servicio, importe, estado) y solo difieren las fechas absolutas; recargar sobre datos existentes es idempotente (borra y recrea Eleva sin tocar otras clínicas).
- [ ] T067 [P] [US4] Test de integración `backend/src/test/java/es/citaclara/semilla/DatosDemostracionDocumentadosTest.java`: `[001][FR-033]` lee `specs/001-nucleo-agenda/datos-demostracion.md` (ruta relativa a la raíz del repositorio) y compara cada cifra con la semilla cargada: citas totales de la historia y del futuro, por estado, por profesional, importes totales en céntimos por estado y por profesional, y al menos tres ejemplos citados por posición («día laborable −3», «día laborable +2»); si alguna difiere, falla indicando cuál.

### Implementación de US4

- [ ] T068 [P] [US4] Crear `backend/src/main/java/es/citaclara/semilla/DiasLaborables.java` (posición relativa → `LocalDate` y viceversa, solo lunes a viernes, sin festivos) y `backend/src/main/java/es/citaclara/semilla/CatalogoDemostracion.java` (constantes: clínica, clave `eleva-demo-2026`, horario, profesionales, servicios y las 40 fichas de pacientes ficticias con nombres españoles, teléfonos `+346…` únicos y emails `@ejemplo.es`). Hace pasar T064.
- [ ] T069 [US4] Crear `backend/src/main/java/es/citaclara/semilla/GeneradorSemilla.java` (puro, sin BD; depende de T068): `java.util.Random` con semilla fija constante; para cada día laborable −40…−1 y +1…+10, por profesional, desde las 09:00 encadena huecos de 0–60 min en múltiplos de 15 y servicios de su especialidad (≈ 20 % primeras visitas) mientras la cita quepa antes de las 20:00, con densidad menor en el futuro para dejar huecos; pacientes elegidos con el mismo `Random`; en la historia asigna por **cuota exacta** `round(0,10·N)` `NO_ASISTIDA` y `round(0,08·N)` `CANCELADA` elegidas con el mismo `Random`, resto `COMPLETADA`; en el futuro todas `RESERVADA`. Produce citas con posición relativa + hora local; el orden de consumo del `Random` no depende del día de la semana de la carga.
- [ ] T070 [US4] Crear `backend/src/main/java/es/citaclara/semilla/CargadorSemilla.java` (`@Transactional`; depende de T069): borra Clínica Eleva y todo lo que cuelga de ella, la recrea con la clave en BCrypt y guarda las citas por `RepositorioCita` (la restricción `cita_sin_solape` también la protege; único camino exento de RN2, FR-032) convirtiendo posición relativa + hora local a instantes con `ZonaClinica` y el `Clock`; devuelve un resumen de recuentos.
- [ ] T071 [US4] Crear `backend/src/main/java/es/citaclara/semilla/ComandoSemilla.java` (`ApplicationRunner` activo solo con el perfil `semilla`): ejecuta `CargadorSemilla`, imprime el resumen de recuentos en español y termina el proceso con código 0 (o distinto de 0 si falla). Verificar que `docker compose run --rm semilla` funciona con el servicio definido en T006. Hace pasar T065 y T066.
- [ ] T072 [US4] Generar `specs/001-nucleo-agenda/datos-demostracion.md` con los recuentos reales de la semilla (citas totales de historia y futuro, por estado, por profesional, importes en euros con formato «1.234,50 €» y en céntimos, porcentajes de no asistidas y canceladas, y ejemplos concretos por posición relativa como «día laborable −3: María, 10:00–10:45, Sesión fisioterapia, 40,00 €, Completada»), nunca con fechas absolutas ni por semana (FR-033), y la clave de demostración. Hace pasar T067.

**Punto de control**: `docker compose run --rm semilla` carga Eleva; T064–T067 en verde; las cifras del documento se reproducen.

---

## Fase 7: Pulido y aspectos transversales

**Propósito**: aceptación extremo a extremo en portátil y 360 px con accesibilidad, revisión de textos, trazabilidad y cierre de la feature.

- [ ] T073 Crear `frontend/playwright.config.ts`: `baseURL` `http://localhost:8080`, proyectos `portatil` (1366×768) y `movil` (360×780, `isMobile`, `hasTouch`) en Chromium; `globalSetup` en `frontend/tests/e2e/preparar.ts` que ejecuta `docker compose run --rm semilla` y fija el contexto; utilidades en `frontend/tests/e2e/utilidades.ts`: `entrar(page)`, `sinDesplazamientoHorizontal(page)` (`document.documentElement.scrollWidth <= clientWidth`) y `sinInfraccionesAxe(page)` (`@axe-core/playwright`, falla con infracciones `serious` o `critical`), y lectura de posiciones relativas desde `datos-demostracion.md`.
- [ ] T074 [P] Crear `frontend/tests/e2e/acceso-agenda.spec.ts` (escenarios 1–5, 20 y 21 de [quickstart.md](quickstart.md)): `[001][FR-001][US1-AC1]`, `[001][US1-AC2]`, `[001][US1-AC3]` (hoy por defecto, María, Jorge y Lucía en el selector), `[001][US1-AC4][US1-AC5]` día laborable −1 con María, sábado «La clínica no atiende este día.», `[001][FR-002]` «Cerrar sesión» vuelve al acceso, enlace con `?profesional=&fecha=` reabre la misma vista y el botón atrás funciona, `[001][SC-007]` agenda visible en < 2 s tras cambiar de día o profesional, `[001][US1-AC7][SC-009]` sin desplazamiento horizontal a 360 px y `[001][FR-028]` axe sin infracciones serias en acceso y agenda, en claro y en oscuro (`colorScheme`).
- [ ] T075 [P] Crear `frontend/tests/e2e/alta-cita.spec.ts` (escenarios 6–15 de quickstart.md) en el día laborable +1: `[001][US2-AC1]` «Reservar aquí» → «Sesión fisioterapia» → paciente existente → cita con fin +45 min y «40,00 €»; `[001][US2-AC2][FR-016]` solape con mensaje y datos conservados; `[001][US2-AC3]` contigua; `[001][US2-AC4]` tras cancelar; `[001][US2-AC6]` María a la misma hora; `[001][US2-AC7]` hora pasada de hoy; `[001][FR-020]` paciente duplicado y «Usar esta ficha»; `[001][US2-AC8]` paciente nuevo; `[001][US2-AC9]` Lucía solo nutrición; `[001][US2-AC10]` 19:30 con 45 min; diálogo como hoja inferior a 360 px sin desplazamiento horizontal y axe sin infracciones serias con el diálogo abierto.
- [ ] T076 [P] Crear `frontend/tests/e2e/estados.spec.ts` (escenarios 16–19 de quickstart.md): `[001][US3-AC4]` futura con solo «Cancelar cita»; `[001][US3-AC6]` cerrada sin acciones; `[001][US3-AC1]` confirmación de cancelación con texto exacto; `[001][US3-AC7]` dos pestañas (dos `page` del mismo contexto) y aviso «Otra persona acaba de cambiar esta cita…»; axe y 360 px. Los escenarios 16 (cita ya empezada: solo «Completada» y «No asistió», US3-AC2/AC3/AC5) dependen de la hora real, porque la semilla no pone citas el día de carga y el reloj del servidor no se controla en e2e: quedan cubiertos por T058 (backend con reloj controlado) y T060 (componente), y se validan a mano según quickstart.md; documentarlo con un comentario en el fichero, sin tests desactivados.
- [ ] T077 Revisar todos los textos visibles en `frontend/src/**` y los `mensaje` de `backend/src/main/java/es/citaclara/**` frente a [contracts/ui.md](contracts/ui.md) y contracts/api.yaml (textos exactos, español de España, sin jerga). Añadir `frontend/tests/e2e/textos.spec.ts`: `[001][FR-027]` recorre acceso, agenda, alta (con un rechazo provocado) y acciones, y comprueba que ningún texto visible contiene códigos (`SOLAPE`, `HUECO_RECIEN_OCUPADO`, `409`, `422`, `NO_ENCONTRADO`…), identificadores numéricos internos ni términos en inglés; `[001][FR-026]` fechas `dd/mm/aaaa`, horas de 24 h e importes «NN,NN €».
- [ ] T078 Ejecutar la guía completa de [quickstart.md](quickstart.md): `docker compose up -d --build`, `docker compose run --rm semilla`, `cd backend && ./mvnw verify`, `cd frontend && npm ci && npm test && npm run e2e` y `./scripts/trazabilidad.sh` (debe salir vacía, puerta 2); corregir cualquier FR/AC sin test o test en rojo (puerta 4). Ningún test desactivado.
- [ ] T079 Actualizar `specs/MAPA.md` con el estado de la spec 001-nucleo-agenda (de «Borrador» a «Implementada») y comprobar que `spec.md` refleja el comportamiento entregado (puerta 1).
- [ ] T080 **Último paso — mantenimiento**: actualizar `CLAUDE.md` (raíz del repositorio; crearlo si no existe) con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[001] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features. Candidatas: RN1 garantizada por la *exclusion constraint* `cita_sin_solape` (ningún camino de escritura exento); importes en céntimos enteros y formateador único `frontend/src/formato/euros.ts`; `timestamptz` + `Europe/Madrid` (`ZonaClinica`) + `Clock` inyectado; el API intercambia fecha y hora locales de Madrid y el frontend no hace aritmética de zonas; errores Problem Details con `codigo` + `mensaje` en español listo para mostrar; convención de trazabilidad `[spec][FR][USn-ACm]` y `scripts/trazabilidad.sh`; aislamiento por `clinica_id` con repositorios filtrados por la clínica de la sesión; semilla por posición relativa en días laborables y recuentos en `datos-demostracion.md` verificados por test; backend organizado por capacidad de dominio.

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Fase 1 (Andamiaje)**: sin dependencias. T006 depende de T004 y T005; T010 de T007 y T008.
- **Fase 2 (Fundamentos)**: depende de la fase 1. **Bloquea todas las historias.**
- **Fase 3 (US1, P1)**: depende de la fase 2.
- **Fase 4 (US2, P1)**: depende de la fase 2. En backend es independiente de US1; en frontend reutiliza `PantallaAgenda` y `HuecoLibre` de US1 (T057 depende de T043 y T041). Su test `AltaCitaTest` usa `GET /api/agenda` en una aserción (US2-AC1), por lo que T054 debe integrarse tras T039 para que T045 pase completo.
- **Fase 5 (US3, P2)**: depende de la fase 2 (máquina de estados) y, en frontend, de `TarjetaCita` (T041) y `PantallaAgenda` (T043). Sus tests de backend crean citas con `DatosPrueba`, sin depender de US2.
- **Fase 6 (US4, P2)**: depende solo de la fase 2; puede hacerse en paralelo con las fases 3–5.
- **Fase 7 (Pulido)**: depende de todas las historias (los e2e necesitan la semilla y las tres pantallas).

### Dependencias dentro de cada historia

- Tests primero (deben fallar) → modelos/funciones puras → servicios → controladores → frontend → integración en pantallas.
- Backend: T017–T024 antes de T025–T026; T037 → T038 → T039; T050–T051 → T052 → T053 → T054; T061 → T062; T068 → T069 → T070 → T071 → T072.
- Frontend: T027–T030 antes de cualquier pantalla; T041–T042 → T043 → T044; T055 → T056 → T057; T063 tras T043.

### Oportunidades de paralelismo

- Fase 1: T002, T003, T004, T005, T008 y T009 en paralelo tras T001 (T003, T005 no dependen de T001).
- Fase 2: todos los tests T011–T016 en paralelo; T017–T022 y T027–T030 en paralelo.
- Con la fase 2 terminada: un equipo/agente en US1, otro en US2 (backend), otro en US3 (backend) y otro en US4.
- Dentro de cada historia, todas las tareas de tests marcadas [P] en paralelo.
- Fase 7: T074, T075 y T076 en paralelo tras T073.

---

## Ejemplos de ejecución en paralelo

### US1

```text
# Tests a la vez:
T032 CalculadoraHuecosTest.java · T033 AgendaApiTest.java · T034 AgendaCambioHorarioTest.java · T035 agenda.test.tsx · T036 acceso.test.tsx
# Implementación a la vez:
T037 CalculadoraHuecos.java · T040 PantallaAcceso.tsx · T041 TarjetaCita.tsx + HuecoLibre.tsx · T042 SelectorProfesional.tsx + NavegadorDia.tsx
```

### US2

```text
# Tests a la vez:
T045 AltaCitaTest.java · T046 AltaConcurrenteTest.java · T047 PacientesTest.java · T048 SesionCaducadaAltaTest.java · T049 alta-cita.test.tsx
# Implementación a la vez:
T050 pacientes (repositorio, servicio, controlador) · T051 NuevaCitaPeticion.java · T055 BuscadorPaciente.tsx + FormularioPacienteNuevo.tsx
```

### US3

```text
T058 CambioEstadoTest.java · T059 CambioEstadoConcurrenteTest.java · T060 acciones-cita.test.tsx
```

### US4

```text
# Tests a la vez:
T064 DiasLaborablesTest.java · T065 SemillaTest.java · T066 SemillaDeterminismoTest.java · T067 DatosDemostracionDocumentadosTest.java
```

---

## Estrategia de implementación

### MVP primero (US1 + US2)

1. Fase 1 (andamiaje) y fase 2 (fundamentos).
2. Fase 3 (US1): validar de forma independiente la agenda con datos de prueba.
3. Fase 4 (US2): validar altas, solapes y las 10 altas simultáneas (puerta 3).
4. **PARAR y VALIDAR**: US1 + US2 son el MVP que da sentido al producto.

### Entrega incremental

1. Andamiaje + fundamentos → base lista.
2. + US1 → agenda consultable (demo con datos de prueba).
3. + US2 → MVP con reservas sin solapes.
4. + US3 → agenda fiel a la realidad.
5. + US4 → demo reproducible con Clínica Eleva (puede adelantarse en paralelo).
6. Pulido → e2e, accesibilidad, trazabilidad, `MAPA.md` y `CLAUDE.md`.

### Equipo en paralelo

Tras la fase 2: persona/agente A con US1 (backend + frontend), B con el backend de US2, C con el backend de US3 y D con US4; el frontend de US2 y US3 se integra cuando US1 tiene `PantallaAgenda`. Cada historia se cierra con sus tests en verde.

---

## Notas

- [P] = ficheros distintos sin dependencias pendientes.
- Cada test lleva la referencia `[001][…]` en su nombre visible; un test sin referencia debe justificarse o desaparecer (Principio VI).
- Verificar que los tests fallan antes de implementar.
- Hacer commit al terminar cada tarea o grupo lógico.
- No añadir dependencias ni funcionalidad fuera de lo justificado en plan.md (Principio IV): sin Spring Security completo, sin enrutador, sin gestor de estado, sin librería de componentes.
