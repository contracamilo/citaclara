# Modelo de datos: Recordatorios de cita por email

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Decisiones**: [research.md](research.md)

Migración `V3__recordatorios.sql` (Flyway, SQL; Hibernate en `validate`). Convenciones de la 001: `clinica_id` en toda tabla de negocio, instantes en `timestamptz`.

## Tabla nueva: `recordatorio` (propiedad de esta feature)

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` identidad | PK |
| `clinica_id` | `bigint` | NOT NULL, FK a `clinica` (`ON DELETE CASCADE`) |
| `cita_id` | `bigint` | NOT NULL, **UNIQUE**: como mucho un recordatorio por cita (FR-004) |
| `email_destino` | `varchar(254)` | NOT NULL; copia del email del paciente en el momento de generar (FR-005) |
| `enlace_huella` | `char(64)` | NOT NULL, UNIQUE; SHA-256 hexadecimal del enlace de cancelación (D5) |
| `generado_en` | `timestamptz` | NOT NULL; instante del `Clock` en la generación (FR-005) |

- FK compuesta `(cita_id, clinica_id)` → `cita (id, clinica_id)` con `ON DELETE CASCADE`: un recordatorio nunca apunta a una cita de otra clínica (aislamiento, FR-008). Requiere `UNIQUE (id, clinica_id)` en `cita`; si no existe, se pide a la 001 o se crea en la V2 como restricción adicional sin cambiar datos.
- Índice por `enlace_huella` (la UNIQUE ya lo es) para la búsqueda de las operaciones públicas.
- No hay columna de estado: una fila existe solo si el recordatorio se generó y se guardó (FR-007). Un fallo deja la cita sin fila y se reintenta.

## Columnas nuevas en tablas de la 001 (peticiones al propietario de la 001)

| Tabla | Columna | Tipo | Reglas | Motivo |
|---|---|---|---|---|
| `clinica` | `telefono` | `varchar(20)` | NOT NULL tras la semilla (la V2 la crea con valor por defecto técnico y la semilla de Eleva la rellena) | FR-009, pantalla fuera de plazo |
| `cita` | `cancelada_por` | `varchar(10)` | nulo o `'PACIENTE'` (CHECK); solo puede tener valor si `estado = 'CANCELADA'` | FR-016 |

`cita.cancelada_por` solo lo escribe la cancelación desde el enlace; las cancelaciones de recepción lo dejan nulo. No altera `cita_sin_solape` ni las transiciones de estado.

## Estados y transiciones

No se añaden estados de cita. El recordatorio no tiene ciclo de vida: nace al generarse y no cambia.

```text
Cita RESERVADA ──(ventana: ahora+24h ≤ inicio < ahora+48h)──▶ Recordatorio generado (fila única)
Cita RESERVADA ──(paciente confirma, ahora ≤ inicio−24h)────▶ Cita CANCELADA, cancelada_por = PACIENTE
```

Situación que devuelve la consulta del enlace (no se almacena; se calcula con el reloj):

| Estado de la cita | Momento | Situación |
|---|---|---|
| RESERVADA | `ahora ≤ inicio − 24 h` | `ACTIVA` |
| RESERVADA | `ahora > inicio − 24 h` (incluida cita ya iniciada) | `FUERA_DE_PLAZO` |
| CANCELADA, COMPLETADA, NO_ASISTIDA | cualquiera | `YA_CERRADA` |

## Entidades clave de la spec

- **Recordatorio** → tabla `recordatorio`.
- **Enlace de cancelación** → no es tabla: es el valor aleatorio del correo; en base de datos solo existe su huella.
- **Cita, Paciente, Clínica** → tablas de la 001; esta feature las lee y cancela citas con las reglas de la 001.

## Reglas de integridad que prueban los tests

1. Dos inserciones concurrentes del recordatorio de la misma cita: una prospera, la otra no inserta (FR-004).
2. Un recordatorio nunca referencia una cita de otra clínica (FK compuesta).
3. `cancelada_por` no admite valores distintos de `PACIENTE` ni aparece en citas no canceladas.
4. Borrar una cita (o una clínica) borra su recordatorio.
