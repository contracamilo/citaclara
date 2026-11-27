# Investigación: Recordatorios de cita por email

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Cada decisión sigue el formato Decisión / Motivo / Alternativas descartadas.

## D1. Proceso diario y hora de ejecución

- **Decisión**: `@Scheduled(cron = "0 0 9 * * *", zone = "Europe/Madrid")` dentro del backend, todos los días (también fines de semana), configurable con `RECORDATORIOS_CRON`. La lógica vive en `ServicioRecordatorios.generar()`, que recibe el `Clock` inyectado; el programador solo la invoca.
- **Motivo**: FR-001 pide un proceso diario; 09:00 hace que el paciente reciba el aviso a primera hora del día anterior al tramo 09:00–09:00 siguiente y que una cita cualquiera caiga en la ventana exactamente una vez. La ventana es [T+24 h, S+24 h), donde S es la siguiente ejecución (misma hora local del día siguiente): las ventanas de ejecuciones consecutivas encajan exactamente, también el día del cambio de horario, cuando el intervalo real entre ejecuciones es de 23 o 25 h. Una ventana fija [T+24 h, T+48 h) dejaría un hueco de una hora el día de 25 h (citas sin recordatorio) y un solape el de 23 h; el `UNIQUE` evitaría el duplicado pero no el hueco.
- **Descartado**: cron externo (más piezas que operar); ejecutar cada hora (la spec fija un proceso diario y Sara decidió «24 h»); hora distinta de las 09:00 (la clínica puede cambiarla por configuración sin tocar código).
- **Limitación conocida (documentada en la spec)**: una cita reservada después de la ejecución de las 09:00 y cuyo inicio queda a menos de 24 h de la ejecución siguiente nunca entra en la ventana y no recibe recordatorio. Es coherente con «nunca menos de 24 h de antelación» y con que el paciente acaba de acordarla.

## D2. Correo: dependencia y modos

- **Decisión**: `spring-boot-starter-mail` (Jakarta Mail). Interfaz `EnviadorCorreo` con `EnviadorCorreoFichero` (por defecto) y `EnviadorCorreoSmtp` (activo solo si existe `CORREO_SMTP_HOST`). El modo se decide por configuración (FR-021). Ambos construyen el mismo `MimeMessage`; el modo simulado lo escribe con `writeTo`.
- **Motivo**: UTF-8, codificación de asunto (RFC 2047) y cuerpo, `Message-ID` y cabeceras correctas sin escribirlas a mano. Es la única dependencia nueva y se vincula a FR-020 y FR-021 (Principio IV).
- **Descartado**: generar el `.eml` a mano (riesgo con tildes y ñ; hay que rehacerlo al pasar a SMTP); otra librería de correo (no aporta nada sobre la estándar de Spring).
- **Alcance de la prueba**: solo el modo simulado se prueba de extremo a extremo; del SMTP real se prueba únicamente que se elige por configuración (spec, suposiciones).

## D3. Garantía de no duplicados

- **Decisión**: tabla `recordatorio` con `UNIQUE (cita_id)`. Cada cita se procesa en su propia transacción: `INSERT … ON CONFLICT (cita_id) DO NOTHING`; si no inserta, otra ejecución ya la tiene y se omite. Con la fila insertada se construye y escribe el correo; si la escritura falla, la excepción revierte la transacción y la cita no queda registrada (FR-007), de modo que se reintenta en la siguiente ejecución.
- **Idempotencia ante caídas**: nombre de fichero determinista `recordatorio-cita-{citaId}.eml`, escritura a un temporal y movimiento atómico. Si el proceso muere tras mover el fichero y antes del `COMMIT`, la repetición reescribe el mismo fichero (con un enlace nuevo, cuya huella es la que queda guardada) sin generar un segundo correo.
- **Concurrencia**: dos ejecuciones simultáneas sobre la misma cita se serializan en el `UNIQUE`: la segunda espera al `COMMIT` de la primera y recibe «0 filas insertadas».
- **Descartado**: comprobar con `SELECT` antes de insertar (condición de carrera); marcar «enviado» con un booleano en `cita` (modifica la tabla de la 001 y no aísla el historial de envíos); bloqueo de la fila del profesional (es para altas de citas; aquí no se inserta en la agenda).

## D4. Selección de citas

- **Decisión**: consulta `RepositorioCita.paraRecordatorio(desde, hasta)` con `desde = ahora + 24 h` y `hasta = (ahora + 1 día local) + 24 h` (D1), `estado = RESERVADA`, `inicio >= desde and inicio < hasta`, paciente con email no vacío y `not exists (recordatorio de esa cita)`; trae clínica, paciente, profesional y servicio con `join fetch` para no consultar por cita.
- **Motivo**: FR-001, FR-003, FR-006. Medir en `Instant` (la «siguiente ejecución» se calcula con `ZonedDateTime.plusDays(1)` en Madrid) hace que el cambio de horario no desplace ni duplique citas (Principio II). El proceso es global (todas las clínicas) pero cada correo solo usa los datos de su cita (FR-008).
- **Descartado**: calcular la ventana en fechas locales (rompe con 23/25 h); una consulta por clínica (más código sin beneficio).

## D5. Enlace de cancelación

- **Decisión**: 32 bytes de `SecureRandom` codificados en Base64 URL (43 caracteres). Se guarda solo `SHA-256(enlace)` en `recordatorio.enlace_huella`; no se puede recuperar el enlace desde la base de datos. URL del correo: `{CITACLARA_URL_PUBLICA}/cancelar/{enlace}` (por defecto `http://localhost:8080`).
- **Motivo**: FR-012 y FR-018. Un volcado de la base de datos no permite cancelar citas. 256 bits hacen inviable adivinarlo, así que no hace falta limitar intentos (a diferencia de la clave de clínica, deuda de la 001).
- **Descartado**: JWT firmado (clave a custodiar y rotar, sin ventaja); identificador de cita firmado con HMAC (acopla el enlace a un secreto global); guardar el enlace en claro.

## D6. Operaciones públicas y sesión

- **Decisión**: `GET /api/cancelacion/{enlace}` (consulta, sin efectos) y `POST /api/cancelacion/{enlace}` con cuerpo JSON `{}` (confirma). Se excluyen de `InterceptorSesion` en `ConfiguracionWeb` mediante `excludePathPatterns("/api/cancelacion/**")`; `SoloJson` sigue aplicando a todo POST.
- **Motivo**: FR-012 («abrir solo muestra, cancelar exige confirmación»): los analizadores de enlaces del correo hacen `GET`, nunca `POST` JSON. Un enlace desconocido, manipulado o de otra clínica responde `404 NO_ENCONTRADO` con el mismo mensaje que cualquier recurso inexistente (convención 001 de aislamiento). La defensa CSRF no aplica: la autoridad es el propio enlace, no una cookie, y el POST no admite formularios.
- **Descartado**: un solo `GET` que cancele (lo dispararían los escáneres de correo); un formulario HTML tradicional (el producto no usa formularios clásicos y rompe la regla «solo JSON»).

## D7. Plazo de 24 h y cancelación

- **Decisión**: `ServicioCancelacionPaciente.cancelar(enlace)` en una transacción: localiza la cita por huella, y si `ahora > inicio − 24 h` responde `FUERA_DE_PLAZO` (el límite exacto de 24 h aún se puede cancelar: «hasta 24 h antes»). Si es válida, aplica `MaquinaEstadosCita.transicion(..., CANCELAR, ...)` (misma regla de la 001), marca `cancelada_por = PACIENTE` y guarda con el bloqueo optimista de la versión de `Cita`.
- **Concurrencia (FR-019)**: si el guardado falla por versión, se relee la cita: si ya está cancelada por el paciente se responde el mismo resultado de éxito («tu cita está cancelada»); si la canceló recepción o cambió de otro modo, se responde `ESTADO_YA_CAMBIADO`-equivalente con el estado real y un mensaje claro. Doble envío = una sola cancelación y respuestas coherentes.
- **Descartado**: bloqueo pesimista de la fila (la 001 ya usa versión para los cambios de estado); permitir cancelar hasta el inicio (Sara decidió 24 h).

## D8. Respuesta de la consulta

- **Decisión**: la consulta devuelve `situacion` ∈ `ACTIVA` (cancelable), `FUERA_DE_PLAZO` (reservada pero a menos de 24 h o ya empezada), `YA_CERRADA` (cancelada, completada o no asistida) y los datos mínimos de la cita (clínica, profesional, servicio, fecha, hora de inicio) y el teléfono de la clínica. No devuelve teléfono ni email del paciente, ni importe, ni identificadores internos.
- **Motivo**: FR-017/FR-018 y mínima exposición de datos; la pantalla puede mostrar el mensaje correcto sin interpretar códigos.

## D9. Contenido del correo

- **Decisión**: `text/plain` UTF-8 en español de España. Asunto: «Recordatorio de tu cita en {clínica}: {dd/MM/yyyy} a las {HH:mm}». Cuerpo: saludo con el nombre, profesional, servicio, fecha larga sin ambigüedad («viernes 27/11/2026») y hora de 24 h, el enlace de cancelación con la frase «Si no puedes venir, cancélala hasta 24 horas antes: {enlace}», y el teléfono de la clínica para cambios o cancelaciones posteriores. Sin importe (FR-011) y sin contenido clínico.
- **Saneado**: el nombre y la dirección se validan como `InternetAddress` estricta; saltos de línea en nombres se eliminan (inyección de cabeceras). Si la dirección no es válida, la cita se omite con un aviso en el registro y no detiene el proceso (FR-007).
- **Descartado**: HTML con botón (más superficie de pruebas de accesibilidad y de clientes de correo; el texto con enlace cubre el objetivo); plantillas configurables (FR-022).

## D10. Datos de la clínica y de la cancelación en esquema

- **Decisión**: `V2__recordatorios.sql` añade `clinica.telefono varchar(20)` (obligatorio en la semilla; las clínicas existentes reciben un valor al cargar la semilla), `cita.cancelada_por varchar(10) CHECK (cancelada_por IN ('PACIENTE'))` y la tabla `recordatorio`. Son peticiones a la propietaria de la 001 (ver plan).
- **Motivo**: FR-009 (teléfono en el correo y en la pantalla fuera de plazo) y FR-016 (distinguir quién canceló). La tabla `recordatorio` pertenece a esta feature.
- **Descartado**: tabla aparte `cancelacion_paciente` (una unión más en cada consulta de agenda para un solo dato); texto genérico sin teléfono (incumple FR-009).

## D11. Frontend

- **Decisión**: en `App.tsx`, antes de resolver la clínica, si la ruta es `/cancelar/{enlace}` se renderiza `PantallaCancelacion` (sin sesión). nginx ya envía cualquier ruta a `index.html`, así que no hay que tocarlo. Riesgo de colisión: un identificador de clínica de la 001 (`^[a-z0-9-]+$`) podría llamarse «cancelar». No existe ninguna hoy; se documenta «cancelar» como identificador reservado en el README y la semilla lo respeta (la 001 no tiene alta de clínicas).
- **Pantalla**: tres estados visibles (activa con botón «Cancelar mi cita», confirmada, ya no cancelable con teléfono) y un error genérico; botón de objetivo táctil ≥ 44 px; textos de contracts/ui.md.
- **Agenda**: `TarjetaCita` muestra «Cancelada por el paciente» cuando `canceladaPor = PACIENTE` (FR-016).
- **Descartado**: añadir un enrutador (decisión D15 de la 001); pantalla en servidor (no hay plantillas de servidor en el stack).

## D12. Operación en Docker

- **Decisión**: volumen `./datos/salida-correo:/app/datos/salida-correo` en `api` y en el servicio `recordatorios` (perfil `herramientas`, una ejecución manual: `docker compose run --rm recordatorios`). `datos/salida-correo/` se crea con permisos de escritura para el usuario sin privilegios de la imagen y los `.eml` se ignoran en git.
- **Motivo**: FR-020 pide ficheros en `datos/salida-correo/`; el comando permite demostrar el flujo sin esperar a las 09:00 (quickstart y e2e).
- **Descartado**: carpeta dentro de la imagen (se pierde al recrear el contenedor).
