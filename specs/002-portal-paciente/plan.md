# Plan de implementación: Portal del paciente

**Rama**: `002-portal-paciente` | **Fecha**: 27/11/2026 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación de la feature en `specs/002-portal-paciente/spec.md` (decisiones de Sara del 27/11/2026: acceso por enlace al email, plazo de 24 h y aviso activo en recepción), sobre el stack y las convenciones de la feature 001.

## Resumen

Esta feature crea un portal del paciente en `/{identificador}/mis-citas`, pensado para el móvil. El paciente entra con un **enlace de un solo uso** enviado al email de su ficha, ve sus citas próximas y pasadas, y **cancela** las suyas siempre que falten más de 24 horas. La recepción ve un **aviso** en el panel con las cancelaciones del portal pendientes de revisar.

Enfoque técnico:
- **Backend**: un paquete nuevo, `portal`, en el backend Spring Boot de 001, con una migración Flyway `V2` (dos tablas nuevas y dos índices o restricciones).
- **Enlace**: secreto aleatorio guardado como hash y consumido de forma atómica.
- **Sesión**: sesión propia de 30 minutos absolutos, separada de la del panel.
- **Cancelación**: reutiliza la máquina de estados y el bloqueo optimista de 001.
- **Correo**: envío SMTP asíncrono con Mailpit en el entorno de demostración.
- **Frontend**: pantallas nuevas en la misma SPA de React, sin enrutador.

Detalle y alternativas en [research.md](research.md).

## Contexto técnico

**Lenguaje/versión**: Java 25 (backend) y TypeScript 5 (frontend), igual que en 001.

**Dependencias principales**:
- Las de 001, sin cambios.
- Nueva: `spring-boot-starter-mail` (SMTP).
- Nuevo en el entorno: servicio Mailpit en `docker-compose.yml`, con versión fijada en la implementación. Solo sirve para demostración y pruebas e2e.

**Almacenamiento**: PostgreSQL 18. Migración `V2__portal_paciente.sql` con las tablas `solicitud_enlace_portal` y `cancelacion_portal`, el índice `paciente_email_portal` y la restricción `cita_id_clinica` ([data-model.md](data-model.md)).

**Pruebas**:
- JUnit 5 + Testcontainers, sobre `PruebaIntegracionBase`, con un `BuzonPrueba` en memoria que sustituye a `CorreoPortal`.
- Vitest + Testing Library.
- Playwright + axe; el enlace se lee del API de Mailpit.

**Plataforma objetivo**: la de 001. El portal se prioriza para el navegador del móvil (Safari iOS y Chrome Android, últimas dos versiones).

**Tipo de proyecto**: aplicación web (la misma de 001).

**Objetivos de rendimiento**:
- «Mis citas» visible en menos de 2 s tras abrir el enlace, con margen para SC-001 (10 s).
- Cancelación confirmada en menos de 1 s, con margen para SC-002 (30 s).

**Restricciones**:
- Respuesta indistinguible para emails inexistentes (FR-003).
- 5 solicitudes por email y hora (FR-004).
- Enlace de 10 min y un solo uso; sesión de 30 min absolutos.
- Plazo de 24 h reales.
- Datos de salud: nunca se registra el email ni el secreto.
- 360 px y WCAG 2.2 AA.

**Escala/alcance**: 3 pantallas de portal, 1 aviso y 1 diálogo en el panel, 7 operaciones de API nuevas y 2 tablas.

Todas las incógnitas quedan resueltas en [research.md](research.md) (R1–R13). No queda ningún «NEEDS CLARIFICATION».

## Comprobación de la constitución

*PUERTA: debe superarse antes de la Fase 0 y revisarse tras la Fase 1.*

| Principio / regla | Cumplimiento en este plan | Estado |
|---|---|---|
| **I. Spec primero** | Todo lo planificado sale de `spec.md` y los contratos citan su FR o escenario. La spec está registrada en `specs/MAPA.md` con un único propietario. Durante el plan se corrigió en la spec el texto del aviso neutro de US1-AC1 («Si no te llega, llama a la clínica»), para alinearlo con su caso límite; lo hizo esta feature como propietaria. **No se modifica ninguna spec de 001**: el aviso de recepción es comportamiento propio de 002 (Supuestos de la spec) y su incorporación a la cabecera del panel se coordina con el propietario de 001 antes de implementarla (fase 6). | ✅ |
| **II. Los números no admiten creatividad** | Importes en céntimos con el formateador único (`50,00 €`); fechas dd/mm/aaaa y 24 h, siempre calculadas en el servidor con `ZonaClinica` y `Clock`; plazo de 24 h sobre `Instant`, probado en los días de cambio de hora (R6). | ✅ |
| **III. El solape es el fallo capital** | La feature escribe en la agenda (cancelar), pero solo hacia un estado libre, y `cita_sin_solape` sigue cubriendo todo. Incluye pruebas de alta en el hueco liberado, de 10 altas simultáneas tras cancelar desde el portal y de cancelación y alta en paralelo (R9, puerta 3). | ✅ |
| **IV. Simplicidad y cero alcance fantasma** | Una dependencia nueva justificada (abajo); sin enrutador, sin JWT, sin sesión propia en BD, sin tiempo real. Fuera de alcance, y por tanto no se construye: reservar o mover, pagos, recordatorios, lista de espera, motivo de cancelación, SMS y edición de ficha. | ✅ |
| **V. Demostrable con datos reproducibles** | La semilla no cambia. Las cifras por paciente se documentan en `datos-demostracion.md` de 002 y las verifica un test (R11). Las pruebas e2e crean sus propias citas para poder repetirse. | ✅ |
| **VI. Los tests acompañan a la spec** | Prefijo `[002][FR-xxx][USn-ACm]` en todas las pruebas. `scripts/trazabilidad.sh` admite exclusiones por spec; en 002 solo se excluyen SC-001 y SC-002, que se miden con usuarios reales (R12). | ✅ |
| **VII. Interfaz clara y moderna** | Contrato de textos exactos ([contracts/ui.md](contracts/ui.md)); mensajes del API listos para mostrar; estados con color, icono y texto; axe y 360 px en Playwright. | ✅ |
| **VIII. Español de España** | Portal, email y panel en español de España; tuteo al paciente; artefactos en español. | ✅ |
| **Trabajo en paralelo** | Paquete backend propio (`portal`) y carpetas de frontend propias (`portal/`, `cancelaciones/`). Los cambios en código de 001 son mínimos y están enumerados en «Puntos de contacto con 001». Las convenciones nuevas se registran en `CLAUDE.md` en el último paso. | ✅ |
| **Puertas de merge 1–6** | Cubiertas por las fases 1–7 y [quickstart.md](quickstart.md). | ✅ |

**Dependencias externas nuevas y su justificación (Principio IV)**:

| Dependencia | Necesidad de la spec que la justifica |
|---|---|
| `spring-boot-starter-mail` | Enviar el enlace de acceso al email de la ficha (FR-001, FR-007). |
| Mailpit (solo en `docker-compose.yml`, no es dependencia del producto) | Demostrar y probar el portal sin enviar correos reales a las direcciones ficticias (FR-031). |

**Reevaluación tras la Fase 1**: el modelo de datos, los contratos y la guía rápida mantienen todos los puntos anteriores. Las únicas modificaciones al esquema de 001 son aditivas (un índice y una restricción `UNIQUE` redundante con la PK). Sin infracciones.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/002-portal-paciente/
├── spec.md
├── plan.md                  # Este fichero
├── research.md              # Fase 0 (R1–R13)
├── data-model.md            # Fase 1
├── quickstart.md            # Fase 1
├── contracts/
│   ├── api.yaml             # Fase 1 — OpenAPI 3.1 del portal y del aviso
│   └── ui.md                # Fase 1 — pantallas, textos y email
├── datos-demostracion.md    # Implementación — citas por paciente de la semilla (FR-030)
├── checklists/
└── tasks.md                 # Fase 2 (/speckit-tasks)
```

### Código fuente (raíz del repositorio)

```text
docker-compose.yml                         # + servicio mailpit; api recibe SMTP_* y URL_PUBLICA
scripts/trazabilidad.sh                    # exclusiones por spec (R12)

backend/
├── pom.xml                                # + spring-boot-starter-mail
└── src/
    ├── main/
    │   ├── java/es/citaclara/
    │   │   ├── portal/                    # NUEVO
    │   │   │   ├── acceso/                # SolicitudEnlacePortal, RepositorioSolicitudes, ServicioEnlaces (R1, R3),
    │   │   │   │                          # SesionPortal, InterceptorPortal (R4), ControladorAccesoPortal
    │   │   │   ├── correo/                # CorreoPortal (puerto), CorreoPortalSmtp, envío tras el commit (R2)
    │   │   │   ├── citas/                 # ServicioMisCitas, CitaPortal, ReglaCancelacionPortal (R6),
    │   │   │   │                          # ServicioCancelacionPortal (R7), ControladorPortal
    │   │   │   └── cancelaciones/         # CancelacionPortal, RepositorioCancelaciones, ControladorCancelacionesPortal (R8)
    │   │   ├── acceso/ConfiguracionWeb.java   # (001) excluir /api/portal/** del panel; registrar InterceptorPortal
    │   │   └── cita/ServicioEstadoCita.java   # (001) + cancelarDesdePortal(…) reutilizando la máquina de estados (R7)
    │   └── resources/
    │       ├── application.yml            # + spring.mail.* y citaclara.url-publica desde variables de entorno
    │       └── db/migration/V2__portal_paciente.sql
    └── test/java/es/citaclara/
        ├── portal/                        # acceso, límite, sesión, mis citas, plazo, cancelación, concurrencia, aviso, aislamiento
        ├── portal/DatosPortalDocumentadosTest.java
        └── soporte/                       # + BuzonPrueba (sustituye a CorreoPortal); ClienteApi con helpers del portal

frontend/
├── src/
│   ├── App.tsx                            # (001) segundo segmento «mis-citas» → AppPortal
│   ├── api/                               # + tipos y llamadas del portal y del aviso
│   ├── portal/                            # NUEVO: AppPortal, PantallaEntradaPortal, PantallaMisCitas,
│   │                                      # TarjetaCitaPortal, DistintivoEstadoPortal, DialogoCancelarCita
│   ├── cancelaciones/                     # NUEVO: AvisoCancelaciones, DialogoCancelacionesPortal
│   └── agenda/PantallaAgenda.tsx          # (001) montar AvisoCancelaciones en la cabecera
└── tests/
    ├── unit/                              # portal y aviso
    └── e2e/                               # portal-acceso, portal-citas, portal-cancelar, aviso-cancelaciones, portal-360
                                           # + utilidad para leer el último enlace de Mailpit
```

**Decisión de estructura**: se sigue la organización por capacidad de dominio de 001. Todo lo nuevo vive en `portal` (backend) y en `portal/` y `cancelaciones/` (frontend), para no chocar con features paralelas.

**Puntos de contacto con código de 001**: son cinco y ninguno cambia el comportamiento de 001.
1. `ConfiguracionWeb`: excluir `/api/portal/**` del interceptor del panel y registrar `InterceptorPortal`.
2. `ServicioEstadoCita`: añadir una operación pública que reutiliza la misma transición.
3. `App.tsx`: despachar `mis-citas`.
4. `PantallaAgenda`: montar el aviso en la cabecera.
5. `ServicioAcceso.iniciarSesion`: ya invalida la sesión previa, así que no cambia.

## Fases de implementación (guía para `/speckit-tasks`)

Cada fase termina con sus pruebas en verde y la referencia `[002][…]` en cada prueba.

1. **Andamiaje**:
   - Migración `V2` completa.
   - `spring-boot-starter-mail` y configuración `spring.mail.*` y `citaclara.url-publica` por variables de entorno.
   - Servicio `mailpit` en Compose: SMTP interno y bandeja en `127.0.0.1:${PUERTO_CORREO:-8025}`.
   - `BuzonPrueba` en la base de pruebas.
   - Exclusiones por spec en `scripts/trazabilidad.sh`.
   - Job e2e de CI con `URL_CORREO`.
2. **Fundamentos** (bloquean las historias):
   - `SesionPortal` e `InterceptorPortal` (30 min absolutos, `Cache-Control: no-store`) y exclusión del panel (FR-005, FR-029).
   - Errores nuevos en `Mensajes` con los textos de [api.yaml](contracts/api.yaml).
   - Cliente API del frontend para el portal y despacho de `mis-citas` en `App.tsx`.
3. **US1 — Acceso por enlace (P1)**:
   - Pedir enlace con respuesta neutra y límite que cuenta todas las solicitudes (R3).
   - Envío asíncrono tras el commit (R2).
   - Consumo atómico y caducidad (R1) y salida.
   - Pantallas de entrada y apertura del enlace.
   - Pruebas: indistinguibilidad (SC-007), 6.ª solicitud, enlace usado o caducado (SC-009), dos aperturas simultáneas del mismo enlace, enlace de otra clínica y email con mayúsculas o espacios.
4. **US2 — Mis citas (P1)**:
   - `ServicioMisCitas` con la tabla de vista de [data-model.md](data-model.md) calculada en servidor.
   - Varias fichas con el mismo email.
   - `PantallaMisCitas` con tarjetas y distintivos.
   - Pruebas de aislamiento entre pacientes y entre clínicas (SC-004) y ejemplos de la semilla (Teresa Méndez, Rocío Garrido).
5. **US3 — Cancelar (P1)**:
   - `ReglaCancelacionPortal` y `cancelarDesdePortal` con registro en `cancelacion_portal`, idempotencia y conflictos (R6, R7).
   - Diálogo de cancelación.
   - Pruebas del límite exacto de 24 h, del cambio de hora y del fin de semana; 10 cancelaciones simultáneas entre portal y recepción (SC-005).
   - **Pruebas de solape del Principio III** (R9, puerta 3).
6. **US4 — Aviso en recepción (P2)**:
   - Coordinar con el propietario de la spec 001 la incorporación del aviso a la cabecera del panel (constitución, «Trabajo en paralelo»).
   - Endpoints de lista y revisión idempotente (R8).
   - `AvisoCancelaciones` y `DialogoCancelacionesPortal`.
   - Pruebas: contador, orden, «Hueco ya pasado», revisión doble y simultánea, cancelaciones de recepción excluidas, aislamiento entre clínicas.
7. **Pulido y cierre**:
   - `DatosPortalDocumentadosTest` y `datos-demostracion.md` de 002 (FR-030, puerta 5).
   - Pruebas e2e de [quickstart.md](quickstart.md) en portátil y a 360 px con axe (SC-006).
   - Revisión de textos frente a [contracts/ui.md](contracts/ui.md).
   - Revisión de registros: ningún email ni secreto (R13).
   - `scripts/trazabilidad.sh specs/002-portal-paciente` vacío y suite completa en verde.
   - Actualizar `README.md` (Mailpit, `URL_PUBLICA`, `SMTP_*`) y el estado en `specs/MAPA.md`.
   - **Último paso — mantenimiento**: actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[002] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features. Candidatas:
     - La sesión del portal es independiente de la del panel y ambas son mutuamente excluyentes en un navegador.
     - Los secretos de un solo uso se guardan como hash y se consumen con `UPDATE … RETURNING`.
     - Las respuestas que no deben revelar existencia cuentan los límites también para los inexistentes.
     - Todo correo sale por un puerto propio, asíncrono tras el commit, con Mailpit en local y e2e.
     - La información de origen de una cita vive en tablas propias de la feature, no en columnas de `cita`.
     - Exclusiones de trazabilidad por spec.

## Seguimiento de complejidad

Sin infracciones de la constitución que justificar. Notas de complejidad aceptada:

| Elemento | Por qué es necesario | Alternativa más simple descartada porque |
|---|---|---|
| Registrar también las solicitudes de emails inexistentes | Sin ello, el límite de FR-004 revelaría qué emails existen y se incumpliría FR-003. | Limitar solo los emails conocidos permite averiguarlos a la 6.ª solicitud. |
| Envío de email asíncrono tras el commit | Evita que el tiempo de respuesta delate si el email existe (FR-003) y que un SMTP caído deshaga la solicitud. | El envío síncrono es más simple, pero introduce un canal temporal. |
| Consumo del enlace por POST desde el fragmento | Los escáneres de correo visitan los enlaces con GET y los gastarían antes que el paciente. | Consumir con GET directo deja al paciente con «enlace no válido» en clientes de correo corporativos. |
