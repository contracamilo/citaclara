---

description: "Lista de tareas de la feature 005-security-audit"
---

# Tareas: Auditoría de seguridad (Stripe y OWASP Top 10)

**Entrada**: documentos de diseño en `specs/005-security-audit/` ([spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)).

**Pruebas**: obligatorias (Principio VI y FR-017). Cada test se llama `[005][FR-xxx][USn-ACm]` (convención [001]). Los tests que reproducen un hallazgo se escriben **rojos** junto a su corrección en la fase US3 y se integran **verdes**; no se desactiva ningún test.

**Organización**: por historia de usuario. Los hallazgos concretos (H1–H15 de [research.md](research.md) D9) son **candidatos**: cada tarea de corrección se ejecuta solo si el informe (US1) confirma el hallazgo como crítico o alto y no toca una spec ajena; en otro caso se anota «derivado» o «no aplica» en su ficha.

**Nomenclatura**: las «Fases» de este fichero son de tareas (1 a 8). Las del plan se citan siempre como «Fase N del plan» ([plan.md](plan.md): 1 auditoría, 2 correcciones sin tocar otras specs, 3 correcciones tras G1, 4 cierre).

## Formato: `[ID] [P?] [Historia] Descripción con ruta`

- **[P]**: se puede hacer en paralelo (ficheros distintos, sin dependencias abiertas).
- **[USn]**: historia de la spec (US1 informe OWASP, US2 Stripe/PCI, US3 plan de mejoras y correcciones, US4 datos de pacientes, US5 controles de CI).

## Convenciones de rutas

- Backend: `backend/src/main/java/es/citaclara/…` y `backend/src/test/java/es/citaclara/…`
- Frontend: `frontend/src/…` y `frontend/tests/…`
- Entregables de la feature: `specs/005-security-audit/…`

---

## Fase 1: Preparación

**Propósito**: dejar el punto de partida medido y la estructura lista.

- [X] T001 **Aprobación de la spec (Principio I)**: tras revisar las clarificaciones del 27/11/2026, el propietario de la feature pasa `**Estado**` de «Borrador» a «Aprobada» en `specs/005-security-audit/spec.md` y la fila de `specs/MAPA.md`; hasta entonces no se implementa nada (decisión humana, no automática). La línea `**Excluidos de la trazabilidad**: SC-003 SC-009` ya está en la spec
- [X] T002 [P] Crear los paquetes vacíos `backend/src/main/java/es/citaclara/seguridad/package-info.java` y `backend/src/test/java/es/citaclara/seguridad/` (código transversal de seguridad separado de `acceso`)
- [ ] T003 Medir la línea base: ejecutar `cd backend && ./mvnw verify`, `cd frontend && npm ci && npm test`, `URL_BASE=http://localhost:8080 npm run e2e` y `./scripts/trazabilidad.sh`; anotar el `git rev-parse HEAD` que se auditará y confirmar que todo está en verde antes de cambiar nada

---

## Fase 2: Cimientos (bloquea todas las historias)

**Propósito**: que la trazabilidad funcione para esta spec y que haya soporte común de pruebas.

**⚠️ CRÍTICO**: ninguna historia empieza antes de terminar esta fase.

- [X] T004 Adaptar `scripts/trazabilidad.sh` (D15): leer los criterios excluidos de la línea `**Excluidos de la trazabilidad**:` de la spec, con `SC-004 SC-005 SC-006` como valor por defecto si la línea no existe (la 001 debe seguir saliendo igual). Los encabezados en español de la 005 ya los reconoce el script, y no hace falta aceptar los de la plantilla en inglés
- [X] T005 Comprobar `scripts/trazabilidad.sh` sobre `specs/001-nucleo-agenda` (salida vacía, mismo total que antes) y sobre `specs/005-security-audit` (lista todos los FR, SC y escenarios aún sin test, con SC-003 y SC-009 excluidos); avisar al propietario de la 001 del cambio de script en la descripción del PR
- [X] T006 [P] Ampliar `backend/src/test/java/es/citaclara/soporte/DatosPrueba.java` con una segunda clínica con profesionales, servicios, pacientes y citas propios (para las pruebas entre clínicas)
- [X] T007 [P] Crear `backend/src/test/java/es/citaclara/soporte/CapturaRegistros.java`: *appender* de Logback en memoria con utilidades «el texto capturado no contiene X» para comprobar que los registros no filtran datos personales
- [X] T008 [P] Crear `backend/src/test/java/es/citaclara/seguridad/LectorInforme.java`: analizador de `informe-auditoria.md` y `requisitos-pagos.md` según [contracts/informe-auditoria.md](contracts/informe-auditoria.md) (cabecera, tabla de diez veredictos, fichas, secciones fijas)

**Punto de control**: la trazabilidad es fiable para 005 y las utilidades de prueba existen.

---

## Fase 3: Historia US1 — Informe de auditoría contra OWASP Top 10 (P1) 🎯 MVP

**Objetivo**: informe reproducible con veredicto y evidencia por categoría OWASP Top 10:2025.

**Prueba independiente**: `InformeAuditoriaTest` y `FichasHallazgoTest` en verde sobre `informe-auditoria.md`, y un revisor ajeno reproduce un hallazgo al azar siguiendo solo su ficha.

### Pruebas de US1 (escribir primero; las del informe fallan hasta T020)

- [X] T009 [P] [US1] `InformeAuditoriaTest` en `backend/src/test/java/es/citaclara/seguridad/InformeAuditoriaTest.java`: `[005][FR-001][FR-018][SC-001][US1-AC1]` exige cabecera con commit de 7 a 40 hexadecimales, fecha `DD/MM/AAAA` y `OWASP Top 10:2025`, y exactamente 10 filas A01–A10 con veredicto ∈ {`cumple`, `cumple con reservas`, `no cumple`, `no aplica`} (este último con justificación en `Evidencia`)
- [X] T010 [P] [US1] `FichasHallazgoTest` en `backend/src/test/java/es/citaclara/seguridad/FichasHallazgoTest.java`: `[005][FR-002][FR-013][FR-020][FR-021][FR-022][SC-002][SC-008][US1-AC2][US1-AC3][US3-AC1]` aplica las cinco reglas del contrato (identificadores `H-nn` únicos y sin huecos; mejora y criterio de aceptación no vacíos si crítica o alta; `derivado` exige referencia al mapa; `corregido` exige un test `[005]…` que exista en el repositorio (FR-020: todo hallazgo crítico o alto corregido tiene su prueba de regresión); sin datos personales reales ni secretos) y una sexta regla **incondicional** (no depende de G1): H2 y H3 constan o bien como `corregido` con su test (`LimiteIntentosTest`, `UsuariosIndividualesTest`) o bien como `derivado` con la solicitud registrada en `specs/MAPA.md`, y no queda ningún hallazgo crítico o alto `abierto` sin una de las dos cosas (FR-021, FR-022, SC-008)
- [X] T011 [P] [US1] `AislamientoClinicaTest` en `backend/src/test/java/es/citaclara/seguridad/AislamientoClinicaTest.java`: `[005][FR-003][SC-006]` con dos clínicas, recorre **todos** los endpoints y verbos del API con identificadores de la otra clínica (citas, pacientes, profesionales, servicios, agenda, huecos) y exige la misma respuesta 404 que un recurso inexistente y cero datos ajenos; incluye identificadores adivinables y pacientes duplicados entre clínicas
- [X] T012 [P] [US1] `SesionSeguridadTest` en `backend/src/test/java/es/citaclara/seguridad/SesionSeguridadTest.java`: `[005][FR-004]` comprueba cookie `HttpOnly`, `SameSite=Strict`, regeneración de sesión al iniciar (fijación), caducidad absoluta de 8 h con `RelojControlable`, cierre de sesión que invalida la cookie y respuesta equivalente para clínica inexistente y clave incorrecta
- [X] T013 [P] [US1] `EntradasMaliciosasTest` en `backend/src/test/java/es/citaclara/seguridad/EntradasMaliciosasTest.java`: `[005][FR-005]` envía metacaracteres SQL, comillas, HTML/script, Unicode y cadenas largas en nombre, teléfono, email, `q` y rutas; exige respuestas JSON con `nosniff`, datos almacenados literalmente y ninguna alteración de otras filas

### Implementación de US1

- [X] T014 [US1] Crear el esqueleto de `specs/005-security-audit/informe-auditoria.md` con la cabecera de reproducibilidad (commit de T003, fecha, alcance, método, herramientas), la tabla de diez categorías vacía y las secciones fijas finales del contrato
- [X] T015 [US1] Revisar **A01 Control de acceso defectuoso** y **A06 Diseño inseguro**: `backend/src/main/java/es/citaclara/**/Controlador*.java`, `Repositorio*.java`, `ControladorAcceso.java` (enumeración por `GET /api/clinicas/{identificador}`, H11), modelo de `V1__esquema_inicial.sql` y respuesta `PACIENTE_DUPLICADO` (H15); anotar veredicto y evidencia en el informe
- [X] T016 [US1] Revisar **A02 Configuración incorrecta** y **A04 Fallos criptográficos**: `backend/src/main/resources/application.yml`, `docker-compose.yml`, `frontend/nginx.conf`, `backend/Dockerfile`, `frontend/Dockerfile`, `README.md`, `CLAUDE.md`, salida de `ComandoSemilla` (H1, H4, H5, H6, H7, H13); comprobar BCrypt y TLS; anotar veredicto y evidencia
- [X] T017 [US1] Revisar **A03 Cadena de suministro** y **A08 Integridad de software o datos**: ejecutar localmente, con las imágenes de contenedor oficiales de cada herramienta y una versión fija anotada en la cabecera del informe (sin instalarlas en el proyecto), OSV-Scanner sobre `backend/pom.xml` y `frontend/package-lock.json` y gitleaks sobre el repositorio y su historial; revisar `.github/workflows/ci.yml` (acciones por etiqueta, H12) e imágenes por etiqueta; volcar resultados a la sección «Resultado de las herramientas automáticas» y anotar veredictos
- [X] T018 [US1] Revisar **A05 Inyección** y **A10 Mala gestión de condiciones excepcionales**: `RepositorioPaciente.buscar` (comodines `%`/`_` y longitud de `q`, H10), `ManejadorErrores.java` y registro de excepciones (H8), cuerpos truncados, JSON profundo y tamaños; anotar veredicto y evidencia
- [X] T019 [US1] Revisar **A07 Fallos de autenticación** y **A09 Fallos de registro y alerta**: `ServicioAcceso.java`, `InterceptorSesion.java`, `ConfiguracionWeb.java` (sin límite de intentos H2, clave compartida H3, clave de demostración publicada H1) y ausencia de registro de eventos de seguridad (H9); anotar veredicto y evidencia
- [X] T020 [US1] Consolidar el informe: asignar `H-nn` consecutivos, severidad según el criterio D4, parte afectada, `Verificación` (con el test de T011–T013 o el que se escriba en US3), mejora, esfuerzo, criterio de aceptación y estado; completar la sección «Requisitos para otras features» para 002, 003 y 004; hasta aquí `InformeAuditoriaTest` y `FichasHallazgoTest` deben pasar
- [X] T021 [P] [US1] `AlcanceLocalTest` en `backend/src/test/java/es/citaclara/seguridad/AlcanceLocalTest.java`: `[005][FR-019][US1-AC2]` comprueba que las pruebas de seguridad solo usan el servidor local de pruebas (URL base local y base de datos de Testcontainers) y que el informe declara en `Alcance` que no se probó contra datos reales ni terceros
- [ ] T022 [US1] **Puerta G0**: pedir la revisión del informe al propietario de la feature y a una persona ajena a la auditoría que reproduzca al menos el 90 % de los hallazgos solo con su ficha (SC-003, manual); incorporar las correcciones de redacción

**Punto de control**: US1 completa y entregable por sí sola (informe verificado).

---

## Fase 4: Historia US2 — Preparación para pagos con Stripe (P1)

**Objetivo**: lista de comprobación de Stripe/PCI con veredicto y requisitos `RP-nnn` verificables para la futura integración.

**Prueba independiente**: `RequisitosPagosTest` en verde sobre `requisitos-pagos.md`.

### Pruebas de US2

- [X] T023 [P] [US2] `RequisitosPagosTest` en `backend/src/test/java/es/citaclara/seguridad/RequisitosPagosTest.java`: `[005][FR-010][SC-004][US2-AC1]` exige que cada punto de la lista de Stripe/PCI tenga veredicto (`se cumple`, `no se cumple` o `no aplica todavía`) y `[005][FR-011][US2-AC2][US2-AC3]` exige requisitos `RP-nnn` únicos que cubran: tarjeta nunca en CitaClara, firma de avisos verificada sobre el cuerpo sin modificar, tratamiento idempotente de avisos repetidos o desordenados, importe decidido por el servidor en céntimos y en euros, claves restringidas fuera del código, conciliación al céntimo, y los dos orígenes de pago (recepción y paciente en línea) con referencias no adivinables, de un solo uso y con caducidad

### Implementación de US2

- [X] T024 [US2] Crear `specs/005-security-audit/requisitos-pagos.md` con la lista de comprobación de Stripe y PCI DSS para comercios que delegan la captura (D5), un veredicto por punto contra el sistema actual y enlaces a la [guía de seguridad de Stripe](https://docs.stripe.com/security/guide), [avisos](https://docs.stripe.com/webhooks) y [claves](https://docs.stripe.com/keys)
- [X] T025 [US2] Redactar en `specs/005-security-audit/requisitos-pagos.md` los requisitos `RP-001…` (enunciado verificable, origen `recepcion`/`paciente en línea`/`ambos`, verificación, fuente) tal como los fija [research.md](research.md) D5 y [data-model.md](data-model.md)
- [ ] T026 [US2] Contrastar cada `RP-nnn` y cada veredicto de la lista con la documentación vigente de Stripe en el momento de la auditoría y anotar la fecha de consulta en la cabecera del documento
- [X] T027 [US2] Enlazar `requisitos-pagos.md` desde la sección «Requisitos para otras features» del informe para que los propietarios de 002 (portal del paciente) y de la futura feature de pagos los apliquen sin editar sus specs

**Punto de control**: US2 completa; US1 y US2 pueden presentarse como MVP de diagnóstico.

---

## Fase 5: Historia US3 — Plan de mejoras y correcciones (P2)

**Objetivo**: plan priorizado y corrección de los hallazgos críticos y altos que no tocan specs ajenas (FR-020); los demás, derivados con solicitud registrada.

**Prueba independiente**: cada hallazgo crítico o alto tiene mejora y criterio; los corregidos tienen un test rojo→verde; los derivados constan en el mapa.

**Dependencia**: requiere el informe de US1 (T020). Cada grupo de abajo se ejecuta **solo si el informe lo confirma como crítico o alto**; si es medio o bajo, pasa al plan de seguimiento (T031 y T076).

### Pruebas y registro de US3

- [X] T028 [P] [US3] `MapaSpecsTest` en `backend/src/test/java/es/citaclara/seguridad/MapaSpecsTest.java`: `[005][FR-014][US3-AC2]` exige que cada hallazgo `derivado` figure en `specs/MAPA.md` con spec afectada y propietario, y que ninguna spec ajena (002, 003, 004) haya sido modificada por esta rama
- [X] T029 [P] [US3] `MensajesSeguridadTest` en `backend/src/test/java/es/citaclara/seguridad/MensajesSeguridadTest.java`: `[005][FR-015]` comprueba que todos los mensajes visibles nuevos (413, 429, bloqueo, permiso) están en español de España, sin códigos ni jerga y con indicación de qué hacer
- [X] T030 [US3] Registrar en `specs/MAPA.md` las solicitudes al propietario de la 001 (H2 limitación de intentos, H3 usuarios individuales, H11 contrato público de clínica, H14 retención y derechos) con el texto exacto propuesto, y avisar a los propietarios de 002, 003 y 004 de los requisitos que les afectan
- [X] T031 [US3] Completar en el informe el **plan de mejoras ordenado por riesgo** de todos los hallazgos, con esfuerzo relativo (S/M/L, D18), parte (servidor/interfaz/entorno), propietario previsto y criterio de aceptación (FR-013); T076 recoge después solo lo que queda para el seguimiento

### Correcciones que no tocan otras specs (Fase 2 del plan)

#### Cabeceras y endurecimiento de nginx (H6, H7, H13)

- [ ] T032 [US3] Escribir `frontend/tests/e2e/seguridad.spec.ts`: `[005][FR-005][FR-006]` comprueba en cada ruta (`/`, `/eleva`, `/assets/*`, `/api/*`) las cabeceras de [contracts/cabeceras-seguridad.md](contracts/cabeceras-seguridad.md), ausencia de `Server` con versión, cero violaciones de la política de contenido (escuchando `securitypolicyviolation`), `Cache-Control: no-store` en `/api` y la cookie con `HttpOnly` y `SameSite=Strict`; debe fallar antes de T033
- [ ] T033 [US3] Aplicar en `frontend/nginx.conf` las cabeceras, `server_tokens off`, `client_max_body_size 64k`, `proxy_set_header X-Forwarded-For $remote_addr` (sin concatenar el valor del cliente) y `Strict-Transport-Security` solo con `TLS_ACTIVO=true`; si la interfaz exige `unsafe-inline`, documentarlo como hallazgo bajo y no añadirlo en silencio
- [ ] T034 [US3] Añadir `limit_req` en `frontend/nginx.conf`: `/api/` 20 peticiones por segundo con ráfaga de 40 y ruta de acceso 5 por minuto con ráfaga de 5, con respuesta 429 y 413 que la interfaz traduce a un mensaje comprensible en `frontend/src/api/cliente.ts` y `frontend/src/estilos.css` si procede
- [ ] T035 [US3] Si H13 se confirma como alto: ejecutar nginx sin privilegios y la API con sistema de ficheros de solo lectura y límites en `docker-compose.yml`, `frontend/Dockerfile` y `backend/Dockerfile`; justificar por escrito cualquier imagen nueva (Principio IV)

#### Configuración segura por entorno (H1, H4, H5)

- [ ] T036 [P] [US3] `PerfilProduccionTest` en `backend/src/test/java/es/citaclara/seguridad/PerfilProduccionTest.java`: `[005][FR-006]` el arranque con perfil `produccion` **falla** con contraseña de BD de desarrollo, `COOKIE_SEGURA` distinta de `true` o clínicas con la clave de demostración, con un mensaje claro; con valores correctos arranca
- [ ] T037 [US3] Crear `backend/src/main/java/es/citaclara/seguridad/ValidacionProduccion.java` (activo solo con el perfil `produccion`) y ajustar `backend/src/main/resources/application.yml` para que el perfil exija `DB_CLAVE` sin valor por defecto
- [ ] T038 [US3] Hacer que `backend/src/main/java/es/citaclara/semilla/ComandoSemilla.java` se niegue a ejecutarse con el perfil `produccion` (H1) y dejar de imprimir la clave de demostración fuera del perfil de demostración
- [ ] T039 [P] [US3] Crear `docker-compose.produccion.yml` sin valores por defecto para secretos, con `COOKIE_SEGURA=true` y `TLS_ACTIVO`, y etiquetar los valores de `docker-compose.yml` como «solo desarrollo»; actualizar `README.md` con la advertencia

#### Límites de entrada (H10)

- [ ] T040 [P] [US3] `LimitesEntradaTest` en `backend/src/test/java/es/citaclara/seguridad/LimitesEntradaTest.java`: `[005][FR-009]` con `q` de 80 caracteres (acepta) y de 81 (rechaza con mensaje de usuario), `%`, `_` y `\` tratados como texto literal, cuerpo mayor de 64 KB rechazado y JSON mal formado o profundo con 400 sin traza; debe fallar antes de T041
- [ ] T041 [US3] Limitar `q` a 80 caracteres y escapar `%`, `_` y `\` en `backend/src/main/java/es/citaclara/paciente/ServicioPacientes.java` y `RepositorioPaciente.java` (cláusula `ESCAPE`), y fijar tamaños máximos de cuerpo en `backend/src/main/resources/application.yml`

#### Cambios tras la puerta G1 (tocan la spec 001)

- [ ] T042 [US3] **Puerta G1**: no empezar T043–T056 hasta que el propietario de la 001 apruebe por escrito las solicitudes de T030 (limitación de intentos FR-022, usuarios individuales FR-021, contrato público) y actualice `specs/001-nucleo-agenda/spec.md`; si no aprueba, dejar H2 y H3 en estado `derivado` en el informe y saltar a T057
- [ ] T043 [P] [US3] `LimiteIntentosTest` en `backend/src/test/java/es/citaclara/seguridad/LimiteIntentosTest.java`: `[005][FR-022]` con `RelojControlable`, 3 fallos seguidos desde un origen producen 429 `ACCESO_BLOQUEADO` con `Retry-After` de 300 s, y los siguientes fallos tras el bloqueo duplican la espera (600, 1200, 2400 y 3600 s como máximo); 15 fallos desde cualquier origen en 15 minutos bloquean la clínica 15 minutos y una sesión ya abierta sigue funcionando; un acceso correcto o 24 h sin fallos reinician la cuenta; la clave correcta tampoco entra durante el bloqueo; tras el plazo vuelve a entrar; un origen distinto no queda bloqueado; clínica inexistente y clave incorrecta cuentan igual y responden igual; intentos simultáneos no superan el umbral
- [ ] T044 [US3] Crear `backend/src/main/resources/db/migration/V2__intentos_acceso.sql`: tabla `intento_acceso` con `id bigint GENERATED ALWAYS AS IDENTITY`, `clinica_id bigint` nullable, `identificador varchar(40)`, `origen varchar(64)`, `instante timestamptz`, `correcto boolean`; índices `(identificador, origen, instante)` y `(identificador, instante)`; **sin** guardar la clave, ni parcial
- [ ] T045 [US3] Crear `backend/src/main/java/es/citaclara/acceso/ServicioBloqueoAcceso.java` que calcula el bloqueo a partir de los intentos recientes con el `Clock` inyectado, purga los de más de 24 h e integrarlo en `ServicioAcceso.iniciarSesion` y `ControladorAcceso` con el mensaje «Has probado demasiadas veces. Espera N minutos e inténtalo de nuevo.» en `comun/errores/Mensajes.java`
- [ ] T046 [P] [US3] `UsuariosIndividualesTest` en `backend/src/test/java/es/citaclara/seguridad/UsuariosIndividualesTest.java`: `[005][FR-021]` acceso con usuario y clave, atribución del usuario en la sesión, revocación inmediata de un usuario sin afectar a otro, respuesta idéntica para usuario inexistente, inactivo o clave incorrecta, rol `RECEPCION` sin permiso para `/api/usuarios` (403 `PERMISO_INSUFICIENTE`) y aislamiento entre clínicas
- [ ] T047 [US3] Crear `backend/src/main/resources/db/migration/V3__usuarios.sql`: tabla `usuario` con `id bigint GENERATED ALWAYS AS IDENTITY`, `clinica_id bigint NOT NULL` con clave foránea a `clinica` y `UNIQUE (id, clinica_id)`, `nombre_usuario varchar(60)` con `UNIQUE (clinica_id, lower(nombre_usuario))`, `nombre varchar(120)`, `clave_hash varchar(100)` (BCrypt), `rol varchar(16)` con `CHECK (rol IN ('RECEPCION','ADMINISTRACION'))`, `activo boolean`, `creado_en timestamptz`
- [ ] T048 [US3] Crear el paquete de usuarios (`backend/src/main/java/es/citaclara/acceso/Usuario.java`, `RepositorioUsuario.java`, `ServicioUsuarios.java`, `ControladorUsuarios.java`) según [contracts/api-acceso.yaml](contracts/api-acceso.yaml): listar, crear y revocar, solo para `ADMINISTRACION`, siempre filtrado por la clínica de la sesión
- [ ] T049 [US3] Cambiar `ServicioAcceso.java`, `InterceptorSesion.java` y `SesionClinica.java` para que la sesión guarde `usuarioId` además de `clinicaId`, compruebe que el usuario sigue `activo` en cada petición y mantenga el mismo coste de respuesta ante usuario inexistente; añadir `GET /api/sesion`
- [ ] T050 [US3] Periodo de transición: aceptar temporalmente la clave de clínica **y** el usuario, con un aviso en el informe; retirar la clave compartida (`clinica.clave_hash`) en una migración posterior cuando el propietario de la 001 lo confirme
- [ ] T051 [US3] Actualizar `backend/src/main/java/es/citaclara/semilla/CargadorSemilla.java` y `CatalogoDemostracion.java` con usuarios de demostración deterministas y solicitar al propietario de la 001 que actualice `specs/001-nucleo-agenda/datos-demostracion.md`; `DatosDemostracionDocumentadosTest` debe seguir en verde
- [ ] T052 [US3] Adaptar `frontend/src/acceso/PantallaAcceso.tsx` y `frontend/src/api/cliente.ts`: campo de usuario, mensajes `CREDENCIALES_INCORRECTAS` y de bloqueo en español de España y sin jerga, y objetivo táctil ≥ 44 px; actualizar `frontend/tests/unit/acceso.test.tsx`
- [ ] T053 [US3] Ampliar `frontend/tests/e2e/seguridad.spec.ts` con el bloqueo visible en 360 px y en portátil, comprobando texto no desbordado, objetivos táctiles ≥ 44 px y axe (`pantallaAccesible`)
- [ ] T054 [US3] Adaptar `frontend/tests/e2e/preparar.ts` y `utilidades.ts` para iniciar sesión con usuario de demostración y que las pruebas e2e existentes sigan en verde
- [ ] T055 [US3] Si H11 se confirma como alto: acotar `GET /api/clinicas/{identificador}` en `ControladorAcceso.java` según lo que apruebe el propietario de la 001 (por ejemplo respuesta uniforme y límite de peticiones) y ajustar su contrato
- [ ] T056 [US3] Comprobar que las pruebas de solape y altas simultáneas de la 001 (`AltaConcurrenteTest`, `RestriccionSolapeEsquemaTest`, `CambioEstadoConcurrenteTest`) siguen en verde tras los cambios de acceso (Principio III)

### Cierre de US3

- [X] T057 [US3] Para cada hallazgo crítico o alto corregido, actualizar su ficha a `corregido` con el identificador del test rojo→verde; para cada derivado, `derivado` con referencia al mapa
- [X] T058 [US3] Verificar que `FichasHallazgoTest` y `MapaSpecsTest` pasan con el informe actualizado

**Punto de control**: no queda hallazgo crítico o alto sin corrección o solicitud registrada (SC-008).

---

## Fase 6: Historia US4 — Protección de datos de pacientes (P2)

**Objetivo**: inventario de datos personales y de salud, registros sin datos innecesarios y evaluación de los derechos RGPD.

**Prueba independiente**: `InventarioDatosTest` y `RegistroEventosTest` en verde; el informe responde si se pueden atender acceso, rectificación y supresión.

### Pruebas de US4

- [X] T059 [P] [US4] `InventarioDatosTest` en `backend/src/test/java/es/citaclara/seguridad/InventarioDatosTest.java`: `[005][FR-012][SC-005][US4-AC1][US4-AC3]` exige en el informe un inventario con `dato`, `ubicacion`, `acceso`, `proteccion`, `conservacion` y `derechos` para cada dato personal o de salud, y el plazo de referencia de 5 años desde la última cita con anonimización posterior que no altere importes ni totales históricos
- [X] T060 [P] [US4] `RegistroEventosTest` en `backend/src/test/java/es/citaclara/seguridad/RegistroEventosTest.java`: `[005][FR-008][SC-005][US4-AC2]` provoca un duplicado de paciente, un acceso fallido, una sesión caducada y un recurso ajeno con `CapturaRegistros`, y comprueba que ningún registro contiene nombre, teléfono, email ni clave, que cada evento de seguridad lleva `instante`, `evento`, `clinicaId`, `origen` truncado e `idCorrelacion`, y que las excepciones se registran con clase y `SQLState` sin el mensaje del controlador; debe fallar antes de T062

### Implementación de US4

- [X] T061 [US4] Completar la sección «Inventario de datos personales» de `specs/005-security-audit/informe-auditoria.md`: nombre, teléfono, email, citas y especialidad (dato de salud implícito), clave; ubicación (tablas, registros, copias, salida de la semilla), quién accede, protección, plazo actual y de referencia, y si hoy se pueden atender acceso, rectificación y supresión
- [ ] T062 [US4] Crear en `backend/src/main/java/es/citaclara/seguridad/` el registro de eventos de seguridad (D10): `EventoSeguridad.java` con el conjunto cerrado `ACCESO_CORRECTO`, `ACCESO_FALLIDO`, `ACCESO_BLOQUEADO`, `SESION_CADUCADA`, `RECURSO_AJENO`, `PETICION_RECHAZADA`, `FiltroCorrelacion.java` con identificador de correlación y salida JSON a la salida estándar; sin dependencias nuevas
- [ ] T063 [US4] Invocar el registro de eventos desde `ServicioAcceso.java`, `InterceptorSesion.java` y los 404 de recurso ajeno en los repositorios o controladores, sin escribir datos personales
- [X] T064 [US4] Cambiar `backend/src/main/java/es/citaclara/comun/errores/ManejadorErrores.java` (H8) para registrar clase y `SQLState` y no el mensaje de `DataIntegrityViolationException`, y revisar `LOG.error` en el resto del backend
- [X] T065 [US4] Documentar en el informe la evaluación de retención y derechos (D14), marcar como `derivado` lo que requiera cambiar el modelo de datos de la 001 y proponer la spec de seguimiento `006-derechos-datos` con su propietario, sin construir la anonimización aquí

**Punto de control**: US4 completa; el registro nuevo está cubierto por tests.

---

## Fase 7: Historia US5 — Verificación continua de seguridad (P3)

**Objetivo**: que una regresión de seguridad rompa el merge, como ya ocurre con el solape.

**Prueba independiente**: `ControlesCiTest` en verde y, en una rama de prueba, una dependencia vulnerable y un secreto de prueba bloquean la integración.

### Pruebas de US5

- [X] T066 [P] [US5] `ControlesCiTest` en `backend/src/test/java/es/citaclara/seguridad/ControlesCiTest.java`: `[005][FR-007][FR-016][SC-007][US5-AC1][US5-AC2]` analiza `.github/workflows/ci.yml` y `.github/dependabot.yml` y exige un paso de OSV-Scanner sobre `backend/pom.xml` y `frontend/package-lock.json` con umbral alto o crítico, un paso de gitleaks sobre el historial de la rama, todas las acciones de terceros fijadas por SHA de 40 caracteres, y Dependabot para Maven, npm, GitHub Actions y Docker
- [X] T067 [P] [US5] `TrazabilidadScriptTest` en `backend/src/test/java/es/citaclara/seguridad/TrazabilidadScriptTest.java`: `[005][FR-017]` ejecuta `scripts/trazabilidad.sh` sobre `specs/005-security-audit` y sobre `specs/001-nucleo-agenda` y exige salida vacía y código 0; complementa al trabajo `trazabilidad` de CI porque hace que `./mvnw verify` ya falle en local ante un hueco de trazabilidad, sin esperar al PR

### Implementación de US5

- [X] T068 [US5] Añadir a `.github/workflows/ci.yml` un trabajo `seguridad` con OSV-Scanner (sobre el POM y el *lockfile*; si la herramienta no filtra por severidad, aplicar el umbral alto o crítico con un paso que lea su salida JSON y falle solo con esas severidades, y dejar constancia de ello en el informe) y gitleaks como binario con `fetch-depth: 0`, y hacerlo requisito del trabajo `e2e`; permisos `contents: read`
- [X] T069 [US5] Fijar por SHA de commit todas las acciones de `.github/workflows/ci.yml` (`actions/checkout`, `setup-java`, `setup-node`, `upload-artifact`), con la etiqueta como comentario
- [X] T070 [P] [US5] Crear `.github/dependabot.yml` para `maven` (`/backend`), `npm` (`/frontend`), `github-actions` (`/`) y `docker` (`/backend`, `/frontend`), con agrupación semanal
- [X] T071 [P] [US5] Crear `.gitleaks.toml` en la raíz con reglas por defecto y una lista de permitidos acotada para la clave de demostración documentada (ruta y expresión concretas, no genéricas)
- [ ] T072 [US5] Probar en una rama desechable que una dependencia con vulnerabilidad grave conocida y una clave falsa tipo `sk_live_…` hacen fallar la integración con un mensaje legible; anotar los enlaces a las ejecuciones en el informe y borrar la rama (escenarios 11 y 12 de [quickstart.md](quickstart.md))

**Punto de control**: US5 completa; las historias se pueden entregar en cualquier orden respetando las dependencias.

---

## Fase 8: Cierre y tareas transversales

**Propósito**: reauditoría, validación completa y mantenimiento.

- [ ] T073 Reauditar (FR-018, SC-008): repetir el método de [quickstart.md](quickstart.md) secciones 1 a 3 en la rama corregida, comparar hallazgos abiertos y cerrados frente al informe inicial y completar la sección «Reauditoría» del informe
- [ ] T074 Comprobar a mano (SC-009, FR-015) el flujo de recepción en 360 px y en portátil (acceso, búsqueda de paciente, alta de cita): sin pasos extra salvo el campo de usuario si se aprobó G1, sin mensajes técnicos y con axe sin infracciones
- [ ] T075 Ejecutar la suite completa: `cd backend && ./mvnw verify`, `cd frontend && npm ci && npm test`, `URL_BASE=http://localhost:8080 npm run e2e`, `./scripts/trazabilidad.sh specs/005-security-audit` y `./scripts/trazabilidad.sh` (ambas deben salir vacías) y la verificación de la semilla (puerta de merge 4 y 5)
- [X] T076 Redactar la sección «Plan de seguimiento» del informe, solo con lo que no se corrige en esta feature: hallazgos medios y bajos, derivados y specs propuestas (`006-derechos-datos`), con propietario propuesto y esfuerzo
- [X] T077 Poner el estado de la spec 005 en `specs/MAPA.md` («Implementada» o «Implementada con solicitudes pendientes») y comprobar que `specs/005-security-audit/spec.md` refleja el comportamiento entregado (puerta de merge 1)
- [X] T078 **Mantenimiento (último paso)**: actualizar `CLAUDE.md` con las decisiones de diseño y convenciones nuevas de esta feature, una línea por decisión y con referencia a la spec (p. ej. «[005] …»). No incluir entradas por incluir: solo información transversal y relevante para el proyecto que puedan aprovechar futuras features

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Preparación (Fase 1)**: sin dependencias.
- **Cimientos (Fase 2)**: depende de la Fase 1; bloquea todas las historias.
- **US1 (Fase 3)**: tras los cimientos. Es el MVP.
- **US2 (Fase 4)**: tras los cimientos; independiente de US1 salvo T027 (enlaza desde el informe).
- **US3 (Fase 5)**: depende de US1 (T020). El bloque posterior a **G1 (T042)** depende además de la aprobación del propietario de la 001.
- **US4 (Fase 6)**: el inventario (T061) depende del informe (T020); T062–T064 son independientes de US3 salvo que T063 usa lo que cree T045/T049 si G1 se aprobó (en otro caso se registra solo lo que exista).
- **US5 (Fase 7)**: independiente de US3 y US4; solo necesita los cimientos.
- **Cierre (Fase 8)**: tras las historias deseadas.

### Dependencias dentro de cada historia

- Los tests de un hallazgo se escriben y **fallan** antes de su corrección; se integran verdes.
- US3: T032 → T033/T034; T036 → T037/T038; T040 → T041; T043 → T044 → T045; T046 → T047 → T048 → T049 → T050 → T051 → T052 → T053 → T054.
- No se toca el código de acceso (T043–T056) antes de G1 (T042).

### Oportunidades de paralelismo

- Cimientos: T006, T007 y T008 a la vez.
- US1: los cinco tests T009–T013 y T021 a la vez; las revisiones T015–T019 escriben en el mismo informe y se hacen por orden.
- US2 se puede hacer en paralelo con US1 por otra persona.
- US3: los grupos nginx, configuración y entradas son independientes entre sí; T043 y T046 (tests) a la vez.
- US5 entera en paralelo con US3 y US4.

### Ejemplo de paralelismo (US1)

```text
Tarea: T009 InformeAuditoriaTest en backend/src/test/java/es/citaclara/seguridad/InformeAuditoriaTest.java
Tarea: T010 FichasHallazgoTest en backend/src/test/java/es/citaclara/seguridad/FichasHallazgoTest.java
Tarea: T011 AislamientoClinicaTest en backend/src/test/java/es/citaclara/seguridad/AislamientoClinicaTest.java
Tarea: T012 SesionSeguridadTest en backend/src/test/java/es/citaclara/seguridad/SesionSeguridadTest.java
Tarea: T013 EntradasMaliciosasTest en backend/src/test/java/es/citaclara/seguridad/EntradasMaliciosasTest.java
```

---

## Estrategia de implementación

### MVP primero (US1 + US2)

1. Fases 1 y 2.
2. Fase 3 (US1): informe verificado; puerta G0.
3. Fase 4 (US2): requisitos de pagos, que se pueden entregar antes de empezar cualquier feature de cobros.
4. **Parar y validar**: el diagnóstico completo ya aporta valor aunque no se corrija nada.

### Entrega incremental

1. Cimientos → US1 → US2 (diagnóstico).
2. US3 sin G1 (nginx, configuración, límites) → PR.
3. US4 (registros sin datos personales, inventario) → PR.
4. US5 (CI) → PR.
5. US3 tras G1 (limitación de intentos, usuarios individuales) cuando el propietario de la 001 apruebe.
6. Cierre y reauditoría.

### Estrategia con varias personas

1. Una persona hace Preparación y Cimientos.
2. Después: A en US1, B en US2, C en US5; al terminar US1, A pasa a US3 y B a US4.
3. Las solicitudes a la 001 (T030) se envían en cuanto el informe confirma los hallazgos, para que G1 no bloquee el cierre.

---

## Notas

- Las tareas con hallazgos candidatos se omiten o se reclasifican si la auditoría no los confirma; la omisión se anota en la ficha, no se hace en silencio.
- Cada corrección trae su test rojo→verde; ningún test se desactiva para integrar (Principio VI).
- No se editan las specs 001 (sin aprobación), 002, 003 ni 004; sus cambios van como solicitud en `specs/MAPA.md`.
- Hacer commit tras cada tarea o grupo lógico; detenerse en cualquier punto de control para validar.

## Estado de la implementación (27/11/2026)

- **Hechas**: informe, requisitos de pagos, pruebas de evidencia y de formato, corrección de H-03 (registros sin datos personales), controles de CI (OSV-Scanner, gitleaks, acciones por SHA, Dependabot) y plan de seguimiento.
- **No hechas, por la puerta G1** (T042–T056: límite de intentos y usuarios individuales): requieren la aprobación del propietario de la 001; constan como `derivado` (solicitud S-001 en `specs/MAPA.md`).
- **No hechas, por el límite de FR-020** (solo críticos y altos): T032–T041 (cabeceras, perfil de producción, límites de entrada), pasan al plan de seguimiento del informe. T035 tampoco.
- **Pendientes de una persona**: T003 (línea base e2e), T022 (revisión G0 por una persona ajena), T026 (contraste con la documentación vigente de Stripe), T072 (rama desechable en CI), T073 (reauditoría con el método del quickstart), T074 (flujo a mano en 360 px) y T062/T063 (registro de eventos, ligado a S-001).
