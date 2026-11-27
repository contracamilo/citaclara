---

description: "Lista de tareas de implementación de la feature 002-portal-paciente"
---

# Tareas: Portal del paciente

**Entrada**: documentos de diseño de `specs/002-portal-paciente/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.yaml](contracts/api.yaml), [contracts/ui.md](contracts/ui.md), [quickstart.md](quickstart.md)

**Tests**: OBLIGATORIOS. La constitución exige que cada regla y criterio de aceptación tenga un test que lo referencie (Principio VI y puertas de merge 2–4), y la feature escribe en la agenda, así que el Principio III exige pruebas de solape. Convención (`CLAUDE.md`, [001]): el nombre visible de cada test empieza por `[002][FR-xxx][USn-ACm]` (JUnit con `@DisplayName`; Vitest y Playwright en el título). Los tests de cada historia se escriben primero y DEBEN fallar antes de implementar. SC-001 y SC-002 se miden con usuarios reales y quedan excluidos de la trazabilidad (research R12).

**Organización**: por historia de usuario, para implementar y probar cada una de forma independiente.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: paralelizable (ficheros distintos, sin dependencias de tareas incompletas).
- **[Historia]**: US1, US2, US3, US4 (solo en las fases de historia).
- Cada descripción incluye la ruta exacta del fichero.

## Convenciones de rutas

- Backend nuevo: `backend/src/main/java/es/citaclara/portal/{acceso,correo,citas,cancelaciones}/`; tests en `backend/src/test/java/es/citaclara/portal/`.
- Frontend nuevo: `frontend/src/portal/` y `frontend/src/cancelaciones/`; tests unitarios en `frontend/tests/unit/`, e2e en `frontend/tests/e2e/`.
- Reglas transversales que respeta toda tarea:
  - Importes en céntimos enteros con el único formateador `frontend/src/formato/euros.ts`.
  - Instantes `Instant`/`timestamptz`, lógica en `Europe/Madrid` con `ZonaClinica` y el `Clock` inyectado (nunca `now()` directo).
  - El API intercambia fecha `YYYY-MM-DD` y hora `HH:mm` locales, y el frontend no hace aritmética de fechas.
  - Errores como `ErrorNegocio` con `codigo` y `mensaje` en español de España (textos exactos en [contracts/api.yaml](contracts/api.yaml)).
  - Todo acceso a datos se filtra por `clinica_id`.
  - Nunca se registra en logs un email, un secreto de enlace ni su hash (R13).
  - Las pruebas de integración heredan de `soporte/PruebaIntegracionBase` y usan `RelojControlable`.

---

## Fase 1: Andamiaje (infraestructura compartida)

**Propósito**: dependencia de correo, esquema `V2`, Mailpit en el entorno y trazabilidad por spec.

- [ ] T001 Añadir `spring-boot-starter-mail` (gestionado por el BOM de Spring Boot, sin versión propia) a `backend/pom.xml`. Es la única dependencia nueva del producto (plan, Principio IV).
- [ ] T002 [P] Ampliar `backend/src/main/resources/application.yml`:
  - `spring.mail.host: ${SMTP_HOST:localhost}`, `port: ${SMTP_PUERTO:1025}`, `username: ${SMTP_USUARIO:}` y `password: ${SMTP_CLAVE:}`.
  - `citaclara.email-remitente: ${EMAIL_REMITENTE:citas@citaclara.local}`.
  - `citaclara.url-publica: ${URL_PUBLICA:http://localhost:8080}` (R13: la URL del enlace nunca se toma de la cabecera `Host`).
  - En el perfil `semilla`, sin cambios.
- [ ] T003 [P] Crear `backend/src/main/resources/db/migration/V2__portal_paciente.sql` según [data-model.md](data-model.md):
  - Índice parcial `paciente_email_portal ON paciente (clinica_id, lower(btrim(email))) WHERE email IS NOT NULL`.
  - `ALTER TABLE cita ADD CONSTRAINT cita_id_clinica UNIQUE (id, clinica_id)`.
  - Tabla `solicitud_enlace_portal`:
    - `id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
    - `clinica_id bigint NOT NULL REFERENCES clinica (id) ON DELETE CASCADE`
    - `email_normalizado varchar(254) NOT NULL`
    - `token_hash char(64) UNIQUE` (nulo si el email no coincide con ninguna ficha)
    - `solicitada_en timestamptz NOT NULL`
    - `caduca_en timestamptz NOT NULL`
    - `usada_en timestamptz`
    - `CHECK (caduca_en > solicitada_en)` y `CHECK (usada_en IS NULL OR token_hash IS NOT NULL)`
    - Índice `(clinica_id, email_normalizado, solicitada_en)`
  - Tabla `cancelacion_portal`:
    - `cita_id bigint PRIMARY KEY`
    - `clinica_id bigint NOT NULL`
    - `cancelada_en timestamptz NOT NULL`
    - `revisada_en timestamptz`
    - `FOREIGN KEY (cita_id, clinica_id) REFERENCES cita (id, clinica_id) ON DELETE CASCADE`
    - `CHECK (revisada_en IS NULL OR revisada_en >= cancelada_en)`
    - Índice parcial `(clinica_id, cancelada_en DESC) WHERE revisada_en IS NULL`
  - Cabecera con comentarios que citen FR y R, como en `V1__esquema_inicial.sql`.
- [ ] T004 [P] Añadir a `docker-compose.yml`:
  - Servicio `mailpit` con imagen `axllent/mailpit` en una versión exacta publicada hace más de dos semanas, SMTP interno en 1025 y bandeja web publicada solo en `127.0.0.1:${PUERTO_CORREO:-8025}:8025`.
  - En el ancla `x-entorno-api`: `SMTP_HOST: mailpit`, `SMTP_PUERTO: "1025"` y `URL_PUBLICA: http://localhost:${PUERTO_WEB:-8080}`.
  - `api` depende de `mailpit` (`service_started`).
  - Comentario de cabecera actualizado (FR-031, R2).
- [ ] T005 [P] Generalizar las exclusiones de `scripts/trazabilidad.sh` para que dependan de la spec: `case "$numero" in 001) excluidos="SC-004 SC-005 SC-006";; 002) excluidos="SC-001 SC-002";; *) excluidos="";; esac`, con un comentario que explique por qué (R12). La salida para `specs/001-nucleo-agenda` no cambia.
- [ ] T006 [P] En `.github/workflows/ci.yml`:
  - Pasar `URL_CORREO: http://localhost:8025` al paso e2e, que ya levanta `docker compose`, con lo que Mailpit queda incluido.
  - Ejecutar `./scripts/trazabilidad.sh specs/002-portal-paciente` además de la de 001 en el job de trazabilidad.

---

## Fase 2: Fundamentos (bloquean todas las historias)

**Propósito**: puerto de correo, sesión del portal separada del panel, mensajes, normalización de email y clientes de prueba y frontend.

**⚠️ CRÍTICO**: ninguna historia empieza antes de completar esta fase.

- [ ] T007 [P] Añadir a `backend/src/main/java/es/citaclara/comun/errores/Mensajes.java` los textos exactos de [contracts/api.yaml](contracts/api.yaml):

  | Constante | Texto |
  |---|---|
  | `EMAIL_NO_VALIDO` | «Escribe un email válido, por ejemplo nombre@correo.es.» |
  | `ENLACES_AGOTADOS` | «Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.» |
  | `ENLACE_NO_VALIDO` | «Este enlace ya no es válido. Pide uno nuevo.» |
  | `SIN_SESION_PORTAL` | «Entra con el enlace que te enviamos por email para ver tus citas.» |
  | `SESION_PORTAL_CADUCADA` | «Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.» |
  | `FUERA_DE_PLAZO` | «Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.» |
  | `CITA_PORTAL_YA_CAMBIADA` | «Esta cita acaba de cambiar. Revisa su estado.» |

- [ ] T008 [P] Crear `backend/src/main/java/es/citaclara/portal/acceso/EmailPortal.java`:
  - `normalizar(String)` devuelve `strip().toLowerCase(Locale.ROOT)`.
  - Validación de formato con la misma regla que 001 (D11; reutilizar la validación existente de `paciente`, sin duplicarla). Si el formato no es válido, `ErrorNegocio` 400 `EMAIL_NO_VALIDO`.
  - Test unitario `backend/src/test/java/es/citaclara/portal/EmailPortalTest.java`: `[002][FR-002]` normaliza «  Teresa.Mendez@Ejemplo.es » → «teresa.mendez@ejemplo.es».
- [ ] T009 [P] Crear el puerto de correo en `backend/src/main/java/es/citaclara/portal/correo/`:
  - `CorreoPortal.java`, interfaz con `void enviarEnlace(String email, String nombreClinica, String url)`.
  - `CorreoPortalSmtp.java`, que usa `JavaMailSender`, texto plano, asunto «Tu enlace para entrar en {clínica}» y el cuerpo exacto de [contracts/ui.md §4](contracts/ui.md), con remitente `citaclara.email-remitente` y nombre visible de la clínica.
  - `EnlaceSolicitado.java`, un record de evento.
  - `EnvioEnlaces.java`, con `@TransactionalEventListener(phase = AFTER_COMMIT)` y `@Async`, que llama a `CorreoPortal`. Ante una excepción la registra sin el email ni la URL.
  - Activar `@EnableAsync` en una clase de configuración del paquete `portal/correo` (R2).
- [ ] T010 [P] Crear `backend/src/test/java/es/citaclara/soporte/BuzonPrueba.java`:
  - Implementa `CorreoPortal` en memoria, con lista concurrente de correos (email, clínica, url).
  - Métodos `ultimoEnlace(String email)`, `secreto(String url)` (extrae lo que hay tras `#enlace=`), `enviados()`, `vaciar()` y `fallarEnvios(boolean)` (cuando está activo, `enviarEnlace` lanza una excepción, para T066).
  - Registrarlo como `@Primary` en `backend/src/test/java/es/citaclara/soporte/ConfiguracionPruebas.java`, con un ejecutor `@Async` síncrono en pruebas (`SyncTaskExecutor`) para que las aserciones sean deterministas.
  - Exponer `protected BuzonPrueba buzon` en `PruebaIntegracionBase.java` y vaciarlo en el `@BeforeEach` existente.
- [ ] T011 Crear `backend/src/main/java/es/citaclara/portal/acceso/SesionPortal.java`:
  - Duración `Duration.ofMinutes(30)`.
  - Atributos `citaclara.portal.clinicaId`, `citaclara.portal.email` y `citaclara.portal.accesoEn`.
  - Constantes de petición `CLINICA = "portalClinicaId"` y `EMAIL = "portalEmail"`.
  - `abrir(HttpServletRequest, clinicaId, email, Instant)`: invalida la sesión previa (incluida la del panel, R4), crea una nueva y fija los atributos.
  - `cerrar(HttpServletRequest)`.
- [ ] T012 Crear `backend/src/main/java/es/citaclara/portal/acceso/InterceptorPortal.java` (depende de T011):
  - Si falta la sesión, `ErrorNegocio` 401 `SIN_SESION_PORTAL`.
  - Si `!ahora.isBefore(accesoEn + 30 min)`, invalida y lanza 401 `SESION_PORTAL_CADUCADA` (FR-005).
  - Fija los atributos de petición y añade `Cache-Control: no-store` a la respuesta (US1-AC6).
  - `DELETE /api/portal/sesion` siempre pasa.
- [ ] T013 Modificar `backend/src/main/java/es/citaclara/acceso/ConfiguracionWeb.java` (depende de T012):
  - Añadir `/api/portal/**` a `excludePathPatterns` del interceptor del panel.
  - Registrar `InterceptorPortal` en `/api/portal/**`, excluyendo `/api/portal/*/enlaces` y `/api/portal/*/sesion`, con `order(1)`.
  - El interceptor `SoloJson` sigue aplicándose a todo `/api/**`.
  - Comentario: FR-029.
- [ ] T014 [P] Crear `backend/src/main/java/es/citaclara/portal/acceso/RepositorioPacientesPortal.java` (consultas con `JdbcTemplate` o `@Query` nativa sobre el índice `paciente_email_portal`):
  - `existeConEmail(clinicaId, emailNormalizado)`.
  - `nombresConEmail(clinicaId, emailNormalizado)`: ordenados por nombre.
  - Las dos filtran por `clinica_id = ? AND lower(btrim(email)) = ?` (R5).
- [ ] T015 [P] Ampliar `backend/src/test/java/es/citaclara/soporte/ClienteApi.java` con dos ayudas:
  - `pedirEnlace(identificador, email)`.
  - `entrarEnPortal(identificador, email, BuzonPrueba)`: pide el enlace, toma el secreto del buzón y hace `POST /api/portal/{id}/sesion`, comprobando 204.
- [ ] T016 [P] Ampliar `backend/src/test/java/es/citaclara/soporte/DatosPrueba.java`:
  - `paciente(...)` admite email.
  - Nuevo escenario `escenarioPortal()` sobre `escenario()`: Teresa Méndez `teresa.mendez@ejemplo.es`, Laura Romero `laura.romero@ejemplo.es`, una ficha «Hijo de Laura» con el mismo email de Laura, un paciente sin email y, en `otraClinica()`, un paciente con el email de Teresa.
- [ ] T017 [P] Añadir los tipos del portal y del aviso (`MisCitas`, `CitaPortal`, `EstadoPortal`, `CancelacionPortal`, `CancelacionesPortal`) a `frontend/src/api/tipos.ts`, según los esquemas de [contracts/api.yaml](contracts/api.yaml).
- [ ] T018 Ampliar `frontend/src/api/cliente.ts` (depende de T017):
  - Funciones `pedirEnlace`, `abrirSesionPortal`, `cerrarSesionPortal`, `obtenerMisCitas`, `cancelarCitaPortal`, `obtenerCancelacionesPortal` y `marcarCancelacionRevisada`.
  - Los 401 `SIN_SESION_PORTAL` y `SESION_PORTAL_CADUCADA` NO disparan `EVENTO_SESION_PERDIDA` del panel: emiten su propio evento `sesion-portal-perdida`.
  - Test `frontend/tests/unit/portal-cliente.test.ts`: `[002][FR-029]` un 401 del portal no emite el evento del panel.
- [ ] T019 Modificar `frontend/src/App.tsx` (depende de T018) para que, si el segundo segmento de la ruta es `mis-citas`, renderice `AppPortal` de `frontend/src/portal/AppPortal.tsx` en lugar del panel (R10). Crear `AppPortal.tsx` como esqueleto con los estados `cargando`, `entrada`, `abriendo-enlace`, `mis-citas` y `error`. Sin enrutador.
- [ ] T020 [P] Añadir a `frontend/tests/e2e/utilidades.ts`:
  - `ultimoEnlace(email)`: consulta el API de Mailpit en `process.env.URL_CORREO ?? 'http://localhost:8025'` (`GET /api/v1/search?query=to:{email}`, después `GET /api/v1/message/{id}`) y extrae la URL `…/mis-citas#enlace=…`.
  - `entrarEnPortal(page, email)`.
  - `vaciarCorreo()`: `DELETE /api/v1/messages`.

**Punto de control**: con la fase 2 completa, el panel de 001 sigue en verde (`./mvnw verify`, `npm test`) y el portal ya tiene sesión, interceptor y cliente.

---

## Fase 3: Historia de usuario 1 — Entrar con un enlace al email (Prioridad: P1) 🎯 MVP

**Objetivo**: el paciente pide un enlace con el email de su ficha, lo abre y entra sin cuenta ni contraseña. La respuesta no revela si el email existe y hay límite de solicitudes.

**Prueba independiente**: con la semilla, pedir el enlace para teresa.mendez@ejemplo.es, abrirlo desde Mailpit y llegar a la página que la saluda. Con un email inexistente, comprobar la misma respuesta y que no se envía nada.

### Tests de la historia 1 (escribir primero; DEBEN fallar)

- [ ] T021 [P] [US1] Crear `backend/src/test/java/es/citaclara/portal/AccesoPortalTest.java` con:
  - `[002][FR-001][FR-002][US1-AC1][US1-AC2]` Pedir enlace para Teresa responde 202, el buzón recibe un correo para `teresa.mendez@ejemplo.es` con URL `http://localhost:8080/eleva/mis-citas#enlace=…`, y abrir la sesión con el secreto responde 204 y da acceso a `GET /api/portal/citas` (200). Además, la tabla `paciente` no cambia.
  - `[002][FR-002]` « Teresa.Mendez@Ejemplo.es » encuentra la misma ficha.
  - `[002][FR-003][SC-007][US1-AC3]` Un email inexistente responde 202 con cuerpo y cabeceras relevantes idénticos a los de un email existente, y el buzón queda vacío.
  - `[002][FR-002]` Un paciente sin email no puede entrar (no hay correo) y un email mal formado responde 400 `EMAIL_NO_VALIDO`.
  - `[002][FR-006][SC-009][US1-AC4]` Al reutilizar un secreto, a los 10 min exactos (`reloj.avanzar(10 min)`) y con un secreto de Eleva usado en otra clínica, la respuesta es 401 `ENLACE_NO_VALIDO`. Pedir un enlace nuevo no invalida una sesión ya abierta.
  - `[002][FR-006][SC-009]` Dos aperturas simultáneas del mismo secreto (2 hilos con `CountDownLatch`, 20 repeticiones) dan exactamente un 204.
  - `[002][FR-004][US1-AC7]` La 6.ª solicitud en una hora para el mismo email responde 429 `ENLACES_AGOTADOS`, tanto para un email existente como para uno inexistente. Pasada una hora desde la primera, se acepta. 10 solicitudes simultáneas dejan exactamente 5 aceptadas.
  - `[002][FR-005][US1-AC5]` A los 30 min de entrar, `GET /api/portal/citas` responde 401 `SESION_PORTAL_CADUCADA`, aunque haya habido actividad entre medias.
  - `[002][US1-AC6]` `DELETE /api/portal/sesion` responde 204, la siguiente petición responde 401, y las respuestas de `/api/portal/**` llevan `Cache-Control: no-store`.
  - `[002][FR-029]` La sesión del portal no abre `GET /api/contexto` ni `GET /api/agenda` (401); la comprobación sobre el aviso está en T054. La sesión del panel no abre `GET /api/portal/citas` (401). Abrir sesión en el portal invalida la del panel en el mismo `ClienteApi`.
- [ ] T022 [P] [US1] Crear `backend/src/test/java/es/citaclara/portal/CorreoPortalSmtpTest.java`: `[002][FR-007]` con un `JavaMailSender` simulado (captura del `SimpleMailMessage`, sin dependencias nuevas), comprobar asunto «Tu enlace para entrar en Clínica Eleva» y que el cuerpo contiene el nombre de la clínica, la URL, «caduca en 10 minutos» y «puedes ignorar este mensaje».
- [ ] T023 [P] [US1] Crear `frontend/tests/unit/portal-entrada.test.tsx`:
  - `[002][US1-AC1][US1-AC3]` Tras enviar cualquier email, se muestra el aviso neutro exacto de [contracts/ui.md §1](contracts/ui.md).
  - `[002][US1-AC7]` Con 429 se muestra «Has pedido demasiados enlaces…».
  - `[002][US1-AC4]` Con 401 `ENLACE_NO_VALIDO` se muestra «Este enlace ya no es válido. Pide uno nuevo.» y el formulario.
  - `[002][US1-AC2]` Con `#enlace=abc` en la URL, se llama a `history.replaceState` sin el fragmento y a `abrirSesionPortal('eleva','abc')`.
  - `[002][US1-AC5]` El evento `sesion-portal-perdida` lleva a la entrada con «Tu acceso ha caducado…».
- [ ] T024 [P] [US1] Crear `frontend/tests/e2e/portal-acceso.spec.ts`, que lee los correos de Mailpit:
  - `[002][FR-031][US1-AC1][US1-AC2]` Pedir el enlace para Teresa, leerlo en Mailpit, abrirlo y ver «Hola, Teresa».
  - `[002][US1-AC4]` Reabrir el enlace muestra «Este enlace ya no es válido…».
  - `[002][US1-AC3]` `nadie@ejemplo.es` muestra el mismo aviso y Mailpit no recibe correo.
  - `[002][US1-AC6]` «Salir» devuelve a la entrada y el botón atrás no muestra citas.

### Implementación de la historia 1

- [ ] T025 [US1] Crear `backend/src/main/java/es/citaclara/portal/acceso/RepositorioSolicitudesEnlace.java` (`JdbcTemplate`) con:
  - `contarDesde(clinicaId, email, Instant desde)`.
  - `insertar(clinicaId, email, tokenHash|null, solicitadaEn, caducaEn)`.
  - `borrarAntiguas(clinicaId, email, Instant antesDe)`.
  - `consumir(clinicaId, tokenHash, Instant ahora)`: `UPDATE solicitud_enlace_portal SET usada_en = ? WHERE clinica_id = ? AND token_hash = ? AND usada_en IS NULL AND caduca_en > ? RETURNING email_normalizado`, que devuelve `Optional<String>` (R1).
- [ ] T026 [US1] Crear `backend/src/main/java/es/citaclara/portal/acceso/ServicioEnlaces.java` (depende de T025, T014, T009).

  `pedir(identificador, email)` hace en una transacción:
  1. Resolver la clínica (404 si no existe) y bloquearla con `SELECT … FOR UPDATE`. Si no existe un método equivalente a `RepositorioProfesional.bloquear` en `RepositorioClinica`, añadir `bloquear(id)` (R3).
  2. `borrarAntiguas` de más de 24 h.
  3. Si `contarDesde(ahora − 1 h) >= 5`, lanzar 429 `ENLACES_AGOTADOS`.
  4. Si el email coincide con alguna ficha, generar un secreto de 32 bytes con `SecureRandom`, base64url sin relleno, guardar su SHA-256 en hexadecimal y publicar `EnlaceSolicitado` con la URL `{url-publica}/{identificador}/mis-citas#enlace={secreto}`. Si no coincide, insertar con `token_hash = NULL`. `caduca_en = ahora + 10 min`.

  `consumir(identificador, secreto, request)`: hashear el secreto, consumir y abrir `SesionPortal` con el email devuelto. Si no se consume nada, 401 `ENLACE_NO_VALIDO`. Nunca se registra el email ni el secreto.
- [ ] T027 [US1] Crear `backend/src/main/java/es/citaclara/portal/acceso/ControladorAccesoPortal.java` (depende de T026) con:
  - `POST /api/portal/{identificador}/enlaces` (`{email}` → 202 sin cuerpo).
  - `POST /api/portal/{identificador}/sesion` (`{enlace}` → 204).
  - `DELETE /api/portal/sesion` (→ 204).

  Los records de petición llevan `@NotBlank` con mensajes en español.
- [ ] T028 [P] [US1] Crear `frontend/src/portal/PantallaEntradaPortal.tsx` con los textos exactos de [contracts/ui.md §1](contracts/ui.md):
  - Título «Tus citas en {clínica}», nombre obtenido con `GET /api/clinicas/{id}`.
  - Campo «Tu email» (`type="email"`, `autocomplete="email"`) y botón «Enviarme el enlace».
  - Aviso neutro y botón «Pedir otro enlace».
  - Errores 400 y 429, y aviso de caducidad.
  - Reutiliza `ui/Boton` y `ui/Aviso`.
- [ ] T029 [US1] Completar `frontend/src/portal/AppPortal.tsx` (depende de T028):
  - Al montar, leer `#enlace=` y borrarlo con `history.replaceState`.
  - Mostrar «Entrando en tus citas…» mientras llama a `abrirSesionPortal`.
  - Si va bien, pasar a `mis-citas`. Con `ENLACE_NO_VALIDO`, volver a la entrada con su aviso.
  - Sin fragmento, intentar `obtenerMisCitas`: si responde 401, mostrar la entrada.
  - Escuchar `sesion-portal-perdida`.

**Punto de control**: US1 funciona por sí sola. T021–T024 en verde.

---

## Fase 4: Historia de usuario 2 — Ver mis citas futuras y pasadas (Prioridad: P1)

**Objetivo**: el paciente ve sus próximas citas y su historial con datos completos y estados en su lenguaje, sin ver nunca nada de otros pacientes.

**Prueba independiente**: con la semilla, Teresa Méndez ve en «Próximas citas» su cita del día laborable +1 (María, 10:15–11:15, Primera visita fisioterapia, 50,00 €, «Reservada»), y Rocío Garrido ve la del día laborable −3 como «Realizada».

### Tests de la historia 2 (escribir primero; DEBEN fallar)

- [ ] T030 [P] [US2] Crear `backend/src/test/java/es/citaclara/portal/MisCitasTest.java` con:
  - `[002][FR-008][FR-009][US2-AC1]` Una cita reservada de Teresa con María a las 10:15–11:15, «Primera visita fisioterapia» y 5000 céntimos aparece en `proximas` con `fecha`, `horaInicio` «10:15», `horaFin` «11:15», `profesional` «María», `servicio`, `importeCentimos` 5000 y `estadoPortal` `RESERVADA`. `clinicaNombre` es «Clínica Eleva».
  - `[002][FR-008][US2-AC2]` Las próximas citas van por inicio ascendente y las pasadas por inicio descendente.
  - `[002][FR-009][US2-AC3][US2-AC4]` Comprueba cada fila de la tabla «Vista de cita en el portal» de [data-model.md](data-model.md): `COMPLETADA` → `REALIZADA`, `NO_ASISTIDA` → `NO_ACUDISTE` y `CANCELADA` → `CANCELADA`, todas en `pasadas` con `cancelable=false`.
  - `[002][US2-AC5]` Una cita futura cancelada por recepción aparece en `pasadas` como `CANCELADA`.
  - `[002][US2-AC6]` Una reservada ya iniciada aparece en `pasadas` como `PENDIENTE_CIERRE` y no es cancelable.
  - `[002][US2-AC7]` Un paciente sin citas futuras recibe `proximas` vacía y conserva su historial.
  - `[002][FR-010]` Una cita completada con 3 años de antigüedad aparece.
  - `[002][FR-026]` Una cita el día del cambio a horario de verano (29/03/2026 a las 10:00 locales) se devuelve con `horaInicio` «10:00».
  - `[002][FR-028]` Con dos fichas con el email de Laura, se devuelven las citas de ambas con su `paciente` y `pacientes` tiene los dos nombres.
  - `[002][FR-011][FR-028][SC-004][US2-AC8]` Teresa nunca recibe citas de Laura ni del paciente de otra clínica que comparte su email. La respuesta no contiene teléfonos ni identificadores de otras fichas.
- [ ] T031 [P] [US2] Crear `frontend/tests/unit/portal-mis-citas.test.tsx`:
  - `[002][FR-009][FR-026][US2-AC1]` La tarjeta muestra la fecha larga, «10:15–11:15», «con María», el servicio, «50,00 €» y «Reservada».
  - `[002][US2-AC3][US2-AC4][US2-AC6]` Cada `estadoPortal` muestra su texto de [contracts/ui.md §3](contracts/ui.md), con icono, y sin botones en las pasadas.
  - `[002][US2-AC7]` Sin próximas citas aparece «No tienes citas próximas. Si quieres pedir una, llama a la clínica.».
  - `[002][FR-027]` La cabecera muestra el nombre de la clínica.
  - El saludo es «Hola, Teresa», y con varias fichas aparece el nombre del paciente en cada tarjeta.
- [ ] T032 [P] [US2] Crear `frontend/tests/e2e/portal-citas.spec.ts`:
  - `[002][US2-AC1]` Teresa Méndez ve en «Próximas citas» su cita de la semilla del día laborable +1 (no se afirma que sea la primera: su orden real lo documenta T063) (`diaLaborable(hoy, 1)`, María, 10:15–11:15, 50,00 €).
  - `[002][US2-AC3]` Rocío Garrido ve en «Citas pasadas» la del día laborable −3 (María, 10:00–10:45, 40,00 €, «Realizada») sin botones.

### Implementación de la historia 2

- [ ] T033 [P] [US2] Crear `backend/src/main/java/es/citaclara/portal/citas/CitaPortal.java`, un record con los campos de [contracts/api.yaml](contracts/api.yaml) `CitaPortal` (incluido `motivoNoCancelable`, que puede ser nulo). Crear también `EstadoPortal.java` (enum `RESERVADA`, `PENDIENTE_CIERRE`, `REALIZADA`, `NO_ACUDISTE`, `CANCELADA`) y `MisCitas.java` (record con `clinicaNombre`, `pacientes`, `proximas` y `pasadas`).
- [ ] T034 [P] [US2] Crear `backend/src/main/java/es/citaclara/portal/citas/ReglaCancelacionPortal.java`:
  - `PLAZO = Duration.ofHours(24)`.
  - `dentroDePlazo(Instant inicio, Instant ahora)` devuelve `Duration.between(ahora, inicio).compareTo(PLAZO) > 0` (R6).
  - Se usa aquí para calcular `cancelable` y `motivoNoCancelable` (`Mensajes.FUERA_DE_PLAZO`); US3 la reutiliza.
- [ ] T035 [US2] Añadir a `backend/src/main/java/es/citaclara/cita/RepositorioCita.java` la consulta `delPortal(clinicaId, emailNormalizado)`: citas con `JOIN FETCH` de profesional, servicio y paciente, donde `c.clinicaId = :clinicaId` y `lower(trim(p.email)) = :email`. Es una lectura que no cambia el comportamiento de 001.
- [ ] T036 [US2] Crear `backend/src/main/java/es/citaclara/portal/citas/ServicioMisCitas.java` (depende de T033–T035, T014):
  - Construye `MisCitas` con la tabla «Vista de cita en el portal» de [data-model.md](data-model.md), usando `ZonaClinica` para la fecha y las horas locales y el `Clock`.
  - Ordena según FR-008.
  - `pacientes` sale de `RepositorioPacientesPortal.nombresConEmail`.
  - Método `vista(Cita, Instant ahora)` reutilizable por US3.
- [ ] T037 [US2] Crear `backend/src/main/java/es/citaclara/portal/citas/ControladorPortal.java` con `GET /api/portal/citas`, que lee `SesionPortal.CLINICA` y `SesionPortal.EMAIL` de los atributos de petición (depende de T036).
- [ ] T038 [P] [US2] Crear `frontend/src/portal/DistintivoEstadoPortal.tsx`: color, icono de `lucide-react` y texto por `estadoPortal`, nunca solo color, con los tokens de `estilos.css`.
- [ ] T039 [P] [US2] Crear `frontend/src/portal/TarjetaCitaPortal.tsx`:
  - Fecha larga con `formato/fechas.ts`, franja, «con {profesional}», servicio, importe con `formato/euros.ts` y distintivo.
  - Si hay varias fichas, muestra el nombre del paciente.
  - Si `motivoNoCancelable`, muestra ese texto.
  - El botón «Cancelar cita» solo aparece si `cancelable`. Su acción la conecta US3.
  - Tarjeta a todo el ancho a 360 px y objetivos táctiles de al menos 44 px.
- [ ] T040 [US2] Crear `frontend/src/portal/PantallaMisCitas.tsx` (depende de T038, T039):
  - Cabecera con el nombre de la clínica, saludo y «Salir» (llama a `cerrarSesionPortal` y vuelve a la entrada).
  - Bloques «Próximas citas» y «Citas pasadas», y mensaje sin próximas.
  - Conectarla en `AppPortal.tsx`.

**Punto de control**: US1 y US2 funcionan juntas. T030–T032 en verde.

---

## Fase 5: Historia de usuario 3 — Cancelar una cita futura (Prioridad: P1)

**Objetivo**: el paciente cancela, con confirmación, una cita propia a la que le faltan más de 24 h. El hueco queda libre y se registra el origen para el aviso.

**Prueba independiente**: crear desde el panel una cita de Laura Romero con Jorge a más de 24 h, cancelarla desde el portal y verla libre en la agenda de Jorge.

### Tests de la historia 3 (escribir primero; DEBEN fallar)

- [ ] T041 [P] [US3] Crear `backend/src/test/java/es/citaclara/portal/ReglaCancelacionPortalTest.java` (unitario, sin Spring):
  - `[002][FR-012][SC-003]` Para un inicio a las 09:45 de un miércoles, a las 09:44:59 del martes está dentro de plazo y a las 09:45:00 ya no.
  - `[002][FR-012]` Con el cambio de hora (29/03/2026 y 25/10/2026, `Europe/Madrid`), el plazo son 24 h reales.
  - `[002][FR-012]` Una cita del lunes a las 10:00 se puede cancelar hasta el domingo a las 09:59:59.
- [ ] T042 [P] [US3] Crear `backend/src/test/java/es/citaclara/portal/CancelacionPortalTest.java` con:
  - `[002][FR-012][FR-015][FR-017][US3-AC1][US3-AC2]` Laura cancela su cita con Jorge de 09:45 a 10:30 a las 09:00 del día anterior: responde 200 con `estadoPortal` `CANCELADA`, la cita queda `CANCELADA` en BD, existe `cancelacion_portal` con `cancelada_en` igual al reloj, y la agenda del panel de Jorge muestra libre la franja 09:45–10:30.
  - `[002][FR-013][FR-014][SC-003][US3-AC4][US3-AC5]` A las 09:45:00 del día anterior, la respuesta es 422 `FUERA_DE_PLAZO` y la cita sigue `RESERVADA`. La cita ya iniciada también da 422 o no es cancelable. La recepción sí puede cancelarla en ese momento con el endpoint de 001.
  - `[002][FR-016][US3-AC6]` Si la recepción la marca o cancela antes, el portal recibe 409 `ESTADO_YA_CAMBIADO` con el mensaje «Esta cita acaba de cambiar. Revisa su estado.» y `citaActual` en formato `CitaPortal` (nunca el mensaje ni el formato del panel), tanto por versión distinta como por choque simultáneo del bloqueo optimista.
  - `[002][FR-005][US1-AC5]` Con la sesión caducada (30 min), cancelar responde 401 `SESION_PORTAL_CADUCADA` y la cita sigue `RESERVADA`.
  - `[002][US3-AC7]` Dos POST seguidos del mismo paciente responden 200 y 200, con un solo `cancelacion_portal`.
  - `[002][FR-028]` Cancelar la cita de otro paciente o de otra clínica responde 404 y no cambia nada.
  - `[002][FR-018]` Una cita cancelada no vuelve a `RESERVADA` por ningún endpoint del portal, y no existe ningún endpoint del portal para crear o mover citas (`POST /api/portal/citas` responde 404 o 405).
- [ ] T043 [P] [US3] Crear `backend/src/test/java/es/citaclara/portal/CancelacionPortalConcurrenteTest.java`: `[002][FR-016][SC-005]` 10 hilos, 5 que cancelan desde el portal y 5 que cancelan desde recepción, sobre la misma cita, en 20 repeticiones. La cita queda `CANCELADA` una vez, hay como mucho una fila en `cancelacion_portal`, y el resto recibe 409 con el estado real o 200 idempotente (solo el portal). Nunca un 500.
- [ ] T044 [P] [US3] Crear `backend/src/test/java/es/citaclara/portal/SolapeTrasCancelacionPortalTest.java` (Principio III, R9):
  - `[002][FR-015]` Tras cancelar desde el portal, un alta de recepción en el mismo hueco se registra.
  - `[002][FR-015]` Tras la cancelación, 10 altas simultáneas sobre el hueco liberado dejan exactamente 1 registrada, en 20 repeticiones.
  - `[002][FR-015]` Con la cancelación del portal y una alta de recepción en paralelo sobre el mismo hueco, `SELECT` nunca encuentra dos citas ocupantes solapadas de Jorge.
- [ ] T045 [P] [US3] Crear `frontend/tests/unit/portal-cancelar.test.tsx`:
  - `[002][US3-AC1]` La confirmación muestra «¿Cancelar tu cita del {fecha larga} a las 09:45 con Jorge?».
  - `[002][US3-AC3]` «Volver» no llama al API.
  - `[002][US3-AC2]` Si va bien, aparece «Tu cita se ha cancelado.» y la tarjeta pasa a pasadas.
  - `[002][US3-AC5]` Con 422, aparece el texto de fuera de plazo.
  - `[002][US3-AC6]` Con 409, aparece «Esta cita acaba de cambiar. Revisa su estado.» con la tarjeta de `citaActual`.
  - `[002][US3-AC7]` El botón queda desactivado mientras espera.
  - `[002][US3-AC4]` Sin `cancelable`, no hay botón y sí el texto del motivo.
- [ ] T046 [P] [US3] Crear `frontend/tests/e2e/portal-cancelar.spec.ts`: `[002][US3-AC1][US3-AC2]` con `ApiPrueba`, buscar un hueco libre de Jorge a más de 24 h (`huecoLibre`) y reservarlo para Laura Romero. Después, en el portal como laura.romero@ejemplo.es, «Cancelar cita» y «Sí, cancelar» muestran «Tu cita se ha cancelado.», y la agenda de Jorge en el panel muestra el hueco libre. No cancela citas de la semilla.

### Implementación de la historia 3

- [ ] T047 [US3] Crear `backend/src/main/java/es/citaclara/portal/cancelaciones/RepositorioCancelaciones.java` (`JdbcTemplate`):
  - `insertar(citaId, clinicaId, Instant canceladaEn)`.
  - `existe(citaId, clinicaId)`.

  US4 añade los métodos de lectura y revisión.
- [ ] T048 [US3] Añadir a `backend/src/main/java/es/citaclara/cita/ServicioEstadoCita.java` el método público `cancelarDesdePortal(Long clinicaId, Long citaId, int versionVista, ComprobacionPortal comprobacion)`, que sigue el orden de comprobación de [data-model.md](data-model.md) (R7):
  1. Misma transacción y mismo bloqueo optimista que `cambiar`, pero **sin** la comprobación de versión de `cambiar` al principio.
  2. Llama primero a `comprobacion.verificar(cita, versionVista, ahora)`, que puede devolver una cita ya resuelta (idempotencia: se devuelve sin cambios) o lanzar el error del portal. La comprobación del portal hace, en este orden, propiedad (404), idempotencia, versión y estado (409) y plazo (422), según [data-model.md](data-model.md).
  3. Si no hay resultado previo, aplica `MaquinaEstadosCita.transicion(…, Accion.CANCELAR, …)` y ejecuta `comprobacion.despuesDeCancelar(cita, ahora)` (insertar en `cancelacion_portal`) antes de `saveAndFlush`.
  4. Si salta `ObjectOptimisticLockingFailureException`, relee la cita en una transacción nueva y lanza `comprobacion.conflicto(citaActual, ahora)`. Así el error lo construye el portal, con su mensaje y su formato `CitaPortal`; nunca se usa `yaCambiada` de recepción.

  Crear la interfaz funcional `backend/src/main/java/es/citaclara/cita/ComprobacionPortal.java`. Los tests de 001 (`CambioEstadoTest`, `CambioEstadoConcurrenteTest`) siguen en verde sin cambios.
- [ ] T049 [US3] Crear `backend/src/main/java/es/citaclara/portal/citas/ServicioCancelacionPortal.java` (depende de T047, T048, T034, T036), que implementa la `ComprobacionPortal`:
  - Si el email del paciente de la cita no coincide con el de la sesión, 404.
  - Si la cita está `CANCELADA` y `existe` en `cancelacion_portal`, devuelve la vista (200, idempotente).
  - Si la versión no coincide o el estado no es `RESERVADA`, 409 con código `ESTADO_YA_CAMBIADO`, mensaje `Mensajes.CITA_PORTAL_YA_CAMBIADA` («Esta cita acaba de cambiar. Revisa su estado.») y `citaActual` como `CitaPortal`. `conflicto(...)` construye este mismo error para la carrera del bloqueo optimista.
  - Si `!dentroDePlazo`, 422 `FUERA_DE_PLAZO`.
  - Después de la transición, `insertar`.

  Devuelve `CitaPortal` mediante `ServicioMisCitas.vista`.
- [ ] T050 [US3] Añadir `POST /api/portal/citas/{citaId}/cancelacion` (`{version}` → 200 `CitaPortal`) a `backend/src/main/java/es/citaclara/portal/citas/ControladorPortal.java` (depende de T049).
- [ ] T051 [P] [US3] Crear `frontend/src/portal/DialogoCancelarCita.tsx` sobre `ui/Dialogo` (hoja inferior en móvil), con los textos exactos de [contracts/ui.md §3](contracts/ui.md): «Sí, cancelar» desactivado durante la petición, «Volver», y gestión de 200, 409 y 422.
- [ ] T052 [US3] Conectar en `frontend/src/portal/PantallaMisCitas.tsx` y `TarjetaCitaPortal.tsx` el botón «Cancelar cita» con `DialogoCancelarCita` (depende de T051). Si va bien, mover la cita a «Citas pasadas» como «Cancelada» y mostrar el aviso. Con 409, sustituir la tarjeta por `citaActual`.

**Punto de control**: US1, US2 y US3 funcionan. Las pruebas de solape (T044) están en verde (puerta de merge 3).

---

## Fase 6: Historia de usuario 4 — Aviso de cancelaciones en recepción (Prioridad: P2)

**Objetivo**: la recepción ve en el panel cuántas cancelaciones del portal tiene sin revisar, su lista y puede marcarlas como revisadas.

**Prueba independiente**: tras cancelar desde el portal una cita de Laura Romero, el panel muestra «1 cancelación de pacientes por revisar» y, al marcarla como revisada, el aviso desaparece.

- [ ] T053 [US4] Coordinar con el propietario de la spec 001 (registrado en `specs/MAPA.md`) la incorporación del aviso a la cabecera de `PantallaAgenda`. El comportamiento es de 002 (Supuestos de su spec) y la agenda del día no cambia (FR-023). Dejar constancia del acuerdo en `specs/MAPA.md` (columna de estado o nota bajo la tabla) antes de T062. **Bloquea T062.**

### Tests de la historia 4 (escribir primero; DEBEN fallar)

- [ ] T054 [P] [US4] Crear `backend/src/test/java/es/citaclara/portal/AvisoCancelacionesTest.java` con:
  - `[002][FR-019][SC-008][US4-AC1]` Tras la cancelación desde el portal de Laura, `GET /api/cancelaciones-portal` con la sesión del panel devuelve `pendientes` 1.
  - `[002][FR-020][US4-AC2]` Cada elemento tiene `paciente` «Laura Romero», `profesional` «Jorge», `horaInicio` «09:45», `horaFin` «10:30», `servicio` «Sesión fisioterapia», `canceladaFecha` y `canceladaHora` locales. Con dos cancelaciones, el orden va de la más reciente a la más antigua.
  - `[002][FR-021][US4-AC3]` `POST /api/cancelaciones-portal/{citaId}/revision` responde 204 y `pendientes` baja a 0.
  - `[002][FR-022][US4-AC4]` Una cancelación hecha por recepción no aparece.
  - `[002][FR-021][US4-AC5]` Marcar dos veces responde 204 y 204, y 2 hilos simultáneos dejan un único `revisada_en` sin errores.
  - `[002][FR-020][US4-AC6]` Con el reloj después del inicio del hueco, el elemento sigue pendiente con `huecoPasado=true`.
  - `[002][US4-AC7]` Con la clave de otra clínica, `pendientes` es 0, y revisar el `citaId` de Eleva responde 404.
  - `[002][FR-023]` La respuesta de `GET /api/agenda` del día es idéntica antes y después de marcar la cancelación como revisada.
- [ ] T055 [P] [US4] Crear `frontend/tests/unit/aviso-cancelaciones.test.tsx`:
  - `[002][FR-019]` Con `pendientes` 0 no se pinta nada. Con 1, el texto es «1 cancelación de pacientes por revisar». Con 3, «3 cancelaciones de pacientes por revisar».
  - `[002][FR-020][US4-AC2][US4-AC6]` El diálogo lista los elementos con los textos de [contracts/ui.md §5](contracts/ui.md) y el distintivo «Hueco ya pasado».
  - `[002][US4-AC3]` «Marcar como revisada» quita el elemento. Con la lista vacía aparece «No hay cancelaciones pendientes.» y el aviso desaparece.
  - `[002][SC-008]` `PantallaAgenda` vuelve a pedir el aviso al cambiar de día o de profesional.
- [ ] T056 [P] [US4] Crear `frontend/tests/e2e/aviso-cancelaciones.spec.ts`: `[002][US4-AC1][US4-AC3]` crear la cita con `ApiPrueba`, cancelarla desde el portal (`entrarEnPortal`), entrar en el panel y ver el aviso. Abrirlo, comprobar los datos y marcar como revisada: el aviso desaparece. Usa sus propios huecos para poder repetirse.

### Implementación de la historia 4

- [ ] T057 [US4] Añadir a `backend/src/main/java/es/citaclara/portal/cancelaciones/RepositorioCancelaciones.java`:
  - `pendientes(clinicaId)`: `JOIN` con cita, paciente, profesional y servicio, `WHERE revisada_en IS NULL AND clinica_id = ?`, `ORDER BY cancelada_en DESC`.
  - `marcarRevisada(citaId, clinicaId, Instant ahora)`: `UPDATE cancelacion_portal SET revisada_en = ? WHERE cita_id = ? AND clinica_id = ? AND revisada_en IS NULL`, que devuelve el número de filas.
  - `existe(citaId, clinicaId)`, ya creado en T047.
- [ ] T058 [US4] Crear `backend/src/main/java/es/citaclara/portal/cancelaciones/ServicioCancelacionesPortal.java` (depende de T057):
  - Construye la respuesta con `pendientes`, `cancelaciones` (record `CancelacionPortalRespuesta` según [contracts/api.yaml](contracts/api.yaml)), fechas y horas locales con `ZonaClinica`, y `huecoPasado = !ahora.isBefore(inicio)`.
  - `revisar`: si `marcarRevisada` devuelve 0 y no `existe`, 404. En cualquier otro caso, 204.
- [ ] T059 [US4] Crear `backend/src/main/java/es/citaclara/portal/cancelaciones/ControladorCancelacionesPortal.java` (depende de T058) con `GET /api/cancelaciones-portal` y `POST /api/cancelaciones-portal/{citaId}/revision`. Ambos reciben `@RequestAttribute(SesionClinica.CLINICA)`, protegidos por el interceptor del panel.
- [ ] T060 [P] [US4] Crear `frontend/src/cancelaciones/DialogoCancelacionesPortal.tsx` sobre `ui/Dialogo`, con título «Cancelaciones de pacientes», elementos con los textos de [contracts/ui.md §5](contracts/ui.md), «Marcar como revisada» y estado vacío «No hay cancelaciones pendientes.».
- [ ] T061 [US4] Crear `frontend/src/cancelaciones/AvisoCancelaciones.tsx` (depende de T060):
  - Botón con icono de campana y el texto en singular o plural. A 360 px muestra el icono y el número, con el texto completo como `aria-label`.
  - Solo se pinta si `pendientes > 0`.
  - Abre el diálogo.
  - Prop `clave` para volver a pedir los datos.
- [ ] T062 [US4] Montar `AvisoCancelaciones` en la cabecera de `frontend/src/agenda/PantallaAgenda.tsx`, junto a «Cerrar sesión», con `clave` derivada de `profesional` y `fecha`, para que se recargue al cambiar de día o de profesional (SC-008) (depende de T053, T061). No cambia nada más de la agenda.

**Punto de control**: las cuatro historias funcionan. T054–T056 en verde.

---

## Fase 7: Pulido y cierre

**Propósito**: cifras reproducibles, móvil y accesibilidad, textos, privacidad, documentación y puertas de merge.

- [ ] T063 [P] Crear `backend/src/test/java/es/citaclara/portal/DatosPortalDocumentadosTest.java` siguiendo el mecanismo de `semilla/DatosDemostracionDocumentadosTest.java`: misma propiedad `-DactualizarDatosDemostracion=true` y bloque entre marcadores `<!-- portal:inicio -->` y `<!-- portal:fin -->`.
  - `[002][FR-030]` Con el generador puro de 001, sin modificarlo, calcular para Teresa Méndez, Laura Romero, Rocío Garrido y Gonzalo Cruz:
    - Citas de historia por estado e importes en céntimos y con el formato «1234,50 €».
    - Citas futuras e importe.
    - Primera cita futura y última pasada por posición relativa (día laborable, profesional, franja, servicio).
  - Generar y verificar `specs/002-portal-paciente/datos-demostracion.md`, con la cabecera y la forma de lectura de la versión de 001.
- [ ] T064 [P] Crear `frontend/tests/e2e/portal-360.spec.ts`: `[002][FR-024][SC-006]` a 360 px y en portátil, comprobar con `pantallaAccesible` (axe, sin desplazamiento horizontal, sin texto desbordado, objetivos táctiles de al menos 44 px) la entrada del portal, el aviso neutro, «Mis citas» de Teresa, el diálogo de cancelación, el aviso de recepción y su diálogo.
- [ ] T065 [P] Ampliar `frontend/tests/e2e/textos.spec.ts` con `[002][FR-025]`: en todas las pantallas del portal no aparecen códigos (`ENLACE_`, `SESION_`, `FUERA_DE_PLAZO`, `ESTADO_`), identificadores numéricos de cita ni términos técnicos, y todos los textos coinciden con [contracts/ui.md](contracts/ui.md).
- [ ] T066 [P] Crear `backend/src/test/java/es/citaclara/portal/PrivacidadRegistrosTest.java`: `[002][FR-003][FR-011]` con `OutputCaptureExtension`, pedir enlaces para emails existentes e inexistentes, abrir sesión, cancelar y provocar un fallo de envío (con `BuzonPrueba` configurado para lanzar una excepción). La salida no contiene ningún email, secreto ni hash (R13).
- [ ] T067 [P] Actualizar `README.md` en los apartados de entorno y variables:
  - Servicio Mailpit y su bandeja en `http://localhost:8025` (`PUERTO_CORREO`).
  - Variables `URL_PUBLICA`, `SMTP_HOST`, `SMTP_PUERTO`, `SMTP_USUARIO`, `SMTP_CLAVE` y `EMAIL_REMITENTE`.
  - Dirección del portal `/{identificador}/mis-citas`.
  - `URL_CORREO` para e2e.
- [ ] T068 Revisión de puertas de merge (depende de todas las anteriores):
  - `cd backend && ./mvnw verify`, `cd frontend && npm test` y, con el entorno levantado y la semilla cargada, `URL_BASE=http://localhost:8080 URL_CORREO=http://localhost:8025 npm run e2e`, todo en verde.
  - `./scripts/trazabilidad.sh specs/002-portal-paciente` y `./scripts/trazabilidad.sh specs/001-nucleo-agenda` con salida vacía.
  - Recorrer [quickstart.md](quickstart.md) a mano.
- [ ] T069 Actualizar el estado de `002-portal-paciente` en `specs/MAPA.md` a «Implementada» y confirmar que `spec.md` refleja el comportamiento entregado (puerta 1).
- [ ] T070 **Último paso — mantenimiento**: actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[002] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features. Candidatas (plan.md, fase 7):
  - La sesión del portal (`SesionPortal`, 30 min) es independiente de la del panel, y en un mismo navegador abrir una cierra la otra.
  - Los secretos de un solo uso se guardan como SHA-256 y se consumen con un único `UPDATE … RETURNING`.
  - Las respuestas que no deben revelar existencia aplican los límites también a lo inexistente.
  - Todo correo sale por un puerto propio, asíncrono tras el commit (`CorreoPortal`/`BuzonPrueba`), con Mailpit en local y e2e.
  - El origen de una cita vive en tablas propias de la feature (`cancelacion_portal`), no en columnas de `cita`.
  - `scripts/trazabilidad.sh` admite exclusiones por spec.
  - Comandos nuevos (Mailpit, `URL_CORREO`) en «Comandos».

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Fase 1 (Andamiaje)**: sin dependencias.
- **Fase 2 (Fundamentos)**: depende de la fase 1 y bloquea todas las historias.
- **US1 (fase 3)**: depende de la fase 2. Es el MVP.
- **US2 (fase 4)**: depende de la fase 2. Para probarla de principio a fin necesita entrar en el portal (US1), aunque sus pruebas de integración pueden escribirse en paralelo usando `ClienteApi.entrarEnPortal`.
- **US3 (fase 5)**: depende de US2 (`ServicioMisCitas.vista`, `TarjetaCitaPortal`, `ReglaCancelacionPortal`).
- **US4 (fase 6)**: depende de US3, porque sin cancelaciones del portal no hay aviso, y de T053 (coordinación con 001) para T062.
- **Fase 7**: depende de todas las historias. T070 es siempre la última tarea.

### Dentro de cada historia

Primero los tests, que deben fallar. Después, en este orden: modelos y repositorios, servicios, controladores y, por último, frontend.

### Oportunidades de paralelismo

- Fase 1: T002–T006 en paralelo tras T001.
- Fase 2: T007–T010, T014–T017 y T020 en paralelo. T011 → T012 → T013 en serie. T018 → T019.
- US1: T021–T024 en paralelo. T028 en paralelo con T025–T027.
- US2: T030–T032 en paralelo. T033, T034, T038 y T039 en paralelo.
- US3: T041–T046 en paralelo. T051 en paralelo con T047–T050.
- US4: T054–T056 en paralelo. T060 en paralelo con T057–T059.
- Fase 7: T063–T067 en paralelo.

## Ejemplo de paralelismo: historia de usuario 3

```bash
# Tests de US3 a la vez (ficheros distintos):
T041 ReglaCancelacionPortalTest.java
T042 CancelacionPortalTest.java
T043 CancelacionPortalConcurrenteTest.java
T044 SolapeTrasCancelacionPortalTest.java
T045 portal-cancelar.test.tsx
T046 portal-cancelar.spec.ts

# Después, backend y frontend en paralelo:
T047 → T048 → T049 → T050   (backend)
T051                         (frontend: DialogoCancelarCita)
```

## Estrategia de implementación

### MVP primero

1. Fases 1 y 2.
2. US1 (fase 3): el paciente entra con su enlace. Validar con T021–T024.
3. US2 (fase 4): con la consulta de citas, el portal ya ahorra la llamada más frecuente («¿cuándo tengo cita?»). Este es el primer incremento demostrable a Sonia.

### Entrega incremental

1. Fases 1 y 2, después US1 y US2: consulta de citas.
2. US3: cancelación con plazo de 24 h y pruebas de solape (puerta 3).
3. US4: aviso en recepción, tras la coordinación con el propietario de 001.
4. Fase 7: cifras, accesibilidad, privacidad y `CLAUDE.md`.

## Notas

- Cada tarea `[P]` toca ficheros distintos y no depende de tareas incompletas.
- No se cancelan citas de la semilla en ninguna prueba e2e: cada prueba crea las suyas (R11).
- Los cambios en código de 001 se limitan a: T007 (`Mensajes`), T013 (`ConfiguracionWeb`), T018 (`api/cliente.ts`, `api/tipos.ts`), T019 (`App.tsx`), T026 (`RepositorioClinica.bloquear`, si no existe), T035 (`RepositorioCita`), T048 (`ServicioEstadoCita`) y T062 (`PantallaAgenda`), más las ayudas de prueba de T010, T015, T016 y T020 y `textos.spec.ts` (T065). Los tests de 001 deben seguir en verde tras cada uno.
- Hacer commit tras cada tarea o grupo lógico. Parar en cada punto de control para validar la historia por separado.
