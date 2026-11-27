# Guía rápida de validación: Portal del paciente

**Feature**: `002-portal-paciente` | **Contratos**: [api.yaml](contracts/api.yaml), [ui.md](contracts/ui.md) | **Modelo**: [data-model.md](data-model.md)

Estos escenarios demuestran la feature de principio a fin con la semilla de Clínica Eleva. Las pruebas e2e de Playwright los automatizan. Las fechas se citan por posición en días laborables respecto al día de carga ([datos-demostracion.md de 001](../001-nucleo-agenda/datos-demostracion.md)).

## Requisitos previos

```bash
docker compose up -d --build          # db, api, web y mailpit (bandeja de correo de pruebas)
docker compose run --rm semilla       # Clínica Eleva; la semilla no cambia en esta feature
```

- Portal: `http://localhost:8080/eleva/mis-citas`
- Panel de recepción: `http://localhost:8080/eleva` (clave `eleva-demo-2026`)
- Bandeja de Mailpit (donde llegan los enlaces; nunca sale correo real): `http://localhost:8025`

## Pruebas automáticas

```bash
cd backend && ./mvnw verify                                   # incluye [002] contra PostgreSQL real
cd frontend && npm ci && npm test                             # unitarias del portal y del aviso
URL_BASE=http://localhost:8080 URL_CORREO=http://localhost:8025 npm run e2e
./scripts/trazabilidad.sh specs/002-portal-paciente           # debe salir vacía
```

## Escenarios manuales

### 1. Entrar con el enlace (US1)

1. Abrir el portal y escribir `teresa.mendez@ejemplo.es`. Aparece el aviso neutro de [ui.md §1](contracts/ui.md).
2. En Mailpit, abrir el correo «Tu enlace para entrar en Clínica Eleva» y su enlace. Llegas a «Mis citas» con «Hola, Teresa».
3. Volver a abrir el mismo enlace. Aparece «Este enlace ya no es válido. Pide uno nuevo.»
4. Pedir el enlace para `nadie@ejemplo.es`. Aparece el mismo aviso que en el paso 1 y Mailpit no recibe nada.
5. Pedir 6 enlaces seguidos para el mismo email. El 6.º muestra «Has pedido demasiados enlaces…».

### 2. Ver mis citas (US2)

1. Como Teresa Méndez: en «Próximas citas» aparece primero la del día laborable +1 con María, 10:15–11:15, Primera visita fisioterapia, 50,00 €, «Reservada».
2. Entrar como `rocio.garrido@ejemplo.es`: en «Citas pasadas» aparece la del día laborable −3 con María, 10:00–10:45, Sesión fisioterapia, 40,00 €, «Realizada», sin botones.
3. Los recuentos completos de cada paciente están en [datos-demostracion.md](datos-demostracion.md), que se genera durante la implementación (FR-030).

### 3. Cancelar y avisar a recepción (US3, US4)

1. En el panel, reservar para Laura Romero una cita en un hueco libre de Jorge a más de 24 horas vista. Las pruebas no cancelan citas de la semilla, para poder repetirse.
2. En el portal, como `laura.romero@ejemplo.es`, pulsar «Cancelar cita» y luego «Sí, cancelar». Aparece «Tu cita se ha cancelado.»
3. En el panel, recargar la agenda de Jorge. La franja aparece libre y la cabecera muestra «1 cancelación de pacientes por revisar».
4. Abrir el aviso y pulsar «Marcar como revisada». El aviso desaparece.
5. Fuera de plazo: con una cita a menos de 24 horas, el portal muestra «Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.» y no ofrece el botón. Las pruebas de integración lo comprueban moviendo el `RelojControlable`.

### 4. Aislamiento y móvil

- La sesión del portal no abre el panel: `/eleva` sigue pidiendo la clave.
- La clave del panel no abre el portal.
- En un viewport de 360 px, todas las pantallas del portal y el diálogo del aviso pasan `pantallaAccesible` (axe, sin desbordes y con objetivos táctiles de al menos 44 px).

## Resultado esperado

Todos los escenarios anteriores se cumplen, la suite está en verde y `scripts/trazabilidad.sh specs/002-portal-paciente` no lista nada pendiente. Con eso se cumplen las puertas de merge 2, 4 y 5 de la constitución.
