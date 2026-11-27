# Modelo de datos: Auditoría de seguridad

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Dos grupos: (A) entidades **documentales** de la auditoría (viven en Markdown, no en la base de datos) y (B) datos **persistentes nuevos** que solo existen si se aprueba la puerta G1. Todo lo demás (clínica, paciente, cita…) se hereda de la spec 001 sin cambios.

## A. Entidades documentales

### Informe de auditoría (`informe-auditoria.md`)

| Campo | Regla |
|---|---|
| Versión del código auditado | Hash de commit; obligatorio (FR-018) |
| Fecha y edición OWASP | `OWASP Top 10:2025`, fecha de consulta |
| Alcance y método | Qué se revisó y con qué técnicas; qué queda fuera |
| Veredictos | Exactamente 10, uno por categoría A01–A10: `cumple`, `cumple con reservas`, `no cumple`, `no aplica` (este con justificación) |
| Hallazgos | Lista de fichas (abajo) |
| Resultado de la reauditoría | Abiertos/cerrados respecto al informe anterior |

### Hallazgo

| Campo | Regla |
|---|---|
| `id` | `H-nn`, único e inmutable |
| `categoria` | Una de A01–A10 |
| `severidad` | `crítica`, `alta`, `media`, `baja` (criterio D4 de la investigación) |
| `datosAfectados` | Subconjunto de: pacientes, pagos, sesión, agenda, configuración |
| `parte` | `servidor`, `interfaz`, `entorno` (una o varias) |
| `verificacion` | Pasos reproducibles o referencia al test que lo reproduce |
| `mejora` | Acción propuesta. **Obligatoria** si la severidad es crítica o alta |
| `esfuerzo` | `S`, `M`, `L` |
| `criterioAceptacion` | Verificable. **Obligatorio** si crítica o alta |
| `estado` | `abierto` → `corregido` (con test verde) o `derivado` (con solicitud al propietario y fecha) |
| `toca` | Spec ajena afectada, si la hay |

**Transiciones**: `abierto → corregido` (exige test que falla antes y pasa después) · `abierto → derivado` (exige solicitud registrada en el mapa) · `derivado → corregido` (cuando el propietario aprueba y se implementa). No hay vuelta a `abierto` salvo regresión detectada por la reauditoría, que crea un hallazgo nuevo.

### Requisito de pagos (`requisitos-pagos.md`)

| Campo | Regla |
|---|---|
| `id` | `RP-nnn` |
| `enunciado` | Verificable y sin referencia a una tecnología concreta salvo Stripe |
| `origen` | `recepcion`, `paciente en línea` o `ambos` |
| `verificacion` | Cómo se comprobará cuando exista la feature de pagos |
| `fuente` | Práctica de Stripe o requisito PCI DSS que lo respalda |

### Inventario de datos personales

| Campo | Regla |
|---|---|
| `dato` | Nombre, teléfono, email, citas y especialidad (dato de salud implícito), clave de acceso |
| `ubicacion` | Tabla, registro, copia, caché o salida de la semilla |
| `acceso` | Quién puede leerlo hoy |
| `proteccion` | Cifrado de tránsito/reposo, hash, aislamiento por clínica |
| `conservacion` | Plazo actual y plazo de referencia (5 años desde la última cita) |
| `derechos` | ¿Se puede atender acceso, rectificación y supresión hoy? |

## B. Datos persistentes nuevos (solo tras la puerta G1)

Todo importe y fecha respeta los Principios II y las convenciones [001]: instantes en `timestamptz`, `Clock` inyectado, aislamiento por `clinica_id`.

### `intento_acceso` (migración `V2`)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `bigint` identidad | |
| `clinica_id` | `bigint` nullable | Nulo si el identificador de clínica no existe (no se revela cuál es) |
| `identificador` | `varchar(40)` | Lo escrito en la ruta, truncado |
| `origen` | `varchar(64)` | Dirección de red del cliente tal como la fija nginx (`$remote_addr`) |
| `instante` | `timestamptz` | |
| `correcto` | `boolean` | |

- Índice por `(identificador, origen, instante)` y por `(identificador, instante)`.
- Purga de filas con más de 24 h. **No** guarda la clave, ni siquiera parcial.
- El bloqueo se **calcula** a partir de los intentos recientes con el `Clock`; no hay un estado «bloqueado» que pueda quedar inconsistente.

### `usuario` (migración `V3`)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `bigint` identidad | |
| `clinica_id` | `bigint` | FK a `clinica`; `UNIQUE (id, clinica_id)` por convención [001] |
| `nombre_usuario` | `varchar(60)` | `UNIQUE (clinica_id, lower(nombre_usuario))` |
| `nombre` | `varchar(120)` | Nombre visible |
| `clave_hash` | `varchar(100)` | BCrypt, nunca la clave |
| `rol` | `varchar(16)` | `RECEPCION` o `ADMINISTRACION` (`CHECK`) |
| `activo` | `boolean` | Revocar = `false`; invalida sus sesiones en la siguiente petición |
| `creado_en` | `timestamptz` | |

- La sesión conserva `clinicaId` (como hoy) y añade `usuarioId`; el interceptor comprueba en cada petición que el usuario sigue `activo` (revocación inmediata).
- `clinica.clave_hash` se mantiene durante la transición y se elimina en una migración posterior cuando el propietario de la 001 confirme la retirada.
- Sin datos de pacientes en esta tabla; el nombre de usuario no se escribe en registros fuera de eventos de seguridad.

## Relación con la spec 001

Ninguna tabla existente se modifica salvo, tras G1, la retirada futura de `clinica.clave_hash`. La exclusion constraint `cita_sin_solape` y las claves foráneas compuestas no se tocan.
