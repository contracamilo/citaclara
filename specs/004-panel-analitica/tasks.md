---

description: "Lista de tareas de implementación de la feature 004-panel-analitica"
---

# Tareas: Panel de analítica

**Entrada**: documentos de diseño de `specs/004-panel-analitica/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.yaml](contracts/api.yaml), [contracts/ui.md](contracts/ui.md), [quickstart.md](quickstart.md)

**Tests**: OBLIGATORIOS (constitución, Principio VI y puerta de merge 2). El nombre visible de cada test empieza por `[004][FR-xxx][USn-ACm]` (`@DisplayName` en JUnit; título en Vitest y Playwright). Los tests de cada historia se escriben primero y DEBEN fallar antes de implementar. No hay pruebas de solape (Principio III) porque la feature no escribe en la agenda; en su lugar, T034 prueba que usarla no cambia ningún dato.

**Organización**: por historia de usuario, en orden de prioridad (US1, US2 y US4 son P1; US3 es P2). Los números de historia son los de la spec.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: paralelizable (ficheros distintos, sin dependencias de tareas incompletas).
- **[Historia]**: US1–US4 (solo en las fases de historia).
- Cada descripción incluye la ruta exacta del fichero.

## Convenciones de rutas y reglas transversales

- Backend: `backend/src/main/java/es/citaclara/analitica/`; tests en `backend/src/test/java/es/citaclara/analitica/`.
- Frontend: `frontend/src/analitica/`; tests unitarios en `frontend/tests/unit/`; e2e en `frontend/tests/e2e/`.
- Importes en **céntimos enteros** y el único formateador `frontend/src/formato/euros.ts`; instantes `Instant`, semanas y «hoy» con `ZonaClinica` y el `Clock` inyectado (nunca `now()`); errores en Problem Details; toda consulta filtrada por el `clinicaId` de la sesión (`@RequestAttribute(SesionClinica.CLINICA)`); nombres de dominio en español.
- **Solo lectura (FR-003)**: ninguna tarea añade migraciones, tablas, ni usa `save`/`delete`/`JpaRepository` en el paquete `analitica`.
- Orden de las listas del API: profesionales como en el contexto de sesión (`RepositorioProfesional.findByClinicaIdOrderByNombre`: Jorge, Lucía, María en la semilla) y servicios como `RepositorioServicio.findByClinicaIdOrderByEspecialidadAscNombreAsc`.
- Cifras de la semilla: se verifican con el reloj fijado en **lunes 30/11/2026 a las 12:00 (Madrid)** y el `CargadorSemilla` real; las cifras son las de «Cifras de la semilla» de la spec.

---

## Fase 1: Preparación (trazabilidad por spec)

**Propósito**: que la puerta de trazabilidad mida la 004 con sus propios criterios no automatizables (research D10).

- [X] T001 Añadir a `specs/004-panel-analitica/spec.md`, justo después de la línea `**Estado**`, la línea `**Criterios no automatizables**: SC-003` (se mide con personas, no con tests). No debe empezar por `**SC-` ni `**FR-` para no contarse como identificador.
- [X] T002 Parametrizar `scripts/trazabilidad.sh`: en lugar de la lista fija `excluidos="SC-004 SC-005 SC-006"`, leer los identificadores de la línea `**Criterios no automatizables**:` de la spec analizada; si la spec no tiene esa línea, usar `SC-004 SC-005 SC-006` solo cuando el número de la spec sea `001` y ninguno en las demás. Debe seguir saliendo vacío para `specs/001-nucleo-agenda` y, con la 004 sin tests, listar sus FR/SC/escenarios (salvo SC-003).
- [X] T003 [P] Añadir al trabajo `trazabilidad` de `.github/workflows/ci.yml` un segundo paso `./scripts/trazabilidad.sh specs/004-panel-analitica`.

---

## Fase 2: Fundamentos (bloquea todas las historias)

**Propósito**: tipos, formato y estructura compartidos por las cuatro historias.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta terminar esta fase.

### Backend

- [X] T004 [P] Escribir `backend/src/test/java/es/citaclara/analitica/ProporcionTest.java`: `[004][FR-010] el porcentaje se redondea half-up una sola vez, con enteros` con 30/321 → 93 décimas, 37/320 → 116, 1/39 → 26, 5/41 → 122, 4/55 → 73, 1/16 (6,25 %) → 63, 1/8 → 125, 0/10 → 0; `[004][FR-011] un total 0 da décimas nulas (Sin datos)`; `[004][FR-010] parte = total da 1000`.
- [X] T005 [P] Crear `backend/src/main/java/es/citaclara/analitica/Proporcion.java`: record `Proporcion(long parte, long total, Integer decimas)` con `static Proporcion de(long parte, long total)`; `decimas` = `null` si `total` es 0 y, si no, `(2000 * parte + total) / (2 * total)` con aritmética entera (half-up, sin `double`). Constraints del modelo: `parte` ≥ 0, `total` ≥ 0, `decimas` nulo solo si `total = 0`.
- [X] T006 [P] Escribir `backend/src/test/java/es/citaclara/analitica/PeriodoAnaliticaTest.java`: `[004][FR-007] las 8 semanas terminan el domingo anterior a la semana en curso` (hoy lunes 30/11/2026 → de 05/10/2026 a 29/11/2026; hoy miércoles 25/11/2026 → la última semana es 16/11–22/11; hoy domingo 29/11/2026 → la última es 16/11–22/11), `[004][FR-007] devuelve 8 semanas consecutivas lunes–domingo`, `[004][FR-007][US3-AC3] la semana del cambio de hora de octubre (19/10–25/10/2026) dura 169 horas y sus límites son instantes de medianoche de Madrid`.
- [X] T007 [P] Crear `backend/src/main/java/es/citaclara/analitica/PeriodoAnalitica.java`: record `Semana(LocalDate inicio, LocalDate fin)` (lunes y domingo) y `PeriodoAnalitica` con `static PeriodoAnalitica de(LocalDate hoy)` que da las 8 semanas completas anteriores a la semana en curso (de la más antigua a la más reciente), `Instant desde()` y `Instant hasta()` mediante `ZonaClinica.inicioDelDia` (hasta = lunes de la semana en curso, excluido), y `Optional<Integer> semanaDe(Instant)` según `ZonaClinica.fechaLocal`.
- [X] T008 [P] Crear `backend/src/main/java/es/citaclara/analitica/CitaCerrada.java` (record `profesionalId`, `servicioId`, `inicio`, `fin`, `importeCentimos`, `estado` y `minutos()` = `Duration.between(inicio, fin).toMinutes()`) y `RepositorioAnalitica.java`: interfaz que extiende `org.springframework.data.repository.Repository<es.citaclara.cita.Cita, Long>` (NO `JpaRepository`) con una única consulta JPQL `cerradasEntre(clinicaId, desde, hasta)` con expresión de constructor `new CitaCerrada(...)` que filtra por `c.clinicaId = :clinicaId`, `c.inicio >= :desde and c.inicio < :hasta` y `c.estado in (COMPLETADA, NO_ASISTIDA, CANCELADA)` (las reservadas no se cargan).
- [X] T009 [P] Crear `backend/src/main/java/es/citaclara/analitica/AnaliticaRespuesta.java` con los records exactos de [contracts/api.yaml](contracts/api.yaml): `Analitica(boolean sinDatos, UltimaSemana ultimaSemana, Ingresos ingresos, List<SemanaEvolucion> evolucion)`, `UltimaSemana(Semana semana, List<ProfesionalAnalitica>)`, `ProfesionalAnalitica(profesionalId, nombre, especialidadNombre, Proporcion tasaNoAsistencia, Proporcion ocupacion)`, `Ingresos(List<ServicioIngresos>, long totalCentimos, long totalNoObtenidoCentimos)`, `ServicioIngresos(servicioId, nombre, citasCompletadas, importeCentimos, citasNoAsistidas, importeNoObtenidoCentimos)`, `SemanaEvolucion(Semana, completadas, noAsistidas, canceladas, Proporcion tasaNoAsistencia, long ingresosCentimos, List<OcupacionProfesional> ocupacion)`. Fechas como `LocalDate` (JSON `YYYY-MM-DD`), importes `long`.

### Frontend

- [X] T010 [P] Añadir a `frontend/src/api/tipos.ts` los tipos del API de analítica (`Proporcion` con `decimas: number | null`, `Analitica`, etc., espejo de `contracts/api.yaml`) y a `frontend/src/api/cliente.ts` el método `obtenerAnalitica(senal?)` (GET `/api/analitica`, mismo tratamiento de errores y de `SESION_CADUCADA` que el resto).
- [X] T011 [P] Escribir `frontend/tests/unit/formato.test.ts` (ampliar) o `frontend/tests/unit/formato-analitica.test.ts`: `[004][FR-010] formatearProporcion(93) da «9,3 %»` (normalizando U+00A0 como en `formato.test.ts`), `1000 → «100,0 %»`, `0 → «0,0 %»`, `null → «Sin datos»` `[004][FR-011]`; `[004][FR-014] formatearSemana({inicio:'2026-11-23', fin:'2026-11-29'}) = «23/11/2026 – 29/11/2026»`.
- [X] T012 [P] Crear `frontend/src/formato/porcentaje.ts` (`formatearDecimas(decimas: number | null): string` con `Intl.NumberFormat('es-ES', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 })` sobre `decimas / 1000`, o «Sin datos» si es nulo; sin recalcular ni redondear nada más) y ampliar `frontend/src/formato/fechas.ts` con `formatearSemana`.
- [X] T013 [P] Escribir `frontend/tests/unit/grafico-barras.test.tsx`: `[004][FR-012] cada barra muestra su nombre y su valor como texto visible`, `[004][FR-013] la información no depende solo del color: la barra con valor «Sin datos» no se dibuja y su texto sigue visible` y `[004][FR-011] un valor nulo nunca se muestra como 0 %`.
- [X] T014 [P] Crear `frontend/src/analitica/GraficoBarras.tsx`: lista de filas (nombre, barra con `width` proporcional a `valor/máximo` mediante CSS, texto del valor y texto secundario opcional como «1 de 39 citas»); fila sin valor muestra solo el texto «Sin datos» y no dibuja barra; semántica de lista/`role="img"` con descripción de todos los valores (contracts/ui.md §1.1); usa los tokens OKLCH existentes de `frontend/src/estilos.css`, objetivos y textos legibles a 360 px.
- [X] T015 Crear `frontend/src/analitica/PantallaAnalitica.tsx` (depende de T010): cabecera como la de la agenda («Cerrar sesión» y enlace «Agenda» con `aria-current` en la actual), título «Analítica de la clínica», estado «Cargando la analítica…» (`aria-busy`) y estado de error con el mensaje del API (o «No se ha podido cargar la analítica. Inténtalo de nuevo.») y botón «Reintentar»; de momento sin secciones. Añadir en `frontend/src/agenda/PantallaAgenda.tsx` el enlace «Analítica» a `/{identificador}/analitica` en la cabecera (objetivo táctil ≥ 44 px).
- [X] T016 Modificar `frontend/src/App.tsx` (depende de T015): decidir la vista por el segundo segmento de la ruta (`/{identificador}/analitica` → `PantallaAnalitica`, cualquier otra → agenda) con una función pura exportada `vistaDeRuta(ruta)` (como `identificadorDeRuta`), manteniendo sin enrutador ni estado global; tras iniciar sesión se permanece en la misma ruta.

**Punto de control**: tipos, formato y estructura listos; las historias pueden empezar.

---

## Fase 3: Historia de usuario 1 — Ver cuánto rinde cada profesional (Prioridad: P1) 🎯 MVP

**Objetivo**: ocupación y tasa de no asistencia de la última semana completa por profesional (FR-004, FR-005, FR-008, FR-009, FR-011).

**Prueba independiente**: con la semilla cargada en lunes, la página muestra María 54,5 % / 2,6 % (1 de 39), Jorge 48,2 % / 12,2 % (5 de 41) y Lucía 48,6 % / 7,3 % (4 de 55).

### Tests de la historia 1 (escribirlos primero; deben fallar)

- [X] T017 [P] [US1] Escribir `backend/src/test/java/es/citaclara/analitica/CalculadoraUltimaSemanaTest.java` (unitario, sin base de datos): `[004][FR-008][US1-AC2] la tasa de no asistencia es no asistidas ÷ (completadas + no asistidas + canceladas)`, `[004][FR-008] la calculadora ignora cualquier fila reservada que llegue (defensa en profundidad)`, `[004][FR-009][US1-AC3] la ocupación son los minutos completados ÷ minutos del horario de la semana (3.300 con L–V 09:00–20:00)`, `[004][FR-009] las no asistidas y las canceladas no suman minutos`, `[004][FR-011][US1-AC4] un profesional sin citas cerradas da tasa «Sin datos» (decimas nulas) y ocupación 0`, `[004][FR-011] una semana sin horario de atención da ocupación «Sin datos»`, `[004][FR-004][US1-AC1] aparecen todos los profesionales de la clínica en el orden recibido, aunque no tengan citas`, `[004][FR-007][US3-AC3] una cita del domingo 23:59 y otra del lunes 00:00 de Madrid caen en semanas distintas`, y `[004][FR-007][US3-AC3] las citas de la noche del cambio de hora (25/10/2026) no se pierden ni se duplican`.
- [X] T018 [P] [US1] Escribir `backend/src/test/java/es/citaclara/analitica/AnaliticaSemillaTest.java` (hereda de `PruebaIntegracionBase`; reloj en lunes 30/11/2026 12:00 Madrid; `CargadorSemilla.cargar()`; sesión en `eleva` con `eleva-demo-2026`): `[004][FR-015][SC-001][US1-AC2] tasa de no asistencia de la última semana: Jorge 122 décimas…` comprobando las tres filas de «Última semana de la semilla» (María 1 de 39 → 26, Jorge 5 de 41 → 122, Lucía 4 de 55 → 73) y `[004][FR-015][US1-AC3] ocupación de la última semana: María 1.800 de 3.300 → 545, Jorge 1.590 → 482, Lucía 1.605 → 486`. El orden del API es Jorge, Lucía, María.
- [X] T019 [P] [US1] Escribir `frontend/tests/unit/analitica-resumen.test.tsx` con datos de `frontend/tests/unit/datos.ts` ampliado (fixture `analiticaEleva`): `[004][FR-005][FR-012][US1-AC1] la página muestra los tres profesionales con su ocupación y su tasa, con valor y «x de y citas» visibles`, `[004][FR-011][US1-AC4] un profesional sin datos muestra «Sin datos» y no 0 %`, `[004][FR-014] el subtítulo muestra la semana con fechas dd/mm/aaaa`.

### Implementación de la historia 1

- [X] T020 [US1] Crear `backend/src/main/java/es/citaclara/analitica/CalculadoraAnalitica.java` (clase final, métodos estáticos puros, sin base de datos ni reloj): `ultimaSemana(PeriodoAnalitica, List<CitaCerrada>, List<Profesional>, List<HorarioAtencion>)` que devuelve `UltimaSemana` según FR-008/FR-009: tasa = `Proporcion.de(noAsistidas, completadas + noAsistidas + canceladas)`; ocupación = `Proporcion.de(minutosCompletados, minutosHorario)`, con `minutosHorario` = suma semanal de `cierre − apertura` de las filas de horario. Hace pasar T017.
- [X] T021 [US1] Crear `ServicioAnalitica.java` (`@Service`, `@Transactional(readOnly = true)`; recibe `RepositorioAnalitica`, `RepositorioProfesional`, `RepositorioServicio`, `RepositorioHorario` y `Clock`) y `ControladorAnalitica.java` (`GET /api/analitica`, `@RequestAttribute(SesionClinica.CLINICA) Long clinicaId`): calcula `PeriodoAnalitica.de(ZonaClinica.hoy(reloj))`, carga las citas cerradas del periodo y compone `Analitica` con `ultimaSemana`; hasta que existan US2 y US3 sus secciones salen vacías (`Ingresos` sin servicios y con totales 0, `evolucion` con las 8 semanas a cero). `especialidadNombre` con `Especialidades.nombre`. Hace pasar T018.
- [X] T022 [P] [US1] Crear `frontend/src/analitica/ResumenProfesionales.tsx`: sección «Última semana» con subtítulo «Semana del dd/mm/aaaa al dd/mm/aaaa» y dos `GraficoBarras` («Ocupación» y «Tasa de no asistencia» con «x de y citas») con los textos de apoyo exactos de [contracts/ui.md](contracts/ui.md) §1.1; valores con `formatearDecimas`.
- [X] T023 [US1] Conectar `ResumenProfesionales` en `PantallaAnalitica.tsx` (carga con `api.obtenerAnalitica`, `AbortController`, estado y subtítulo «Últimas 8 semanas: del … al …» a partir de `evolucion`). Hace pasar T019.

**Punto de control**: US1 funciona y se demuestra por sí sola (MVP).

---

## Fase 4: Historia de usuario 2 — Ver de dónde vienen los ingresos (Prioridad: P1)

**Objetivo**: ingresos por servicio (completadas) e ingresos no obtenidos por no asistencias, exactos al céntimo (FR-006, FR-010, FR-016).

**Prueba independiente**: con la semilla en lunes, los cuatro servicios suman 34.870,00 € y los no obtenidos 4275,00 €.

### Tests de la historia 2 (escribirlos primero; deben fallar)

- [X] T024 [P] [US2] Escribir `backend/src/test/java/es/citaclara/analitica/CalculadoraIngresosTest.java`: `[004][FR-006][US2-AC3] solo las completadas suman en ingresos; reservadas, canceladas y no asistidas no`, `[004][FR-006] el importe es el de la cita, no el precio actual del servicio` (cita con importe distinto del precio), `[004][FR-010][US2-AC2] la suma de importes de los servicios es exactamente el total`, `[004][FR-006][US2-AC4] un servicio sin citas completadas aparece con 0 € y 0 citas`, `[004][FR-016][US2-AC5] los no obtenidos suman solo las no asistidas, por servicio y en total; las canceladas no suman`.
- [X] T025 [P] [US2] Ampliar `AnaliticaSemillaTest.java`: `[004][FR-015][SC-001][SC-002][US2-AC1] ingresos por servicio de la semilla` (Primera visita fisioterapia 97 citas 485000, Sesión fisioterapia 426 y 1704000, Consulta nutrición 277 y 969500, Primera visita nutrición 73 y 328500; total 3487000, igual al «Completada» de `specs/001-nucleo-agenda/datos-demostracion.md`) y `[004][FR-016][SC-002][US2-AC5] ingresos no obtenidos de la semilla` (224000, 55000, 94500, 54000; total 427500). Orden de servicios: el de `findByClinicaIdOrderByEspecialidadAscNombreAsc`.
- [X] T026 [P] [US2] Escribir `frontend/tests/unit/analitica-ingresos.test.tsx`: `[004][FR-006][US2-AC1] cada servicio muestra importe formateado y número de citas, y el Total`, `[004][FR-010][US2-AC2] el Total mostrado es la suma de las filas mostradas`, `[004][FR-006][US2-AC4] un servicio con 0 citas muestra «0,00 €» y «0 citas» sin barra`, `[004][FR-016][US2-AC5] el bloque «Ingresos no obtenidos por no asistencias» muestra sus importes y nunca la palabra «ahorro»`.

### Implementación de la historia 2

- [X] T027 [US2] Añadir a `CalculadoraAnalitica.java` el método `ingresos(List<CitaCerrada>, List<Servicio>)` (completadas → `importeCentimos`/`citasCompletadas`; no asistidas → `importeNoObtenidoCentimos`/`citasNoAsistidas`; totales como suma exacta de `long`) y conectarlo en `ServicioAnalitica.java` sustituyendo la sección vacía. Hace pasar T024 y T025.
- [X] T028 [P] [US2] Crear `frontend/src/analitica/IngresosPorServicio.tsx`: sección «Ingresos por servicio» (subtítulo «Citas completadas de las últimas 8 semanas», `GraficoBarras` con `formatearEuros` y «N citas», fila «Total») y bloque «Ingresos no obtenidos por no asistencias» (subtítulo «Importe de las citas a las que el paciente no acudió», misma estructura y «Total»). El «Total» es el que devuelve el API.
- [X] T029 [US2] Conectar `IngresosPorServicio` en `PantallaAnalitica.tsx`. Hace pasar T026.

**Punto de control**: US1 y US2 funcionan por separado y juntas.

---

## Fase 5: Historia de usuario 4 — Entrar sin riesgo y sin tocar nada (Prioridad: P1)

**Objetivo**: acceso con la misma sesión, aislamiento por clínica, estado vacío y garantía de solo lectura (FR-001, FR-002, FR-003, SC-005).

**Prueba independiente**: sin sesión no hay datos; con la clave de una clínica no se ven los de otra; usar la página no cambia ningún dato.

### Tests de la historia 4 (escribirlos primero; deben fallar)

- [X] T030 [P] [US4] Escribir `backend/src/test/java/es/citaclara/analitica/AnaliticaApiTest.java` (hereda de `PruebaIntegracionBase`): `[004][FR-001][US4-AC1] sin sesión responde 401 SIN_SESION y no devuelve datos`, `[004][FR-001][US4-AC1] con la sesión caducada (reloj +8 h) responde 401 SESION_CADUCADA`, `[004][FR-002][US4-AC2] con la clave de una clínica no aparece ningún profesional, servicio ni cita de otra y las cifras no la incluyen` (dos clínicas con datos distintos), `[004][FR-008][US1-AC2] una cita reservada con inicio en la última semana no cuenta en ninguna cifra`, `[004][FR-011][US4-AC4] una clínica sin citas devuelve sinDatos=true, todo a cero y todos los profesionales con «Sin datos»`, `[004][FR-007][US3-AC2] las 8 semanas siempre están presentes y en orden`.
- [X] T031 [P] [US4] Ampliar `AnaliticaApiTest.java`: `[004][FR-003][SC-005][US4-AC3] usar la analítica no modifica ningún dato` — recuento y suma de `importe_centimos` de `cita`, recuentos de `paciente`, `profesional`, `servicio`, `clinica` y `horario_atencion` y `max(version)` de las citas antes y después de llamar varias veces a `GET /api/analitica` con la semilla cargada; `[004][FR-003] RepositorioAnalitica no es CrudRepository ni JpaRepository (sin save ni delete)` (reflexión) y `[004][FR-003] la analítica no expone ninguna operación de escritura` (`POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/analitica` responden 405 o 415, nunca 2xx).
- [X] T032 [P] [US4] Escribir `frontend/tests/unit/analitica-acceso.test.tsx`: `[004][FR-001][US4-AC1] en /eleva/analitica sin sesión se muestra la pantalla de acceso y ningún dato`, `[004][FR-001][US4-AC1] una sesión caducada muestra «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.»`, `[004][FR-012][US4-AC4] sinDatos muestra «Aún no hay citas cerradas en las últimas 8 semanas. Cuando la clínica complete citas, aquí verás su evolución.» y ningún gráfico`, `[004][FR-012] un error de carga muestra el aviso y «Reintentar» recarga`, y `[004][FR-001] vistaDeRuta('/eleva/analitica') devuelve analítica y vistaDeRuta('/eleva') la agenda`.

### Implementación de la historia 4

- [X] T033 [US4] Poner `sinDatos` en `ServicioAnalitica.java` (verdadero si no se cargó ninguna cita cerrada) y comprobar que `ControladorAnalitica.java` no tiene más mapeos que el `@GetMapping`; verificar que ningún fichero de `backend/src/main/java/es/citaclara/analitica/` importa `JpaRepository`, `save` ni `delete` (revisión manual anotada en la descripción del PR). Hace pasar T030 y T031.
- [X] T034 [US4] Mostrar en `PantallaAnalitica.tsx` el estado vacío de [contracts/ui.md](contracts/ui.md) §2 cuando `sinDatos` es verdadero (sin gráficos) y reutilizar `PantallaAcceso` y el evento `EVENTO_SESION_PERDIDA` para sesión perdida, (la permanencia en la misma ruta tras entrar ya la resuelve T016). Hace pasar T032.

**Punto de control**: acceso, aislamiento, vacío y solo lectura demostrados.

---

## Fase 6: Historia de usuario 3 — Ver la evolución de las últimas 8 semanas (Prioridad: P2)

**Objetivo**: ingresos, tasa de no asistencia y ocupación por profesional, semana a semana (FR-007, FR-014).

**Prueba independiente**: con la semilla en lunes, la evolución muestra las 8 semanas con las cifras de la spec.

### Tests de la historia 3 (escribirlos primero; deben fallar)

- [X] T035 [P] [US3] Escribir `backend/src/test/java/es/citaclara/analitica/CalculadoraEvolucionTest.java`: `[004][FR-007][US3-AC1] devuelve 8 semanas de la más antigua a la más reciente con ingresos, tasa y ocupación por profesional`, `[004][FR-007][US3-AC2] una semana sin citas aparece con 0 € y «Sin datos» en tasa, sin omitirse`, `[004][FR-007][US3-AC3] una cita se cuenta en una sola semana`, `[004][FR-010] la suma de los ingresos semanales es igual al total de ingresos del periodo` y `[004][FR-009] la ocupación semanal usa los minutos del horario de esa semana`.
- [X] T036 [P] [US3] Ampliar `AnaliticaSemillaTest.java`: `[004][FR-010][US3-AC2] con la semilla cargada en miércoles los totales siguen cuadrando (Σ servicios = total = Σ semanas) y la semana más antigua tiene menos citas que las demás`, `[004][FR-015][SC-001][SC-002][US3-AC1] la evolución de la semilla` con las 8 filas de «Evolución de la semilla» (completadas, no asistidas, canceladas, décimas de la tasa 81, 74, 136, 96, 75, 143, 119, 74 y décimas del total 100, e ingresos 438000, 461000, 421000, 399000, 455000, 405000, 433000, 475000; suma 3487000) y `[004][FR-015][US3-AC1] ocupación semanal por profesional` con la tabla «Ocupación semanal por profesional de la semilla» (p. ej. semana 6 de Jorge: 35,5 % → 355 décimas).
- [X] T037 [P] [US3] Escribir `frontend/tests/unit/analitica-evolucion.test.tsx`: `[004][FR-007][US3-AC1] la tabla muestra 8 semanas en orden con ingresos, tasa y una celda de ocupación por profesional`, `[004][FR-014] cada semana se etiqueta con fecha de inicio y fin dd/mm/aaaa`, `[004][FR-011][US3-AC2] una semana sin citas muestra «0,00 €» y «Sin datos»`, `[004][FR-010] la fila «Total de las 8 semanas» coincide con la suma de las semanas mostradas`.

### Implementación de la historia 3

- [X] T038 [US3] Añadir a `CalculadoraAnalitica.java` el método `evolucion(PeriodoAnalitica, List<CitaCerrada>, List<Profesional>, List<HorarioAtencion>)` (una `SemanaEvolucion` por semana con recuentos, `tasaNoAsistencia` de la clínica, `ingresosCentimos` de las completadas y una `OcupacionProfesional` por profesional, en el mismo orden que `ultimaSemana.profesionales`) y conectarlo en `ServicioAnalitica.java`. Hace pasar T035 y T036.
- [X] T039 [P] [US3] Crear `frontend/src/analitica/Evolucion.tsx`: sección «Evolución semana a semana» con una tabla accesible (`<table>` con cabeceras) de una fila por semana (Semana «dd/mm/aaaa – dd/mm/aaaa», Ingresos con barra, No asistencia con «x de y citas» o «Sin datos», y una celda por profesional con su nombre, valor y barra), apilando las celdas en pantallas estrechas sin desplazamiento horizontal, y la fila «Total de las 8 semanas».
- [X] T040 [US3] Conectar `Evolucion` en `PantallaAnalitica.tsx`. Hace pasar T037.

**Punto de control**: las cuatro historias funcionan.

---

## Fase 7: Calidad transversal y cierre

**Propósito**: accesibilidad, 360 px, rendimiento, trazabilidad, documentación y cierre del proceso.

- [X] T041 [P] Escribir `frontend/tests/e2e/analitica.spec.ts` reutilizando `tests/e2e/utilidades.ts` (`ApiPrueba`, `pantallaAccesible`): `[004][FR-001][US4-AC1] sin sesión /eleva/analitica pide la clave y tras entrar muestra la analítica`, `[004][FR-012][SC-001][US1-AC1] los valores mostrados son los que devuelve el API formateados` (sin cifras literales: el entorno se carga el día de ejecución, research D9), `[004][FR-010][SC-002][US2-AC2] el Total de ingresos mostrado es la suma de las filas`, `[004][FR-013][SC-004] a 360 px no hay desplazamiento horizontal, el texto no se desborda, los objetivos táctiles miden ≥ 44 px y axe no encuentra infracciones serias en claro y oscuro`, `[004][FR-014][US3-AC1] las 8 semanas se etiquetan con fechas dd/mm/aaaa`, `[004][SC-006] la página con todos sus gráficos es visible en menos de 3 s`, y `[004][FR-003][SC-005] el enlace «Agenda» devuelve a la agenda sin cambios en las citas`.
- [X] T042 Ejecutar `./scripts/trazabilidad.sh specs/004-panel-analitica` y `./scripts/trazabilidad.sh` (001): ambos deben salir vacíos; si falta algún FR, SC o escenario, añadir el test que lo cubra antes de seguir.
- [X] T043 Ejecutar la suite completa y corregir lo que falle: `cd backend && ./mvnw verify`, `cd frontend && npm ci && npm test && npm run build`, y con el entorno levantado `docker compose up -d --build && docker compose run --rm semilla && cd frontend && URL_BASE=http://localhost:8080 npm run e2e`. Anotar en el PR cualquier test desactivado: no debe haber ninguno (Principio VI).
- [X] T044 Recorrer manualmente [quickstart.md](quickstart.md) (§2 y §3) con la semilla cargada en lunes y en otro día, comprobando que en el segundo caso los totales siguen cuadrando entre sí aunque la semana más antigua sea parcial; corregir los pasos del quickstart que no funcionen tal cual.
- [X] T045 [P] Añadir a `README.md` una línea que mencione la página `/{identificador}/analitica` y su enlace desde la agenda (sin duplicar el contenido de la spec).
- [X] T046 Cerrar el estado del proceso: pasar la spec 004 de «Borrador» a «Implementada» en la cabecera de `specs/004-panel-analitica/spec.md` y en `specs/MAPA.md`, y anotar en la descripción del PR que las features 002 y 003 deben pedir a la 004 el ajuste de definiciones si cambian los estados de cita (spec, Supuestos → Dependencias). Comprobar que `specs/004-panel-analitica/checklists/requirements.md` sigue completo.
- [X] T047 **(Último paso)** Actualizar `CLAUDE.md` (sección «Decisiones y convenciones transversales») con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión, con referencia a la spec (p. ej. «[004] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features. Candidatas a evaluar (conservar solo las transversales): repositorios de solo lectura que extienden `Repository` sin `save`/`delete` y `@Transactional(readOnly = true)`; porcentajes como `{parte, total, decimas}` con un único redondeo half-up en el servidor y «Sin datos» (nunca 0 %) con denominador cero; definiciones de tasa de no asistencia (no asistidas ÷ todas las citas cerradas) y ocupación (minutos completados ÷ minutos del horario); semanas lunes–domingo en `Europe/Madrid` con «hoy» del servidor y las cifras semanales de la semilla solo reproducibles con la carga en lunes (reloj fijado a lunes 30/11/2026 en tests, e2e sin cifras literales); gráficos propios sin librería con valor visible; `scripts/trazabilidad.sh` por spec con la línea «Criterios no automatizables»; rutas de vista `/{identificador}/analitica` sin enrutador.

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Fase 1 (Preparación)**: sin dependencias; T003 depende de T002.
- **Fase 2 (Fundamentos)**: depende de la Fase 1 solo en T001 (para que la trazabilidad mida bien); **bloquea todas las historias**. T015 depende de T010; T016 depende de T015.
- **Fases 3–6 (historias)**: dependen de la Fase 2.
  - **US1** (Fase 3) es el MVP: crea `CalculadoraAnalitica`, el servicio, el controlador y la pantalla con datos.
  - **US2** (Fase 4), **US4** (Fase 5) y **US3** (Fase 6) amplían lo creado en US1 (`CalculadoraAnalitica.java`, `ServicioAnalitica.java`, `PantallaAnalitica.tsx`); si se hacen en paralelo por personas distintas, coordinar esos tres ficheros. Cada historia se prueba por sí sola con su fixture.
- **Fase 7 (Cierre)**: depende de todas las historias; T047 es siempre la última tarea.

### Orden dentro de cada historia

Tests (fallan) → calculadora → servicio/controlador → componente → conexión en la pantalla.

### Oportunidades de paralelismo

- Fase 2: T004–T009 (backend) y T010–T014 (frontend) son ficheros distintos y se pueden repartir entre dos personas.
- En cada historia, los tests de backend y de frontend (`[P]`) son ficheros distintos.
- US2, US3 y US4 pueden avanzar a la vez una vez terminada US1, repartiendo los ficheros compartidos.

### Ejemplo de paralelismo (Fase 2)

```text
Persona A (backend): T004 → T005, T006 → T007, T008, T009
Persona B (frontend): T010, T011 → T012, T013 → T014 → T015 → T016
```

## Estrategia de implementación

1. **MVP**: Fases 1, 2 y 3 (US1). Con esto Sara ya puede enseñar ocupación y no asistencia por profesional.
2. **Incremental**: añadir US2 (el dinero, argumento de renovación), después US4 (cierra seguridad y solo lectura; necesaria antes de mostrar la página a nadie), después US3 (tendencia).
3. **Cierre**: Fase 7 con la suite completa, la trazabilidad y la actualización de `CLAUDE.md` como último paso.
4. **Puerta de merge**: no se integra hasta que T042 y T043 estén en verde y las cifras de la spec se reproduzcan con la semilla (T018, T025, T036).
