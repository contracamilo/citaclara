# Especificación de feature: Auditoría de seguridad (Stripe y OWASP Top 10)

**Rama de la feature**: `005-security-audit`

**Creada**: 27/11/2026

**Estado**: Borrador (pendiente de aprobación de su propietario)

**Propietario**: feature 005-security-audit (registrado en `specs/MAPA.md`)

**Entrada**: descripción del usuario: «Auditoría de seguridad. Ya que tendrá información de pacientes y pagos debemos hacer que el desarrollo cumpla protocolos de seguridad, para evitar brechas: evaluar sobre STRIPE y OWASP Top 10 2026, proponer mejoras tanto en back y front si es necesario»

## Clarificaciones

### Sesión 2026-11-27

- Q: ¿Esta feature debe limitarse a entregar el informe y el plan de mejoras, o también debe corregir los hallazgos críticos y altos? → A: Informe más corrección en esta feature de los hallazgos críticos y altos que no afecten a specs ajenas; el resto, en seguimiento.
- Q: ¿Quién introducirá el pago con tarjeta cuando CitaClara cobre? → A: Ambos: recepción (panel autenticado) y paciente en línea (sin sesión de clínica).
- Q: ¿Cuánto tiempo debe conservarse un dato de paciente tras su última cita? → A: 5 años desde la última cita y después anonimizar.
- Q: Si la clave compartida por clínica resulta un riesgo alto, ¿se corrige en esta feature o se deriva a la spec 001? → A: Se corrige en esta feature introduciendo usuarios individuales, lo que modifica la spec 001 con la aprobación previa de su propietario.
- Q: Si la auditoría confirma que la falta de límite de intentos de acceso es un riesgo alto, ¿debe corregirse en esta feature (con la aprobación del propietario de la 001) o solo derivarse como solicitud a la 001? → A: Se corrige en esta feature, con la aprobación previa del propietario de la 001, igual que los usuarios individuales (FR-022).

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - Informe de auditoría contra OWASP Top 10 (Prioridad: P1)

El responsable del producto necesita saber, con evidencia, qué riesgos de seguridad tiene hoy CitaClara antes de manejar más datos de pacientes y de empezar a cobrar. Se revisa el sistema completo (servidor, interfaz, entorno de despliegue y proceso de integración continua) contra cada categoría del OWASP Top 10 vigente y se entrega un informe con los hallazgos.

**Por qué esta prioridad**: sin un diagnóstico verificable no se puede priorizar ninguna mejora; es el valor mínimo de la feature y puede entregarse solo.

**Prueba independiente**: revisar el informe y comprobar que cada categoría del OWASP Top 10 tiene un veredicto (cumple / cumple con reservas / no cumple / no aplica justificado), con evidencia reproducible para cada hallazgo.

**Escenarios de aceptación**:

1. **Dado** el sistema en su estado actual, **cuando** se completa la auditoría, **entonces** el informe contiene un veredicto por cada una de las categorías del OWASP Top 10 vigente, sin categorías omitidas.
2. **Dado** un hallazgo del informe, **cuando** otra persona sigue los pasos de verificación indicados, **entonces** reproduce el hallazgo sin información adicional.
3. **Dado** un hallazgo, **cuando** se lee su ficha, **entonces** incluye severidad, datos afectados (pacientes, pagos, sesión, agenda), parte afectada (servidor, interfaz o ambas) y mejora propuesta.

---

### Historia de usuario 2 - Preparación para pagos con Stripe (Prioridad: P1)

Antes de aceptar pagos, el responsable necesita saber si el diseño actual y el previsto cumplen las prácticas de seguridad de Stripe y los requisitos de PCI DSS para comercios que delegan la captura de tarjeta. La auditoría evalúa la preparación del producto y propone los requisitos de seguridad que debe cumplir cualquier futura integración de cobros.

**Por qué esta prioridad**: los datos de pago son el activo de mayor riesgo y el que más daño causa si se filtra; hay que fijar las reglas antes de construir cobros, no después.

**Prueba independiente**: comprobar que el informe contiene una lista de comprobación de Stripe con veredicto por punto y un conjunto de requisitos de seguridad para la integración futura, cada uno verificable.

**Escenarios de aceptación**:

1. **Dado** el sistema actual, **cuando** se evalúa contra las prácticas de seguridad de Stripe, **entonces** el informe indica por cada práctica si se cumple, no se cumple o no aplica todavía.
2. **Dado** la integración futura de cobros, **cuando** se revisan los requisitos propuestos, **entonces** establecen que los datos de tarjeta nunca llegan ni se almacenan en CitaClara, que los avisos de pago de Stripe se aceptan solo si se verifica su autenticidad, que no se procesa dos veces el mismo aviso, y que las claves de Stripe viven fuera del código y del repositorio.
3. **Dado** los importes de CitaClara (céntimos enteros), **cuando** se evalúa el cobro, **entonces** el importe cobrado lo decide siempre el sistema y nunca un valor enviado desde el navegador.

---

### Historia de usuario 3 - Plan de mejoras priorizado para servidor e interfaz (Prioridad: P2)

El responsable necesita convertir los hallazgos en trabajo concreto. El informe incluye un plan de mejoras ordenado por riesgo, separando lo que corresponde al servidor, a la interfaz y al entorno, con una estimación de esfuerzo relativo y las features de seguimiento que habría que abrir.

**Por qué esta prioridad**: da valor accionable, pero depende del informe (P1).

**Prueba independiente**: comprobar que cada hallazgo de severidad crítica o alta tiene una mejora propuesta, su propietario previsto y su criterio de aceptación.

**Escenarios de aceptación**:

1. **Dado** el informe, **cuando** se consulta el plan, **entonces** cada hallazgo crítico o alto tiene mejora, parte afectada (servidor/interfaz/entorno), esfuerzo relativo y criterio de aceptación verificable.
2. **Dado** una mejora que cambia comportamiento visible o contratos de otra spec, **cuando** se registra, **entonces** se propone como spec nueva o como solicitud al propietario de la spec afectada, nunca editando una spec ajena sin su aprobación.

---

### Historia de usuario 4 - Protección de datos de pacientes (Prioridad: P2)

El responsable necesita saber si los datos de pacientes (datos de contacto e información de salud implícita en citas y especialidades) están protegidos conforme al RGPD y a su carácter de datos de salud: quién accede, qué se registra, cuánto se conserva y qué se filtra en errores, registros o copias.

**Por qué esta prioridad**: es una obligación legal y un riesgo reputacional alto, pero no bloquea el diagnóstico general.

**Prueba independiente**: comprobar que el informe incluye un inventario de datos personales y de salud con su ubicación, quién accede, conservación y protección, y los incumplimientos detectados.

**Escenarios de aceptación**:

1. **Dado** el sistema, **cuando** se completa el inventario, **entonces** cada dato personal figura con dónde se guarda, quién puede leerlo y cómo se protege.
2. **Dado** los registros, errores y copias, **cuando** se inspeccionan, **entonces** el informe indica si contienen datos personales y propone cómo evitarlo.
3. **Dado** un paciente que solicita acceso, rectificación o supresión de sus datos, **cuando** se evalúa el sistema, **entonces** el informe indica si hoy puede atenderse y qué falta.

---

### Historia de usuario 5 - Verificación continua de seguridad (Prioridad: P3)

El equipo necesita que lo auditado no se degrade. Se proponen controles automáticos en el proceso de integración (análisis de dependencias vulnerables, detección de secretos en el repositorio y pruebas de seguridad repetibles) para que una regresión de seguridad rompa el merge, como ya ocurre con las pruebas de solape.

**Por qué esta prioridad**: sostiene el valor a largo plazo; se puede abordar después de corregir los hallazgos.

**Prueba independiente**: introducir deliberadamente una dependencia con vulnerabilidad conocida o un secreto de prueba en una rama y comprobar que la integración la rechaza.

**Escenarios de aceptación**:

1. **Dado** una rama con una dependencia con vulnerabilidad grave conocida, **cuando** se ejecuta la integración continua, **entonces** el merge se bloquea con un mensaje que explica el motivo.
2. **Dado** una rama que incluye una credencial o clave, **cuando** se ejecuta la integración continua, **entonces** se detecta y se bloquea.

---

### Casos límite

- ¿Qué pasa con categorías del OWASP Top 10 que no aplican al sistema? Se marcan «no aplica» con justificación escrita, no se omiten.
- ¿Qué pasa si un hallazgo exige cambiar una spec ajena (por ejemplo la 001)? Se solicita al propietario; la auditoría no edita specs ajenas sin su aprobación previa (FR-014, salvo lo previsto en FR-021 y FR-022).
- ¿Qué pasa si el propietario de la 001 no aprueba los cambios de FR-021 y FR-022? Los hallazgos quedan abiertos como «derivados» con la solicitud registrada en el mapa, y la feature se cierra igualmente (SC-008).
- ¿Qué pasa si el producto aún no tiene cobros? Se evalúa la preparación y se fijan requisitos obligatorios para la futura feature de pagos, sin construir nada de Stripe en esta feature.
- ¿Qué pasa con hallazgos que afectan a los datos de demostración? Los datos ficticios no cuentan como datos reales, pero la clave de demostración publicada no debe poder usarse en un entorno real.
- ¿Qué pasa con hallazgos en dependencias de terceros sin parche disponible? Se registran con mitigación y fecha de revisión.
- ¿Qué pasa si una mejora reduce la facilidad de uso de recepción? Se anota el impacto y se exige que los mensajes sigan sin jerga técnica (Principio VII).
- ¿Qué pasa si una recepcionista legítima falla la clave varias veces? Ve un mensaje claro que indica cuánto esperar, sin términos técnicos, y recupera el acceso al terminar el bloqueo sin intervención de nadie.

## Requisitos *(obligatorio)*

### Requisitos funcionales

- **FR-001**: La auditoría DEBE evaluar el sistema completo (servidor, interfaz, entorno de despliegue e integración continua) contra cada categoría del OWASP Top 10 vigente, dejando un veredicto y su evidencia por categoría.
- **FR-002**: Cada hallazgo DEBE registrarse con identificador, severidad (crítica, alta, media, baja), datos afectados, parte afectada (servidor, interfaz, entorno), pasos de verificación reproducibles y mejora propuesta.
- **FR-003**: La auditoría DEBE evaluar específicamente el control de acceso y el aislamiento por clínica, intentando acceder a recursos de otra clínica por todas las vías (lectura, escritura, listados y cualquier identificador adivinable).
- **FR-004**: La auditoría DEBE evaluar la gestión de sesión y de la clave de acceso: protección contra intentos repetidos de adivinación, caducidad, cierre de sesión, cookies, y exposición de la clave de demostración.
- **FR-005**: La auditoría DEBE evaluar la validación de todas las entradas, la protección contra inyección y la protección de la interfaz contra la ejecución de contenido malicioso (incluidas las cabeceras de seguridad del navegador).
- **FR-006**: La auditoría DEBE evaluar la configuración de los entornos: secretos y credenciales fuera del código, valores por defecto inseguros, puertos expuestos, versiones de imágenes y exposición de información interna en errores.
- **FR-007**: La auditoría DEBE evaluar la cadena de suministro: dependencias del servidor y de la interfaz, imágenes de contenedor y flujo de integración continua, identificando componentes con vulnerabilidades conocidas o sin mantenimiento.
- **FR-008**: La auditoría DEBE evaluar el registro de eventos de seguridad (accesos fallidos, accesos a datos de pacientes, errores de autorización) y la capacidad de detectar un abuso, sin que los registros contengan datos personales innecesarios.
- **FR-009**: La auditoría DEBE evaluar la resistencia a abuso y a sobrecarga (límites de peticiones, tamaños máximos de entrada, operaciones costosas). Los límites que se apliquen NO DEBEN rechazar el uso normal de recepción: una persona que use el panel de forma continua durante una jornada de 8 horas no recibe ningún rechazo por límite de peticiones.
- **FR-010**: La auditoría DEBE incluir una lista de comprobación de las prácticas de seguridad de Stripe y de PCI DSS para comercios que delegan la captura de tarjeta, con veredicto por punto.
- **FR-011**: El informe DEBE fijar requisitos de seguridad obligatorios para cualquier integración futura de cobros, incluyendo como mínimo: ningún dato de tarjeta pasa por CitaClara ni se almacena; verificación de la autenticidad de cada aviso de Stripe; tratamiento idempotente de avisos repetidos o desordenados; importe decidido siempre por el sistema; claves secretas fuera del código; y conciliación al céntimo entre CitaClara y Stripe (Principio II). Los requisitos DEBEN cubrir los dos orígenes de pago: recepción desde el panel autenticado y paciente en línea sin sesión de clínica; en este último, ningún paciente puede ver, iniciar ni modificar el cobro de otro, y los enlaces o referencias de pago no son adivinables ni reutilizables tras completarse o caducar.
- **FR-012**: La auditoría DEBE inventariar los datos personales y de salud de pacientes (dónde se guardan, quién accede, conservación, protección) y evaluar si pueden atenderse solicitudes de acceso, rectificación y supresión del RGPD. El plazo de referencia de conservación es de 5 años desde la última cita del paciente, tras el cual los datos personales se anonimizan sin alterar los importes ni los totales históricos (Principio II); la auditoría comprueba si el sistema puede cumplirlo y, si no, lo registra como hallazgo.
- **FR-013**: El informe DEBE incluir un plan de mejoras priorizado por riesgo, con esfuerzo relativo, parte afectada y criterio de aceptación verificable por cada hallazgo crítico o alto.
- **FR-014**: Salvo lo previsto en FR-021 y FR-022, las mejoras que cambien comportamiento de otras specs o requieran funcionalidad nueva DEBEN proponerse como specs nuevas o como solicitudes a los propietarios, y quedar registradas en `specs/MAPA.md`; esta feature no modifica specs ajenas.
- **FR-015**: Toda mejora propuesta DEBE mantener los mensajes visibles para el usuario en español de España y sin jerga técnica; los motivos técnicos de un rechazo de seguridad no se muestran en pantalla.
- **FR-016**: La auditoría DEBE proponer controles automáticos en el proceso de integración (dependencias vulnerables, secretos en el repositorio, pruebas de seguridad repetibles) que bloqueen el merge ante una regresión.
- **FR-017**: Las pruebas de seguridad que se creen como evidencia DEBEN referenciar su hallazgo o requisito mediante el esquema de trazabilidad del proyecto, y la suite completa DEBE seguir en verde.
- **FR-018**: El informe DEBE ser reproducible: indicar versión del código auditado, fecha, alcance, herramientas y método, de modo que una reauditoría compare resultados con el informe anterior.
- **FR-019**: La auditoría NO DEBE realizar pruebas intrusivas contra entornos con datos reales ni contra servicios de terceros; se ejecuta sobre entornos locales o de demostración.
- **FR-020**: Esta feature DEBE corregir los hallazgos de severidad crítica y alta cuya corrección no cambie el comportamiento definido en specs ajenas, salvo lo previsto en FR-021 y FR-022; cada corrección DEBE ir acompañada de una prueba que reproduzca el hallazgo y compruebe que ya no ocurre. Los hallazgos medios y bajos, y los críticos o altos que sí afecten a specs ajenas, quedan en el plan de seguimiento.
- **FR-021**: Si la auditoría califica de alto o crítico el acceso mediante una clave compartida por clínica, esta feature DEBE sustituirlo por acceso con usuarios individuales por clínica, de forma que cada acceso a datos de pacientes y cada operación quede atribuida a una persona concreta y pueda revocarse sin afectar al resto. Al ser un cambio de comportamiento de la spec 001, requiere la aprobación previa de su propietario y su actualización (Principio I) antes de implementarse; el acceso de recepción debe seguir siendo usable sin formación (Principio VII).
- **FR-022**: Si la auditoría califica de alto o crítico que el acceso no limite los intentos fallidos, esta feature DEBE limitarlos con un bloqueo temporal creciente por origen y por clínica; durante el bloqueo tampoco se acepta una clave correcta desde ese origen, y la persona ve cuánto debe esperar. Cambia el comportamiento que la spec 001 declara como deuda consciente (sin límite de intentos), por lo que requiere la misma aprobación previa de su propietario y la actualización de su spec antes de implementarse.

### Entidades clave *(incluir si la feature maneja datos)*

- **Hallazgo**: debilidad detectada. Atributos: identificador, categoría OWASP, severidad, datos afectados, parte afectada, evidencia, estado.
- **Informe de auditoría**: conjunto de veredictos por categoría y hallazgos, con versión del código, fecha, alcance y método.
- **Mejora propuesta**: acción que corrige uno o más hallazgos. Atributos: parte afectada, esfuerzo relativo, criterio de aceptación, feature o propietario de seguimiento.
- **Requisito de pagos**: regla de seguridad obligatoria para la futura integración con Stripe, verificable.
- **Inventario de datos personales**: relación de cada dato personal o de salud con su ubicación, acceso, conservación y protección.

**Severidad de un hallazgo** (criterio común para FR-002, FR-013 y FR-020):

- **Crítica**: acceso a datos de otra clínica o ejecución de acciones sin sesión, o exposición de credenciales reales.
- **Alta**: compromiso de cuentas viable sin condiciones especiales (por ejemplo, adivinar una clave sin límite de intentos) o fuga de datos personales a registros o a terceros.
- **Media**: debilidad que requiere condiciones previas o reduce la defensa en profundidad.
- **Baja**: endurecimiento o información menor.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: El 100 % de las categorías del OWASP Top 10 vigente tiene veredicto y evidencia en el informe.
- **SC-002**: El 100 % de los hallazgos críticos y altos tiene mejora propuesta con criterio de aceptación verificable y esfuerzo relativo.
- **SC-003**: Al menos el 90 % de los hallazgos puede reproducirse por una persona ajena a la auditoría siguiendo únicamente la ficha del hallazgo.
- **SC-004**: El 100 % de los puntos de la lista de comprobación de Stripe y PCI DSS tiene veredicto, y las reglas obligatorias de cobros quedan fijadas antes de que empiece cualquier feature de pagos.
- **SC-005**: Todos los datos personales y de salud de pacientes están inventariados, y cero datos personales innecesarios aparecen en registros y mensajes de error.
- **SC-006**: Intentar acceder a datos de otra clínica por cualquier vía auditada produce cero accesos y la misma respuesta que un recurso inexistente.
- **SC-007**: Los dos casos de prueba de regresión de seguridad (una dependencia con vulnerabilidad grave conocida y una credencial incluida a propósito) bloquean el merge en el 100 % de las ejecuciones.
- **SC-008**: Tras aplicar las correcciones de esta feature, una reauditoría no encuentra hallazgos críticos ni altos abiertos, salvo los que dependen de una spec ajena, que constan con su solicitud al propietario registrada.
- **SC-009**: El personal de recepción completa sus tareas habituales (iniciar sesión, buscar paciente, reservar cita) con el mismo número de pantallas que antes y sin mensajes técnicos; el único paso nuevo admitido es escribir el nombre de usuario al iniciar sesión, si se aprueba FR-021.

**Excluidos de la trazabilidad**: SC-003 SC-009

## Supuestos

- El entregable de esta feature es el informe de auditoría, el plan de mejoras y la corrección de los hallazgos críticos y altos que no afecten a specs ajenas (FR-020), más los cambios de acceso de FR-021 y FR-022 si el propietario de la 001 los aprueba; el resto se ejecuta en features o specs de seguimiento (Principio IV, sin alcance fantasma).
- Los cobros los podrán iniciar tanto recepción como el paciente en línea (portal de la spec 002); la auditoría trata el acceso público sin sesión como superficie de ataque a evaluar.
- CitaClara aún no integra cobros: la evaluación de Stripe cubre la preparación y los requisitos de la integración futura, y no construye ninguna integración de Stripe.
- Se asume el modelo de Stripe en el que la captura de tarjeta la realiza Stripe (la tarjeta nunca toca CitaClara), lo que reduce el alcance PCI DSS al mínimo para comercios.
- «OWASP Top 10 2026» se interpreta como la edición vigente más reciente del OWASP Top 10 en la fecha de la auditoría; el informe cita la edición exacta usada.
- Se asume que los datos de pacientes se tratan como datos de salud bajo el RGPD y la normativa española aplicable; el plazo de conservación de 5 años es una referencia técnica, no asesoramiento jurídico; la validación jurídica final queda fuera del alcance técnico y corresponde a la clínica o a su asesoría legal.
- La auditoría se hace sobre el código y los entornos locales o de demostración de la rama principal en el momento de empezar, con herramientas estándar de análisis y revisión manual.
- Los cambios de FR-021 y FR-022 dependen de que el propietario de la spec 001 apruebe el cambio; hasta entonces se mantiene el acceso actual y las solicitudes quedan registradas en `specs/MAPA.md`.
- Otras features en paralelo (portal del paciente, recordatorios, panel de analítica) amplían la superficie de ataque; los requisitos de seguridad resultantes se registran para que sus propietarios los apliquen, sin editar sus specs.
- Fuera de alcance: pentesting contra producción real, certificación formal (PCI DSS, ENS, ISO 27001), auditoría de la infraestructura del proveedor de alojamiento y construcción de la integración de pagos.
