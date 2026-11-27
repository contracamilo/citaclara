# Informe de auditoría de seguridad de CitaClara

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Requisitos de cobros**: [requisitos-pagos.md](requisitos-pagos.md)

## Cabecera de reproducibilidad

- **Commit auditado**: 5ba131e
- **Fecha**: 27/11/2026
- **Edición OWASP**: OWASP Top 10:2025
- **Alcance**: backend (Spring Boot), frontend (React servido por nginx), entorno (Docker Compose, Dockerfiles), integración continua (GitHub Actions) y repositorio, sobre el entorno local con la semilla de demostración. Fuera de alcance: producción, infraestructura del alojamiento, pentesting contra datos reales o terceros (FR-019) y certificaciones formales.
- **Método**: revisión de código y configuración por categoría; pruebas dinámicas escritas como tests (paquete `es.citaclara.seguridad`, con Testcontainers y PostgreSQL real); sondeos con `curl` sobre el entorno de demostración local con datos ficticios; análisis automático de dependencias y secretos.
- **Herramientas**: OSV-Scanner 2.7.1 (imagen oficial), gitleaks 8.30.1 (imagen oficial), JUnit 5 con Spring Boot Test, JDK 25 y `curl`.

## Veredictos por categoría

El estado corresponde al commit auditado, antes de las correcciones de esta feature.

| Categoría | Veredicto | Hallazgos | Evidencia |
|---|---|---|---|
| A01 Control de acceso defectuoso | cumple con reservas | H-02, H-15 | `AislamientoClinicaTest`: recursos de otra clínica responden igual que uno inexistente por agenda, alta, estado y búsqueda; sin sesión nada responde. Reservas: no hay roles ni atribución y el identificador de clínica se puede enumerar |
| A02 Configuración de seguridad incorrecta | no cumple | H-05, H-06, H-07, H-08, H-09, H-10, H-14, H-16 | Sondeo de `/` y `/api`: sin cabeceras de seguridad, `Server: nginx/1.28.3`, credenciales de BD por defecto, sin TLS ni perfil de producción |
| A03 Fallos en la cadena de suministro | cumple con reservas | H-13 | OSV-Scanner sin incidencias en 169 paquetes npm; cobertura nula de Maven (12 paquetes filtrados por versiones del BOM); acciones por etiqueta |
| A04 Fallos criptográficos | cumple con reservas | H-05 | BCrypt para la clave de clínica y comparación con coste constante ante clínica inexistente (`SesionSeguridadTest`); sin TLS en el entorno entregado |
| A05 Inyección | cumple con reservas | H-12 | `EntradasMaliciosasTest`: SQL, HTML y Unicode se tratan como texto y no dañan la base; comodines `%` y `_` sin escapar en la búsqueda |
| A06 Diseño inseguro | no cumple | H-01, H-04 | Sin límite de intentos y sin conservación, anonimización ni derechos RGPD en el modelo |
| A07 Fallos de autenticación | no cumple | H-01, H-02, H-07 | 25 claves erróneas seguidas, 25 respuestas 401 y ningún bloqueo; clave única compartida por clínica |
| A08 Fallos de integridad de software o datos | cumple con reservas | H-13 | Solo JSON en POST y sin deserialización de objetos arbitrarios; integración continua sin fijar acciones por SHA |
| A09 Fallos de registro y alerta | no cumple | H-03, H-11 | Hibernate registraba el valor del teléfono en una violación de unicidad; sin registro de eventos de seguridad |
| A10 Mala gestión de condiciones excepcionales | cumple con reservas | H-12, H-16 | Errores del API sin trazas ni clases (`EntradasMaliciosasTest`); sin límite de tamaño ni de peticiones en nginx |

## Hallazgos

### H-01 · Sin límite de intentos de acceso

- **Categoría**: A07
- **Severidad**: alta
- **Datos afectados**: sesión, pacientes, agenda
- **Parte**: servidor
- **Verificación**: 25 peticiones `POST /api/clinicas/eleva/sesion` con claves erróneas seguidas devuelven 25 veces 401 sin bloqueo (sondeo local); además la spec 001 lo declara como deuda consciente (FR-004 de la 001)
- **Mejora**: bloqueo temporal creciente por origen (3 fallos → 5 min, duplicando hasta 60 min) y por clínica (15 fallos en 15 min → 15 min), con mensaje claro y sesiones abiertas intactas (FR-022 de esta spec)
- **Esfuerzo**: M
- **Criterio de aceptación**: `LimiteIntentosTest` con reloj controlado: 3 fallos bloquean 300 s, la clave correcta no entra durante el bloqueo, se levanta solo y otro origen no queda bloqueado
- **Estado**: derivado (solicitud S-001 de `specs/MAPA.md` al propietario de la 001)
- **Toca**: 001

### H-02 · Clave única compartida por clínica

- **Categoría**: A07
- **Severidad**: alta
- **Datos afectados**: sesión, pacientes, agenda
- **Parte**: servidor, interfaz
- **Verificación**: `ServicioAcceso.iniciarSesion` solo comprueba una clave por clínica; la sesión guarda `clinicaId` y ningún usuario, así que ningún acceso ni operación se atribuye a una persona y no se puede revocar a una sin cambiar la clave de todas
- **Mejora**: usuarios individuales por clínica con rol, revocación inmediata y atribución en los eventos de seguridad (FR-021)
- **Esfuerzo**: L
- **Criterio de aceptación**: `UsuariosIndividualesTest`: acceso con usuario y clave, revocación sin afectar a otros, misma respuesta ante usuario inexistente o inactivo y aislamiento entre clínicas
- **Estado**: derivado (solicitud S-001 de `specs/MAPA.md` al propietario de la 001)
- **Toca**: 001

### H-03 · Datos personales en los registros del servidor

- **Categoría**: A09
- **Severidad**: alta
- **Datos afectados**: pacientes
- **Parte**: servidor
- **Verificación**: al violar la unicidad del teléfono, Hibernate registraba en `WARN` el mensaje de PostgreSQL con el valor: `Key (clinica_id, telefono)=(1, +34…) already exists`; reproducido por `RegistroEventosTest` antes de la corrección
- **Mejora**: silenciar los registros de error SQL de Hibernate y registrar en `ManejadorErrores` solo clases, `SQLState` y el primer método propio, nunca el mensaje
- **Esfuerzo**: S
- **Criterio de aceptación**: ningún registro contiene teléfono, nombre, email ni clave tras una violación de restricción ni tras peticiones rechazadas
- **Estado**: corregido (`[005][FR-008][SC-005][US4-AC2]` en `RegistroEventosTest` y `ManejadorErroresRegistroTest`)
- **Toca**: ninguna

### H-04 · Sin conservación, anonimización ni derechos RGPD

- **Categoría**: A06
- **Severidad**: alta
- **Datos afectados**: pacientes
- **Parte**: servidor
- **Verificación**: ninguna clase ni migración borra o anonimiza datos (búsqueda de «anonim», «supresi» y «delete from» sin resultados en `backend/src/main`); no hay operación de acceso, rectificación ni supresión de una ficha
- **Mejora**: spec de seguimiento `006-derechos-datos` con anonimización a los 5 años desde la última cita (sin alterar importes ni totales) y vías de acceso, rectificación y supresión
- **Esfuerzo**: L
- **Criterio de aceptación**: un paciente con última cita hace más de 5 años queda anonimizado y los importes históricos se conservan al céntimo
- **Estado**: derivado (solicitud S-002 de `specs/MAPA.md`)
- **Toca**: 001

### H-05 · Entorno entregado sin TLS y con la cookie sin `Secure` por defecto

- **Categoría**: A04
- **Severidad**: media
- **Datos afectados**: sesión
- **Parte**: entorno
- **Verificación**: `COOKIE_SEGURA` es `false` por defecto en `application.yml` y en `docker-compose.yml`; nginx escucha solo en el puerto 80
- **Mejora**: perfil `produccion` que exija `COOKIE_SEGURA=true`, terminación TLS documentada y `Strict-Transport-Security` activo solo con TLS
- **Esfuerzo**: M
- **Criterio de aceptación**: el arranque con perfil `produccion` falla si la cookie no es segura
- **Estado**: abierto
- **Toca**: ninguna

### H-06 · Credenciales de base de datos por defecto

- **Categoría**: A02
- **Severidad**: media
- **Datos afectados**: pacientes, configuración
- **Parte**: entorno
- **Verificación**: `DB_CLAVE` vale `citaclara` por defecto en `application.yml` y en `docker-compose.yml`, y el puerto de la base solo se publica en `127.0.0.1`
- **Mejora**: perfil `produccion` sin valor por defecto, y un `docker-compose.produccion.yml` de ejemplo con secretos obligatorios
- **Esfuerzo**: S
- **Criterio de aceptación**: el arranque con perfil `produccion` falla con la contraseña de desarrollo
- **Estado**: abierto
- **Toca**: ninguna

### H-07 · Clave de demostración publicada y semilla sin salvaguarda

- **Categoría**: A07
- **Severidad**: media
- **Datos afectados**: sesión, configuración
- **Parte**: entorno
- **Verificación**: la clave de demostración figura en `README.md`, `CLAUDE.md`, `CatalogoDemostracion.java` y en la salida de `ComandoSemilla`; nada impide ejecutar la semilla en un entorno real
- **Mejora**: la semilla se niega con perfil `produccion` y la clave de demostración deja de imprimirse fuera del perfil de demostración
- **Esfuerzo**: S
- **Criterio de aceptación**: la semilla termina con error en el perfil `produccion`
- **Estado**: abierto
- **Toca**: 001

### H-08 · nginx sin cabeceras de seguridad y con la versión visible

- **Categoría**: A02
- **Severidad**: media
- **Datos afectados**: sesión, pacientes
- **Parte**: entorno, interfaz
- **Verificación**: `curl -I` sobre `/` y `/api/sesion` no devuelve `Content-Security-Policy`, `X-Content-Type-Options`, `Referrer-Policy` ni `Permissions-Policy`, y devuelve `Server: nginx/1.28.3`
- **Mejora**: las cabeceras de [contracts/cabeceras-seguridad.md](contracts/cabeceras-seguridad.md) y `server_tokens off`
- **Esfuerzo**: S
- **Criterio de aceptación**: `seguridad.spec.ts` comprueba las cabeceras en cada ruta y cero violaciones de la política de contenido
- **Estado**: abierto
- **Toca**: ninguna

### H-09 · Respuestas del API cacheables y sin `nosniff`

- **Categoría**: A02
- **Severidad**: media
- **Datos afectados**: pacientes, agenda
- **Parte**: servidor, entorno
- **Verificación**: `curl -I` sobre `/api/sesion` autenticado no devuelve `Cache-Control: no-store` ni `X-Content-Type-Options`
- **Mejora**: `Cache-Control: no-store` y `nosniff` en todo `/api/**`
- **Esfuerzo**: S
- **Criterio de aceptación**: toda respuesta de `/api/**` lleva ambas cabeceras
- **Estado**: abierto
- **Toca**: ninguna

### H-10 · `X-Forwarded-For` suplantable

- **Categoría**: A02
- **Severidad**: baja
- **Datos afectados**: configuración
- **Parte**: entorno
- **Verificación**: `frontend/nginx.conf` usa `$proxy_add_x_forwarded_for`, que concatena el valor enviado por el cliente
- **Mejora**: fijar `X-Forwarded-For` a `$remote_addr`; imprescindible antes de limitar intentos por origen (H-01)
- **Esfuerzo**: S
- **Criterio de aceptación**: una petición con `X-Forwarded-For` falso llega al API con la dirección real
- **Estado**: abierto
- **Toca**: ninguna

### H-11 · Sin registro de eventos de seguridad

- **Categoría**: A09
- **Severidad**: media
- **Datos afectados**: sesión
- **Parte**: servidor
- **Verificación**: ninguna clase de `backend/src/main/java/es/citaclara/acceso` registra accesos correctos, fallidos ni sesiones caducadas
- **Mejora**: registro estructurado de eventos de seguridad con identificador de correlación y sin datos personales (research D10)
- **Esfuerzo**: M
- **Criterio de aceptación**: cada acceso fallido, sesión caducada y recurso ajeno genera un evento sin datos personales
- **Estado**: abierto
- **Toca**: ninguna

### H-12 · Búsqueda de pacientes sin longitud máxima y con comodines sin escapar

- **Categoría**: A05
- **Severidad**: media
- **Datos afectados**: pacientes
- **Parte**: servidor
- **Verificación**: `GET /api/pacientes?q=%25%25` devuelve 20 fichas (límite de la consulta); `q` no tiene longitud máxima
- **Mejora**: longitud máxima de 80 caracteres y escape de `%`, `_` y `\`
- **Esfuerzo**: S
- **Criterio de aceptación**: `q` con `%` se trata como texto literal y con 81 caracteres se rechaza con un mensaje claro
- **Estado**: abierto
- **Toca**: ninguna

### H-13 · Cadena de suministro sin controles automáticos

- **Categoría**: A03
- **Severidad**: media
- **Datos afectados**: configuración
- **Parte**: entorno
- **Verificación**: `.github/workflows/ci.yml` fija las acciones por etiqueta (`@v4`); no hay análisis de dependencias ni de secretos; OSV-Scanner filtró 12 paquetes de `backend/pom.xml` por no resolver las versiones del BOM, así que Maven queda sin cubrir
- **Mejora**: trabajo `seguridad` en CI con OSV-Scanner y gitleaks, acciones fijadas por SHA, Dependabot y un análisis de Maven con las versiones ya resueltas
- **Esfuerzo**: M
- **Criterio de aceptación**: `ControlesCiTest` en verde y, en una rama desechable, una dependencia vulnerable y un secreto de prueba bloquean la integración
- **Estado**: corregido, salvo la cobertura de Maven de OSV-Scanner, que sigue parcial (`[005][FR-007][FR-016][SC-007][US5-AC1]` en `ControlesCiTest`)
- **Toca**: ninguna

### H-14 · Contenedores sin endurecer

- **Categoría**: A02
- **Severidad**: baja
- **Datos afectados**: configuración
- **Parte**: entorno
- **Verificación**: `frontend/Dockerfile` ejecuta nginx con el proceso maestro como root y `docker-compose.yml` no fija límites ni sistema de ficheros de solo lectura; las imágenes se fijan por etiqueta y no por *digest*
- **Mejora**: nginx sin privilegios, límites de recursos y *digest* en las imágenes
- **Esfuerzo**: M
- **Criterio de aceptación**: el proceso de nginx no es root y las imágenes llevan *digest*
- **Estado**: abierto
- **Toca**: ninguna

### H-15 · Identificadores de clínica enumerables

- **Categoría**: A01
- **Severidad**: baja
- **Datos afectados**: configuración
- **Parte**: servidor
- **Verificación**: `GET /api/clinicas/eleva` responde 200 con el nombre y `GET /api/clinicas/zzzz` responde 404 (sondeo local)
- **Mejora**: respuesta uniforme y límite de peticiones en la pantalla de acceso, según lo que apruebe el propietario de la 001
- **Esfuerzo**: S
- **Criterio de aceptación**: no se distingue una clínica existente de una inexistente por la ruta pública
- **Estado**: derivado (solicitud S-001 de `specs/MAPA.md` al propietario de la 001)
- **Toca**: 001

### H-16 · Sin límite de tamaño ni de peticiones en nginx

- **Categoría**: A10
- **Severidad**: media
- **Datos afectados**: configuración
- **Parte**: entorno
- **Verificación**: `frontend/nginx.conf` no define `client_max_body_size` ni `limit_req`, y 25 peticiones de acceso seguidas se aceptan sin frenar
- **Mejora**: `client_max_body_size 64k` y `limit_req` generoso en `/api/` y estricto en la ruta de acceso, sin rechazar el uso normal (FR-009)
- **Esfuerzo**: S
- **Criterio de aceptación**: un cuerpo de más de 64 KB se rechaza y una jornada de uso normal no recibe ningún 429
- **Estado**: abierto
- **Toca**: ninguna

## Plan de mejoras

Orden por riesgo. Esfuerzo: S ≤ 0,5 día, M de 1 a 2 días, L de 3 a 5 días.

| Orden | Hallazgo | Severidad | Parte | Esfuerzo | Destino |
|---|---|---|---|---|---|
| 1 | H-01 límite de intentos | alta | servidor | M | Solicitud S-001 a la 001; implementación en esta feature tras su aprobación (FR-022) |
| 2 | H-02 usuarios individuales | alta | servidor, interfaz | L | Solicitud S-001 a la 001; implementación en esta feature tras su aprobación (FR-021) |
| 3 | H-04 derechos y conservación | alta | servidor | L | Spec de seguimiento `006-derechos-datos`, solicitud S-002 |
| 4 | H-03 datos personales en registros | alta | servidor | S | Corregido en esta feature |
| 5 | H-05, H-06, H-07 entorno de producción | media | entorno | M | Seguimiento: perfil `produccion` que falla ante valores por defecto |
| 6 | H-08, H-09, H-10 cabeceras y proxy | media y baja | entorno, servidor | S | Seguimiento: cabeceras de seguridad y `X-Forwarded-For` |
| 7 | H-11 eventos de seguridad | media | servidor | M | Seguimiento, junto con H-01 |
| 8 | H-12, H-16 límites de entrada | media | servidor, entorno | S | Seguimiento |
| 9 | H-13 cadena de suministro | media | entorno | M | Corregido en esta feature (CI con OSV-Scanner y gitleaks, acciones por SHA, Dependabot); queda la cobertura de Maven |
| 10 | H-14, H-15 | baja | entorno, servidor | M y S | Seguimiento |

## Inventario de datos personales

| Dato | Ubicación | Acceso | Protección | Conservación | Derechos |
|---|---|---|---|---|---|
| Nombre del paciente | tabla `paciente`, respuestas del API a la sesión de su clínica | personal con sesión de esa clínica | aislamiento por `clinica_id` con claves foráneas compuestas; no se registra tras H-03 | sin plazo hoy; referencia de 5 años desde la última cita | no hay vías de acceso, rectificación ni supresión (H-04) |
| Teléfono del paciente | tabla `paciente`, restricción de unicidad por clínica | igual | igual; en registros hasta H-03 | igual | igual |
| Email del paciente (opcional) | tabla `paciente` | igual | igual | igual | igual |
| Citas y especialidad (dato de salud implícito) | tablas `cita`, `profesional`, `servicio` | igual | igual; importes en céntimos | igual | igual |
| Clave de clínica | tabla `clinica`, columna `clave_hash` | solo el servidor | BCrypt, nunca en claro | mientras exista la clínica | no aplica |
| Sesión | `HttpSession` en memoria del servidor y cookie `HttpOnly` | la persona con la cookie | `SameSite=Strict`, caducidad absoluta de 8 h | 8 h | no aplica |
| Registros del servidor | salida estándar del contenedor | quien acceda al contenedor | sin datos personales tras H-03 | según el alojamiento | no aplica |
| Copias de seguridad | no existen en el repositorio; las gestiona el alojamiento | fuera de alcance | fuera de alcance | fuera de alcance | fuera de alcance |

**Plazo de conservación de referencia**: 5 años desde la última cita de cada paciente; pasado ese plazo la ficha se anonimiza (nombre, teléfono y email) y se conservan las citas con sus importes y totales históricos al céntimo, sin alterarlos. Hoy nada lo aplica (H-04).

## Resultado de las herramientas automáticas

- **OSV-Scanner 2.7.1** sobre `frontend/package-lock.json` y `backend/pom.xml`: sin incidencias en 169 paquetes npm. Escaneó 12 paquetes de Maven y filtró los 12 como «locales o no escaneables» porque sus versiones las gestiona el BOM del proyecto padre, así que **la cobertura de Maven es nula** y no puede darse por verificada (H-13).
- **gitleaks 8.30.1** sobre el historial (18 commits en un clon local del repositorio) y sobre el árbol de trabajo sin Git: sin fugas. El primer intento sobre el *worktree* no escaneó ningún commit y se descartó como evidencia.

## Requisitos para otras features

Aplican, sin editar sus specs, a sus propietarios:

- **002 portal del paciente**: todo acceso público sin sesión de clínica debe cumplir los requisitos `RP-008` y `RP-009` de [requisitos-pagos.md](requisitos-pagos.md), limitar intentos y peticiones desde el primer día y no devolver datos de otros pacientes.
- **003 recordatorios**: no incluir datos de salud en el texto de avisos por canales sin cifrar y no registrar teléfonos ni emails.
- **004 panel de analítica**: agregar sin identificar pacientes y respetar el aislamiento por clínica con pruebas entre clínicas.
- **Futura feature de pagos**: cumplir todos los `RP-nnn`.

## Reauditoría

Comparación con el informe inicial, en la rama de la feature (27/11/2026):

| Hallazgo | Antes | Ahora |
|---|---|---|
| H-03 datos personales en registros | alta, abierto | corregido, con prueba |
| H-13 cadena de suministro | media, abierto | corregido salvo la cobertura de Maven |
| H-01, H-02, H-15 | alta, alta y baja | derivados a la 001 (S-001), pendientes de su propietario |
| H-04 | alta | derivado (S-002) |
| Resto (H-05 a H-12, H-14, H-16) | media y baja | abiertos, en el plan de seguimiento |

Resultado: de 16 hallazgos, 2 corregidos, 4 derivados y 10 abiertos. No queda ningún hallazgo crítico ni alto sin corregir o derivar con su solicitud registrada. SC-008 se cumple por la vía de la derivación, no por la corrección: **la clave compartida y la falta de límite de intentos siguen presentes hasta que el propietario de la 001 apruebe S-001**.

## Plan de seguimiento

Lo que esta feature no corrige (FR-020 limita las correcciones a críticos y altos que no cambian otras specs):

| Hallazgo | Severidad | Propietario propuesto | Esfuerzo | Nota |
|---|---|---|---|---|
| H-01, H-02, H-15 | alta, alta, baja | 001 (solicitud S-001) | M, L, S | Implementar tras aprobación; la 005 ya tiene escritas las condiciones de aceptación |
| H-04 | alta | 001 y nueva spec `006-derechos-datos` (S-002) | L | Conservación de 5 años y derechos RGPD |
| H-05, H-06, H-07 | media | nueva feature de despliegue | M | Perfil `produccion` que falla ante valores por defecto, TLS y semilla bloqueada |
| H-08, H-09, H-10, H-16 | media y baja | la misma | S | Cabeceras de seguridad, `no-store`, `X-Forwarded-For` y límites de nginx; H-10 antes que H-01 |
| H-11 | media | acompaña a S-001 | M | Registro de eventos de seguridad |
| H-12 | media | 001 (búsqueda) | S | Longitud máxima y escape de comodines |
| H-14 | baja | despliegue | M | Contenedores sin privilegios y por *digest* |
| Maven en OSV-Scanner | media | despliegue | S | Analizar con las versiones resueltas del BOM |
