# Plan de implementación: Recordatorios de cita por email

**Rama**: `003-recordatorios-cita` | **Fecha**: 27/11/2026 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación `specs/003-recordatorios-cita/spec.md` (clarificada con Sara el 27/11/2026) sobre el stack ya existente de la feature 001.

## Resumen

Un proceso diario (09:00, hora peninsular) genera un email de recordatorio por cada cita reservada cuyo inicio cae en la ventana [ahora + 24 h, siguiente ejecución + 24 h), sin duplicados. El correo lleva los datos de la cita y un enlace de cancelación único y no adivinable. Abrir el enlace muestra una pantalla pública (sin clave de recepción) y cancelar exige confirmar; se puede cancelar hasta 24 h antes del inicio. Sin SMTP configurado, cada correo se guarda como `.eml` en `datos/salida-correo/`.

Enfoque técnico, en una línea por pieza (detalle y alternativas en [research.md](research.md)):

- **Sin duplicados**: tabla `recordatorio` con `UNIQUE (cita_id)`; cada cita se procesa en su propia transacción con `INSERT … ON CONFLICT DO NOTHING` y fichero de nombre determinista (idempotente ante caídas y ejecuciones simultáneas).
- **Enlace**: 256 bits aleatorios; en base de datos solo se guarda su huella SHA-256.
- **Cancelación del paciente**: dos operaciones públicas (`GET` consulta, `POST` cancela) fuera de la sesión de recepción, que reutilizan la máquina de estados y el bloqueo optimista de la cita (001).
- **Correo**: interfaz `EnviadorCorreo` con implementación a fichero (por defecto) y SMTP (si hay `CORREO_SMTP_HOST`).
- **Frontend**: una pantalla pública nueva en `/cancelar/{enlace}`, sin enrutador (decisión 001 D15).

## Contexto técnico

**Lenguaje/versión**: Java 25 (backend); TypeScript 5 sobre Node 24 (frontend), como la 001.

**Dependencias principales**: las de la 001 más **una nueva**: `spring-boot-starter-mail` (Jakarta Mail), justificada en la comprobación de la constitución (Principio IV) y en [research.md](research.md) D2.

**Almacenamiento**: PostgreSQL 18; migración Flyway `V3__recordatorios.sql` (tabla `recordatorio`, columnas `clinica.telefono` y `cita.cancelada_por`). Ficheros `.eml` en el directorio de salida de correo (volumen de Docker).

**Pruebas**: JUnit 5 + Testcontainers con PostgreSQL real, `RelojControlable` y directorio temporal para `.eml`; Vitest + Testing Library para la pantalla; Playwright + axe para el flujo completo a 360 px.

**Plataforma objetivo**: contenedores Linux (Docker Compose); la pantalla del enlace debe funcionar en el móvil del paciente.

**Tipo de proyecto**: aplicación web (backend API + SPA en nginx), como la 001; el proceso diario vive dentro del backend.

**Objetivos de rendimiento**: una ejecución con las ~100 citas diarias de una clínica pequeña termina en segundos; la pantalla del enlace responde < 1 s.

**Restricciones**: nunca más de un recordatorio por cita (restricción en base de datos); nunca se envía a una dirección real en modo simulado; horas siempre peninsulares y en 24 h; aislamiento por clínica; enlace inválido indistinguible de inexistente.

**Escala/alcance**: 2–5 profesionales por clínica; 2 operaciones públicas de API, 1 pantalla nueva, 1 proceso programado y 1 comando de ejecución manual.

Todas las incógnitas están resueltas en [research.md](research.md) (D1–D12); no queda ningún «NEEDS CLARIFICATION».

## Comprobación de la constitución

| Principio | Veredicto | Cómo se cumple |
|---|---|---|
| I. Spec primero | ✅ | La spec está clarificada; cualquier cambio de comportamiento empieza por ella. Las modificaciones que tocan comportamiento de la 001 se solicitan a su propietario (ver «Peticiones a la spec 001»). |
| II. Números y horas sin creatividad | ✅ | El correo no muestra importes (FR-011). Fechas `dd/MM/yyyy` y `HH:mm` de 24 h con `ZonaClinica`; la ventana se calcula en `Instant` con un `Clock` inyectado, de modo que el cambio de horario no desplaza, duplica ni omite citas (prueba con las dos fechas de cambio de 2027). |
| III. El solape es el fallo capital | ✅ | Esta feature no crea ni mueve citas: solo cancela, y una cancelación libera hueco (nunca ocupa). Aun así incluye pruebas: tras cancelar por el enlace se puede reservar el mismo hueco sin solape, y cancelaciones simultáneas (paciente ×2 y paciente + recepción) aplican solo la primera. La exclusion constraint `cita_sin_solape` sigue siendo la garantía. |
| IV. Simplicidad y cero alcance fantasma | ✅ con 1 dependencia | Ver «Seguimiento de complejidad». No se construye configuración de plantillas ni de plazos, ni estadísticas, ni SMS/WhatsApp (FR-022). |
| V. Datos reproducibles | ✅ | Se documenta el número de recordatorios por posición relativa respecto al día de carga en `datos-demostracion.md` (se crea al implementar), verificado por un test (mismo patrón que la 001). |
| VI. Tests con la spec | ✅ | Cada FR/SC/escenario tendrá test con nombre `[003][FR-xxx][USn-ACm]`; `scripts/trazabilidad.sh specs/003-recordatorios-cita` debe salir vacío (SC-007 se excluye por ser un resultado de negocio fuera del producto: requiere parametrizar la lista de exclusiones del script). |
| VII. Interfaz clara y moderna | ✅ | Pantalla pública móvil primero, sin jerga ni códigos, objetivos ≥ 44 px, axe en e2e. |
| VIII. Español de España | ✅ | Asunto, cuerpo del correo y pantalla en español de España; artefactos de la feature en español. |

**Puerta superada** antes de la Fase 0. **Revisada tras la Fase 1**: sin cambios; el diseño no añade violaciones.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/003-recordatorios-cita/
├── spec.md
├── plan.md                    # Este archivo
├── research.md                # Fase 0: decisiones D1–D12
├── data-model.md              # Fase 1: tabla y columnas nuevas, estados
├── quickstart.md              # Fase 1: validación de extremo a extremo
├── datos-demostracion.md      # Cifras citables (se crea al implementar; lo verifica un test)
├── contracts/
│   ├── api.yaml               # Operaciones públicas de cancelación
│   ├── correo.md              # Contrato del email y de los ficheros .eml
│   └── ui.md                  # Pantalla pública del enlace
├── checklists/requirements.md
└── tasks.md                   # Fase 2 (/speckit-tasks; NO lo crea /speckit-plan)
```

### Código fuente (raíz del repositorio)

```text
backend/
├── pom.xml                                        # + spring-boot-starter-mail
├── src/main/resources/
│   ├── application.yml                            # + citaclara.recordatorios.* y citaclara.correo.*
│   └── db/migration/V3__recordatorios.sql         # nuevo
├── src/main/java/es/citaclara/
│   ├── recordatorio/                              # nuevo paquete (convención 001: una capacidad = un paquete)
│   │   ├── Recordatorio.java, RepositorioRecordatorio.java
│   │   ├── ServicioRecordatorios.java             # ventana, selección y generación por cita
│   │   ├── ProgramadorRecordatorios.java          # @Scheduled 09:00 Europe/Madrid
│   │   ├── ComandoRecordatorios.java              # perfil «recordatorios»: una ejecución y salir
│   │   ├── EnlaceCancelacion.java                 # generación y huella del enlace
│   │   ├── ConstructorCorreoRecordatorio.java     # asunto y cuerpo en español
│   │   ├── ControladorCancelacionPaciente.java    # GET/POST públicos
│   │   ├── ServicioCancelacionPaciente.java       # plazo de 24 h y cancelación
│   │   └── CancelacionRespuesta.java
│   ├── correo/                                    # nuevo paquete
│   │   ├── EnviadorCorreo.java, CorreoSaliente.java
│   │   ├── EnviadorCorreoFichero.java             # modo simulado (.eml, escritura atómica)
│   │   └── EnviadorCorreoSmtp.java                # modo real (solo si hay CORREO_SMTP_HOST)
│   ├── acceso/ConfiguracionWeb.java               # excluir /api/cancelacion/** de la sesión (cambio mínimo)
│   ├── cita/Cita.java, CitaRespuesta.java         # + canceladaPor (petición a 001)
│   └── clinica/Clinica.java                       # + telefono (petición a 001)
└── src/test/java/es/citaclara/recordatorio/ …     # ver «Estrategia de pruebas»

frontend/
├── src/cancelacion/                               # PantallaCancelacion.tsx + cliente en api/
├── src/App.tsx                                    # rama previa: ruta /cancelar/{enlace} sin sesión
├── src/agenda/TarjetaCita.tsx                     # distintivo «Cancelada por el paciente» (FR-016)
└── tests/unit, tests/e2e                          # pruebas de la pantalla y del flujo completo

docker-compose.yml                                 # volumen ./datos/salida-correo en api; servicio «recordatorios»
.gitignore                                         # datos/salida-correo/*.eml
scripts/trazabilidad.sh                            # exclusiones por spec (SC-007)
```

**Decisión de estructura**: aplicación web con backend y frontend ya existentes; la feature añade los paquetes `recordatorio` y `correo` (el correo es una capacidad reutilizable por futuras features) y no crea módulos nuevos.

## Peticiones a la spec 001 (propietaria: feature 001-nucleo-agenda)

Constitución, «Trabajo en paralelo»: esta feature no edita la 001; solicita estos cambios a su propietario y no se fusiona hasta que estén aprobados.

**Estado (27/11/2026)**: implementadas en esta rama, **pendientes de aprobación del propietario de la 001** (tarea T006).

1. **Teléfono de la clínica**: `clinica.telefono` (nuevo dato de la clínica) y, en la semilla, un teléfono ficticio para Clínica Eleva. Es obligatorio para FR-009 y para la pantalla fuera de plazo. Cambia los datos de demostración (Principio V): revisar `datos-demostracion.md` de la 001.
2. **Origen de la cancelación**: `cita.cancelada_por` (`PACIENTE`/nulo) y campo `canceladaPor` en el esquema `Cita` de la API (FR-016). No cambia ninguna transición de estado.
3. **Cancelación sin sesión**: la 001 (FR-001) protege todo el API con la clave de la clínica. Se solicita registrar la excepción acotada de las dos operaciones públicas de `/api/cancelacion/{enlace}`, cuya autoridad es el enlace.

## Estrategia de pruebas

Todas con nombre `[003][FR-xxx][USn-ACm]` y contra PostgreSQL real (heredan de `PruebaIntegracionBase`):

- **Ventana y selección**: límites exactos (24 h incluida, 48 h excluida), estados no reservados, paciente sin email, dos clínicas, cita nueva que sustituye a una cancelada.
- **Sin duplicados**: segunda ejecución, ejecuciones concurrentes (varios hilos sobre el mismo conjunto de citas), fallo a mitad de proceso y relanzamiento, fallo de escritura del fichero (no queda registrado).
- **Cambio de horario**: citas alrededor del 28/03/2027 y del 31/10/2027; hora mostrada correcta y ninguna omitida ni duplicada.
- **Correo**: contenido (datos, 24 h, `dd/MM/yyyy`, teléfono, sin importe), codificación UTF-8 en el `.eml`, nombre de fichero determinista, saneado de cabeceras.
- **Cancelación**: abrir no cancela; confirmar sí; ya no activa; fuera de plazo (límite 24 h exactas ±1 s); enlace inválido y de otra clínica = 404 idéntico; doble confirmación simultánea; paciente + recepción a la vez (409 coherente); tras cancelar se reserva de nuevo el mismo hueco sin solape.
- **Datos de demostración**: `DatosDemostracionRecordatoriosTest` genera/verifica las cifras citables (`-DactualizarDatosDemostracion=true`).
- **Frontend**: Vitest de la pantalla (estados activa, cerrada, fuera de plazo, error); Playwright con la ejecución real del comando, lectura del `.eml`, cancelación a 360 px y axe.

## Seguimiento de complejidad

| Elemento | Por qué es necesario | Alternativa más simple rechazada porque |
|---|---|---|
| Dependencia `spring-boot-starter-mail` | FR-020/FR-021: escribir `.eml` correctos (UTF-8, asunto con tildes, cabeceras) y poder pasar a SMTP real solo por configuración. | Generar el `.eml` a mano: riesgo de correos mal codificados (tildes del español de España) y reescribir el envío real después. |
| Comando de ejecución manual (perfil `recordatorios`) | Demostración, pruebas e2e y operación: esperar a las 09:00 hace imposible validar el flujo; misma pauta que el comando de la semilla. | Hacer el cron configurable y esperar: lento y frágil en e2e. |
| Dos paquetes nuevos (`recordatorio`, `correo`) | El correo es una capacidad aparte con dos implementaciones y se reutilizará; mezclarlo con recordatorios acopla futuras features. | Un único paquete: obligaría a que otra feature dependa de «recordatorio» para enviar correo. |

## Fases de implementación

1. **Cimientos**: peticiones a la 001 aprobadas; migración V2; dependencia de correo; configuración; `EnviadorCorreo` con implementación a fichero.
2. **Generación (US1, P1)**: ventana, selección, recordatorio idempotente, contenido del correo, programador y comando; pruebas de ventana, duplicados y cambio de horario.
3. **Cancelación (US2, P2)**: enlace, operaciones públicas, plazo de 24 h, `canceladaPor`, pantalla pública y distintivo en la agenda; pruebas de concurrencia y e2e.
4. **Cambio de cita (US3, P3)**: pruebas de la cita que sustituye a otra y de enlaces antiguos; sin código nuevo si las fases 2–3 son correctas.
5. **Cierre**: Docker (volumen y servicio), README y `quickstart.md` validado, `datos-demostracion.md`, trazabilidad en vacío y suite completa en verde. **Último paso (mantenimiento): actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[003] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features.**
