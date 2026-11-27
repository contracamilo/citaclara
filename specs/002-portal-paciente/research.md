# Investigación y decisiones: Portal del paciente

**Feature**: `002-portal-paciente` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Esta feature se apoya en el stack y las convenciones de la feature 001 ([research.md de 001](../001-nucleo-agenda/research.md), D1–D16, y `CLAUDE.md`). Aquí solo se recogen las decisiones nuevas. Cada una indica qué FR o criterio la motiva.

## R1. Enlace de acceso de un solo uso (FR-001, FR-002, FR-006, SC-009)

- **Decisión**:
  - El enlace lleva un **secreto aleatorio de 256 bits** (`SecureRandom`, codificado en base64url) que no significa nada por sí mismo. En la base de datos solo se guarda su **hash SHA-256**, así que con una copia de la base de datos no se puede entrar en el portal.
  - El secreto va en el **fragmento** de la URL, no en la ruta ni en los parámetros: `https://{host}/{identificador}/mis-citas#enlace={secreto}`. El fragmento no llega al servidor web, a los registros de nginx ni a la cabecera `Referer`.
  - La página del portal lee el fragmento, lo borra del historial (`history.replaceState`) y lo envía con `POST /api/portal/{identificador}/sesion` en JSON. Así, abrir el enlace con un simple GET no lo consume. Esto protege frente a los antivirus de correo que visitan los enlaces antes que el paciente, aunque no frente a los que ejecutan JavaScript (riesgo aceptado).
  - El consumo es **atómico**: `UPDATE … SET usada_en = :ahora WHERE token_hash = :h AND usada_en IS NULL AND caduca_en > :ahora AND clinica_id = :c RETURNING email_normalizado`. Si no se actualiza ninguna fila, la respuesta es 401 `ENLACE_NO_VALIDO`. Dos aperturas simultáneas del mismo enlace dan, por tanto, exactamente una sesión.
  - El enlace caduca a los 10 minutos (`caduca_en = emitido_en + 10 min`) y el `Clock` inyectado es el que manda.
- **Motivo**: es el mecanismo de enlace mágico más simple que cumple la spec. No hace falta firmar nada ni añadir librerías, y revocarlo es trivial.
- **Alternativas**:
  - JWT firmado en el enlace: hace falta una librería, no es de un solo uso sin estado en el servidor y es más largo.
  - Secreto en la query string: queda en los registros y en el `Referer`.
  - Consumirlo con GET directo: los escáneres de correo lo gastarían antes que el paciente.

## R2. Envío de email (FR-001, FR-007, FR-031)

- **Decisión**:
  - Se usa `spring-boot-starter-mail` (SMTP estándar, `JavaMailSender`) detrás de un puerto propio, `CorreoPortal`, que tiene una sola operación: `enviarEnlace(email, clinica, url)`.
  - El correo es de **texto plano** en español de España (asunto «Tu enlace para entrar en Clínica Eleva»). Incluye el nombre de la clínica, que caduca en 10 minutos y que se puede ignorar si no se ha pedido ([contracts/ui.md](contracts/ui.md) §4).
  - El envío es **asíncrono y posterior al commit** (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`). Así la respuesta HTTP no espera al servidor SMTP (ver R3) y un fallo de envío no deshace la solicitud. Si el envío falla, se registra sin el email ni el secreto, y el paciente puede pedir otro enlace.
  - El SMTP se configura por variables de entorno: `SMTP_HOST`, `SMTP_PUERTO`, `SMTP_USUARIO`, `SMTP_CLAVE` y `EMAIL_REMITENTE`. Elegir proveedor real es cosa del despliegue de cada clínica y queda fuera del plan.
  - **Demostración y e2e** (FR-031): `docker-compose.yml` añade el servicio **Mailpit**, que captura todo el SMTP sin entregarlo. Su bandeja web se publica solo en `127.0.0.1:${PUERTO_CORREO:-8025}`. Playwright lee el enlace desde el API HTTP de Mailpit. A las direcciones `@ejemplo.es` nunca sale un correo real.
  - **Pruebas de integración**: la base de pruebas sustituye `CorreoPortal` por un `BuzonPrueba` en memoria que guarda los enlaces «enviados». Así no hace falta ni SMTP ni ninguna dependencia de pruebas nueva.
- **Motivo**: SMTP es el estándar que entiende cualquier proveedor. El puerto propio aísla el envío para las pruebas, y Mailpit permite ver el enlace en la demo sin código especial.
- **Alternativas**:
  - SDK de un proveedor concreto (SendGrid, SES): ata el producto a un proveedor sin necesidad.
  - GreenMail en pruebas: es una dependencia más para lo que resuelve un doble de 20 líneas.
  - Mostrar el enlace en pantalla en modo demo: es un camino de código que no existe en producción y que FR-003 haría peligroso.

## R3. Respuesta indistinguible y límite de solicitudes (FR-003, FR-004, SC-007)

- **Decisión**:
  - Se guarda **cada solicitud**, exista o no el email, en `solicitud_enlace_portal`. Si el email coincide con alguna ficha, la solicitud lleva `token_hash`; si no, ese campo es `NULL` y no se envía nada.
  - Respuesta: siempre **202** con el mismo cuerpo vacío. El texto visible lo pone el frontend y es único ([contracts/ui.md](contracts/ui.md) §1).
  - Límite (FR-004): si en la última hora ya hay 5 solicitudes para `(clínica, email normalizado)`, la respuesta es **429** `ENLACES_AGOTADOS` hasta que la más antigua de esas 5 cumpla una hora. El límite se aplica igual a los emails inexistentes, porque si no lo hiciera, la 6.ª solicitud revelaría si el email existe.
  - Para evitar diferencias de tiempo, el envío es asíncrono (R2) y ambos caminos hacen una consulta y una inserción.
  - La carrera entre 5 solicitudes simultáneas se cierra bloqueando la fila de la clínica (`FOR UPDATE`) mientras se cuenta y se inserta. Es el mismo patrón que `RepositorioProfesional.bloquear` en 001.
  - Limpieza: cada solicitud borra las filas de su `(clínica, email)` con más de 24 horas. No hace falta ninguna tarea programada.
- **Motivo**: FR-003 y FR-004 se contradicen si el límite solo cuenta los emails conocidos. Contar todas las solicitudes resuelve la contradicción con una sola tabla.
- **Alternativas**:
  - Límite en memoria: se pierde al reiniciar y no funciona con varias instancias.
  - Límite por IP: la spec no lo pide (Principio IV), y en las clínicas hay IP compartidas.

## R4. Sesión del paciente (FR-005, FR-028, FR-029, US1-AC5, US1-AC6)

- **Decisión**:
  - Se reutiliza la `HttpSession` y su cookie (`HttpOnly`, `SameSite=Strict`, research D6 de 001), pero con **atributos propios**: `citaclara.portal.clinicaId`, `citaclara.portal.email` y `citaclara.portal.accesoEn`.
  - Un interceptor nuevo, `InterceptorPortal`, protege `/api/portal/**` (salvo pedir enlace y abrir sesión). Aplica la caducidad **absoluta de 30 minutos** con el `Clock` y responde 401 `SESION_PORTAL_CADUCADA` o `SIN_SESION_PORTAL`.
  - El interceptor del panel excluye `/api/portal/**`. El del portal no protege nada del panel. Con eso se cumple FR-029: la clave no abre el portal y la sesión del portal no abre el panel.
  - **Exclusión mutua por navegador**: abrir una sesión del portal invalida la sesión anterior, y lo mismo al revés (ya lo hace el panel para evitar la fijación de sesión). En un mismo navegador nunca conviven las dos. Es lo más simple y no afecta al caso real: el portátil de recepción y el móvil del paciente son dispositivos distintos.
  - La identidad del paciente es **`(clínica, email normalizado)`** y se evalúa en cada petición: se muestran las citas de las fichas de esa clínica cuyo email coincide **ahora** (casos límite «varias fichas» y «cambia de email»).
  - Las respuestas de `/api/portal/**` llevan `Cache-Control: no-store`. Así, el botón atrás tras «Salir» no enseña datos guardados en la caché (US1-AC6).
- **Motivo**: se reutiliza la infraestructura de sesión de 001 sin piezas nuevas. Vincular la sesión al email sigue la regla de la spec («el acceso usa el email vigente»).
- **Alternativas**:
  - Una tabla y una cookie propias para el portal: más código, para separar dos sesiones que en la práctica no se cruzan.
  - Guardar los id de paciente al entrar: un cambio de email en la ficha no surtiría efecto hasta que caducara la sesión.

## R5. Normalización y búsqueda del email (FR-002, caso límite «mayúsculas o espacios»)

- **Decisión**: se compara `lower(btrim(email))` con el email escrito, normalizado igual en Java (`strip().toLowerCase(Locale.ROOT)`). Para la búsqueda se añade el índice funcional parcial `paciente_email_portal ON paciente (clinica_id, lower(btrim(email))) WHERE email IS NOT NULL`. No se cambia la columna ni la validación de 001. El formato del email escrito se valida igual que en 001 (D11). Si el email está mal escrito, la respuesta es 400 `EMAIL_NO_VALIDO`, que no revela nada porque no depende de que el email exista.
- **Alternativas**:
  - Normalizar la columna al guardar: cambia el comportamiento de 001.
  - `citext`: otra extensión para un único uso.

## R6. Plazo de 24 horas (FR-012–FR-014, SC-003, casos límite de plazo)

- **Decisión**:
  - `ReglaCancelacionPortal.dentroDePlazo(inicio, ahora)` devuelve `Duration.between(ahora, inicio).compareTo(Duration.ofHours(24)) > 0`. Trabaja sobre `Instant`, así que las 24 horas son reales: un cambio de hora no las alarga ni las acorta, y los fines de semana cuentan.
  - El servidor devuelve en cada cita del portal si es `cancelable` y, si no lo es, el motivo, para que el frontend no haga cálculos de fechas (convención de 001).
  - Al registrar la cancelación se vuelve a comprobar el plazo con el reloj del servidor (FR-014).
  - Ejemplo con la semilla: para la cita de Laura Romero del día laborable +2 a las 09:45, a las 09:44:59 del día laborable +1 todavía puede cancelar, y a las 09:45:00 ya no.
- **Alternativas**: calcular el plazo en el frontend, que va contra la convención de 001 de no hacer aritmética de zonas en el cliente y contra FR-014.

## R7. Cancelación desde el portal: transición, registro y concurrencia (FR-015–FR-017, US3-AC6, US3-AC7, SC-005)

- **Decisión**:
  - El paquete `cita` expone una operación pública, `ServicioEstadoCita.cancelarDesdePortal(clinicaId, citaId, versionVista, comprobacion)`, que reutiliza la **misma máquina de estados** (`MaquinaEstadosCita.transicion(…, CANCELAR, …)`) y el mismo bloqueo optimista (`@Version`, research D3 de 001). El portal le pasa la comprobación previa (que la cita es del paciente y que está en plazo). La transición no se duplica: solo hay una regla de «reservada → cancelada».
  - En la **misma transacción** se inserta `cancelacion_portal (cita_id PK, clinica_id, cancelada_en)`. La clave primaria garantiza un solo aviso por cita (SC-005).
  - Conflicto con otro cambio (de recepción o de otro dispositivo): 409 `ESTADO_YA_CAMBIADO` con la cita real (FR-016).
  - **Doble clic del mismo paciente** (US3-AC7): si la cita ya está cancelada y existe su `cancelacion_portal`, la operación responde 200 con la cita cancelada y no da error. Además, el frontend desactiva el botón mientras espera la respuesta.
  - Una cita que no pertenece al email de la sesión responde 404, igual que una inexistente (FR-028).
- **Motivo**: una sola máquina de estados para recepción y portal. El registro de origen vive en una tabla propia de 002, así que `cita` no necesita columnas nuevas.
- **Alternativas**:
  - Columna `cancelada_por` en `cita`: cambia el modelo de 001 para un dato que solo usa 002.
  - Una transición propia en el portal: duplicaría la regla y se desalinearía con el tiempo.

## R8. Aviso de cancelaciones en recepción (FR-019–FR-023, US4, SC-008)

- **Decisión**:
  - Hay dos endpoints del panel, protegidos por la sesión de la clínica: `GET /api/cancelaciones-portal` (contador y lista de pendientes, de la más reciente a la más antigua, con `huecoPasado` calculado en el servidor) y `POST /api/cancelaciones-portal/{citaId}/revision`.
  - La revisión es **idempotente**: `UPDATE … SET revisada_en = :ahora WHERE cita_id = :id AND clinica_id = :c AND revisada_en IS NULL`. Si ya estaba revisada, responde 204 igualmente (US4-AC5). Una cita de otra clínica responde 404.
  - **Frontend**: hay un componente `AvisoCancelaciones` en la cabecera de la agenda, que es la única pantalla con sesión del panel. Pide el contador al cargar la agenda y cada vez que cambian el día o el profesional (SC-008: no se exige tiempo real). Al pulsarlo se abre `DialogoCancelacionesPortal` (hoja inferior en móvil) con la lista y el botón «Marcar como revisada».
  - El endpoint de contexto de 001 no cambia, para no tocar su contrato.
- **Alternativas**:
  - Añadir el contador a `GET /api/contexto`: cambia un contrato de 001 y solo se cargaría al entrar.
  - Sondeo periódico o SSE: la spec descarta el tiempo real (Supuestos).

## R9. Garantía de no solape (Principio III, puerta de merge 3)

- **Decisión**: la cancelación pasa una cita a un estado libre, así que nunca puede crear un solape, y la *exclusion constraint* `cita_sin_solape` sigue cubriendo cualquier escritura. Como la feature escribe en la agenda, incluye estas pruebas contra PostgreSQL real:
  1. Tras cancelar desde el portal, una nueva alta en el mismo hueco se registra (el hueco está realmente libre).
  2. Cancelación desde el portal seguida de **10 altas simultáneas** sobre el hueco liberado: exactamente 1 se registra, en 20 repeticiones.
  3. Cancelación desde el portal y alta de recepción en paralelo sobre el mismo hueco: nunca quedan dos citas ocupantes.

## R10. Frontend del portal (FR-024–FR-027, SC-006)

- **Decisión**:
  - Dirección `/{identificador}/mis-citas`. Nginx ya la sirve, porque todo cae en `index.html`. `App.tsx` mira el segundo segmento de la ruta: si es `mis-citas`, monta `AppPortal`; si no, el panel, que no cambia. Sigue sin haber enrutador (D15 de 001).
  - Pantallas: `PantallaEntradaPortal` (email y mensaje neutro), consumo del enlace con su estado de carga, y `PantallaMisCitas` (bloques «Próximas citas» y «Citas pasadas», tarjetas, diálogo de cancelación y «Salir»).
  - Se reutilizan `ui/` (`Boton`, `Dialogo`, `Aviso`), `formato/` (euros y fechas, el único formateador) y los tokens de diseño. Se trata al paciente de «tú» (FR-025).
  - Los estados del portal tienen distintivo propio con color, icono y texto: Reservada, Realizada, No acudiste, Cancelada y Pendiente de cierre por la clínica.
  - Pensado para móvil: una sola columna, tarjetas a todo el ancho a 360 px y objetivos táctiles de al menos 44 px. Las pruebas e2e del portal pasan por `pantallaAccesible` (axe, 360 px, texto sin desbordar).
- **Alternativas**: una SPA separada para el portal, que duplicaría el empaquetado, los tokens y los componentes para tres pantallas.

## R11. Datos de demostración por paciente (FR-030, Principio V)

- **Decisión**:
  - La semilla **no cambia**.
  - Una prueba `DatosPortalDocumentadosTest` (`[002][FR-030]`) recorre el generador puro de 001 y comprueba la tabla de `specs/002-portal-paciente/datos-demostracion.md`. Para Teresa Méndez, Laura Romero, Rocío Garrido y Gonzalo Cruz, la tabla tiene sus citas futuras y pasadas por estado, sus importes y su primera cita futura, por posición relativa.
  - Se regenera con el mismo indicador que en 001 (`-DactualizarDatosDemostracion=true`).
  - Las pruebas e2e de cancelación no gastan citas de la semilla: crean su propia cita en un hueco libre a más de 24 horas (`ApiPrueba.huecoLibre`) para un paciente de la semilla, y después la cancelan desde el portal. Así se pueden repetir sobre la misma semilla.
- **Alternativas**: añadir pacientes al catálogo para el portal, que cambia la semilla y obligaría a revisar las cifras de 001.

## R12. Trazabilidad con criterios no automatizables (Principio VI)

- **Decisión**:
  - SC-001 (10 s para localizar la próxima cita) y SC-002 (30 s para cancelar) son resultados de uso con pacientes reales. Se excluyen del script, igual que SC-004–SC-006 en 001.
  - `scripts/trazabilidad.sh` pasa a admitir exclusiones **por spec** (una entrada por número de spec en el propio script) en lugar de una lista fija.
  - El resto de FR, SC y escenarios llevan sus pruebas con el prefijo `[002]`.

## R13. Privacidad y registros (FR-003, FR-011, datos de salud)

- **Decisión**:
  - Ni los registros ni los mensajes de error incluyen nunca el email, el secreto del enlace ni su hash.
  - Las respuestas del portal no incluyen identificadores de otras fichas ni el teléfono.
  - La URL pública del enlace sale de la variable `URL_PUBLICA` (por defecto `http://localhost:${PUERTO_WEB:-8080}`), no de la cabecera `Host`, para que nadie pueda fabricar enlaces que apunten a otro dominio.

## Resumen de incógnitas resueltas

| Incógnita del contexto técnico | Resuelta en |
|---|---|
| Formato, almacenamiento y consumo del enlace | R1 |
| Envío de email, demo y pruebas sin correos reales | R2 |
| Respuesta neutra frente al límite de solicitudes | R3 |
| Sesión del paciente y separación del panel | R4 |
| Coincidencia de email sin distinguir mayúsculas | R5 |
| Cálculo del plazo de 24 h | R6 |
| Reutilizar la máquina de estados y la concurrencia | R7 |
| Aviso en recepción sin tiempo real | R8 |
| Pruebas de solape exigidas por el Principio III | R9 |
| Estructura del frontend sin enrutador | R10 |
| Cifras reproducibles por paciente | R11 |
| Trazabilidad de los SC de uso | R12 |
| Privacidad de los datos de salud | R13 |
