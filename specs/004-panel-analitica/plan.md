# Plan de implementación: Panel de analítica

**Rama**: `004-panel-analitica` | **Fecha**: 27/11/2026 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación de la feature en `specs/004-panel-analitica/spec.md` (con sus aclaraciones del 2026-11-27) y el stack ya fijado por la spec 001 (Spring Boot 4 / PostgreSQL / React 19 / Docker).

## Resumen

Página de analítica de **solo lectura** dentro del panel: ocupación y tasa de no asistencia por profesional de la última semana completa, ingresos por servicio (y los ingresos no obtenidos por no asistencias) de las últimas 8 semanas y evolución semana a semana.

Enfoque técnico:

- **Backend**: un único endpoint `GET /api/analitica` en un paquete nuevo `analitica`. Una consulta de solo lectura trae las citas cerradas de la clínica de las últimas 8 semanas naturales y una **calculadora pura** (sin base de datos ni reloj, al estilo de `GeneradorSemilla`) las agrega en Java con `ZonaClinica`. Los porcentajes se devuelven como recuento exacto (`parte`/`total`) más décimas enteras redondeadas una sola vez en el servidor; los importes, en céntimos enteros.
- **Frontend**: una vista nueva en `/{identificador}/analitica`, con gráficos propios en HTML/CSS/SVG (barras con el valor visible y una tabla accesible equivalente), sin librería de gráficos. Enlace «Analítica» / «Agenda» entre las dos vistas.
- **Sin escritura de ningún tipo**: ni migraciones, ni tablas, ni caché. El repositorio de la feature extiende `Repository` (no `JpaRepository`), así que no expone `save` ni `delete`; la transacción es `readOnly`.

Detalle y alternativas en [research.md](research.md).

## Contexto técnico

**Lenguaje/versión**: Java 25 (backend) y TypeScript 5 sobre Node 24 (frontend), como en la 001.

**Dependencias principales**: ninguna nueva. Backend: Spring Boot 4 (`webmvc`, `data-jpa`), el `Clock` inyectado y `ZonaClinica` existentes. Frontend: React 19, Tailwind v4 (tokens OKLCH existentes), `lucide-react` para iconos.

**Almacenamiento**: PostgreSQL 18 existente. **No hay migración Flyway**: se leen `cita`, `profesional`, `servicio` y `horario_atencion` tal como están (FR-003).

**Pruebas**: JUnit 5 + Testcontainers (PostgreSQL real) heredando de `PruebaIntegracionBase`; Vitest + Testing Library; Playwright + axe y 360 px. Nombres de test con `[004][FR-xxx][USn-ACm]`.

**Plataforma objetivo**: la de la 001 (contenedores Linux; navegadores modernos de portátil y móvil).

**Tipo de proyecto**: aplicación web (backend API + SPA servida por nginx en el mismo origen).

**Objetivos de rendimiento**: página completa lista en < 3 s con la semilla (SC-006); la consulta devuelve del orden de 1.000 filas (2–5 profesionales, 8 semanas).

**Restricciones**: solo lectura (FR-003); céntimos enteros; porcentajes calculados desde recuentos exactos y redondeados una vez; semanas lunes–domingo en hora de Madrid; texto íntegro en español de España; sin desplazamiento horizontal a 360 px; la información no depende solo del color.

**Escala/alcance**: clínicas de 2–5 profesionales y 4–10 servicios; 1 pantalla nueva, 1 operación de API.

Todas las incógnitas quedan resueltas en [research.md](research.md) (D1–D11); no queda ningún «NEEDS CLARIFICATION».

## Comprobación de la constitución

*PUERTA: superada antes de la Fase 0; revisada tras la Fase 1 (sin cambios).*

| Principio / regla | Cumplimiento en este plan | Estado |
|---|---|---|
| **I. Spec primero** | Todo lo planificado sale de `spec.md` (cada elemento del contrato cita su FR/AC). La spec está en `specs/MAPA.md` con propietario único (004). No se edita ninguna spec ajena: la 001 solo se lee. | ✅ |
| **II. Los números no admiten creatividad** | Céntimos enteros y el formateador único `formato/euros.ts`; porcentajes desde enteros con redondeo half-up único en el servidor (D4); suma de partes = total comprobada por test; semanas por `ZonaClinica` (D2), incluidas las de cambio de hora. | ✅ |
| **III. El solape es el fallo capital** | La feature **no escribe en la agenda** (FR-003), por lo que no exige pruebas de solape; en su lugar, una prueba demuestra que usarla deja la base intacta (SC-005) y el repositorio no expone escritura (D6). | ✅ |
| **IV. Simplicidad y cero alcance fantasma** | Sin dependencias nuevas (gráficos propios, D5), sin enrutador, sin migración, sin caché, sin selector de periodo. Exportaciones, filtros y «ahorro» atribuido siguen fuera de alcance. | ✅ |
| **V. Demostrable con datos reproducibles** | Las cifras de la spec salen de la semilla y un test de integración las reproduce cargando la semilla con el reloj en lunes (D9); si la semilla cambia, el test falla. La 001 no se modifica. | ✅ |
| **VI. Los tests acompañan a la spec** | Convención `[004][FR-xxx][USn-ACm]`; `scripts/trazabilidad.sh` se parametriza para que cada spec declare sus criterios no automatizables (D10) y CI lo ejecuta también para la 004. | ✅ |
| **VII. Interfaz clara y moderna** | Contrato de interfaz con textos exactos ([contracts/ui.md](contracts/ui.md)); estado vacío y «Sin datos» comprensibles; color + etiqueta + valor; axe + 360 px + objetivos táctiles ≥ 44 px en Playwright. | ✅ |
| **VIII. Español de España** | Textos de interfaz, mensajes y artefactos en español de España. | ✅ |
| **Trabajo en paralelo** | Solo toca su spec y su paquete `analitica`. Archivos compartidos que se tocan, mínimos y aditivos: `App.tsx` (una vista más), `MAPA.md`, `trazabilidad.sh` y `ci.yml`. Se avisa a las features 002/003 de que, si cambian estados de cita, deben pedir a la 004 el ajuste de definiciones. | ✅ |
| **Puertas de merge 1–6** | Cubiertas por las fases 1–4 y por [quickstart.md](quickstart.md). | ✅ |

**Dependencias externas**: ninguna nueva. **Complejidad añadida**: ninguna que justificar (tabla de seguimiento vacía).

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/004-panel-analitica/
├── plan.md              # Este archivo
├── research.md          # Fase 0 (decisiones D1–D10)
├── data-model.md        # Fase 1 (sin tablas nuevas; modelo de lectura)
├── quickstart.md        # Fase 1 (guía de validación)
├── contracts/
│   ├── api.yaml         # GET /api/analitica
│   └── ui.md            # Pantalla, textos y gráficos
├── checklists/requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks; no lo crea /speckit-plan)
```

### Código fuente (raíz del repositorio)

```text
backend/src/main/java/es/citaclara/analitica/      # NUEVO
├── ControladorAnalitica.java      # GET /api/analitica
├── ServicioAnalitica.java         # @Transactional(readOnly): carga y delega
├── RepositorioAnalitica.java      # extends Repository (solo consultas, sin save/delete)
├── CitaCerrada.java               # proyección de lectura (record)
├── CalculadoraAnalitica.java      # lógica pura: semanas, recuentos, minutos, proporciones
├── Proporcion.java                # parte / total / décimas (half-up entero)
└── AnaliticaRespuesta.java        # records del JSON
backend/src/test/java/es/citaclara/analitica/      # NUEVO
├── CalculadoraAnaliticaTest.java  # unitario: reglas, límites, cambio de hora, sin datos
├── AnaliticaSemillaTest.java      # cifras de la spec con la semilla cargada en lunes
├── AnaliticaApiTest.java          # sesión, aislamiento, vacío, sumas, solo lectura
frontend/src/analitica/                            # NUEVO
├── PantallaAnalitica.tsx          # carga, error, vacío y composición
├── ResumenProfesionales.tsx       # US1: ocupación y no asistencia de la última semana
├── IngresosPorServicio.tsx        # US2: ingresos y no obtenidos
├── Evolucion.tsx                  # US3: 8 semanas
├── GraficoBarras.tsx              # barra horizontal con valor visible, reutilizable
└── formato (en src/formato/)      # porcentaje.ts y semana en fechas.ts
frontend/tests/unit/analitica.test.tsx             # NUEVO
frontend/tests/e2e/analitica.spec.ts               # NUEVO
```

Archivos existentes que se modifican de forma aditiva: `frontend/src/App.tsx` (vista por ruta), `frontend/src/api/{cliente,tipos}.ts`, `frontend/src/formato/{fechas,porcentaje}.ts`, la cabecera de `PantallaAgenda.tsx` (enlace «Analítica»), `scripts/trazabilidad.sh`, `.github/workflows/ci.yml`, `specs/MAPA.md`.

**Decisión de estructura**: la de la 001 (capacidad de dominio por paquete); la feature añade su propio paquete `analitica` y su carpeta `analitica` en el frontend, sin tocar `cita`, `agenda` ni `semilla`.

## Fases de implementación

- **Fase 1 — Cálculo y API (backend)**. Calculadora pura con sus tests unitarios (US1–US3, FR-004–FR-011, FR-016); repositorio de lectura, servicio y controlador; pruebas de integración de sesión, aislamiento, vacío, sumas y solo lectura (US4); `AnaliticaSemillaTest` con las cifras de la spec.
- **Fase 2 — Interfaz (frontend)**. Tipos y cliente del API, formateador de porcentajes, pantalla y componentes de gráfico, navegación Agenda ↔ Analítica, estados de carga, error, vacío y «Sin datos»; tests de componentes.
- **Fase 3 — Calidad y trazabilidad**. Playwright (360 px, axe, objetivos táctiles, coherencia con el API), añadir a la spec la línea «Criterios no automatizables» (SC-003), parametrizar `trazabilidad.sh` y añadirlo a CI para la 004 (D10); verificar el tiempo de carga de SC-006.
- **Fase 4 — Cierre**. Ejecutar la suite completa y [quickstart.md](quickstart.md); pasar la spec de «Borrador» a «Implementada» en `MAPA.md` y en la cabecera de `spec.md` (el propietario); avisar a las features 002 y 003 de la dependencia de estados. **Último paso: actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[004] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features.**

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar.
