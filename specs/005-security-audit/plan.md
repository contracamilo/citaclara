# Plan de implementación: Auditoría de seguridad (Stripe y OWASP Top 10)

**Rama**: `005-security-audit` | **Fecha**: 27/11/2026 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación de la feature en `specs/005-security-audit/spec.md` (con las cuatro clarificaciones del 27/11/2026).

## Resumen

Auditar CitaClara contra el **OWASP Top 10:2025** y las prácticas de seguridad de **Stripe y PCI DSS**, entregar un informe reproducible con plan de mejoras y **corregir en esta feature** los hallazgos críticos y altos que no cambien specs ajenas (FR-020). Si la clave compartida por clínica resulta un riesgo alto, se sustituye por usuarios individuales (FR-021), lo que **exige antes la aprobación del propietario de la spec 001**.

Enfoque técnico, sin dependencias de ejecución nuevas:

1. **Auditar** con revisión de código, pruebas dinámicas escritas como tests (el test que reproduce el hallazgo es también su prueba de regresión) y análisis automático de dependencias y secretos.
2. **Informar** en `informe-auditoria.md` (validado por un test) y fijar los requisitos de cobros en `requisitos-pagos.md`.
3. **Corregir** lo que no toca otras specs: cabeceras y endurecimiento de nginx, configuración segura por entorno, registro de eventos de seguridad sin datos personales, límites de entrada y controles de CI.
4. **Corregir tras la puerta G1** lo que sí toca la spec 001: limitación de intentos de acceso y usuarios individuales.

Decisiones y alternativas en [research.md](research.md). El informe y los requisitos de pagos son resultado de la implementación, no de este plan: aquí solo se fijan su método y su formato.

## Contexto técnico

**Lenguaje/versión**: el del proyecto: Java 25 (backend), TypeScript 5 sobre Node 24 (frontend), SQL de PostgreSQL 18, configuración de nginx 1.28 y de GitHub Actions. No se añade ningún lenguaje.

**Dependencias principales**: ninguna nueva en tiempo de ejecución (`spring-security-crypto` ya aporta BCrypt). Herramientas de CI nuevas, justificadas abajo: OSV-Scanner, gitleaks y Dependabot.

**Almacenamiento**: PostgreSQL 18. Migraciones Flyway nuevas, solo SQL: `V2` (intentos de acceso, tras G1) y `V3` (usuarios, tras G1). Nada de lo anterior se toca.

**Pruebas**: JUnit 5 + Spring Boot Test + Testcontainers (paquete `es.citaclara.seguridad`); Vitest para los textos de interfaz nuevos; Playwright + axe para cabeceras, bloqueo y 360 px.

**Plataforma objetivo**: contenedores Linux (Docker Compose), navegadores modernos de portátil y móvil.

**Tipo de proyecto**: aplicación web existente (API + SPA tras nginx); esta feature añade documentación de auditoría y endurece las capas existentes.

**Objetivos de rendimiento**: los controles no degradan el uso de recepción: el acceso correcto y la agenda del día mantienen los tiempos de la spec 001 (SC-007 de la 001); un acceso correcto no incorpora más de 100 ms por el control de intentos.

**Restricciones**: no pruebas intrusivas contra datos reales ni terceros (FR-019); mensajes visibles en español de España sin jerga (FR-015); ninguna corrección cambia importes, fechas ni la garantía de solape (Principios II y III); no se modifican specs ajenas sin su propietario (FR-014).

**Escala/alcance**: 11 operaciones de API, 3 pantallas, 1 nginx, 1 Compose, 1 flujo de CI; 15 hallazgos candidatos (D9 de la investigación).

Todas las incógnitas están resueltas en [research.md](research.md) (D1–D18); no queda ningún «NEEDS CLARIFICATION».

## Comprobación de la constitución

*PUERTA: debe superarse antes de la Fase 0 y revisarse tras la Fase 1.*

| Principio / regla | Cumplimiento en este plan | Estado |
|---|---|---|
| **I. Spec primero** | Todo sale de `spec.md` con trazabilidad FR→fase (tabla más abajo). Lo que cambia la spec 001 (limitación de intentos, usuarios individuales, contrato público de clínica, datos del paciente) queda tras la puerta **G1**: se pide al propietario que la apruebe y actualice **antes** de tocar el código. La spec 005 queda registrada en `specs/MAPA.md`. | ✅ condicionado a G1 |
| **II. Los números no admiten creatividad** | Ninguna corrección toca importes ni fechas. Los requisitos de pagos fijan céntimos enteros, importe decidido por el servidor y conciliación al céntimo (RP-004, RP-005). Los plazos de bloqueo usan el `Clock` inyectado. | ✅ |
| **III. El solape es el fallo capital** | No se escribe en la agenda. Los usuarios individuales no cambian el camino de alta; el alta conserva `FOR UPDATE` y la restricción `cita_sin_solape`. La suite 001 de solape y concurrencia se ejecuta íntegra y debe seguir en verde tras cada fase (puerta de merge 3). | ✅ |
| **IV. Simplicidad y cero alcance fantasma** | Sin SDK de Stripe, sin Spring Security completo, sin Bucket4j, sin SIEM, sin SSO. Se descarta la anonimización automática y los endpoints RGPD de esta feature (D14) y se proponen como spec de seguimiento. Cada herramienta de CI se vincula a FR-016 (tabla de dependencias). | ✅ |
| **V. Demostrable con datos reproducibles** | La semilla sigue siendo determinista. Los usuarios de demostración (tras G1) se añaden a la semilla con valores fijos y se documentan en `datos-demostracion.md` **a través del propietario de la 001**; hasta entonces la semilla no cambia. La verificación `DatosDemostracionDocumentadosTest` debe seguir en verde. | ✅ condicionado a G1 |
| **VI. Los tests acompañan a la spec** | Convención `[005][FR-xxx][USn-ACm]`. Cada hallazgo es un test que falla antes y pasa después. El script de trazabilidad se adapta para leer esta spec (D15) y sale vacío. Ningún test se desactiva. | ✅ |
| **VII. Interfaz clara y moderna** | Los rechazos de seguridad usan mensajes redactados para la clínica (p. ej. «Has probado demasiadas veces. Espera 5 minutos e inténtalo de nuevo.») sin códigos ni jerga; axe y 360 px sobre la pantalla de acceso con bloqueo; el acceso con usuario no añade pasos a recepción. | ✅ |
| **VIII. Español de España** | Informe, requisitos, mensajes y artefactos en español de España; identificadores de código en español de dominio. | ✅ |
| **Trabajo en paralelo** | No se editan specs 002, 003 ni 004: sus requisitos de seguridad se entregan en `requisitos-pagos.md` y en la sección «Requisitos para otras features» del informe, y se avisa a sus propietarios. Solicitudes a la 001 registradas en el mapa. | ✅ |
| **Puertas de merge 1–6** | Cubiertas por las fases 1–5 y por [quickstart.md](quickstart.md). | ✅ |

**Revisión tras la Fase 1 (diseño)**: se mantiene todo en ✅. Único riesgo vivo: si el propietario de la 001 no aprueba a tiempo, H2/H3 permanecen abiertos con la solicitud registrada y SC-008 se cumple por su salvedad explícita, sin saltarse el Principio I.

**Herramientas nuevas y su justificación (Principio IV)**:

| Herramienta | Necesidad de la spec que la justifica |
|---|---|
| OSV-Scanner (CI) | FR-007 y FR-016: detectar dependencias con vulnerabilidades conocidas y bloquear el merge (SC-007). |
| gitleaks (CI, binario) | FR-006 y FR-016: detectar credenciales en el repositorio (SC-007). |
| Dependabot (configuración) | FR-007: mantener al día dependencias, imágenes y acciones, que deja de depender de la memoria del equipo. |

Ninguna se ejecuta en producción ni se añade al `pom.xml` ni al `package.json`.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/005-security-audit/
├── spec.md                      # Especificación (aprobada con clarificaciones)
├── plan.md                      # Este plan
├── research.md                  # Fase 0: decisiones D1–D18 y hallazgos candidatos
├── data-model.md                # Fase 1: entidades de auditoría y datos nuevos
├── quickstart.md                # Fase 1: cómo reproducir la auditoría y verificar
├── contracts/
│   ├── informe-auditoria.md     # Formato obligatorio del informe y de cada hallazgo
│   ├── api-acceso.yaml          # Cambios del API de acceso (tras G1)
│   └── cabeceras-seguridad.md   # Cabeceras y límites exigidos a nginx y al API
├── checklists/requirements.md
├── informe-auditoria.md         # Entregable (fase 1)
├── requisitos-pagos.md          # Entregable (fase 1): RP-001… y lista de Stripe/PCI
└── tasks.md                     # Fase 2 (/speckit-tasks; no lo crea este comando)
```

### Código fuente (raíz del repositorio)

```text
backend/
├── src/main/java/es/citaclara/
│   ├── seguridad/               # NUEVO: registro de eventos, identificador de correlación,
│   │                            #   filtros de límites de entrada, perfil «produccion» (fail-fast)
│   ├── acceso/                  # TRAS G1: limitación de intentos, usuarios individuales
│   ├── comun/errores/           # Sin datos personales en el registro (H8)
│   ├── paciente/                # Escape de comodines y longitud máxima de la búsqueda (H10)
│   └── semilla/                 # Negarse en perfil «produccion» (H1); usuarios (tras G1)
├── src/main/resources/
│   ├── application.yml          # Valores de producción sin credenciales por defecto
│   └── db/migration/            # V2 intentos de acceso, V3 usuarios (tras G1)
└── src/test/java/es/citaclara/seguridad/   # NUEVO: pruebas de la feature (D16)

frontend/
├── nginx.conf                   # Cabeceras, límites, X-Forwarded-For, server_tokens (H6, H7)
├── src/acceso/PantallaAcceso.tsx  # TRAS G1: usuario + clave, mensajes de bloqueo
└── tests/e2e/seguridad.spec.ts  # NUEVO: cabeceras, política de contenido, bloqueo, 360 px

docker-compose.yml               # Etiquetas «solo desarrollo» (H4)
docker-compose.produccion.yml    # NUEVO: ejemplo sin secretos por defecto (D12)
.github/
├── workflows/ci.yml             # OSV-Scanner, gitleaks, acciones fijadas por SHA (D6)
└── dependabot.yml               # NUEVO
scripts/
├── trazabilidad.sh              # Acepta ambas redacciones de encabezado y exclusiones de la spec (D15)
└── verificar-informe.sh         # NUEVO: envoltorio para ejecutar InformeAuditoriaTest en local
```

**Decisión de estructura**: el backend sigue organizado por capacidad de dominio ([001]); el código transversal de seguridad vive en su propio paquete `seguridad` y lo específico de acceso permanece en `acceso`, de modo que las features 002–004 pueden reutilizar el registro de eventos sin depender de `acceso`.

## Fases

Cada fase termina con la suite completa en verde, el script de trazabilidad vacío y las cifras de la semilla reproducibles.

### Fase 1 — Auditoría, informe y requisitos de pagos (bloquea el resto)

Cubre FR-001–FR-012, FR-017–FR-019 · US1, US2, US4 · SC-001, SC-003, SC-004, SC-005, SC-006.

1. Escribir `InformeAuditoriaTest` y el contrato [informe-auditoria.md](contracts/informe-auditoria.md); el test falla (informe aún vacío).
2. Revisar cada categoría A01–A10 con la lista de ficheros de la investigación (D9); por cada hallazgo confirmado, escribir antes el test que lo reproduce (sin corregirlo todavía).
3. Ejecutar OSV-Scanner y gitleaks sobre el repositorio y el historial; volcar resultados al informe.
4. Inventariar datos personales y de salud de pacientes con ubicación, acceso, conservación y protección (FR-012), incluidos registros, errores y copias.
5. Redactar `requisitos-pagos.md` con RP-001… y la lista de comprobación de Stripe/PCI con veredicto (FR-010, FR-011).
6. Completar `informe-auditoria.md`; el test pasa. **Puerta G0**: revisión del informe por el propietario de la feature.
7. Registrar en `specs/MAPA.md` la fila de la spec 005 y las solicitudes pendientes a la 001 (H2, H3, H11, H14) con su justificación; avisar a los propietarios de 002–004.

### Fase 2 — Correcciones que no tocan otras specs (FR-020, FR-015)

Cubre H1, H4–H10, H12, H13 si se confirman como críticos o altos; el resto pasa al plan de seguimiento · US3 · SC-005, SC-007, SC-008.

1. Cabeceras y endurecimiento de nginx ([cabeceras-seguridad.md](contracts/cabeceras-seguridad.md)) con pruebas Playwright por ruta.
2. Perfil `produccion` con arranque que falla ante valores por defecto; semilla que se niega en ese perfil; `docker-compose.produccion.yml`.
3. Paquete `seguridad`: registro de eventos sin datos personales e identificador de correlación; filtrado de excepciones (H8, H9).
4. Límites de entrada: longitud y escape de la búsqueda, tamaños, `limit_req` (H10).
5. CI: OSV-Scanner, gitleaks, Dependabot y acciones fijadas por SHA (US5).
6. Adaptar `scripts/trazabilidad.sh` (D15) y comprobar que sale vacío sobre las specs 001 y 005.
7. Cada corrección: el test del hallazgo pasa de rojo a verde; ningún test se desactiva.

### Fase 3 — Correcciones que cambian la spec 001 (tras la puerta G1)

Cubre H2, H3, H11 · FR-014, FR-021 · US3 · SC-006, SC-008. **No empieza sin la aprobación del propietario de la 001 y la actualización de su spec.**

1. Solicitar al propietario de la 001 los cambios (limitación de intentos en su FR-004, usuarios individuales en su FR-001/FR-002, contrato público de clínica) con el texto propuesto; esperar su aprobación y su actualización de spec.
2. `V2`: tabla de intentos de acceso y purga; servicio de bloqueo con `Clock`; respuesta `429` con mensaje comprensible; pruebas con reloj controlable y con intentos simultáneos.
3. `V3`: tabla de usuarios; sesión con `usuarioId`; contrato de [api-acceso.yaml](contracts/api-acceso.yaml); periodo de transición con ambas vías y retirada de la clave compartida; usuarios de demostración en la semilla y actualización de `datos-demostracion.md` por su propietario.
4. Pantalla de acceso con usuario y clave, textos y bloqueo sin jerga, axe y 360 px.
5. Si el propietario no aprueba, H2/H3 quedan abiertos con la solicitud registrada y se reflejan en SC-008.

### Fase 4 — Reauditoría y cierre

Cubre FR-018, FR-013, SC-008, SC-009.

1. Reejecutar el método con la misma versión del informe; comparar hallazgos abiertos y cerrados y anotar el resultado en el informe.
2. Comprobar a mano en 360 px y en portátil el flujo de recepción (acceso, búsqueda, alta de cita) sin pasos extra ni mensajes técnicos (SC-009).
3. Ejecutar la suite completa (`./mvnw verify`, `npm test`, `npm run e2e`), el script de trazabilidad y la verificación de la semilla.
4. Redactar el plan de seguimiento: hallazgos medios y bajos, y spec `006-derechos-datos` para retención y derechos RGPD, con propietarios propuestos.
5. **Mantenimiento**: actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión y con referencia a la spec (p. ej. «[005] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features.

## Trazabilidad de requisitos a fases

| Requisitos | Fase |
|---|---|
| FR-001–FR-012, FR-018, FR-019 | 1 |
| FR-013, FR-017 | 1 y 4 |
| FR-014 | 1 y 3 |
| FR-015 | 2, 3 y 4 |
| FR-016 | 2 |
| FR-020 | 2 |
| FR-021 | 3 |

## Seguimiento de complejidad

La comprobación de la constitución no tiene violaciones: no hay complejidad que justificar. El único elemento atípico (puerta G1, que condiciona fases a una aprobación ajena) es consecuencia directa del Principio I y de la decisión tomada en la clarificación 4.
