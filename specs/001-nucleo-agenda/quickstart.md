# Guía rápida de validación: Núcleo de agenda de CitaClara

**Feature**: `001-nucleo-agenda` | **Plan**: [plan.md](plan.md)

Guía para levantar el producto y comprobar de extremo a extremo que la feature cumple la spec. No contiene implementación; los detalles de API y datos están en [contracts/api.yaml](contracts/api.yaml), [contracts/ui.md](contracts/ui.md) y [data-model.md](data-model.md).

## Requisitos previos

- Docker con Docker Compose v2.
- Para desarrollo y pruebas fuera de contenedores: JDK 25, Node 24 LTS y Docker en marcha (Testcontainers lo usa).

## 1. Levantar el entorno

```bash
docker compose up -d --build          # db (PostgreSQL 18), api (Spring Boot), web (nginx + React)
docker compose run --rm semilla       # carga Clínica Eleva; repetible, recrea los datos de demostración
```

**Resultado esperado**: `docker compose ps` muestra `db`, `api` y `web` en estado *healthy*; la semilla termina con un resumen de recuentos idéntico al de `datos-demostracion.md`.

Abrir `http://localhost:8080/eleva` y entrar con la clave **`eleva-demo-2026`**.

## 2. Ejecutar las pruebas

```bash
cd backend  && ./mvnw verify          # unitarias + integración con Testcontainers (incluye concurrencia)
cd frontend && npm ci && npm test     # Vitest
cd frontend && npm run e2e            # Playwright contra docker compose con la semilla cargada
./scripts/trazabilidad.sh             # lista FR/AC de la spec sin test referenciado; debe salir vacía
```

**Resultado esperado**: todo en verde (puerta de merge 4) y trazabilidad completa (puerta 2).

## 3. Escenarios de validación manual

Cada escenario cita el criterio de la spec que demuestra. «Día +1» = primer día laborable posterior a hoy (research.md D13).

| # | Pasos | Resultado esperado | Spec |
|---|---|---|---|
| 1 | Abrir `/eleva` sin sesión. | Solo se ve la pantalla de acceso. | US1-AC1 |
| 2 | Introducir una clave errónea. | «La clave no es correcta. Revísala e inténtalo de nuevo.» | US1-AC2 |
| 3 | Entrar con la clave buena. | Agenda de hoy; selector con María, Jorge y Lucía. | US1-AC3 |
| 4 | Ir al día laborable −1 con María. | Citas cerradas con hora, paciente, servicio, importe y estado; huecos libres entre ellas. | US1-AC4, US1-AC5 |
| 5 | Ir a un sábado. | «La clínica no atiende este día.» | FR-015 |
| 6 | Día +1, Jorge: «Reservar aquí» en un hueco de al menos 45 min → «Sesión fisioterapia» → paciente existente. | Cita reservada con fin a +45 min y «40,00 €». | US2-AC1 |
| 7 | Reservar otra cita de Jorge que empiece 30 min después de la anterior. | «Jorge ya tiene una cita de … Elige otro hueco.»; datos conservados. | US2-AC2, FR-016 |
| 8 | Reservar justo al terminar la del paso 6. | Se registra (contiguas). | US2-AC3 |
| 9 | Cancelar la cita del paso 6 y reservar de nuevo a esa misma hora. | Cancelada, hueco libre, nueva reserva registrada. | US3-AC1, US2-AC4 |
| 10 | Misma hora que el paso 6 pero con María. | Se registra. | US2-AC6 |
| 11 | Elegir hoy y una hora ya pasada. | «No se pueden crear citas en el pasado…» | US2-AC7 |
| 12 | Alta con paciente nuevo usando el teléfono de un paciente existente. | Aviso de paciente duplicado y opción «Usar esta ficha». | FR-020 |
| 13 | Alta con paciente nuevo válido. | Ficha creada y cita asociada. | US2-AC8 |
| 14 | Con Lucía, comprobar la lista de servicios. | Solo servicios de nutrición. | US2-AC9 |
| 15 | Hora 19:30 con un servicio de 45 min. | Rechazo indicando el horario 09:00 a 20:00. | US2-AC10 |
| 16 | Hoy, en una cita reservada ya empezada: ver acciones. | Solo «Completada» y «No asistió». | US3-AC2, AC3, AC5 |
| 17 | Una cita futura: ver acciones. | Solo «Cancelar cita». | US3-AC4 |
| 18 | Una cita cerrada: ver acciones. | Ninguna. | US3-AC6 |
| 19 | Abrir la misma cita en dos pestañas; marcar en una y luego en otra. | La segunda ve «Otra persona acaba de cambiar esta cita…» y el estado real. | US3-AC7 |
| 20 | Repetir 3–6 con las herramientas del navegador a 360 px de ancho. | Sin desplazamiento horizontal; todo utilizable. | US1-AC7, SC-009 |
| 21 | «Cerrar sesión». | Vuelta a la pantalla de acceso. | FR-002 |

## 4. Validaciones automáticas clave (no reproducibles a mano)

| Validación | Prueba | Spec |
|---|---|---|
| 10 altas simultáneas sobre el mismo hueco → exactamente 1 registrada, 9 rechazos `HUECO_RECIEN_OCUPADO`/`SOLAPE`; 20 repeticiones | Integración backend (Testcontainers) | US2-AC5, FR-013, SC-001, SC-002 |
| Solape parcial, contención, coincidencia exacta y contigüidad | Integración backend | FR-012, casos límite |
| Inserción directa en BD que solapa → la rechaza la restricción | Integración de esquema | Principio III |
| Dos cambios de estado simultáneos → uno aplicado, otro 409 | Integración backend | FR-025 |
| Sesión con reloj avanzado 8 h → 401 `SESION_CADUCADA`; acción no aplicada | Integración backend con `Clock` controlado | FR-002 |
| Clave de clínica A no ve ni modifica datos de clínica B | Integración backend | FR-003 |
| Cita en el día del cambio de hora de marzo y de octubre: hora local y duración correctas | Unitaria + integración | FR-026, Principio II |
| Dos cargas de la semilla (mismo día y días distintos, incluido un lunes y un miércoles) → idénticas por posición relativa; proporciones 9–11 % y 7–9 %; 0 solapes | Integración de la semilla | US4, SC-008, FR-029–FR-033 |
| Recuentos de la semilla = `datos-demostracion.md` | Integración de la semilla | FR-033, puerta de merge 5 |

## 5. Limpieza

```bash
docker compose down -v                # elimina contenedores y volumen de datos
```
