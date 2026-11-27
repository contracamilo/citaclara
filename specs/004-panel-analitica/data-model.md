# Modelo de datos: Panel de analítica

**Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md) | **Investigación**: [research.md](research.md)

## Almacenamiento

**Sin cambios.** La feature no crea tablas, columnas, índices ni migraciones (FR-003, research D6, D11). Lee, filtrando siempre por la clínica de la sesión:

| Tabla (spec 001) | Qué se lee |
|---|---|
| `cita` | `profesional_id`, `servicio_id`, `inicio`, `fin`, `importe_centimos`, `estado` de las citas **cerradas** (`COMPLETADA`, `NO_ASISTIDA`, `CANCELADA`) con `inicio` en el periodo de 8 semanas |
| `profesional` | Lista de profesionales de la clínica (aparecen aunque no tengan citas) |
| `servicio` | Lista de servicios de la clínica (aparecen aunque no tengan citas, con 0,00 €) |
| `horario_atencion` | Minutos de atención por día de la semana, para el denominador de la ocupación |

## Modelo de lectura (no persistente)

### CitaCerrada (proyección de la consulta)

| Campo | Tipo | Notas |
|---|---|---|
| `profesionalId` | long | |
| `servicioId` | long | |
| `inicio` | Instant | La semana se decide con `ZonaClinica.fechaLocal(inicio)` |
| `fin` | Instant | Los minutos de la cita son `fin − inicio` en minutos reales (método derivado `minutos()`) |
| `importeCentimos` | int | Precio fijado al reservar (FR-006) |
| `estado` | EstadoCita | Solo cerradas; las reservadas no se cargan |

### Proporcion

| Campo | Tipo | Regla |
|---|---|---|
| `parte` | long | Numerador exacto (citas o minutos) |
| `total` | long | Denominador exacto |
| `decimas` | int \| null | `round-half-up(1000·parte/total)` con enteros; `null` si `total = 0` (FR-011, «Sin datos») |

### Semana de análisis

- `inicio` (lunes), `fin` (domingo), ambas fechas locales `YYYY-MM-DD`.
- Periodo = 8 semanas consecutivas terminadas el domingo anterior a la semana en curso; la semana 8 es la «última semana completa».
- Un instante pertenece a la semana de su fecha local de Madrid: nunca a dos ni a ninguna (US3-AC3).

### Cifras por profesional y semana

| Campo | Definición |
|---|---|
| `completadas`, `noAsistidas`, `canceladas` | Recuentos de citas cerradas de ese profesional en la semana |
| `tasaNoAsistencia` | `Proporcion(noAsistidas, completadas + noAsistidas + canceladas)` (FR-008) |
| `ocupacion` | `Proporcion(minutosCompletados, minutosHorario)` (FR-009); `minutosHorario` = suma semanal de `cierre − apertura` del horario de la clínica |

### Ingresos por servicio (periodo de 8 semanas)

| Campo | Definición |
|---|---|
| `citasCompletadas`, `importeCentimos` | Citas `COMPLETADA` del servicio y suma de su `importe_centimos` (FR-006) |
| `citasNoAsistidas`, `importeNoObtenidoCentimos` | Citas `NO_ASISTIDA` y suma de su `importe_centimos` (FR-016) |

Invariantes comprobadas por test:

1. `Σ importeCentimos` de los servicios = `totalCentimos` = `Σ ingresosCentimos` de las 8 semanas (FR-010, SC-002); ídem para los no obtenidos.
2. Para cada semana y profesional: `completadas + noAsistidas + canceladas` = filas cerradas de esa semana y profesional.
3. Ninguna cita se cuenta en dos semanas ni en ninguna (límites de semana y cambios de hora).
4. Ningún dato de otra clínica influye en ninguna cifra (FR-002).

## Transiciones de estado

Ninguna: la feature solo lee. Las reglas de estado siguen siendo las de la spec 001 (FR-021–FR-024).
