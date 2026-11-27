# Guía rápida de validación: Auditoría de seguridad

**Feature**: `005-security-audit` | **Plan**: [plan.md](plan.md)

Cómo reproducir la auditoría y comprobar que lo entregado cumple la spec. Contratos y datos: [contracts/](contracts/) y [data-model.md](data-model.md). No contiene implementación.

## Requisitos previos

- Docker con Docker Compose v2, JDK 25 y Node 24 LTS (igual que la 001).
- Todo se ejecuta **en local contra la semilla de demostración**. No se prueba nunca contra datos reales ni servicios de terceros (FR-019).

## 1. Entorno limpio y reproducible

```bash
docker compose down -v && docker compose up -d --build --wait
docker compose run --rm semilla            # Clínica Eleva
git rev-parse HEAD                         # anotar el commit en la cabecera del informe
```

**Esperado**: `db`, `api` y `web` *healthy*; el commit anotado es el que figura en `informe-auditoria.md`.

## 2. Suites y trazabilidad

```bash
cd backend  && ./mvnw verify                       # incluye es.citaclara.seguridad.* e InformeAuditoriaTest
cd frontend && npm ci && npm test
cd frontend && URL_BASE=http://localhost:8080 npm run e2e   # incluye seguridad.spec.ts
./scripts/trazabilidad.sh specs/005-security-audit          # debe salir vacía
./scripts/trazabilidad.sh                                   # la 001 sigue sin huecos
```

**Esperado**: todo en verde; las dos trazabilidades salen vacías.

## 3. Escenarios de validación

Cada fila cita el requisito o escenario que demuestra.

| # | Pasos | Resultado esperado | Spec |
|---|---|---|---|
| 1 | Abrir `informe-auditoria.md` y contar la tabla de veredictos. | 10 filas, A01–A10, sin omitir ninguna. | FR-001, SC-001, US1-AC1 |
| 2 | Tomar un hallazgo al azar y seguir solo su ficha. | Se reproduce; si estaba `corregido`, el test referenciado pasa y, revirtiendo la corrección, falla. | FR-002, SC-003, US1-AC2 |
| 3 | Revisar fichas críticas y altas. | Todas con mejora, esfuerzo y criterio de aceptación. | FR-013, SC-002, US3-AC1 |
| 4 | Abrir `requisitos-pagos.md`. | Todos los puntos de la lista de Stripe/PCI con veredicto y requisitos `RP-nnn` que cubren recepción y paciente en línea. | FR-010, FR-011, SC-004, US2-AC1, US2-AC2 |
| 5 | Comprobar RP sobre el importe. | Importe siempre decidido por el servidor, en céntimos y en euros. | US2-AC3 |
| 6 | Con dos sesiones de clínicas distintas, pedir por todas las rutas y verbos recursos de la otra clínica. | Siempre 404 igual que un recurso inexistente; cero datos ajenos. | FR-003, SC-006 |
| 7 | `curl -sI http://localhost:8080/` y a `/eleva`. | Las cabeceras de [contracts/cabeceras-seguridad.md](contracts/cabeceras-seguridad.md), sin `Server` con versión. | FR-005, FR-006 |
| 8 | Abrir la aplicación en Chrome con la consola. | Cero violaciones de la política de contenido. | FR-005 |
| 9 | Provocar un error de duplicado de paciente y revisar `docker compose logs api`. | Sin teléfono, nombre ni email en el registro. | FR-008, SC-005, US4-AC2 |
| 10 | Buscar un paciente con `q` de 81 caracteres y con `%`. | Rechazo con mensaje claro; el `%` se trata como texto literal. | FR-009 |
| 11 | En una rama de prueba añadir una dependencia con vulnerabilidad grave conocida. | La integración continua falla con un motivo legible. | FR-016, SC-007, US5-AC1 |
| 12 | En una rama de prueba añadir una clave tipo `sk_live_…` falsa. | gitleaks bloquea el merge. | FR-016, SC-007, US5-AC2 |
| 13 | Arrancar con perfil `produccion` y valores por defecto. | El arranque falla con un mensaje claro; la semilla se niega. | FR-006 |
| 14 | Flujo de recepción (acceso, buscar paciente, reservar cita) en 360 px y en portátil. | Sin pasos añadidos ni mensajes técnicos; axe sin infracciones. | SC-009, FR-015 |

**Solo tras la puerta G1** (propietario de la 001 ha aprobado):

| # | Pasos | Resultado esperado | Spec |
|---|---|---|---|
| 15 | Fallar el acceso repetidamente desde un origen. | Bloqueo con «Has probado demasiadas veces. Espera N minutos…»; tampoco entra con la clave buena durante el bloqueo; pasado el tiempo (reloj controlado) vuelve a entrar. | FR-004 |
| 16 | Entrar con dos usuarios y revocar uno. | El revocado pierde el acceso en la siguiente petición; el otro sigue. | FR-021 |
| 17 | Repetir el escenario 14. | Igual de usable que antes, con un campo más (usuario). | SC-009 |

## 4. Reauditoría

Repetir las secciones 1 a 3 en la rama corregida y volcar la comparación en la sección «Reauditoría» del informe.

**Esperado**: cero críticos y altos abiertos salvo los derivados con solicitud registrada al propietario de su spec (SC-008).

## 5. Herramientas opcionales de apoyo

Un análisis dinámico con OWASP ZAP contra el entorno local puede contrastar los hallazgos. Es opcional, no entra en CI y sus resultados se incorporan al informe solo si se verifican con un test propio.
