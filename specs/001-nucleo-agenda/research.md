# Investigación (Fase 0): Núcleo de agenda de CitaClara

**Feature**: `001-nucleo-agenda` | **Fecha**: 26/11/2026 | **Plan**: [plan.md](plan.md)

Stack propuesto por el usuario: Java + Spring Boot (JPA/Hibernate), React, PostgreSQL y Docker, con una apariencia moderna. Este documento fija versiones, patrones y decisiones que resuelven las incógnitas del contexto técnico. Cada decisión sigue el formato Decisión / Motivo / Alternativas.

---

## D1. Versiones base del stack

- **Decisión**: Java 25 (LTS) · Spring Boot 4 (línea estable vigente al implementar; trae Spring Framework 7 e Hibernate ORM 7 / Jakarta Persistence 3.2) · PostgreSQL 18 · React 19 + TypeScript 5 · Vite como empaquetador · Node 24 LTS solo para construir el frontend · Docker Compose v2.
- **Motivo**: son las líneas LTS/estables a 26/11/2026; Spring Boot 4 + Java 25 dan soporte largo y virtual threads maduros. Se fijan versiones exactas (parche) en `pom.xml`, `package.json` y las imágenes Docker al crear el andamiaje, nunca etiquetas flotantes (`latest`).
- **Alternativas**: Java 21 + Spring Boot 3.5 (soporte más corto, sin ventaja para esta feature); Gradle en vez de Maven (sin necesidad que lo justifique; Maven es el estándar más simple para un único módulo).

## D2. Garantía de no solape (RN1, FR-012, FR-013, Principio III)

- **Decisión**: la garantía vive **en la base de datos** con una *exclusion constraint* de PostgreSQL sobre la tabla `cita`:
  `EXCLUDE USING gist (profesional_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado IN ('RESERVADA','COMPLETADA'))` (requiere la extensión `btree_gist`).
  Encima, el servicio hace una **comprobación previa** dentro de la misma transacción para construir el mensaje amable («Jorge ya tiene una cita de 10:00 a 10:45…»). Si dos altas pasan a la vez la comprobación previa, la restricción rechaza la segunda (SQLSTATE `23P01`), que se traduce a «Ese hueco se acaba de ocupar. Elige otro hueco.» (US2-AC5).
- **Motivo**:
  - Es la única garantía que cubre **todos** los caminos de escritura (alta, semilla, futuros importadores) y cualquier número de instancias del backend, tal como exige el Principio III.
  - El rango semiabierto `[)` hace que las citas contiguas no se solapen (US2-AC3) y cualquier intersección positiva sí (casos límite).
  - El predicado parcial `WHERE estado IN (...)` hace que canceladas y no asistidas no ocupen hueco (FR-024) sin lógica adicional.
  - Al estar en `timestamptz`, el cálculo es sobre instantes reales: los cambios de hora no producen solapes fantasma ni huecos fantasma.
- **Alternativas descartadas**:
  - Solo comprobación en Java (`SELECT … luego INSERT`): sufre condición de carrera; falla SC-002.
  - Aislamiento `SERIALIZABLE`: funciona, pero obliga a reintentos genéricos, hace la garantía dependiente de cada transacción y no protege escrituras fuera del servicio.
  - Bloqueo pesimista (`SELECT … FOR UPDATE` sobre el profesional): correcto, pero serializa todas las altas del profesional y es fácil olvidarlo en un camino nuevo.
  - Tabla de franjas fijas con clave única: impone rejilla, que la spec descarta («la agenda no impone una rejilla fija»).

- **Nota de implementación** (26/11/2026): con altas simultáneas, la *exclusion constraint* puede resolver el conflicto como interbloqueo (`40P01`) en lugar de como violación (`23P01`), y PostgreSQL los resuelve de uno en uno cada `deadlock_timeout`. Para evitarlo, el alta bloquea la fila del profesional (`SELECT … FOR UPDATE`) al empezar su transacción: las altas del mismo profesional se serializan y la comprobación previa ve a la ganadora. La garantía sigue siendo la restricción (cubre cualquier otro camino) y el alta reintenta ante un interbloqueo residual.

## D3. Concurrencia en cambios de estado (FR-025, US3-AC7)

- **Decisión**: bloqueo optimista con `@Version` en la entidad `Cita`. Cada cita viaja al cliente con su `version`; el cambio de estado la envía de vuelta. Si no coincide con la actual (porque otra persona ya cambió el estado), se responde 409 con la cita real y el aviso «Otra persona acaba de cambiar esta cita. Ahora está: Completada.».
- **Motivo**: estándar de JPA, sin dependencias, y cubre tanto la colisión simultánea (excepción de bloqueo optimista) como la petición «tardía» que llega con una versión antigua.
- **Alternativas**: `UPDATE … WHERE estado='RESERVADA'` condicional (también válido, pero duplica el mecanismo que JPA ya ofrece); bloqueo pesimista (innecesario).

## D4. Fechas, horas y cambios de horario (FR-026, Principio II)

- **Decisión**:
  - En base de datos, `inicio` y `fin` son `timestamptz` (instantes). En Java, `Instant` en la entidad y `ZonedDateTime` con `ZoneId.of("Europe/Madrid")` en la lógica.
  - La zona es una constante del producto (`ZONA_CLINICA = Europe/Madrid`), no un dato de la clínica: la spec solo contempla hora peninsular.
  - Toda la lógica de «ahora» usa un `java.time.Clock` inyectado (tests deterministas, cálculo de «hoy» y de RN2).
  - El API intercambia **fecha local** (`2026-11-26`) y **hora local** (`10:00`) de Madrid; el backend convierte. El frontend nunca hace aritmética de zonas horarias: muestra lo que recibe y formatea con `Intl` (`es-ES`, `dd/mm/aaaa`, 24 h).
  - `fin = inicio + duración` se calcula sobre el instante (duración real). Una hora local inexistente (salto de primavera) o ambigua (otoño) se rechaza como «hora no válida» — nunca ocurre con el horario 09:00–20:00, pero queda cubierto por test.
  - «Hoy» lo decide el servidor (fecha de Madrid según su reloj), no el dispositivo, para que un portátil mal configurado no muestre otro día.
- **Motivo**: es la forma más simple de que una cita no se desplace, duplique ni desaparezca en días con cambio de hora (caso límite y Principio II).
- **Alternativas**: guardar `timestamp` sin zona en hora local (ambiguo en la hora repetida de octubre); enviar instantes UTC al cliente y convertir en el navegador (traslada el riesgo a cada pantalla).

## D5. Importes (FR-017, Principio II)

- **Decisión**: importes como **céntimos enteros** (`integer` en PostgreSQL, `int`/`long` en Java, `number` entero en JSON: `4000` = 40,00 €). El importe de la cita se copia del precio del servicio al reservar. El formateo a «40,00 €» se hace en un único módulo del frontend con `Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })`.
- **Motivo**: aritmética exacta sin `BigDecimal` ni escalas; imposible descuadrar un céntimo.
- **Nota para futuras features**: `Intl` en `es-ES` no agrupa miles en números de 4 cifras («1000,00 €» pero «10.000,00 €»), conforme a la norma RAE/CLDR. Si una spec futura exige «1.000,00 €», deberá fijarlo y el formateador central lo ajustará en un solo sitio.
- **Alternativas**: `numeric(10,2)` + `BigDecimal` (exacto pero más verboso y con riesgo de escalas distintas entre capas).

## D6. Acceso con clave de clínica y sesión de 8 horas (FR-001–FR-004)

- **Decisión**:
  - Cada clínica tiene un identificador público corto en la URL del panel (`/eleva`). La pantalla de acceso solo pide la clave; el identificador lo aporta la dirección. La raíz `/` muestra un mensaje que pide abrir la dirección del panel de la clínica.
  - La clave se guarda con hash **BCrypt** (`spring-security-crypto`, solo ese módulo).
  - Sesión de servidor (`HttpSession` estándar de Spring) con cookie `HttpOnly`, `SameSite=Strict` y `Secure` fuera del entorno local. En la sesión se guardan `clinicaId` y `accesoEn`.
  - Caducidad **absoluta** de 8 horas: un interceptor compara `accesoEn` con el `Clock`; si han pasado 8 h invalida la sesión y responde 401 `SESION_CADUCADA`. El tiempo de inactividad del contenedor se sube a 8 h para que no caduque antes (el valor por defecto, 30 min, contradiría FR-002).
  - Cierre explícito: `DELETE /api/sesion`.
  - Sin límite de intentos fallidos (deuda consciente FR-004); el mensaje es siempre el mismo y no revela si el identificador de clínica existe.
  - CSRF: `SameSite=Strict` + mismo origen (nginx sirve frontend y API) + el API solo acepta `application/json`. No se añade Spring Security completo.
- **Motivo**: cumple la spec con el mínimo de piezas; nada de usuarios, roles ni tokens firmados que la spec declara fuera de alcance.
- **Alternativas**: Spring Security completo (sobredimensionado para una clave compartida); JWT en cookie (requiere librería y gestión de revocación para el cierre de sesión); probar la clave contra todas las clínicas (imposible con hash con sal y filtra información).

## D7. Aislamiento por clínica (FR-003)

- **Decisión**: todas las tablas de negocio llevan `clinica_id`; todo acceso pasa por repositorios cuyos métodos reciben el `clinicaId` de la sesión (`findByIdAndClinicaId`, etc.). Un recurso de otra clínica responde igual que uno inexistente (404 con mensaje genérico). Hay un test por recurso que intenta leer y modificar datos de otra clínica.
- **Alternativas**: Row Level Security de PostgreSQL (más robusto, pero añade complejidad de conexión por petición; se reevaluará si llegan más caminos de acceso).

## D8. Huecos libres y acciones disponibles: cálculo en el servidor

- **Decisión**: `GET /api/agenda` devuelve ya calculados las citas del día, los **huecos libres** (intervalos del horario sin citas reservadas o completadas, FR-007) y, para cada cita, la lista de **acciones disponibles** (`CANCELAR`, `COMPLETAR`, `MARCAR_NO_ASISTIDA`) según el estado y el reloj del servidor (FR-022, FR-023).
- **Motivo**: las reglas viven en un solo sitio, se prueban en el backend y el frontend no puede desincronizarse con ellas.

## D9. Migraciones y esquema

- **Decisión**: Flyway con SQL plano (`V1__esquema_inicial.sql`, …); Hibernate en modo `validate`.
- **Motivo**: la *exclusion constraint*, `btree_gist`, `unaccent` y los `CHECK` no se pueden expresar con JPA; el esquema debe estar versionado y ser idéntico en todos los entornos.
- **Alternativas**: `ddl-auto=update` (no reproducible, no crea la restricción); Liquibase (más verboso, sin ventaja aquí).

## D10. Búsqueda de pacientes (FR-018)

- **Decisión**: búsqueda por nombre (sin distinguir mayúsculas ni tildes, con la extensión `unaccent` de PostgreSQL) o por teléfono normalizado (solo dígitos y prefijo). Máximo 20 resultados, ordenados por nombre.
- **Motivo**: «Lucia» debe encontrar a «Lucía»; `unaccent` viene incluida en la imagen oficial de PostgreSQL.

## D11. Validación de teléfono y email (FR-019, FR-020)

- **Decisión**:
  - Teléfono: se normaliza quitando espacios, guiones y puntos. Válido si es un número español de 9 cifras que empieza por 6, 7, 8 o 9 (con `+34` opcional) o un número internacional `+` seguido de 8 a 15 cifras. Se guarda normalizado en formato `+34XXXXXXXXX` / `+XXXXXXXX…`.
  - Unicidad por clínica: índice único `(clinica_id, telefono)`. Si el teléfono ya existe, el alta responde 409 `PACIENTE_DUPLICADO` con la ficha existente para que la interfaz ofrezca usarla.
  - Email opcional, validación sintáctica estándar (`@Email` de Jakarta Validation).
- **Alternativas**: libphonenumber (dependencia pesada para una necesidad cubierta con una expresión regular documentada).

## D12. Alta de cita con paciente nuevo, atómica

- **Decisión**: `POST /api/citas` acepta **o** `pacienteId` **o** `nuevoPaciente {nombre, telefono, email?}`. Paciente y cita se crean en la misma transacción: si la cita se rechaza (solape, pasado, fuera de horario), la ficha tampoco se crea, y la interfaz conserva los datos para corregir (FR-016).
- **Motivo**: evita fichas huérfanas y estados intermedios que la recepcionista tendría que entender; un solo envío, una sola respuesta.

## D13. Datos de demostración deterministas (FR-029–FR-033, Principio V)

- **Decisión**:
  - Generador en Java (`semilla/`) con `java.util.Random` de **semilla fija** (su algoritmo está fijado por la especificación de Java, por lo que la secuencia es idéntica en cualquier JVM) y un `Clock` para el día de carga (fecha de Madrid).
  - **Posición relativa por días laborables**: la clínica atiende de lunes a viernes, así que la historia ocupa los **40 días laborables** anteriores al día de carga («8 semanas») y el futuro los **10 días laborables** posteriores («2 semanas»). La cita *k* del «día laborable −n» es siempre la misma (paciente, servicio, hora local, importe, estado) cualquiera que sea el día de la semana de la carga; solo cambian las fechas absolutas. Así se cumplen a la vez FR-029 y el horario de lunes a viernes. El día de carga nunca recibe citas.
  - Proporciones **por cuota exacta**, no por probabilidad: de las *N* citas de la historia, exactamente `round(0,10·N)` no asistidas y `round(0,08·N)` canceladas, elegidas con el mismo `Random` → SC-008 se cumple siempre.
  - Relleno: por profesional y día, desde las 09:00 se encadenan huecos de 0–60 min (múltiplos de 15) y servicios de su especialidad (≈20 % primeras visitas) hasta que la siguiente cita no quepa antes de las 20:00. En el futuro la densidad es menor para dejar huecos libres.
  - Las citas se insertan por el mismo repositorio que las altas, de modo que la *exclusion constraint* también protege la semilla (Principio III: ningún camino exento). Es el único camino que omite RN2 (FR-032).
  - Carga: idempotente; borra y recrea Clínica Eleva en una transacción. Se ejecuta con un comando explícito (servicio `semilla` de Docker Compose), nunca al arrancar.
  - Clave de demostración documentada: `eleva-demo-2026`.
  - Los recuentos exactos (FR-033) se escriben en `specs/001-nucleo-agenda/datos-demostracion.md` durante la implementación, y un test compara la semilla con ese documento: si alguien cambia la semilla sin actualizar las cifras, la suite falla.
- **Aclaración incorporada a la spec** (sesión de clarificación del 26/11/2026): FR-029, FR-031, FR-033, US4 y los casos límite cuentan ya en **días laborables** (40 hacia atrás, 10 hacia delante; cifras citadas como «día laborable −n / +n»).
- **Alternativas**: días naturales relativos (con cargas en días de la semana distintos, las citas caerían en fin de semana o cambiaría el número total: incumple FR-029); fecha de referencia fija (descartada en la clarificación Q1); ficheros SQL con datos fijos (no permite desplazar fechas).

## D14. Pruebas y trazabilidad (Principio VI)

- **Decisión**:
  - Backend: JUnit 5 + AssertJ + Spring Boot Test + **Testcontainers** con PostgreSQL 18 real (la *exclusion constraint* y `unaccent` no existen en H2). Pruebas de concurrencia con 10 hilos sincronizados por `CountDownLatch` contra el API real (SC-002), repetidas en bucle (p. ej. 20 repeticiones).
  - Frontend: Vitest + Testing Library para formateadores y componentes.
  - Extremo a extremo: Playwright (Chromium) contra el `docker compose` completo con la semilla cargada, en viewport de portátil y de **360 px** (SC-009), con comprobación de accesibilidad automática `@axe-core/playwright` (FR-028).
  - **Convención de trazabilidad**: el nombre visible de cada test empieza por la referencia a la spec, p. ej. `[001][FR-012][US2-AC2] rechaza una cita que pisa otra de Jorge`. JUnit con `@DisplayName`, Vitest/Playwright en el título. Un script `scripts/trazabilidad.sh` lista los FR/AC de la spec sin test referenciado y se ejecuta en CI.
- **Alternativas**: H2 en memoria (no reproduce la garantía principal); Cypress (Playwright es más ligero en CI y trae emulación móvil).

## D15. Interfaz «moderna 2026» (Principio VII, FR-027, FR-028)

- **Decisión**:
  - **Tailwind CSS v4** con tokens de diseño en CSS (`@theme`) en espacio de color **OKLCH**, modo claro y oscuro automático (`prefers-color-scheme`), tipografía **Inter Variable** autoalojada (`@fontsource-variable/inter`, sin llamadas a terceros), números tabulares para horas e importes.
  - Lenguaje visual: superficies limpias con bordes suaves (radio 16–20 px), sombras sutiles y profundidad por capas; cabecera fija translúcida (`backdrop-blur`) con el selector de profesional (control segmentado de 2–5 opciones) y la navegación de día; agenda como **línea de tiempo vertical** (sin desplazamiento horizontal en 360 px), con las citas como tarjetas y los huecos libres como bloques punteados con botón «Reservar aquí».
  - Estados con **color + icono + texto** (nunca solo color): Reservada (índigo), Completada (verde), Cancelada (gris, tachado), No asistida (ámbar). Contraste WCAG 2.2 AA y objetivos táctiles ≥ 44 px.
  - Diálogos con el elemento nativo `<dialog>` (en móvil se presenta como hoja inferior), transiciones de día con la **View Transitions API** y respeto a `prefers-reduced-motion`.
  - Iconos: `lucide-react` (solo los usados, eliminados en el empaquetado).
  - Sin librería de componentes ni de estado global: React 19 (`useActionState`, `useOptimistic` no se usa para reservas — la respuesta del servidor manda), `fetch` nativo y estado local. Sin enrutador: la vista depende de la sesión y el día/profesional se reflejan en la query string con `URLSearchParams` (enlaces compartibles y botón atrás).
- **Motivo**: aspecto actual y cuidado con el mínimo de dependencias (Principio IV); todo lo demás lo da la plataforma web de 2026.
- **Alternativas**: MUI/Chakra (pesadas y con estética reconocible); shadcn/ui + Radix (buena opción, pero añade decenas de piezas para 3 pantallas); TanStack Query/React Router (no hay necesidad en la spec que los justifique todavía).

## D16. Empaquetado y despliegue local

- **Decisión**: `docker-compose.yml` con tres servicios — `db` (postgres:18), `api` (imagen multietapa: Maven + JDK 25 → JRE 25) y `web` (Vite build → nginx que sirve el frontend y hace de proxy de `/api` al backend, mismo origen) — y un cuarto servicio `semilla` (misma imagen que `api`, perfil `herramientas`, sin servidor web) para cargar los datos de demostración. Configuración por variables de entorno; sin secretos en el repositorio salvo la clave de demostración, que es pública por diseño.

## Resumen de incógnitas resueltas

| Incógnita del contexto técnico | Resuelta en |
|---|---|
| Versiones de lenguaje y framework | D1 |
| Garantía de no solape con concurrencia | D2 |
| Concurrencia en cambios de estado | D3 |
| Zona horaria y cambios de hora | D4 |
| Representación de importes | D5 |
| Autenticación y caducidad de 8 h | D6 |
| Aislamiento entre clínicas | D7 |
| Lógica de huecos y acciones | D8 |
| Gestión del esquema | D9 |
| Búsqueda sin tildes | D10 |
| Validación de teléfono/email | D11 |
| Atomicidad paciente + cita | D12 |
| Determinismo de la semilla | D13 |
| Estrategia de pruebas y trazabilidad | D14 |
| Tecnología y estética de la interfaz | D15 |
| Ejecución local y en contenedores | D16 |
