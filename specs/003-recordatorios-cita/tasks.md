---

description: "Lista de tareas de implementación de la feature 003-recordatorios-cita"
---

# Tareas: Recordatorios de cita por email

**Entrada**: documentos de diseño de `specs/003-recordatorios-cita/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.yaml](contracts/api.yaml), [contracts/correo.md](contracts/correo.md), [contracts/ui.md](contracts/ui.md), [quickstart.md](quickstart.md)

**Tests**: OBLIGATORIOS (constitución, Principio VI y puertas de merge 2–4). El nombre visible de cada test empieza por `[003][FR-xxx][USn-ACm]` (`@DisplayName` en JUnit, título en Vitest/Playwright); `./scripts/trazabilidad.sh specs/003-recordatorios-cita` debe salir vacío. Los tests de cada historia se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario, para implementar y probar cada una de forma independiente.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: paralelizable (ficheros distintos, sin dependencias de tareas incompletas).
- **[Historia]**: US1, US2, US3 (solo en las fases de historia).
- Cada descripción incluye la ruta exacta del fichero.

## Convenciones de rutas y reglas transversales

- Backend: `backend/src/main/java/es/citaclara/<capacidad>/`; tests en `backend/src/test/java/es/citaclara/<capacidad>/`. Paquetes nuevos: `recordatorio` y `correo`.
- Frontend: `frontend/src/<capacidad>/`; tests en `frontend/tests/unit/` y `frontend/tests/e2e/`.
- Las pruebas de integración heredan de `soporte/PruebaIntegracionBase` (reloj controlable, `ClienteApi`, `DatosPrueba`); al mover el reloj más de 8 h hay que volver a iniciar sesión.
- Instantes en `Instant`/`timestamptz` con `Clock` inyectado (nunca `now()` directo); fechas `dd/MM/yyyy` y horas `HH:mm` de 24 h con `comun/ZonaClinica`; textos en español de España; errores como `ErrorNegocio` con `codigo` y `mensaje`; esquema solo por Flyway.
- Todo recurso ajeno o enlace no válido responde 404 `NO_ENCONTRADO` idéntico (sin revelar datos).

---

## Fase 1: Andamiaje

**Propósito**: dependencia, configuración, migración y ficheros de entorno.

- [X] T001 Añadir `spring-boot-starter-mail` al `<dependencies>` de `backend/pom.xml` (única dependencia nueva; justificada en plan.md «Seguimiento de complejidad»).
- [X] T002 [P] Añadir a `backend/src/main/resources/application.yml` las propiedades `citaclara.recordatorios.cron` (`${RECORDATORIOS_CRON:0 0 9 * * *}`), `citaclara.correo.directorio` (`${CORREO_DIRECTORIO:datos/salida-correo}`), `citaclara.correo.remitente` (`${CORREO_REMITENTE:no-responder@citaclara.es}`) y `citaclara.url-publica` (`${CITACLARA_URL_PUBLICA:http://localhost:8080}`); añadir al perfil `recordatorios` (nuevo documento YAML, como el de `semilla`) `spring.main.web-application-type: none`. Excluir la autoconfiguración de correo salvo que exista `CORREO_SMTP_HOST` (research D2).
- [X] T003 Crear `backend/src/main/resources/db/migration/V2__recordatorios.sql` según data-model.md: `ALTER TABLE clinica ADD COLUMN telefono varchar(20)` (con valor técnico por defecto para filas existentes y que la semilla rellena); `ALTER TABLE cita ADD COLUMN cancelada_por varchar(10) CHECK (cancelada_por IS NULL OR (cancelada_por = 'PACIENTE' AND estado = 'CANCELADA'))`; `UNIQUE (id, clinica_id)` en `cita` si no existe; `CREATE TABLE recordatorio` con `id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `clinica_id bigint NOT NULL REFERENCES clinica (id) ON DELETE CASCADE`, `cita_id bigint NOT NULL UNIQUE`, `email_destino varchar(254) NOT NULL`, `enlace_huella char(64) NOT NULL UNIQUE`, `generado_en timestamptz NOT NULL` y `FOREIGN KEY (cita_id, clinica_id) REFERENCES cita (id, clinica_id) ON DELETE CASCADE`. Depende de que las peticiones 1 y 2 a la 001 estén aprobadas (T006).
- [X] T004 [P] Crear `datos/salida-correo/.gitkeep`, añadir `datos/salida-correo/*.eml` a `.gitignore` y, en `docker-compose.yml`, el volumen `./datos/salida-correo:/app/datos/salida-correo` en el servicio `api` y un servicio `recordatorios` (imagen `citaclara-api`, perfil `herramientas`, `SPRING_PROFILES_ACTIVE: recordatorios`, mismo entorno y volumen, `depends_on: db`). Dejar la carpeta escribible por el usuario sin privilegios de `backend/Dockerfile` (research D12).
- [X] T005 [P] Parametrizar `scripts/trazabilidad.sh`: si existe `<carpeta>/trazabilidad-excluidos.txt` (identificadores separados por espacios), usarlo en lugar de la lista fija de la 001; crear `specs/003-recordatorios-cita/trazabilidad-excluidos.txt` con `SC-007` y el motivo en un comentario (resultado de negocio medido fuera del producto). Comprobar que `./scripts/trazabilidad.sh` sin argumentos sigue comportándose igual para la 001.

---

## Fase 2: Fundamentos (bloquea todas las historias)

**Propósito**: peticiones a la 001, correo, entidad de recordatorio y soporte de pruebas.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta completar esta fase.

- [ ] T006 Confirmar con el propietario de la spec 001 las tres peticiones de plan.md («Peticiones a la spec 001»): teléfono de la clínica y de la semilla, `canceladaPor`, y excepción de sesión de `/api/cancelacion/**`. Dejar constancia (aprobación o fecha) en la cabecera de `specs/003-recordatorios-cita/plan.md`. No se fusiona sin ellas.
- [X] T007 [P] Añadir `telefono` a `backend/src/main/java/es/citaclara/clinica/Clinica.java` (columna `telefono`, `varchar(20)`, con getter) y rellenarlo para Clínica Eleva en `backend/src/main/java/es/citaclara/semilla/CargadorSemilla.java`/`CatalogoDemostracion.java` con un número ficticio fijo (determinista, Principio V; `+34 910 000 000`). Actualizar `specs/001-nucleo-agenda/datos-demostracion.md` solo en el catálogo si el propietario lo indica.
- [X] T008 [P] Añadir `canceladaPor` a `backend/src/main/java/es/citaclara/cita/Cita.java` (columna `cancelada_por`, `varchar(10)`, nulo o `PACIENTE`; método `cancelarPorPaciente()` con visibilidad de paquete que fije estado `CANCELADA` y `canceladaPor = PACIENTE`) y al `CitaRespuesta` de `backend/src/main/java/es/citaclara/cita/CitaRespuesta.java` como `canceladaPor` (nulo si no aplica); actualizar `specs/001-nucleo-agenda/contracts/api.yaml` con el campo opcional.
- [X] T009 [P] Crear en `backend/src/main/java/es/citaclara/correo/` el registro `CorreoSaliente` (destinatario, nombre visible, asunto, cuerpo, nombre de fichero) y la interfaz `EnviadorCorreo` con `void enviar(CorreoSaliente correo)`; contrato en [contracts/correo.md](contracts/correo.md).
- [X] T010 Implementar `backend/src/main/java/es/citaclara/correo/EnviadorCorreoFichero.java` (modo simulado, por defecto): construye un `MimeMessage` UTF-8 (asunto RFC 2047, `From` de `citaclara.correo.remitente`, `Message-ID` único), lo escribe en un temporal del directorio `citaclara.correo.directorio` (lo crea si no existe) y lo mueve de forma atómica a `recordatorio-cita-{citaId}.eml`; cualquier fallo de E/S lanza excepción sin dejar ficheros a medias. Saneado de cabeceras: eliminar saltos de línea del nombre visible y validar el destinatario como `InternetAddress` estricta (dirección inválida → `IllegalArgumentException`).
- [X] T011 [P] Implementar `backend/src/main/java/es/citaclara/correo/EnviadorCorreoSmtp.java` (usa `JavaMailSender`; mismo `MimeMessage`) y la selección por configuración en `backend/src/main/java/es/citaclara/correo/ConfiguracionCorreo.java`: `EnviadorCorreoSmtp` solo si existe `CORREO_SMTP_HOST`; en otro caso `EnviadorCorreoFichero` (FR-021).
- [X] T012 [P] Crear `backend/src/main/java/es/citaclara/recordatorio/Recordatorio.java` y `RepositorioRecordatorio.java` (entidad inmutable: `clinicaId`, `citaId`, `emailDestino`, `enlaceHuella`, `generadoEn`; consulta nativa `insertarSiNoExiste(...)` con `INSERT ... ON CONFLICT (cita_id) DO NOTHING` que devuelve el número de filas; búsqueda por huella).
- [X] T013 [P] Añadir a `backend/src/main/java/es/citaclara/cita/RepositorioCita.java` la consulta `paraRecordatorio(desde, hasta)`: `estado = RESERVADA`, `inicio >= :desde and inicio < :hasta`, paciente con email no nulo ni vacío, `not exists` de recordatorio de esa cita, `join fetch` de profesional, servicio y paciente, y clínica; orden por `inicio, id`. Más `buscarPorHuella(huella)` que trae cita con sus relaciones y la clínica a través del recordatorio.
- [X] T014 [P] Crear `backend/src/test/java/es/citaclara/soporte/PruebaRecordatoriosBase.java` (hereda de `PruebaIntegracionBase`): directorio temporal por prueba inyectado como `citaclara.correo.directorio` y utilidades `ficherosEml()` y `leerEml(citaId)` (con `jakarta.mail.internet.MimeMessage` para comprobar UTF-8). Añadir a `soporte/DatosPrueba.java` helpers `citaReservadaEn(Instant inicio, ...)` y `pacienteSinEmail(...)`, y un teléfono de clínica por defecto.
- [X] T015 Crear `backend/src/test/java/es/citaclara/recordatorio/RestriccionRecordatorioEsquemaTest.java`: contra PostgreSQL real, `[003][FR-004][US1-AC2]` dos inserciones concurrentes del recordatorio de la misma cita (una prospera, la otra no inserta); `[003][FR-008][US1-AC7]` la FK compuesta rechaza un recordatorio con la clínica de otra; `[003][FR-015][US2-AC2]` `cancelada_por` solo admite `PACIENTE` y solo en citas `CANCELADA`; borrar la cita borra su recordatorio.

**Punto de control**: base lista; las historias pueden empezar.

---

## Fase 3: Historia de usuario 1 — El paciente recibe un recordatorio (Prioridad: P1) 🎯 MVP

**Objetivo**: un proceso diario genera un único recordatorio por cita reservada de la ventana, con los datos de la cita y el enlace, en modo simulado.

**Prueba independiente**: con la semilla cargada, ejecutar el comando `recordatorios` y comprobar un `.eml` por cita de la ventana con paciente con email; una segunda ejecución no cambia nada.

### Tests de la Historia 1 (escribir primero; deben fallar)

- [X] T016 [P] [US1] `backend/src/test/java/es/citaclara/recordatorio/VentanaRecordatoriosTest.java`: `[003][FR-001][FR-002][US1-AC1]` cita en la ventana con email genera su correo; `[003][FR-002][US1-AC4]` límites exactos (inicio = ahora+24 h incluido; ahora+24 h−1 s y ahora+48 h excluidos); `[003][FR-003][US1-AC3]` canceladas, completadas y no asistidas no generan; `[003][FR-006][US1-AC5]` paciente sin email (nulo y vacío) no genera y no impide los demás.
- [X] T017 [P] [US1] `backend/src/test/java/es/citaclara/recordatorio/SinDuplicadosTest.java`: `[003][FR-004][FR-005][US1-AC2]` segunda ejecución el mismo día y al día siguiente no genera ni modifica ficheros; ejecuciones concurrentes (varios hilos sobre las mismas citas) dejan exactamente un recordatorio y un fichero por cita; el registro guarda cita, email, instante del `Clock` y huella.
- [X] T018 [P] [US1] `backend/src/test/java/es/citaclara/recordatorio/FalloRecordatorioTest.java`: `[003][FR-007][US1-AC6]` si la carpeta de salida no se puede escribir, la cita no queda registrada y la siguiente ejecución la genera; una dirección inválida no impide los recordatorios de las demás citas.
- [X] T019 [P] [US1] `backend/src/test/java/es/citaclara/recordatorio/CorreoRecordatorioTest.java`: `[003][FR-009][FR-010][FR-011][FR-020][US1-AC6]` el `.eml` tiene el nombre `recordatorio-cita-{id}.eml`, UTF-8, asunto «Recordatorio de tu cita en {clínica}: dd/MM/yyyy a las HH:mm», profesional, servicio, día y hora de 24 h, enlace `/cancelar/{43 caracteres}`, teléfono de la clínica, sin importe ni identificadores internos; `[003][FR-021]` con `CORREO_SMTP_HOST` se elige `EnviadorCorreoSmtp` y sin él `EnviadorCorreoFichero`.
- [X] T020 [P] [US1] `backend/src/test/java/es/citaclara/recordatorio/AislamientoRecordatoriosTest.java`: `[003][FR-008][SC-006][US1-AC7]` con dos clínicas con citas en la ventana, cada correo contiene solo datos de su clínica y de su paciente y ningún enlace cancela una cita distinta de la mostrada.
- [X] T021 [P] [US1] `backend/src/test/java/es/citaclara/recordatorio/CambioHorarioRecordatoriosTest.java`: `[003][SC-005][US1-AC1]` ejecuciones alrededor del 28/03/2027 y del 31/10/2027: ninguna cita omitida ni duplicada y la hora del correo es la peninsular correcta.
- [X] T022 [P] [US1] `backend/src/test/java/es/citaclara/semilla/DatosDemostracionRecordatoriosTest.java`: `[003][SC-004][US1-AC1]` con la semilla y el reloj en el día de carga a las 09:00, el número de recordatorios por posición relativa coincide con `specs/003-recordatorios-cita/datos-demostracion.md` (zona de recuentos generada con `-DactualizarDatosDemostracion=true`, patrón de `DatosDemostracionDocumentadosTest`); `[003][SC-001]` ninguna ejecución repetida cambia el recuento.

### Implementación de la Historia 1

- [X] T023 [P] [US1] `backend/src/main/java/es/citaclara/recordatorio/EnlaceCancelacion.java`: genera 32 bytes de `SecureRandom` en Base64 URL sin relleno (43 caracteres) y calcula `SHA-256` en hexadecimal (64 caracteres); sin estado.
- [X] T024 [P] [US1] `backend/src/main/java/es/citaclara/recordatorio/ConstructorCorreoRecordatorio.java`: construye `CorreoSaliente` en español de España según [contracts/correo.md](contracts/correo.md): asunto y cuerpo `text/plain`, fecha `dd/MM/yyyy` con día de la semana y hora `HH:mm` con `ZonaClinica`, enlace `{citaclara.url-publica}/cancelar/{enlace}`, teléfono de la clínica; sin importe.
- [X] T025 [US1] `backend/src/main/java/es/citaclara/recordatorio/ServicioRecordatorios.java` (depende de T012, T013, T023, T024, T010): `generar()` calcula `desde = ahora + 24 h` y `hasta = (ahora + 1 día local en Europe/Madrid) + 24 h` con el `Clock` inyectado y, para cada cita de `paraRecordatorio`, abre su propia transacción: `insertarSiNoExiste` (0 filas → siguiente), construye y envía el correo; cualquier excepción revierte esa cita, se registra un aviso y se continúa con las demás (FR-007). Devuelve el número de recordatorios generados.
- [X] T026 [US1] `backend/src/main/java/es/citaclara/recordatorio/ProgramadorRecordatorios.java`: `@Scheduled(cron = "${citaclara.recordatorios.cron}", zone = "Europe/Madrid")` que invoca `generar()`; habilitar `@EnableScheduling` en `backend/src/main/java/es/citaclara/CitaClaraApplication.java` o en una configuración propia; desactivado en el perfil `recordatorios` y en las pruebas salvo que se pidan.
- [X] T027 [P] [US1] `backend/src/main/java/es/citaclara/recordatorio/ComandoRecordatorios.java`: `@Profile("recordatorios")` `ApplicationRunner` que ejecuta `generar()` una vez, escribe en el registro cuántos recordatorios generó y termina (patrón de `semilla/ComandoSemilla.java`).
- [X] T028 [US1] Ejecutar `docker compose run --rm semilla && docker compose run --rm recordatorios` y comprobar a mano los pasos 1 y 2 de [quickstart.md](quickstart.md); si el recuento difiere de T022, corregir antes de continuar.

**Punto de control**: US1 completa y demostrable por sí sola (MVP).

---

## Fase 4: Historia de usuario 2 — El paciente cancela desde el email (Prioridad: P2)

**Objetivo**: un enlace público consulta la cita sin modificarla y, con confirmación, la cancela hasta 24 h antes; el hueco queda libre y recepción ve «Cancelada por el paciente».

**Prueba independiente**: abrir el enlace de un `.eml` sin sesión, confirmar y comprobar que la cita está cancelada, su franja libre y reservable de nuevo.

### Tests de la Historia 2 (escribir primero; deben fallar)

- [X] T029 [P] [US2] `backend/src/test/java/es/citaclara/recordatorio/CancelacionPacienteApiTest.java` (sin iniciar sesión): `[003][FR-012][US2-AC1]` el `GET` devuelve `ACTIVA`, datos mínimos y teléfono, sin importe, teléfono ni email del paciente; `[003][FR-012][US2-AC3]` abrir el `GET` varias veces no cambia la cita; `[003][FR-013][US2-AC2]` el `POST` con `{}` cancela, devuelve `YA_CERRADA` con `canceladaPorPaciente = true`; `[003][FR-013][US2-AC5]` a menos de 24 h (límite: ahora = inicio−24 h aún cancelable, un segundo después `422 FUERA_DE_PLAZO` con el teléfono) y cita ya iniciada; `[003][FR-018][US2-AC4]` cita cancelada/completada/no asistida → `YA_CERRADA` sin acción; `[003][FR-018][US2-AC6]` enlace inventado, manipulado o de otra clínica → mismo `404 NO_ENCONTRADO`; `POST` no JSON → `415 TIPO_NO_ADMITIDO`.
- [X] T030 [P] [US2] `backend/src/test/java/es/citaclara/recordatorio/CancelacionConcurrenteTest.java`: `[003][FR-019][US2-AC7]` doble confirmación simultánea del paciente → una sola cancelación y ambas respuestas coherentes (`YA_CERRADA`, `canceladaPorPaciente = true`); `[003][FR-019][US2-AC4]` paciente y recepción a la vez → aplica solo la primera y la otra recibe el estado real (`409 ESTADO_YA_CAMBIADO` si la ganadora fue recepción).
- [X] T031 [P] [US2] `backend/src/test/java/es/citaclara/recordatorio/CancelacionLiberaHuecoTest.java` (Principio III): `[003][FR-015][SC-003][US2-AC2]` tras cancelar por el enlace el hueco figura libre de inmediato en la agenda y puede reservarse de nuevo sin intervención técnica; reservas simultáneas del mismo hueco liberado → exactamente una prospera, sin solape; `[003][FR-016][US2-AC8]` la agenda devuelve `canceladaPor = PACIENTE` solo para la cancelada por el enlace y nulo para la cancelada por recepción.
- [X] T032 [P] [US2] `frontend/tests/unit/PantallaCancelacion.test.tsx` (Vitest + Testing Library, API simulada): `[003][FR-017][US2-AC1]` estado Activa con los dos botones y sin llamar al `POST` al abrir; `[003][FR-017][US2-AC2]` confirmar muestra «Tu cita está cancelada»; `[003][FR-017][US2-AC5]` fuera de plazo con el teléfono como enlace `tel:` y sin botón de cancelar; `[003][FR-017][US2-AC4]` ya no activa; `[003][FR-018][US2-AC6]` enlace no válido; error con «Reintentar»; el botón se desactiva mientras se envía. Y `[003][FR-016][US2-AC8]` en `TarjetaCita.test.tsx` «Cancelada por el paciente».
- [X] T033 [P] [US2] `frontend/tests/e2e/cancelacion.spec.ts` (Playwright + axe, patrón de `ApiPrueba.huecoLibre`): crea su cita, ejecuta el comando de recordatorios, lee el `.eml`, abre el enlace a 360 px, comprueba texto no desbordado, objetivos táctiles ≥ 44 px y `pantallaAccesible`; `[003][SC-002][US2-AC2]` completa el flujo (abrir, revisar, confirmar) en tres pasos y menos de 30 s; `[003][FR-017][US2-AC2]` confirma y comprueba en el panel la franja libre y «Cancelada por el paciente» (`[003][FR-016][US2-AC8]`).

### Implementación de la Historia 2

- [X] T034 [US2] En `backend/src/main/java/es/citaclara/acceso/ConfiguracionWeb.java` añadir `/api/cancelacion/**` a `excludePathPatterns` de `InterceptorSesion` (el `SoloJson` sigue aplicándose); comentario con la referencia a la petición 3 de plan.md.
- [X] T035 [P] [US2] `backend/src/main/java/es/citaclara/recordatorio/CancelacionRespuesta.java`: registro con `situacion` (`ACTIVA`, `FUERA_DE_PLAZO`, `YA_CERRADA`), `canceladaPorPaciente`, `clinica {nombre, telefono}` y `cita {fecha, horaInicio, profesional, servicio}` exactamente como [contracts/api.yaml](contracts/api.yaml); sin importe, teléfono ni email del paciente ni identificadores.
- [X] T036 [US2] `backend/src/main/java/es/citaclara/recordatorio/ServicioCancelacionPaciente.java` (depende de T008, T013, T035): `consultar(enlace)` calcula la situación con el `Clock` (`ACTIVA` si RESERVADA y `ahora <= inicio − 24 h`; `FUERA_DE_PLAZO` si RESERVADA y no; `YA_CERRADA` en otro estado); `cancelar(enlace)` en una transacción aplica `MaquinaEstadosCita.transicion(..., CANCELAR, ...)`, `cita.cancelarPorPaciente()` y guarda con la versión de la cita; ante `ObjectOptimisticLockingFailureException` relee: ya cancelada por el paciente → mismo resultado de éxito; otro estado → `409 ESTADO_YA_CAMBIADO` con la `cancelacion` actual; fuera de plazo → `422 FUERA_DE_PLAZO` con mensaje «Ya no puedes cancelar desde aquí. Llama a la clínica al {teléfono}.»; huella desconocida → `ErrorNegocio.noEncontrado()`.
- [X] T037 [US2] `backend/src/main/java/es/citaclara/recordatorio/ControladorCancelacionPaciente.java`: `GET` y `POST /api/cancelacion/{enlace}` (`@PathVariable` con patrón `^[A-Za-z0-9_-]{43}$`; otro formato → 404 `NO_ENCONTRADO`), delegando en T036.
- [X] T038 [P] [US2] Cliente y tipos del frontend: `frontend/src/api/cliente.ts` (`consultarCancelacion`, `confirmarCancelacion`; sin el manejador de sesión perdida) y `frontend/src/api/tipos.ts` (`Cancelacion`, `SituacionCancelacion`, `canceladaPor` en `Cita`).
- [X] T039 [US2] `frontend/src/cancelacion/PantallaCancelacion.tsx` con los siete estados de [contracts/ui.md](contracts/ui.md), reutilizando `ui/Boton.tsx` y `ui/Aviso.tsx`, textos en español de España, objetivos táctiles ≥ 44 px, enlace `tel:` y botón desactivado mientras se envía.
- [X] T040 [US2] En `frontend/src/App.tsx`, antes de resolver la clínica, renderizar `PantallaCancelacion` cuando la ruta sea `/cancelar/{enlace}` (sin sesión, sin llamar a `obtenerContexto`); ajustar `identificadorDeRuta` para que «cancelar» no se trate como clínica en esa ruta.
- [X] T041 [P] [US2] `frontend/src/agenda/TarjetaCita.tsx`: mostrar «Cancelada por el paciente» junto al distintivo cuando `canceladaPor = 'PACIENTE'` (FR-016); sin cambios en las canceladas por la clínica.

**Punto de control**: US1 y US2 funcionan; el flujo completo del [quickstart.md](quickstart.md) pasos 1–4 es reproducible.

---

## Fase 5: Historia de usuario 3 — El recordatorio sigue a la cita cuando cambia (Prioridad: P3)

**Objetivo**: cancelar y volver a crear una cita no deja enlaces antiguos operativos ni reenvía el recordatorio viejo; la cita nueva recibe el suyo solo si cae en la ventana.

**Prueba independiente**: con una cita ya recordada, cancelarla desde recepción y crear otra; el enlace antiguo no afecta a la nueva y solo la nueva recibe recordatorio si está en la ventana.

### Tests de la Historia 3

- [X] T042 [P] [US3] `backend/src/test/java/es/citaclara/recordatorio/CambioCitaRecordatorioTest.java`: `[003][FR-014][US3-AC1]` tras cancelar la cita recordada desde recepción, el enlace antiguo da `YA_CERRADA` y no cancela otra cita del mismo paciente; `[003][FR-014][US3-AC2]` la cita nueva dentro de la ventana recibe su recordatorio con enlace distinto y datos actuales; `[003][FR-014][US3-AC3]` la cita nueva fuera de la ventana no genera recordatorio y el antiguo no se reenvía; `[003][FR-004]` un paciente con varias citas en la ventana recibe un correo por cita, cada uno cancelando solo la suya.

### Implementación de la Historia 3

- [X] T043 [US3] Ejecutar T042; si algún caso falla, corregir en `ServicioRecordatorios.java` o `ServicioCancelacionPaciente.java` (no se prevé código nuevo: la cita nueva es una fila distinta y el recordatorio antiguo queda ligado a la cita cancelada).

**Punto de control**: las tres historias son independientes y están verificadas.

---

## Fase 6: Pulido y transversal

- [X] T044 [P] Test `backend/src/test/java/es/citaclara/recordatorio/AlcanceV1Test.java`: `[003][FR-022]` las únicas rutas públicas nuevas son `GET` y `POST /api/cancelacion/{enlace}` (inspeccionar `RequestMappingHandlerMapping`) y solo existen los enviadores `EnviadorCorreoFichero` y `EnviadorCorreoSmtp` (sin SMS ni WhatsApp).
- [X] T045 [P] Generar `specs/003-recordatorios-cita/datos-demostracion.md` con `./mvnw verify -DactualizarDatosDemostracion=true` (T022): recuento de recordatorios por posición relativa respecto al día de carga, cómo reproducirlo y enlace a esta spec; revisar que el texto cita solo cifras generadas.
- [X] T046 [P] Actualizar `README.md`: modo simulado y carpeta `datos/salida-correo/`, `docker compose run --rm recordatorios`, variables de entorno de [contracts/correo.md](contracts/correo.md), hora 09:00 configurable y «cancelar» como identificador de clínica reservado.
- [X] T047 Ejecutar completo [quickstart.md](quickstart.md) (pasos 1–6) sobre `docker compose` limpio y anotar cualquier discrepancia en la spec o en el código.
- [X] T048 Poner `specs/003-recordatorios-cita/spec.md` y `specs/MAPA.md` en estado «Implementada» (Principio I; puerta de merge 1) y corregir la spec si el comportamiento entregado difiere.
- [X] T049 Verificar la trazabilidad con `./scripts/trazabilidad.sh specs/003-recordatorios-cita` (debe salir vacío) y la suite completa: `cd backend && ./mvnw verify`, `cd frontend && npm ci && npm test` y `URL_BASE=http://localhost:8080 npm run e2e` (puertas de merge 2–5).
- [X] T050 Actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[003] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features (candidatas: `UNIQUE(cita_id)` + `ON CONFLICT` como patrón de idempotencia, enlaces con huella SHA-256, capacidad `correo/` y modo simulado, rutas públicas excluidas de la sesión, ventana en `Instant`).

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Fase 1** (T001–T005): sin dependencias; T003 espera a T006 para fijar el contrato de la migración.
- **Fase 2** (T006–T015): depende de la Fase 1; **bloquea** todas las historias. T006 es previa a T007, T008 y T003.
- **Fase 3 (US1)**: depende de la Fase 2. **Fase 4 (US2)**: depende de la Fase 2 y reutiliza `EnlaceCancelacion` y la huella de US1 (T023, T012), por lo que conviene terminar US1 antes. **Fase 5 (US3)**: depende de US1 y US2 (reutiliza ambas).
- **Fase 6**: depende de las historias deseadas; T050 es siempre la última tarea.

### Dependencias dentro de cada historia

- Los tests (T016–T022, T029–T033, T042) se escriben primero y deben fallar.
- Entidad/consulta → servicio → controlador o programador → frontend.
- T025 depende de T010, T012, T013, T023 y T024; T036 depende de T008, T013 y T035; T040 depende de T038 y T039.

### Oportunidades de paralelismo

- Fase 1: T002, T004 y T005 en paralelo tras T001.
- Fase 2: T007, T008, T009, T011, T012, T013 y T014 son de ficheros distintos.
- US1: todos los tests T016–T022 en paralelo; T023, T024 y T027 en paralelo.
- US2: T029–T033 en paralelo (backend y frontend a la vez); T035, T038 y T041 en paralelo.
- Fase 6: T044, T045 y T046 en paralelo.

```text
# Ejemplo US1: lanzar juntos los tests de la historia
T016 VentanaRecordatoriosTest   T017 SinDuplicadosTest     T018 FalloRecordatorioTest
T019 CorreoRecordatorioTest     T020 AislamientoRecordatoriosTest
T021 CambioHorarioRecordatoriosTest   T022 DatosDemostracionRecordatoriosTest
```

## Estrategia de implementación

1. **MVP**: Fases 1 y 2, luego US1 (T016–T028). Parar y validar con el comando manual: un correo por cita de la ventana y ninguno duplicado. Ya reduce los olvidos aunque el paciente aún no pueda cancelar.
2. **Incremento 2**: US2 (T029–T041): cancelación pública y distintivo en la agenda.
3. **Incremento 3**: US3 (T042–T043): comprobación del cambio de cita.
4. **Cierre**: Fase 6, con la trazabilidad en vacío, la suite en verde y `CLAUDE.md` actualizado.

## Notas

- Convención de commits de la 001: un commit por tarea o grupo lógico; `.eml` generados nunca se versionan.
- El importe no aparece en el correo ni en la pantalla pública (FR-011); no formatear euros en esta feature.
- Esta feature no crea citas: ninguna tarea toca `RepositorioProfesional.bloquear` ni la restricción `cita_sin_solape`; las pruebas de solape de T031 verifican que la cancelación no la altera.
- Si el propietario de la 001 rechaza alguna petición (T006), actualizar plan.md y estas tareas antes de continuar.
