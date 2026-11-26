# Modelo de datos: Núcleo de agenda de CitaClara

**Feature**: `001-nucleo-agenda` | **Fecha**: 26/11/2026 | **Plan**: [plan.md](plan.md) | **Investigación**: [research.md](research.md)

Convenciones: nombres de tablas y columnas en español y `snake_case`; identificadores `bigint` generados; instantes en `timestamptz` (UTC en almacenamiento, Europe/Madrid en presentación, ver D4); importes en céntimos enteros (D5). Todas las entidades de negocio cuelgan de una clínica (`clinica_id`, FR-003).

## Diagrama

```text
clinica 1───* horario_atencion
   │
   ├──1───* profesional ──┐
   ├──1───* servicio      │ (misma especialidad)
   ├──1───* paciente      │
   └──1───* cita *────────┘
              *───1 servicio
              *───1 paciente
```

## Entidades

### Clínica (`clinica`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | bigint PK | |
| `identificador` | varchar(40) | Único, minúsculas, `^[a-z0-9-]+$`. Aparece en la URL del panel (`/eleva`). |
| `nombre` | varchar(120) | Obligatorio. «Clínica Eleva». |
| `clave_hash` | varchar(100) | Hash BCrypt de la clave de panel (FR-001). Nunca se expone. |

### Horario de atención (`horario_atencion`)

| Campo | Tipo | Reglas |
|---|---|---|
| `clinica_id` | FK → clinica | |
| `dia_semana` | smallint | 1 = lunes … 7 = domingo (ISO-8601). PK compuesta con `clinica_id`. |
| `apertura` | time | Hora local de Madrid. |
| `cierre` | time | `cierre > apertura` (sin cruzar medianoche). |

Un día sin fila = clínica cerrada. Semilla: lunes a viernes 09:00–20:00 (Supuestos de la spec).

### Profesional (`profesional`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | bigint PK | |
| `clinica_id` | FK → clinica | |
| `nombre` | varchar(120) | Obligatorio. |
| `especialidad` | varchar(40) | Obligatorio; valor en minúsculas (`fisioterapia`, `nutricion`). |

### Servicio (`servicio`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | bigint PK | |
| `clinica_id` | FK → clinica | |
| `nombre` | varchar(120) | Obligatorio. |
| `especialidad` | varchar(40) | Un profesional solo ofrece servicios con su misma especialidad (FR-010). |
| `duracion_minutos` | integer | `> 0` y múltiplo de 5. |
| `precio_centimos` | integer | `>= 0`. |

### Paciente (`paciente`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | bigint PK | |
| `clinica_id` | FK → clinica | |
| `nombre` | varchar(120) | Obligatorio, recortado. |
| `telefono` | varchar(20) | Obligatorio, normalizado (D11). **Único por clínica** `(clinica_id, telefono)` (FR-020). |
| `email` | varchar(254) | Opcional, formato válido (FR-019). |

Índice de búsqueda: expresión `lower(unaccent(nombre))` (FR-018, D10). No hay edición ni baja en esta feature.

### Cita (`cita`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | bigint PK | |
| `clinica_id` | FK → clinica | Debe coincidir con la de profesional, servicio y paciente. |
| `profesional_id` | FK → profesional | |
| `servicio_id` | FK → servicio | Misma especialidad que el profesional (FR-010). |
| `paciente_id` | FK → paciente | |
| `inicio` | timestamptz | Hora local en múltiplos de 5 min; posterior al momento actual al crear (RN2, FR-014), salvo la semilla (FR-032). |
| `fin` | timestamptz | `inicio + duracion_minutos` del servicio, calculado por el sistema (FR-009). `CHECK (fin > inicio)`. |
| `importe_centimos` | integer | Copia de `precio_centimos` al reservar (FR-017); no cambia aunque cambie el precio. |
| `estado` | varchar(12) | `RESERVADA`, `COMPLETADA`, `CANCELADA`, `NO_ASISTIDA` (`CHECK`). Nace `RESERVADA` (FR-011). |
| `version` | integer | Bloqueo optimista (FR-025, D3). |
| `creada_en` | timestamptz | Auditoría técnica mínima; no se muestra. |

**Restricción de no solape (RN1, FR-012, FR-013, D2)**:

```text
EXCLUDE USING gist (
  profesional_id WITH =,
  tstzrange(inicio, fin, '[)') WITH &&
) WHERE (estado IN ('RESERVADA', 'COMPLETADA'))
```

- Rango semiabierto: citas contiguas permitidas (US2-AC3).
- Solo ocupan hueco reservadas y completadas (FR-024, US2-AC4).
- Se controla por profesional, no por clínica ni por paciente (US2-AC6).

## Reglas de validación del alta de cita (orden de comprobación)

1. Sesión válida y recursos de la clínica de la sesión (FR-001, FR-003).
2. Paciente: `pacienteId` existente **o** `nuevoPaciente` válido y con teléfono no repetido (FR-019, FR-020).
3. Servicio de la especialidad del profesional (FR-010) → si no, `SERVICIO_NO_DISPONIBLE`.
4. Hora local válida, múltiplo de 5 min y existente en Madrid (D4) → si no, `HORA_NO_VALIDA`.
5. `inicio > ahora` (RN2, FR-014) → si no, `HORA_PASADA`.
6. `[inicio, fin)` dentro del horario de atención de ese día de la semana (FR-015) → si no, `FUERA_DE_HORARIO` (indicando el horario).
7. Comprobación previa de solape (mensaje con la cita que lo provoca) → `SOLAPE`.
8. Inserción; si la restricción salta (carrera) → `HUECO_RECIEN_OCUPADO` (US2-AC5).

Los pasos 2–8 ocurren en una única transacción (D12).

## Máquina de estados de la cita (FR-021–FR-023)

```text
                 ┌── cancelar        [ahora < inicio] ──▶ CANCELADA
RESERVADA ───────┼── completar       [ahora ≥ inicio] ──▶ COMPLETADA
                 └── marcar no asist.[ahora ≥ inicio] ──▶ NO_ASISTIDA
COMPLETADA, CANCELADA, NO_ASISTIDA: finales (sin acciones)
```

- Las acciones disponibles se calculan en el servidor con el `Clock` y se devuelven con cada cita (D8).
- Ninguna transición pasa de un estado libre a uno ocupante, por lo que los cambios de estado nunca pueden provocar un solape.
- Petición con versión desfasada o transición ya no permitida por cambio concurrente → `ESTADO_YA_CAMBIADO` con la cita real (US3-AC7).

## Modelos derivados (no persistidos)

### Agenda del día

Para `(profesional, fecha)`:

- `horario`: apertura y cierre de ese día, o vacío si la clínica cierra.
- `citas`: todas las citas cuyo inicio cae en esa fecha local, en cualquier estado, ordenadas por inicio (US1-AC4, US1-AC5).
- `huecos`: complemento, dentro del horario, de la unión de los intervalos de citas `RESERVADA`/`COMPLETADA` (FR-007). Un día sin citas = un único hueco con todo el horario (US1-AC6).
- `hoy`: fecha actual en Madrid según el servidor (US1-AC3).

### Sesión

`clinicaId` + `accesoEn` (instante). Válida si `ahora < accesoEn + 8 h` (FR-002, D6).

## Datos de demostración (resumen; detalle en D13)

| Elemento | Valor |
|---|---|
| Clínica | Clínica Eleva, identificador `eleva`, clave `eleva-demo-2026`, L–V 09:00–20:00 |
| Profesionales | María (fisioterapia), Jorge (fisioterapia), Lucía (nutrición) |
| Servicios | Sesión fisioterapia 45 min 4000 · Primera visita fisioterapia 60 min 5000 · Consulta nutrición 30 min 3500 · Primera visita nutrición 45 min 4500 (céntimos) |
| Pacientes | 40, con nombre, teléfono y email ficticios |
| Historia | 40 días laborables anteriores al día de carga, todas cerradas: ≈10 % no asistidas, ≈8 % canceladas (cuota exacta), resto completadas |
| Futuro | 10 días laborables posteriores al día de carga, reservadas, con huecos libres |
| Recuentos exactos | `datos-demostracion.md` (se genera en implementación y lo verifica un test, FR-033) |
