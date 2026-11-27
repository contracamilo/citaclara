# Investigación y decisiones: Panel de analítica

**Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

No quedaba ninguna incógnita de tecnología: la feature reutiliza el stack y las convenciones de la 001. Estas son las decisiones de diseño que sí había que tomar.

## D1. Una sola operación de API que devuelve la página entera

- **Decisión**: `GET /api/analitica`, sin parámetros, devuelve todo lo que pinta la página (última semana por profesional, ingresos por servicio, evolución).
- **Motivo**: no hay selector de periodo (fuera de alcance), así que no hay nada que parametrizar; una sola respuesta garantiza que todas las cifras de la pantalla vienen del mismo instante y cuadran entre sí (Principio II).
- **Alternativas**: un endpoint por gráfico (más viajes, y cifras tomadas en instantes distintos que pueden descuadrar); parámetros `desde`/`hasta` (alcance fantasma, Principio IV).

## D2. Semanas: lunes–domingo calculadas con `ZonaClinica`, «hoy» del servidor

- **Decisión**: «hoy» es `ZonaClinica.hoy(reloj)`. La semana en curso empieza el lunes de esa fecha. Las **8 semanas** son las 8 anteriores completas: desde el lunes `−56 días` hasta el lunes de la semana en curso (excluido). La **última semana completa** es la octava. Los límites de cada semana se convierten a instantes con `ZonaClinica.inicioDelDia`, de modo que una semana con cambio de hora no desplaza, duplica ni pierde citas (spec, Casos límite).
- **Motivo**: es lo que fija la spec (FR-007, Casos límite) y reutiliza la regla ya existente de que «hoy» lo decide el servidor.
- **Alternativas**: agrupar con `date_trunc('week', …)` en SQL (mezcla reglas de zona en SQL y Java; peor de probar); semana móvil de 7 días hacia atrás desde hoy (cifras que cambian cada día y no coinciden con «semana» para la clínica).

## D3. Agregar en Java con una calculadora pura, no en SQL

- **Decisión**: una consulta de solo lectura devuelve las citas **cerradas** (completadas, no asistidas, canceladas) de la clínica con inicio en el periodo, como filas ligeras `CitaCerrada` (profesional, servicio, inicio, minutos, importe, estado). `CalculadoraAnalitica` (función pura de esas filas, el horario, los profesionales, los servicios y la fecha de hoy) produce toda la respuesta.
- **Motivo**: el volumen es mínimo (≈ 1.000 filas con 3 profesionales), la regla de semanas y de zona queda en un único sitio con `ZonaClinica`, y la calculadora se prueba sin base de datos, incluidos cambios de hora y casos vacíos, igual que `GeneradorSemilla`.
- **Minutos de una cita**: `fin − inicio` en minutos (instantes reales), no la duración del servicio, para no depender de que el servicio no cambie.
- **Alternativas**: `GROUP BY` en SQL (rápido pero reparte la lógica de semana/zona entre SQL y Java y es más difícil de probar de forma exhaustiva); vistas materializadas (escritura y mantenimiento, prohibido por FR-003).

## D4. Porcentajes: recuento exacto y redondeo único, en el servidor

- **Decisión**: todo porcentaje viaja como `{parte, total, decimas}` con enteros; `decimas` es el porcentaje ×10 redondeado half-up con aritmética entera (`(2000·parte + total) / (2·total)`), y `null` si `total = 0` (→ «Sin datos», FR-011). El frontend solo formatea (`formato/porcentaje.ts`, CLDR `es-ES`: «9,3 %») y nunca recalcula.
- **Motivo**: FR-010 (redondeo solo al mostrar, un decimal) y Principio II; un único redondeo evita diferencias entre servidor, tests y pantalla, y el número de citas en que se basa (`total`) viaja con él (FR-005).
- **Ocupación**: `parte` = minutos completados, `total` = minutos del horario de la clínica en la semana (suma de `cierre − apertura` de cada día con fila en `horario_atencion`, igual para todos los profesionales). Con el horario de la semilla son 3.300 min/semana.
- **Tasa de no asistencia**: `parte` = no asistidas, `total` = completadas + no asistidas + canceladas (FR-008). Las reservadas se excluyen en la consulta.
- **Alternativas**: devolver `double` (error de coma flotante y redondeos en dos sitios); calcular en el frontend (duplica la regla).

## D5. Gráficos propios en HTML/CSS/SVG, sin librería

- **Decisión**: un componente `GraficoBarras` (barras horizontales con etiqueta y valor visibles) y una tabla con barra de fondo para la evolución. Cada gráfico incluye su valor como texto visible, y el conjunto de datos queda también como `<table>`/lista legible por lector de pantalla.
- **Motivo**: las cifras son pocas (3 profesionales, 4–10 servicios, 8 semanas); una librería (p. ej. Recharts, Chart.js) añade dependencia, peso y problemas de accesibilidad y de 360 px que Principio IV obliga a justificar y aquí no se justifican. Las barras horizontales con valor visible son legibles en móvil sin desplazamiento (FR-012, FR-013).
- **Información no solo por color** (FR-013): cada barra lleva nombre y valor en texto; las series de la evolución por profesional se distinguen por columna de la tabla, no por color.
- **Alternativas**: librería de gráficos (rechazada, ver arriba); gráficos de líneas SVG para la evolución (más difíciles de hacer accesibles en 360 px con 3 series; se pueden añadir en una feature futura si Sara los pide).

## D6. Solo lectura garantizada por construcción

- **Decisión**: `RepositorioAnalitica extends org.springframework.data.repository.Repository<Cita, Long>` con solo métodos de consulta (sin `save`, `delete` ni `JpaRepository`); `ServicioAnalitica` con `@Transactional(readOnly = true)`; solo un `@GetMapping`; **sin migración** ni tablas. Una prueba de integración compara el contenido de las tablas antes y después de usar la analítica (SC-005).
- **Motivo**: FR-003. La constitución (Principio III) no exige aquí pruebas de solape porque no hay escritura, y esta construcción lo hace imposible por tipo, no solo por disciplina.
- **Alternativas**: reutilizar `RepositorioCita` (expone `save`/`delete`); un rol de base de datos de solo lectura (infraestructura extra, Principio IV).

## D7. Aislamiento por clínica y acceso

- **Decisión**: la analítica cuelga de `/api/**`, así que `InterceptorSesion` ya la protege (401 `SIN_SESION`/`SESION_CADUCADA`); el controlador recibe el `clinicaId` de la sesión (`@RequestAttribute(SesionClinica.CLINICA)`) y toda consulta filtra por él; no existe el parámetro de clínica (FR-001, FR-002).
- **Alternativas**: ninguna razonable (convención de la 001).

## D8. Navegación: ruta propia `/{identificador}/analitica`, enlaces normales

- **Decisión**: `App.tsx` toma el identificador del primer segmento (ya lo hace `identificadorDeRuta`) y decide la vista por el segundo (`analitica` o agenda). Entre vistas se navega con enlaces normales («Analítica» en la cabecera de la agenda y «Agenda» en la de la analítica), que recargan la SPA; nginx ya sirve `index.html` en cualquier ruta.
- **Motivo**: sin enrutador (decisión de la 001), enlace compartible y botón atrás funcionando sin código nuevo. El coste de recargar el contexto es despreciable (SC-006).
- **Alternativas**: `?vista=analitica` (mezcla con la query de la agenda); `history.pushState` a mano (más código sin necesidad); añadir un enrutador (dependencia sin justificar).

## D9. Reproducibilidad de las cifras con la semilla

- **Decisión**: `AnaliticaSemillaTest` fija el reloj en un **lunes** (p. ej. 30/11/2026 a las 12:00 de Madrid), carga la semilla con el `CargadorSemilla` real, inicia sesión en Clínica Eleva y comprueba **todas** las cifras de la sección «Cifras de la semilla» de la spec (tasas y ocupación de la última semana, ingresos y no obtenidos por servicio, las 8 semanas y la ocupación semanal por profesional) y que el total coincide con `datos-demostracion.md` (34.870,00 € y 4275,00 €).
- **Motivo**: Principio V y puerta de merge 5; la 001 prohíbe citar la semilla por semana natural (FR-033), pero con el reloj en lunes la posición `−40 … −1` coincide exactamente con las 8 semanas completas, y el test hace explícita esa condición. Si la semilla cambia, el test falla y obliga a revisar la spec.
- **e2e**: el entorno Docker carga la semilla el día en que se ejecute, así que Playwright **no** compara con cifras literales; comprueba que la pantalla muestra lo que devuelve el API (valores formateados) y que las sumas cuadran.
- **Alternativas**: documentar las cifras en `datos-demostracion.md` (es de la 001; solo su propietario puede cambiarlo); fijar la fecha de carga en el entorno (cambia el comportamiento de la semilla, fuera de alcance).

## D10. Trazabilidad por spec

- **Decisión**: `scripts/trazabilidad.sh` excluye hoy una lista fija de criterios de la 001 (`SC-004 SC-005 SC-006`), que en la 004 serían automatizables. Se parametriza: cada spec declara sus criterios no automatizables en una línea `**Criterios no automatizables**: SC-003` de su spec (para la 004: SC-003, medido con personas), y el script lee esa línea; sin línea, ninguno. La 001 conserva su lista actual. CI añade `./scripts/trazabilidad.sh specs/004-panel-analitica`.
- **Motivo**: sin esto, SC-005 (solo lectura) y SC-006 (rendimiento) de la 004 quedarían excluidos en silencio, lo contrario de la puerta de merge 2.
- **Alternativas**: un segundo script (duplicación); no excluir nada (SC-003 no es automatizable).

## D11. Rendimiento

- **Decisión**: sin caché ni índice nuevo. La consulta filtra por `clinica_id` e inicio en un rango de 8 semanas y devuelve ≈ 1.000 filas; el índice existente `cita_profesional_inicio` y el volumen de una clínica pequeña bastan. SC-006 (< 3 s) se comprueba en e2e con la semilla.
- **Alternativas**: índice por `(clinica_id, inicio)` (migración innecesaria para este volumen, y la feature no escribe).
