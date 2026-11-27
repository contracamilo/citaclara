# Contrato de interfaz: analítica (004-panel-analitica)

**Plan**: [../plan.md](../plan.md) · **API**: [api.yaml](api.yaml) · **Base visual**: [001 contracts/ui.md](../../001-nucleo-agenda/contracts/ui.md)

Define pantalla, elementos y textos visibles que la implementación DEBE respetar y que las pruebas comprueban. Todo en español de España; sin códigos ni identificadores internos. Las cifras son las que devuelve el API: el cliente solo las formatea (importes con `formato/euros.ts`, porcentajes con `formato/porcentaje.ts`, fechas `dd/mm/aaaa`).

## Direcciones

| Dirección | Contenido |
|---|---|
| `/{identificador}/analitica` | Analítica de la clínica si hay sesión; pantalla de acceso si no (misma sesión que la agenda, FR-001). Tras entrar con la clave se permanece en la analítica. |

## Cabecera y navegación

- Misma cabecera que la agenda: nombre de la clínica y «Cerrar sesión».
- Enlace «Analítica» en la cabecera de la agenda y enlace «Agenda» en la de la analítica; el actual figura como página actual (`aria-current="page"`). Objetivo táctil ≥ 44 px.

## 1. Cuerpo de la página (orden de arriba abajo)

Título «Analítica de la clínica» y, debajo, «Últimas 8 semanas: del 05/10/2026 al 29/11/2026» (fechas reales del API, `dd/mm/aaaa`).

### 1.1 Última semana por profesional (US1, FR-004, FR-005)

Sección «Última semana» con subtítulo «Semana del 23/11/2026 al 29/11/2026». Dos gráficos de barras horizontales, con una fila por profesional:

- **«Ocupación»**: nombre del profesional · barra · valor «54,5 %». Texto de apoyo de la sección: «Parte del horario de atención en que ha atendido pacientes.»
- **«Tasa de no asistencia»**: nombre · barra · valor «2,6 %» y, debajo o al lado, «1 de 39 citas». Texto de apoyo: «Citas a las que el paciente no acudió, sobre todas las citas cerradas de la semana (incluidas las canceladas).»
- Sin base de cálculo (`decimas = null`): «Sin datos» en lugar de valor y sin barra; nunca «0 %» (FR-011, US1-AC4).
- Cada gráfico es una lista (`<ul aria-label>`) cuyas filas llevan nombre, valor y recuento como texto visible, de modo que equivale a una tabla accesible con los mismos datos (FR-013).

### 1.2 Ingresos por servicio (US2, FR-006, FR-016)

Sección «Ingresos por servicio» con subtítulo «Citas completadas de las últimas 8 semanas».

- Gráfico de barras horizontales, una fila por servicio: nombre · barra · «17.040,00 €» · «426 citas».
- Fila final destacada «Total» con la suma exacta («34.870,00 €»).
- Servicio sin citas completadas: fila con «0,00 €» y «0 citas», sin barra (US2-AC4).
- Bloque «Ingresos no obtenidos por no asistencias» (subtítulo: «Importe de las citas a las que el paciente no acudió»): misma estructura por servicio con «2240,00 €» · «56 citas» y «Total» «4275,00 €». Nunca se llama «ahorro» ni «pérdida estimada» (FR-016).

### 1.3 Evolución de las últimas 8 semanas (US3, FR-007, FR-014)

Sección «Evolución semana a semana». Una tabla con barra de fondo (una fila por semana, de la más antigua a la más reciente), apta para 360 px apilando las celdas:

| Columna | Contenido |
|---|---|
| Semana | «05/10/2026 – 11/10/2026» (fecha de inicio y fin, `dd/mm/aaaa`) |
| Ingresos | Importe exacto + barra proporcional al mayor de las 8 semanas |
| No asistencia | «8,1 %» + «11 de 135 citas» (o «Sin datos») |
| Ocupación | Una celda por profesional con su nombre, «46,8 %» y barra (o «Sin datos») |

Una semana sin citas aparece igualmente con «0,00 €» y «Sin datos» donde corresponda (US3-AC2). Fila final «Total de las 8 semanas» con el ingreso total.

## 2. Estados

| Estado | Visible |
|---|---|
| Cargando | «Cargando la analítica…» (`aria-busy`) |
| Sin citas cerradas (`sinDatos = true`) | Estado vacío: «Aún no hay citas cerradas en las últimas 8 semanas. Cuando la clínica complete citas, aquí verás su evolución.» sin gráficos rotos (US4-AC4) |
| Error de carga | Aviso con el mensaje del API (o «No se ha podido cargar la analítica. Inténtalo de nuevo.») y botón «Reintentar» |
| Sesión caducada | Pantalla de acceso con «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.» (US4-AC1) |

## Requisitos transversales comprobables

| Requisito | Cómo se comprueba |
|---|---|
| Sin desplazamiento horizontal a 360 px (FR-013, SC-004) | Playwright: `scrollWidth <= clientWidth` en la página y en la tabla de evolución. |
| Accesibilidad (FR-013, SC-004) | `@axe-core/playwright` sin infracciones serias o críticas en claro y oscuro; objetivos táctiles ≥ 44 px; la información no depende solo del color (cada barra lleva su valor y nombre en texto). |
| Valores visibles (FR-012) | Cada cifra del gráfico está en el texto de la página, no solo en una descripción emergente. |
| Coherencia con el API (SC-001, SC-002) | Playwright: los valores mostrados son los del API formateados; la suma de los importes por servicio es el «Total» mostrado. |
| Tiempo de carga (SC-006) | Playwright: contenido visible en < 3 s con la semilla. |
