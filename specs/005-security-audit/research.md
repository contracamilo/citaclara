# Investigación: Auditoría de seguridad (Stripe y OWASP Top 10)

**Spec**: [spec.md](spec.md) | **Fecha**: 27/11/2026

Resuelve las incógnitas del plan. Cada decisión: qué, por qué y alternativas descartadas. Los «hallazgos candidatos» de D9 son **hipótesis de una lectura preliminar del código**, no resultados: la auditoría de la fase 1 los confirma, los descarta o los reclasifica.

## D1. Edición del OWASP Top 10 que se audita

- **Decisión**: OWASP Top 10:**2025**, la edición vigente más reciente publicada. No existe una edición «2026» del Top 10 de aplicaciones web (las ediciones «2026» localizadas son de otros proyectos, p. ej. contratos inteligentes). El informe cita la edición y la fecha de consulta (FR-018).
- **Categorías** (10): A01 Control de acceso defectuoso · A02 Configuración de seguridad incorrecta · A03 Fallos en la cadena de suministro de software · A04 Fallos criptográficos · A05 Inyección · A06 Diseño inseguro · A07 Fallos de autenticación · A08 Fallos de integridad de software o datos · A09 Fallos de registro y alerta de seguridad · A10 Mala gestión de condiciones excepcionales.
- **Alternativa descartada**: OWASP Top 10:2021 (obsoleta; carece de A03 y A10 actuales) y OWASP API Security Top 10:2023, que se usa solo como lista de apoyo para A01 (autorización a nivel de objeto y de función) y no como marco principal.
- **Fuente**: <https://top10.owasp.org/2025/>.

## D2. Método de auditoría y evidencia

- **Decisión**: tres técnicas combinadas, todas sobre el entorno local con la semilla de demostración (FR-019):
  1. **Revisión de código y de configuración** guiada por una lista por categoría (ficheros concretos en D9).
  2. **Pruebas dinámicas escritas como tests** (JUnit con la aplicación real y PostgreSQL real; Playwright para cabeceras y navegador): cada hallazgo reproducible queda como un test que **falla** antes de la corrección y **pasa** después. Así la evidencia (SC-003) y la prueba de regresión (FR-020) son lo mismo.
  3. **Análisis automático de dependencias y secretos** (D6) para la cadena de suministro.
- **Por qué**: un hallazgo sin test reproducible no se puede verificar ni reauditar (FR-018) y viola el espíritu del Principio VI.
- **Alternativas descartadas**: escáner dinámico general (ZAP/Burp) como técnica principal: añade herramienta y ruido para una aplicación de 11 endpoints; se anota como complemento opcional en `quickstart.md`.

## D3. Formato y ubicación del informe

- **Decisión**: un único Markdown versionado en `specs/005-security-audit/informe-auditoria.md`, con cabecera de reproducibilidad (commit auditado, fecha, alcance, edición OWASP, herramientas), tabla de veredictos por categoría y una ficha por hallazgo con campos fijos (contrato en [contracts/informe-auditoria.md](contracts/informe-auditoria.md)). Un test JUnit (`InformeAuditoriaTest`) lo valida de forma estructural: diez categorías con veredicto, campos obligatorios, mejora y criterio de aceptación en críticos y altos (FR-001, FR-002, FR-013, SC-001, SC-002).
- **Por qué**: el informe vive junto a la spec, se revisa en el PR y la reauditoría lo compara con un diff. No se publica fuera del repositorio porque describe debilidades explotables.
- **Alternativas descartadas**: informe PDF o en herramienta externa (no versionable ni verificable en CI).

## D4. Escala de severidad

- **Decisión**: cuatro niveles con criterio explícito y propio del producto: **Crítica** = acceso a datos de otra clínica o ejecución de acciones sin sesión, o exposición de credenciales reales; **Alta** = compromiso de cuentas viable sin condiciones especiales (p. ej. adivinación de clave sin límite) o fuga de datos personales a registros/terceros; **Media** = debilidad que requiere condiciones previas o reduce defensa en profundidad; **Baja** = endurecimiento o información menor.
- **Por qué**: CVSS puro puntúa mal un producto con dos roles y un solo tipo de dato; el criterio propio es comprensible para quien decide (Principio VII, aplicado al informe).

## D5. Alcance Stripe y PCI DSS

- **Decisión**: modelo en el que Stripe captura la tarjeta (página alojada o elementos incrustados de Stripe) y CitaClara solo guarda identificadores de Stripe y el estado del cobro. Eso deja a CitaClara en el alcance PCI DSS más reducido (autoevaluación de comercio que delega la captura) y convierte «la tarjeta nunca toca CitaClara» en una regla verificable.
- **Entregable**: `specs/005-security-audit/requisitos-pagos.md`, con requisitos `RP-001…` verificables y la lista de comprobación de Stripe con veredicto (FR-010, FR-011). No se construye integración alguna ni se añade el SDK de Stripe (Principio IV; spec, «Fuera de alcance»).
- **Requisitos que fijará** (basados en la guía de integración de Stripe, <https://docs.stripe.com/security/guide>, <https://docs.stripe.com/webhooks>, <https://docs.stripe.com/keys>):
  - Ningún dato de tarjeta en peticiones, base de datos, registros ni caché de CitaClara; sin campos de tarjeta propios.
  - Avisos (*webhooks*) aceptados solo con firma válida comprobada sobre el cuerpo sin modificar; rechazo 400 si falla; secreto de firma propio por punto de enlace, distinto de las claves de API.
  - Idempotencia: cada evento de Stripe se procesa una sola vez (identificador de evento único) y de forma tolerante al desorden y a la repetición.
  - Importe decidido por el servidor a partir de la cita y su precio en céntimos (Principio II); nunca un importe recibido del navegador; moneda fija EUR.
  - Conciliación al céntimo entre el importe de la cita y el cobrado; las diferencias se muestran, no se corrigen en silencio.
  - Claves restringidas (`rk_…`) con el mínimo de permisos, fuera del código y del repositorio (variables de entorno o gestor de secretos), con rotación; la clave de pruebas y la real nunca coinciden entre entornos.
  - Cobro en línea del paciente: referencias de pago no adivinables, de un solo uso y con caducidad; un paciente no puede ver ni iniciar el cobro de otro; sin cuenta de clínica.
  - Todo extremo que reciba tráfico de Stripe usa TLS y límite de tamaño; el cobro no se marca «pagado» desde el navegador, solo por aviso verificado.
  - Registro de eventos de pago sin datos personales innecesarios; reembolsos y disputas con permiso y rastro de quién los inició.
- **Alternativa descartada**: tratar el alcance PCI como si CitaClara manejara tarjetas (SAQ D): desproporcionado y contrario al modelo de Stripe.

## D6. Controles automáticos de integración continua (US5, FR-016)

- **Decisión**: añadir al flujo existente (`.github/workflows/ci.yml`) cuatro controles que bloquean el merge:
  1. **Dependencias vulnerables**: OSV-Scanner sobre `backend/pom.xml` y `frontend/package-lock.json` (sin clave de API ni cuenta; cubre Maven y npm) con umbral «alta o crítica».
  2. **Secretos**: *gitleaks* ejecutado como binario en un paso (la acción oficial exige licencia en organizaciones); incluye el historial de la rama.
  3. **Actualizaciones**: `dependabot.yml` para Maven, npm, GitHub Actions y Docker, con agrupación semanal.
  4. **Acciones fijadas por SHA** de commit (hoy van por etiqueta `@v4`), con Dependabot actualizándolas.
- **Por qué**: son herramientas de CI, no dependencias de ejecución del producto; se justifican con FR-016 y SC-007.
- **Alternativas descartadas**: OWASP Dependency-Check (necesita clave de la NVD y es lento), Snyk (cuenta externa), SonarQube (servicio que mantener).

## D7. Limitación de intentos de acceso (A07)

- **Decisión**: contador de intentos fallidos **en PostgreSQL** por pareja (clínica, origen) y por clínica, con bloqueo temporal creciente y respuesta `429` con tiempo de espera en el mensaje; el registro de intentos se purga solo (ventana de 24 h). Una clave correcta no queda bloqueada por intentos de otro origen salvo bloqueo global de la clínica.
- **Por qué**: PostgreSQL ya está en el entorno y el estado debe sobrevivir a reinicios y a varias instancias; no añade dependencia (Principio IV). La clínica sigue entrando sin formación con un mensaje claro (Principio VII).
- **Complemento en nginx**: `limit_req` sobre la ruta de acceso como defensa barata antes de llegar al backend.
- **Alternativas descartadas**: Bucket4j o Resilience4j (dependencia nueva para un solo endpoint); contador en memoria (se pierde al reiniciar y no escala a dos instancias).
- **Cambia la spec 001** (su FR-004 declara «sin límite de intentos» como deuda consciente): requiere la aprobación de su propietario (puerta G1).

## D8. Usuarios individuales (FR-021, decisión de la clarificación 4)

- **Decisión**: tabla `usuario` por clínica (nombre de usuario, hash BCrypt, rol básico `RECEPCION`/`ADMINISTRACION`, activo, fecha de alta). La sesión guarda `usuarioId` además de `clinicaId`; la clave compartida de la clínica deja de existir tras un periodo de transición con ambas vías, y el alta de usuarios la hace el rol de administración. La atribución («quién») entra en los eventos de seguridad (D10).
- **Por qué**: atribución individual, revocación sin afectar al resto y base para los permisos (A01, A07, A09). Es la decisión del usuario en la clarificación.
- **Condición**: modifica el comportamiento de la spec 001 (acceso, FR-001/FR-002/FR-004 y el contrato de sesión) y por eso queda tras la puerta **G1**: la fase de implementación correspondiente **no empieza** hasta que el propietario de la 001 apruebe y actualice su spec (Principio I). Si no aprueba, el hallazgo queda abierto con la solicitud registrada (SC-008).
- **Alternativas descartadas**: SSO/OAuth con proveedor externo (dependencia y operación desproporcionadas para clínicas de 2–5 profesionales); Spring Security completo (la 001 lo descartó y no es necesario para BCrypt + sesión).
- **Migración**: la semilla crea usuarios de demostración con clave conocida solo en perfil de demostración (D9, H1).

## D9. Hallazgos candidatos de la lectura preliminar

Hipótesis a confirmar con evidencia en la fase 1. Cada una indica categoría OWASP, parte afectada y si su corrección toca otra spec.

| ID | Candidato | OWASP | Parte | ¿Toca spec ajena? |
|----|-----------|-------|-------|-------------------|
| H1 | Clave de demostración publicada (`eleva-demo-2026`) en `CLAUDE.md`, README y salida de la semilla; nada impide cargar la semilla en un entorno real | A07 / A02 | entorno | No |
| H2 | Sin límite de intentos de acceso (deuda declarada en 001 FR-004) | A07 | servidor | **Sí (001)** |
| H3 | Clave única compartida por clínica: sin atribución ni revocación individual | A07 / A09 / A01 | servidor, interfaz | **Sí (001)** |
| H4 | Credenciales de base de datos por defecto (`citaclara/citaclara`) en `application.yml` y `docker-compose.yml`; `COOKIE_SEGURA=false` por defecto; sin perfil de producción que obligue a cambiarlas | A02 / A04 | entorno | No |
| H5 | Sin TLS en el entorno entregado ni `Strict-Transport-Security`; cookie de sesión no `Secure` por defecto | A04 / A02 | entorno | No |
| H6 | nginx sin cabeceras de seguridad (CSP, `X-Content-Type-Options`, `frame-ancestors`, `Referrer-Policy`, `Permissions-Policy`), sin `server_tokens off` ni `client_max_body_size` | A02 | entorno | No |
| H7 | `X-Forwarded-For` se **añade** al valor del cliente (`$proxy_add_x_forwarded_for`): suplantable; invalidaría el límite de intentos por origen | A02 / A07 | entorno | No |
| H8 | El registro de errores vuelca la excepción completa; en violaciones de integridad el mensaje de PostgreSQL incluye valores (teléfono, clínica) → datos personales en registros | A09 / A10 | servidor | No |
| H9 | Sin registro de eventos de seguridad (accesos correctos y fallidos, sesión caducada, recurso ajeno) ni identificador de correlación | A09 | servidor | No |
| H10 | Búsqueda de pacientes sin longitud máxima de `q` y con comodines `%`/`_` sin escapar; sin límite de peticiones en rutas costosas | A05 / A10 | servidor | No |
| H11 | `GET /api/clinicas/{identificador}` público permite enumerar clínicas por identificador | A01 | servidor | **Sí (001)** (contrato público) |
| H12 | Cadena de suministro: sin análisis de dependencias, sin detección de secretos, acciones por etiqueta, imágenes por etiqueta y no por *digest* | A03 / A08 | CI, entorno | No |
| H13 | Imagen `nginx` ejecuta el proceso maestro como root; la API no tiene límite de memoria ni sistema de ficheros de solo lectura | A02 | entorno | No |
| H14 | Sin plazo de conservación ni anonimización, ni vías de acceso/rectificación/supresión de pacientes (RGPD) | A06 | servidor | **Sí (001)** (modelo de datos) |
| H15 | Mensaje `PACIENTE_DUPLICADO` devuelve nombre y datos de la ficha existente: correcto dentro de la clínica; se confirma que no se filtra entre clínicas | A01 | servidor | No |

**Puntos que ya cumplen** (se verifican, no se asumen): contraseña con BCrypt y comparación de coste constante frente a clínica inexistente; regeneración de sesión al iniciar (fijación); caducidad absoluta de 8 h; cookie `HttpOnly` + `SameSite=Strict`; aislamiento por `clinica_id` con claves foráneas compuestas; recurso ajeno = 404; solo JSON en POST; SQL parametrizado; errores sin trazas hacia el cliente; actuator solo `health` y no expuesto por nginx; contenedor de la API sin root; dependencias fijadas.

## D10. Registro de eventos de seguridad (A09, FR-008)

- **Decisión**: registro estructurado en JSON hacia la salida estándar con un conjunto cerrado de eventos (`ACCESO_CORRECTO`, `ACCESO_FALLIDO`, `ACCESO_BLOQUEADO`, `SESION_CADUCADA`, `RECURSO_AJENO`, `PETICION_RECHAZADA`) y los campos `instante`, `evento`, `clinicaId`, `usuarioId` (cuando exista), `origen` (red, truncado) e `idCorrelacion`. **Nunca** nombre, teléfono, email ni clave. Las excepciones se registran con clase y `SQLState`, sin el mensaje del controlador (H8).
- **Por qué**: permite detectar abuso sin crear un segundo almacén de datos personales. Sin dependencia nueva (SLF4J y `Clock` ya existen).
- **Alternativas descartadas**: tabla de auditoría en BD (se plantea para acciones de usuario en la feature de seguimiento; aquí basta el registro) y envío a un SIEM (fuera de alcance).

## D11. Cabeceras y endurecimiento de nginx

- **Decisión**: política de contenido restrictiva compatible con la interfaz (`default-src 'self'`; estilos y fuentes propios; sin `unsafe-inline` si el *build* de Vite lo permite), `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Permissions-Policy` vacía, `Cross-Origin-Opener-Policy: same-origin`, `server_tokens off`, `client_max_body_size 64k`, `X-Forwarded-For $remote_addr`, y `Strict-Transport-Security` activo solo cuando el despliegue termina TLS (parámetro de entorno). Las cabeceras se **prueban** con Playwright en cada ruta de la interfaz (incluida la pantalla de acceso) y con una prueba de navegador que carga la aplicación y comprueba que no hay violaciones de la política.
- **Por qué**: cierra H6 y H7 sin tocar el comportamiento visible.

## D12. Configuración segura por entorno (A02, A04)

- **Decisión**: perfil `produccion` que **falla al arrancar** si la contraseña de base de datos es la de desarrollo, si `COOKIE_SEGURA` no es `true` o si hay clínicas con la clave de demostración; la semilla se niega a ejecutarse con ese perfil. Docker Compose conserva los valores de desarrollo, etiquetados «solo desarrollo», y un `docker-compose.produccion.yml` de ejemplo exige secretos por variables sin valor por defecto. Se documenta que el cifrado en reposo y las copias de seguridad dependen del proveedor de alojamiento (fuera de alcance, spec).

## D13. Límites de entrada y abuso (A05, A10, FR-009)

- **Decisión**: longitud máxima de la búsqueda (80 caracteres), escape de `%`, `_` y `\` en `LIKE`, tamaño máximo de cuerpo, límite de peticiones por origen en nginx para `/api/` (generoso para recepción) y estricto para la ruta de acceso, y respuestas `4xx` coherentes ante condiciones excepcionales (cuerpo truncado, JSON profundo, tipos inesperados). Cada límite se prueba con un caso límite exacto y uno excesivo.

## D14. Retención y derechos RGPD (FR-012)

- **Decisión de alcance**: la feature **evalúa e inventaría** y deja en el informe lo que falta; no construye la anonimización a los 5 años ni los endpoints de acceso/supresión, que cambian el modelo de datos y el contrato de la spec 001 y se proponen como spec de seguimiento (`006-derechos-datos`, a registrar en el mapa por su propietario). Si la auditoría califica la ausencia como alta, se aplica FR-021/FR-014 (solicitud al propietario).
- **Por qué**: cumple la aclaración 3 (plazo de referencia de 5 años) sin ampliar el alcance sin spec (Principio IV).

## D15. Trazabilidad con el script existente

- **Hallazgo del plan**: `scripts/trazabilidad.sh` solo reconoce los encabezados en español de la spec 001 («### Historia de usuario N», «**Escenarios de aceptación»») y fija los criterios excluidos (SC-004–006). La spec 005 usa los encabezados de la plantilla (`### User Story N`, `**Acceptance Scenarios**`), así que el script **no vería sus escenarios**.
- **Decisión**: (a) hacer que el script acepte ambas redacciones de encabezado y (b) leer los criterios excluidos de una línea `**Excluidos de la trazabilidad**:` de la propia spec (con valor por defecto actual para la 001). Es un cambio de infraestructura de proceso, no de comportamiento del producto; se avisa al propietario de la 001 en el PR.
- **Alternativa descartada**: traducir los encabezados de la spec 005 a mano (frágil y la próxima spec repetiría el problema).

## D16. Pruebas que se crean

- Paquete `es.citaclara.seguridad` (backend) con pruebas de integración heredando de `PruebaIntegracionBase`: aislamiento entre clínicas en **todos** los endpoints y por todos los verbos; limitación de intentos y expiración del bloqueo con `RelojControlable`; registros sin datos personales (capturando el *appender*); límites de entrada; perfil `produccion` que falla al arrancar con valores por defecto; informe bien formado.
- Pruebas Playwright en `frontend/tests/e2e/seguridad.spec.ts`: cabeceras en cada ruta, ausencia de violaciones de política, cookie `HttpOnly`/`SameSite`, comportamiento del bloqueo con mensaje comprensible en 360 px y axe.
- Nombre visible: `[005][FR-xxx][USn-ACm]` (convención transversal [001]).
- **Regla**: ningún test de seguridad se desactiva ni se marca como «fallo esperado» para integrar (Principio VI).

## D17. Orden de entrega y puertas

- **G0**: informe completo y validado (fase 1) antes de corregir nada.
- **G1**: aprobación del propietario de la spec 001 y actualización de esa spec, para H2/H3/H11/H14 (limitación de intentos, usuarios individuales, contrato público, datos del paciente). Hasta entonces solo se avanza en lo que no la toca.
- **G2**: ningún hallazgo crítico o alto abierto sin corrección ni solicitud registrada (SC-008).

## D18. Estimación de esfuerzo relativo (para el plan de mejoras, FR-013)

Escala S (≤ 0,5 día), M (1–2 días), L (3–5 días). Preliminar: H1 S · H2 M · H3 L · H4 S · H5 M · H6 S · H7 S · H8 S · H9 M · H10 S · H11 S · H12 M · H13 M · H14 L (seguimiento) · H15 S.
