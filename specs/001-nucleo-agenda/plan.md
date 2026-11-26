# Plan de implementación: Núcleo de agenda de CitaClara

**Rama**: `001-nucleo-agenda` | **Fecha**: 26/11/2026 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación de la feature en `specs/001-nucleo-agenda/spec.md` y stack propuesto por el usuario: «java-springboot (JPA-hibernate) / react / postgreSQL / docker con una apariencia moderna 2026».

## Resumen

Primera feature de CitaClara: panel de recepción protegido por la clave de la clínica para consultar la agenda del día de un profesional, dar de alta citas sin solapes ni en el pasado, marcar su resultado y cargar datos de demostración deterministas de Clínica Eleva.

Enfoque técnico: backend **Spring Boot 4 (Java 25, JPA/Hibernate 7)** sobre **PostgreSQL 18**, donde la regla capital RN1 se garantiza con una ***exclusion constraint*** (`btree_gist` + `tstzrange`) que cubre cualquier camino de escritura y cualquier concurrencia; frontend **React 19 + TypeScript + Vite + Tailwind CSS v4** con una interfaz móvil primero, línea de tiempo vertical, modo claro/oscuro y tokens OKLCH; todo orquestado con **Docker Compose** (db, api, web con nginx y un servicio `semilla`). Detalle y alternativas en [research.md](research.md).

## Contexto técnico

**Lenguaje/versión**: Java 25 (LTS) en el backend; TypeScript 5 sobre Node 24 LTS (solo construcción) en el frontend.

**Dependencias principales**:
- Backend: Spring Boot 4 (`web`, `data-jpa`, `validation`, `actuator`), Hibernate ORM 7, Flyway, driver JDBC de PostgreSQL, `spring-security-crypto` (solo BCrypt).
- Frontend: React 19, Vite, Tailwind CSS v4, `@fontsource-variable/inter`, `lucide-react`.

**Almacenamiento**: PostgreSQL 18 con extensiones `btree_gist` y `unaccent`; esquema gestionado con migraciones Flyway en SQL; Hibernate en modo `validate`.

**Pruebas**: JUnit 5 + AssertJ + Spring Boot Test + Testcontainers (PostgreSQL real); Vitest + Testing Library; Playwright + `@axe-core/playwright` para extremo a extremo, accesibilidad y 360 px.

**Plataforma objetivo**: contenedores Linux (Docker Compose); navegadores modernos de portátil y móvil (últimas dos versiones de Chrome, Safari, Firefox y Edge).

**Tipo de proyecto**: aplicación web (backend API + frontend SPA servido por nginx en el mismo origen).

**Objetivos de rendimiento**: agenda del día visible en < 2 s (SC-007); 10 altas simultáneas resueltas sin solape en el 100 % de las repeticiones (SC-002).

**Restricciones**: cero solapes (Principio III); importes exactos al céntimo (céntimos enteros); hora peninsular española con cambios de horario; interfaz íntegramente en español de España; sin desplazamiento horizontal a 360 px; WCAG 2.2 AA.

**Escala/alcance**: clínicas de 2–5 profesionales; semilla con 1 clínica, 3 profesionales, 4 servicios, 40 pacientes y del orden de cientos de citas; 3 pantallas (acceso, agenda, alta de cita) y 8 operaciones de API.

Todas las incógnitas quedan resueltas en [research.md](research.md) (D1–D16); no queda ningún «NEEDS CLARIFICATION».

## Comprobación de la constitución

*PUERTA: debe superarse antes de la Fase 0 y revisarse tras la Fase 1.*

| Principio / regla | Cumplimiento en este plan | Estado |
|---|---|---|
| **I. Spec primero** | Todo el comportamiento planificado sale de `spec.md` (cada endpoint y pantalla cita su FR/AC en los contratos). La spec está registrada en `specs/MAPA.md` con propietario único. La lectura «días laborables» de la semilla (research D13) se incorporó a la spec por su propietario mediante `/speckit-clarify`. | ✅ |
| **II. Los números no admiten creatividad** | Céntimos enteros y un único formateador `es-ES` (D5); importe copiado al reservar (FR-017); `timestamptz` + `Europe/Madrid` + `Clock` inyectado; pruebas en los días de cambio de hora (D4). | ✅ |
| **III. El solape es el fallo capital** | *Exclusion constraint* en BD que cubre alta, semilla y cualquier camino futuro (D2); pruebas de solape parcial, contención, coincidencia, contigüidad, inserción directa y 10 altas simultáneas repetidas. | ✅ |
| **IV. Simplicidad y cero alcance fantasma** | Sin Spring Security completo, sin enrutador, sin gestor de estado, sin librería de componentes. Cada dependencia está justificada abajo. Fuera de alcance de la spec (acceso de pacientes, recordatorios, analítica, pagos, usuarios, edición de catálogo y fichas) no se construye. | ✅ |
| **V. Demostrable con datos reproducibles** | Semilla con `java.util.Random` de semilla fija, cuotas exactas y posición relativa por días laborables; recuentos documentados en `datos-demostracion.md` y verificados por test (D13). | ✅ |
| **VI. Los tests acompañan a la spec** | Convención `[001][FR-xxx][USn-ACm]` en el nombre de cada test y script de trazabilidad en CI (D14). Suite completa en verde como condición de merge. | ✅ |
| **VII. Interfaz clara y moderna** | Contrato de interfaz con textos exactos ([contracts/ui.md](contracts/ui.md)); mensajes del API ya redactados para la clínica; estados con color + icono + texto; axe + 360 px en Playwright (D15). | ✅ |
| **VIII. Español de España** | Textos de interfaz, mensajes del API y artefactos en español de España; el código usa nombres de dominio en español. | ✅ |
| **Trabajo en paralelo** | La feature solo toca su spec. Convenciones transversales nuevas (céntimos, zona, trazabilidad, errores) se registrarán en `CLAUDE.md` para las features paralelas (último paso del plan). | ✅ |
| **Puertas de merge 1–6** | Cubiertas por las fases 3–7 y por la guía [quickstart.md](quickstart.md). | ✅ |

**Dependencias externas y su justificación (Principio IV)**:

| Dependencia | Necesidad de la spec que la justifica |
|---|---|
| Spring Boot (web, data-jpa, validation) + Hibernate | Stack elegido por el usuario; API (FR-001–FR-025) y persistencia. |
| Spring Boot Actuator | *Health checks* de Docker Compose (arranque ordenado del entorno de demostración, US4). |
| Flyway | La garantía RN1 (FR-012/FR-013) exige SQL que JPA no genera; esquema reproducible (Principio V). |
| `spring-security-crypto` | Guardar la clave de panel con hash (FR-001) sin el framework de seguridad completo. |
| PostgreSQL `btree_gist` | *Exclusion constraint* de RN1 (FR-012, FR-013). |
| PostgreSQL `unaccent` | Buscar «Lucia» y encontrar «Lucía» (FR-018). |
| React + Vite + TypeScript | Stack elegido por el usuario; interfaz de recepción (FR-005–FR-028). |
| Tailwind CSS v4 | Diseño responsive a 360 px (SC-009) y tokens de contraste en claro/oscuro (FR-028) con un solo sistema. |
| `@fontsource-variable/inter` | Tipografía legible con cifras tabulares para horas e importes (FR-026, FR-028), autoalojada. |
| `lucide-react` | Iconos que acompañan a los estados para no depender solo del color (FR-006, FR-028). |
| Testcontainers | Probar la *exclusion constraint* y la concurrencia sobre PostgreSQL real (SC-001, SC-002). |
| Playwright + `@axe-core/playwright` | Pruebas de aceptación, 360 px (SC-009) y accesibilidad (FR-028). |
| Vitest + Testing Library | Pruebas de formateadores (euros, fechas) y componentes (FR-017, FR-026). |

**Reevaluación tras la Fase 1**: el modelo de datos, los contratos y la guía rápida mantienen todos los puntos anteriores. La lectura de «semanas» como días laborables (D13) ya está aclarada en la spec. Sin infracciones.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/001-nucleo-agenda/
├── spec.md
├── plan.md                  # Este fichero
├── research.md              # Fase 0
├── data-model.md            # Fase 1
├── quickstart.md            # Fase 1
├── contracts/
│   ├── api.yaml             # Fase 1 — OpenAPI 3.1 del panel de recepción
│   └── ui.md                # Fase 1 — pantallas, textos y comprobaciones de interfaz
├── datos-demostracion.md    # Implementación — recuentos exactos de la semilla (FR-033)
├── checklists/
└── tasks.md                 # Fase 2 (/speckit-tasks; no lo crea este comando)
```

### Código fuente (raíz del repositorio)

```text
docker-compose.yml                  # db, api, web; semilla (perfil «herramientas»)
scripts/
└── trazabilidad.sh                 # FR/AC de la spec sin test referenciado

backend/
├── pom.xml
├── Dockerfile                      # multietapa: Maven + JDK 25 → JRE 25
└── src/
    ├── main/
    │   ├── java/es/citaclara/
    │   │   ├── CitaClaraApplication.java
    │   │   ├── comun/              # ZonaClinica, configuración de Clock, errores (ProblemDetail + códigos), validación de teléfono
    │   │   ├── acceso/             # sesión con clave, interceptor de caducidad absoluta 8 h, clínica de la sesión
    │   │   ├── clinica/            # Clinica, HorarioAtencion, Profesional, Servicio (entidades + repositorios) y contexto
    │   │   ├── paciente/           # Paciente, búsqueda, alta
    │   │   ├── cita/               # Cita, EstadoCita, reglas de alta (RN1/RN2/horario), máquina de estados
    │   │   ├── agenda/             # agenda del día: citas + huecos libres + acciones
    │   │   └── semilla/            # generador determinista y comando de carga
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/       # V1__esquema_inicial.sql (incluye la exclusion constraint)
    └── test/java/es/citaclara/
        ├── cita/                   # reglas, solapes, concurrencia, estados
        ├── agenda/                 # cálculo de huecos y cambios de hora
        ├── acceso/                 # clave, caducidad, aislamiento entre clínicas
        ├── paciente/
        ├── semilla/                # determinismo, proporciones, recuentos
        └── soporte/                # base Testcontainers, reloj controlable

frontend/
├── package.json
├── vite.config.ts
├── Dockerfile                      # Vite build → nginx
├── nginx.conf                      # estáticos + proxy /api → api:8080
├── src/
│   ├── main.tsx
│   ├── App.tsx                     # acceso ↔ agenda según sesión; parámetros en la query string
│   ├── estilos.css                 # Tailwind v4 + tokens @theme (OKLCH, claro/oscuro)
│   ├── api/                        # cliente fetch, tipos del contrato, traducción de errores
│   ├── formato/                    # euros, fechas y horas es-ES (único sitio)
│   ├── acceso/                     # PantallaAcceso
│   ├── agenda/                     # PantallaAgenda, SelectorProfesional, NavegadorDia, LineaTiempo, TarjetaCita, HuecoLibre, AccionesCita
│   ├── citas/                      # DialogoAltaCita, BuscadorPaciente, FormularioPacienteNuevo
│   └── ui/                         # Boton, Dialogo, Aviso, Distintivo de estado
└── tests/
    ├── unit/                       # Vitest
    └── e2e/                        # Playwright (portátil + 360 px + axe)
```

**Decisión de estructura**: aplicación web con dos proyectos hermanos (`backend/`, `frontend/`) y orquestación en la raíz. El backend se organiza **por capacidad de dominio** (acceso, clínica, paciente, cita, agenda, semilla), no por capas técnicas, para que futuras features paralelas añadan paquetes propios con mínimo conflicto. El frontend sigue la misma división por pantallas/capacidades.

## Fases de implementación (guía para `/speckit-tasks`)

Cada fase termina con sus tests en verde y con la referencia `[001][…]` en cada test.

1. **Andamiaje**: `docker-compose.yml`, proyectos `backend/` y `frontend/` con versiones fijadas, Dockerfiles, nginx, *health checks*, migración `V1` completa (tablas, `CHECK`, índice único de teléfono, índice `unaccent`, *exclusion constraint*), base de tests con Testcontainers, script de trazabilidad.
2. **Fundamentos** (bloquea las historias): `ZonaClinica` + `Clock`, formato de errores con mensajes en español, acceso con clave + sesión con caducidad absoluta de 8 h + cierre (FR-001–FR-004), aislamiento por clínica (FR-003), endpoint de contexto, formateadores `es-ES` del frontend, tokens de diseño y componentes base de UI.
3. **US1 — Agenda del día (P1)**: cálculo de huecos y acciones en el servidor, `GET /api/agenda`, pantalla de acceso y pantalla de agenda (selector, navegación, línea de tiempo), pruebas de cambio de hora y de 360 px.
4. **US2 — Alta de cita (P1)**: reglas de alta en el orden de [data-model.md](data-model.md), paciente nuevo atómico, búsqueda de pacientes, traducción de la violación de la restricción a `HUECO_RECIEN_OCUPADO`, diálogo de alta que conserva los datos; **pruebas de solape y de 10 altas simultáneas** (Principio III, puerta 3).
5. **US3 — Resultado de la cita (P2)**: máquina de estados con `@Version`, endpoint de cambio de estado, acciones en tarjeta con confirmación de cancelación, pruebas de concurrencia en estados.
6. **US4 — Datos de demostración (P2)**: generador determinista por días laborables, cuotas exactas, servicio `semilla` de Compose, pruebas de determinismo (cargas en distintos días de la semana) y proporciones; generar `datos-demostracion.md` con los recuentos exactos y el test que lo verifica (FR-033, puerta 5). Puede desarrollarse en paralelo con las fases 3–5 tras la fase 2.
7. **Pulido y cierre**:
   - Pruebas extremo a extremo de [quickstart.md](quickstart.md) en Playwright (portátil y 360 px) con axe.
   - Revisión de textos visibles frente a [contracts/ui.md](contracts/ui.md) (sin jerga ni códigos).
   - Ejecutar `scripts/trazabilidad.sh` y la suite completa; actualizar `specs/MAPA.md` (estado de la spec).
   - **Último paso — mantenimiento**: actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[001] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features (candidatas: RN1 mediante *exclusion constraint*; importes en céntimos enteros y formateador único; `timestamptz` + `Europe/Madrid` + `Clock` inyectado; API con fecha/hora locales; errores Problem Details con `codigo` + `mensaje` en español; convención de trazabilidad de tests; aislamiento por `clinica_id`; semilla por días laborables relativos).

## Seguimiento de complejidad

Sin infracciones de la constitución que justificar. Notas de complejidad aceptada:

| Elemento | Por qué es necesario | Alternativa más simple descartada porque |
|---|---|---|
| *Exclusion constraint* con `btree_gist` | Única garantía de RN1 válida para cualquier camino de escritura y concurrencia (Principio III). | La comprobación solo en Java falla con altas simultáneas (SC-002). |
| Testcontainers en lugar de BD en memoria | La garantía principal solo existe en PostgreSQL. | H2 no admite *exclusion constraints* ni `unaccent`. |
| Caducidad absoluta propia sobre `HttpSession` | FR-002 exige 8 h desde el acceso, no por inactividad. | La caducidad estándar del contenedor es por inactividad. |
