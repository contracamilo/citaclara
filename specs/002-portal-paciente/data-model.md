# Modelo de datos: Portal del paciente

**Feature**: `002-portal-paciente` | **Spec**: [spec.md](spec.md) | **Decisiones**: [research.md](research.md)

Esta feature amplía el modelo de 001 ([data-model.md de 001](../001-nucleo-agenda/data-model.md)) con una sola migración, `V2__portal_paciente.sql`. No cambia ninguna columna existente. Toda tabla nueva lleva `clinica_id` (convención de aislamiento de 001).

## Cambios sobre tablas de 001 (solo índices y restricciones)

| Tabla | Cambio | Motivo |
|---|---|---|
| `paciente` | Índice parcial `paciente_email_portal ON paciente (clinica_id, lower(btrim(email))) WHERE email IS NOT NULL` | Buscar fichas por email sin distinguir mayúsculas ni espacios (FR-002, R5) |
| `cita` | `CONSTRAINT cita_id_clinica UNIQUE (id, clinica_id)` | Permite la FK compuesta desde `cancelacion_portal` (aislamiento por clínica, FR-028) |

## Entidades nuevas

### SolicitudEnlacePortal (`solicitud_enlace_portal`)

Una fila por cada vez que alguien pide un enlace, exista o no el email (R3).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` identidad | PK |
| `clinica_id` | `bigint` | FK → `clinica`, `ON DELETE CASCADE` |
| `email_normalizado` | `varchar(254)` | `lower(btrim(email))` del email escrito; obligatorio |
| `token_hash` | `char(64)` | SHA-256 en hexadecimal del secreto del enlace; `NULL` si el email no coincide con ninguna ficha; `UNIQUE` |
| `solicitada_en` | `timestamptz` | Instante de la solicitud (`Clock`) |
| `caduca_en` | `timestamptz` | `solicitada_en + 10 min` (FR-006) |
| `usada_en` | `timestamptz` | `NULL` hasta que se abre la sesión; después, no se puede volver a usar |

- **Índice**: `(clinica_id, email_normalizado, solicitada_en)`, que sirve para el límite de 5 por hora (FR-004) y para la limpieza de las filas de más de 24 h.
- **CHECK**: `caduca_en > solicitada_en`; `usada_en IS NULL OR token_hash IS NOT NULL`.
- **Validez del enlace**: `token_hash = h AND usada_en IS NULL AND caduca_en > ahora AND clinica_id = c`. El consumo es un único `UPDATE … RETURNING` atómico (R1).

### CancelacionPortal (`cancelacion_portal`)

Registra que una cita la canceló el paciente desde el portal (FR-017) y si recepción ya la ha revisado (FR-021).

| Campo | Tipo | Reglas |
|---|---|---|
| `cita_id` | `bigint` | PK: una cita, como mucho un aviso (SC-005) |
| `clinica_id` | `bigint` | `FOREIGN KEY (cita_id, clinica_id) REFERENCES cita (id, clinica_id) ON DELETE CASCADE` |
| `cancelada_en` | `timestamptz` | Instante de la cancelación (`Clock`) |
| `revisada_en` | `timestamptz` | `NULL` mientras está pendiente; se fija una sola vez (idempotente) |

- **Índice**: `(clinica_id, cancelada_en DESC) WHERE revisada_en IS NULL`, que sirve para el contador y la lista de pendientes (FR-019, FR-020).
- **CHECK**: `revisada_en IS NULL OR revisada_en >= cancelada_en`.
- **Invariante**: solo se inserta en la misma transacción en la que la cita pasa de `RESERVADA` a `CANCELADA` desde el portal. Por tanto, la cita referenciada siempre está `CANCELADA`.

### Sesión del paciente (en `HttpSession`, no persistida)

| Atributo | Contenido |
|---|---|
| `citaclara.portal.clinicaId` | Clínica del portal en el que se entró |
| `citaclara.portal.email` | Email normalizado del enlace consumido |
| `citaclara.portal.accesoEn` | Instante de entrada; la sesión vale mientras `ahora < accesoEn + 30 min` (FR-005) |

Abrir esta sesión invalida cualquier sesión anterior del navegador, incluida la del panel, y lo mismo al revés (R4).

## Pacientes visibles para una sesión

`paciente WHERE clinica_id = sesion.clinicaId AND lower(btrim(email)) = sesion.email`, evaluado en cada petición. Pueden ser varias fichas (caso límite «varias fichas con el mismo email»). Las citas del portal son las de esas fichas y nunca otras (FR-011, FR-028).

## Vista de cita en el portal

Se calcula en el servidor con el `Clock` y `ZonaClinica`. El cliente no hace aritmética de fechas.

| Estado de la cita (001) | Condición | Bloque | Etiqueta del portal | `cancelable` |
|---|---|---|---|---|
| `RESERVADA` | `ahora < inicio − 24 h` | Próximas | Reservada | sí |
| `RESERVADA` | `inicio − 24 h ≤ ahora < inicio` | Próximas | Reservada | no (motivo `FUERA_DE_PLAZO`) |
| `RESERVADA` | `ahora ≥ inicio` | Pasadas | Pendiente de cierre por la clínica | no |
| `COMPLETADA` | — | Pasadas | Realizada | no |
| `NO_ASISTIDA` | — | Pasadas | No acudiste | no |
| `CANCELADA` | — | Pasadas | Cancelada | no |

Orden: «Próximas», por `inicio` ascendente; «Pasadas», por `inicio` descendente (FR-008).

## Transiciones

No hay transiciones nuevas. El portal solo usa `RESERVADA → CANCELADA` de 001 (FR-015), con una guarda adicional:

```text
RESERVADA ── cancelar desde el portal [ficha con el email de la sesión ∧ ahora < inicio − 24 h] ──▶ CANCELADA
                                      + INSERT cancelacion_portal (misma transacción)
```

La guarda de 001 (`ahora < inicio`) queda implícita. Recepción mantiene su regla sin cambios (FR-013).

## Reglas de validación y orden de comprobación al cancelar (FR-012–FR-016, FR-028)

1. Sesión del portal válida, si no 401 `SESION_PORTAL_CADUCADA` o `SIN_SESION_PORTAL`.
2. La cita existe, es de la clínica de la sesión y su paciente tiene el email de la sesión, si no 404 `NO_ENCONTRADO`.
3. Si ya está `CANCELADA` y tiene `cancelacion_portal`, 200 con la cita (idempotente, US3-AC7).
4. La versión coincide con la que vio el paciente y el estado es `RESERVADA`, si no 409 `ESTADO_YA_CAMBIADO` con la cita real.
5. Dentro de plazo (`ahora < inicio − 24 h`), si no 422 `FUERA_DE_PLAZO`.
6. Transición de la máquina de estados de 001, más `INSERT cancelacion_portal` y `saveAndFlush`. Si salta el bloqueo optimista, se responde 409 `ESTADO_YA_CAMBIADO` releyendo la cita.
