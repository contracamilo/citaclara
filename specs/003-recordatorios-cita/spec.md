# Especificación de feature: Recordatorios de cita por email

**Rama de la feature**: `003-recordatorios-cita`

**Creada**: 27/11/2026

**Estado**: Implementada

**Excluidos de la trazabilidad**: SC-007 (resultado de negocio del cliente piloto que se mide fuera del producto)

**Propietario**: feature 003-recordatorios-cita (registrado en `specs/MAPA.md`)

**Entrada**: descripción del usuario: «Recordatorios de cita. El dolor número uno del cliente piloto: la no asistencia. Alcance: un proceso diario genera un recordatorio por email para cada cita reservada de las próximas 24-48 horas, sin duplicar envíos; el email incluye los datos de la cita y una forma de que el paciente cancele si no va a ir (mejor un hueco libre que un no-show). Correo en modo simulado sin SMTP configurado: se escriben ficheros .eml en datos/salida-correo/. Preguntas cerradas para Sara: antelación exacta, qué pasa si el paciente cancela desde el email y con cuánta antelación puede, y si el recordatorio se reenvía cuando la cita se mueve. Fuera de alcance v1: SMS y WhatsApp.»

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - El paciente recibe un recordatorio de su cita (Prioridad: P1)

Cada día, el sistema revisa las citas reservadas que se acercan y envía un email de recordatorio al paciente de cada una. El email le dice con quién, qué servicio, qué día y a qué hora es su cita, y en qué clínica. Una misma cita nunca recibe dos recordatorios, aunque el proceso se ejecute más de una vez el mismo día o se relance tras un fallo.

**Por qué esta prioridad**: es el núcleo de la feature y lo que ataca el dolor del cliente piloto (la no asistencia). Por sí sola ya reduce los olvidos, aunque el paciente no pueda cancelar desde el correo.

**Prueba independiente**: con los datos de demostración cargados y pacientes con email, ejecutar el proceso diario y comprobar que hay un correo por cada cita reservada dentro de la ventana de antelación, con los datos correctos, y que una segunda ejecución no genera ninguno nuevo.

**Escenarios de aceptación**:

1. **Dado** una cita reservada cuyo inicio cae dentro de la ventana de antelación y un paciente con email, **cuando** se ejecuta el proceso diario, **entonces** se genera un recordatorio para ese paciente con clínica, profesional, servicio, fecha y hora de la cita.
2. **Dado** una cita que ya recibió su recordatorio, **cuando** el proceso diario se ejecuta de nuevo (el mismo día o al siguiente), **entonces** no se genera otro recordatorio para esa cita.
3. **Dado** una cita cancelada, completada o no asistida, **cuando** se ejecuta el proceso diario, **entonces** no se genera ningún recordatorio para ella.
4. **Dado** una cita reservada cuyo inicio queda fuera de la ventana de antelación (demasiado pronto o demasiado lejos), **cuando** se ejecuta el proceso diario, **entonces** no se genera recordatorio para ella.
5. **Dado** un paciente sin email en su ficha, **cuando** se ejecuta el proceso diario, **entonces** no se genera recordatorio para su cita y el resto de recordatorios no se ven afectados.
6. **Dado** que el correo se genera en modo simulado (sin servidor de correo configurado), **cuando** se ejecuta el proceso diario, **entonces** cada recordatorio queda guardado como un fichero de correo legible en la carpeta de salida de correo, con destinatario, asunto y cuerpo en español de España.
7. **Dado** dos clínicas con citas en la ventana, **cuando** se ejecuta el proceso diario, **entonces** cada recordatorio solo contiene datos de la clínica de su cita y de su paciente.

---

### Historia de usuario 2 - El paciente cancela desde el email si no va a ir (Prioridad: P2)

Desde el propio recordatorio, el paciente que sabe que no podrá acudir abre un enlace en su móvil, ve los datos de su cita, confirma la cancelación y recibe confirmación de que la cita ha quedado cancelada. El hueco queda libre para que la recepción pueda ofrecerlo a otra persona.

**Por qué esta prioridad**: convierte un posible no-show en un hueco libre aprovechable, que es el segundo objetivo del cliente. Depende de que existan los recordatorios (historia 1).

**Prueba independiente**: tomar un recordatorio generado, abrir su enlace de cancelación sin haber iniciado sesión en el panel, confirmar, y comprobar que la cita pasa a cancelada y su franja vuelve a figurar libre en la agenda de recepción.

**Escenarios de aceptación**:

1. **Dado** un recordatorio con enlace de cancelación y una cita todavía cancelable, **cuando** el paciente abre el enlace, **entonces** ve los datos de su cita y un botón para cancelarla, sin necesidad de clave ni de crear cuenta.
2. **Dado** la pantalla de cancelación, **cuando** el paciente confirma, **entonces** la cita pasa a cancelada, su franja queda libre en la agenda y el paciente ve un mensaje claro de que la cancelación se ha realizado.
3. **Dado** que el paciente solo abre el enlace sin confirmar, **cuando** se revisa la cita, **entonces** sigue reservada (abrir el enlace por sí solo, p. ej. por una herramienta de revisión de correo, nunca cancela).
4. **Dado** una cita ya cancelada, completada o no asistida, **cuando** el paciente abre el enlace, **entonces** ve un mensaje claro de que la cita ya no está activa y no puede cancelarla.
5. **Dado** una cita que empieza en menos de 24 horas, **cuando** el paciente abre el enlace, **entonces** ve un mensaje claro que le indica que debe contactar con la clínica, con el teléfono de la clínica.
6. **Dado** un enlace manipulado, inventado o de otra cita, **cuando** se abre, **entonces** se responde igual que ante un enlace inexistente, sin revelar datos de ninguna cita ni de ninguna clínica.
7. **Dado** que el paciente confirma desde dos pestañas a la vez o recarga tras confirmar, **cuando** el sistema lo procesa, **entonces** la cita se cancela una sola vez y el paciente ve en ambos casos un resultado coherente («la cita está cancelada»).
8. **Dado** una cancelación hecha por el paciente, **cuando** la recepcionista consulta la agenda, **entonces** ve la cita como cancelada y puede distinguir que la canceló el propio paciente desde el recordatorio.

---

### Historia de usuario 3 - El recordatorio sigue a la cita cuando cambia (Prioridad: P3)

Cuando una cita cambia de hora o de profesional, el paciente no debe quedarse con un recordatorio que apunte a datos antiguos, ni con un enlace que cancele algo distinto de lo que cree. El recordatorio antiguo no se reenvía; la cita nueva recibe el suyo solo si cae en la ventana (ver FR-014).

**Por qué esta prioridad**: evita confusiones y cancelaciones erróneas, pero solo importa una vez funcionan las historias 1 y 2.

**Prueba independiente**: con una cita que ya recibió su recordatorio, cancelarla y crear otra para el mismo paciente en otra hora; comprobar que el enlace del recordatorio antiguo no cancela la cita nueva y que el recordatorio de la cita nueva se genera según la regla acordada.

**Escenarios de aceptación**:

1. **Dado** una cita que ya recibió su recordatorio y que después la recepción cancela, **cuando** el paciente abre el enlace antiguo, **entonces** ve que la cita ya no está activa y no se ve afectada ninguna otra cita suya.
2. **Dado** una cita nueva que sustituye a la cancelada y cae en la ventana, **cuando** se ejecuta el proceso diario, **entonces** el paciente recibe el recordatorio de la cita nueva con sus datos actuales.
3. **Dado** una cita nueva que sustituye a una ya recordada y cuyo inicio queda fuera de la ventana, **cuando** se ejecuta el proceso diario, **entonces** no se genera recordatorio para ella, y el recordatorio antiguo no se reenvía con la hora antigua.

---

### Casos límite

- **Cita creada con menos de 24 h de antelación**: no recibe recordatorio (el paciente acaba de acordarla con la clínica); comportamiento esperado.
- **Cita reservada después de la ejecución diaria y que ya queda a menos de 24 h de la siguiente**: por ejemplo, reservada a las 10:00 para el día siguiente a las 12:00 (con ejecución a las 09:00). Nunca entra en la ventana, así que no recibe recordatorio; es coherente con «nunca menos de 24 h» y con que el paciente acaba de acordarla con la clínica. Limitación aceptada de v1 (descubierta al planificar).
- **Cita que empieza en menos de 24 h**: ni recibe recordatorio ni se puede cancelar desde el email; el paciente debe llamar a la clínica.
- **Cita en la franja de cambio de horario (verano/invierno)**: la ventana se calcula en horas reales y la hora que ve el paciente es siempre la hora peninsular española correcta; ninguna cita se duplica ni se omite por el cambio.
- **Proceso que falla a mitad**: al relanzarlo, solo se generan los recordatorios que faltaban; los ya generados no se repiten y los que faltaban no se pierden.
- **Paciente con varias citas en la ventana**: recibe un recordatorio por cada cita, cada uno con su propio enlace de cancelación, de forma que cancelar una no cancele las demás.
- **Paciente con email no válido o inexistente**: el fallo de un recordatorio no impide generar los de los demás.
- **Carpeta de salida no disponible o sin permisos**: el proceso lo comunica de forma clara y no marca como enviado un recordatorio que no se ha podido guardar, de modo que se reintente en la siguiente ejecución.
- **Enlace abierto tarde**: pasado el plazo de 24 h antes del inicio, el enlace ya no permite cancelar y lo explica con claridad, con el teléfono de la clínica.
- **Cita ya iniciada**: nunca se puede cancelar desde el email (rige además la regla de la spec 001: solo se cancela antes del inicio).

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Generación de recordatorios**

- **FR-001**: El sistema DEBE ejecutar un proceso diario que genera un recordatorio por email para cada cita reservada cuyo inicio cae dentro de la ventana de antelación definida en FR-002.
- **FR-002**: La antelación del recordatorio es de 24 horas (decisión de Sara, 27/11/2026). Como el proceso es diario, cada ejecución recuerda a las citas cuyo inicio cae desde 24 h después de la ejecución hasta 24 h después de la ejecución del día siguiente (a la misma hora local): las ventanas de días consecutivos encajan sin huecos ni solapes, incluso los días de cambio de horario, de modo que cada cita entra en la ventana una sola vez y nunca se recuerda con menos de 24 h de antelación.
- **FR-003**: El sistema NO DEBE generar recordatorio para citas completadas, canceladas o no asistidas, ni para citas reservadas fuera de la ventana.
- **FR-004**: El sistema NO DEBE generar más de un recordatorio por cita en la misma vida de la cita, ni siquiera si el proceso se ejecuta varias veces, se relanza tras un fallo o coincide con otra ejecución simultánea. Una cita nueva que sustituye a otra es una cita distinta y tiene su propio recordatorio (FR-014).
- **FR-005**: El sistema DEBE registrar, por cada recordatorio, a qué cita corresponde, cuándo se generó y a qué dirección, para poder comprobar que no hay duplicados y qué se envió.
- **FR-006**: Si el paciente de una cita no tiene email en su ficha, el sistema NO DEBE generar recordatorio para esa cita y DEBE continuar con las demás.
- **FR-007**: El fallo al generar un recordatorio NO DEBE impedir que se generen los demás, y un recordatorio no generado NO DEBE quedar registrado como enviado, de modo que se pueda reintentar.
- **FR-008**: El proceso DEBE respetar el aislamiento por clínica: un recordatorio solo contiene datos de su clínica y de su paciente.

**Contenido del correo**

- **FR-009**: Cada email DEBE incluir: nombre de la clínica, nombre del paciente, profesional, servicio, fecha (día/mes/año), hora de inicio en formato de 24 horas y hora peninsular española, y el enlace de cancelación. DEBE incluir además el teléfono de la clínica para quien prefiera llamar.
- **FR-010**: Todo el texto del email (asunto y cuerpo) DEBE estar redactado en español de España, sin jerga técnica ni identificadores internos visibles, y DEBE leerse correctamente en un móvil.
- **FR-011**: El importe de la cita NO DEBE aparecer en el recordatorio salvo que una spec posterior lo pida (el objetivo del correo es la asistencia, no el cobro).

**Cancelación desde el email**

- **FR-012**: El enlace de cancelación DEBE ser único por cita, no adivinable, y permitir cancelar sin clave de recepción ni cuenta del paciente. Abrirlo solo muestra la cita; cancelar exige una confirmación explícita del paciente.
- **FR-013**: El paciente DEBE poder cancelar desde el email hasta 24 horas antes del inicio de la cita (decisión de Sara, 27/11/2026). Pasado ese plazo el enlace no cancela y muestra un mensaje claro con el teléfono de la clínica para que el paciente llame. Al cancelar solo se libera el hueco: no se avisa a recepción de forma activa; la recepción lo ve en la agenda (FR-016) y el hueco puede reservarse de inmediato.
- **FR-014**: Cuando una cita cambia (en el estado actual del producto, una cita se «mueve» cancelándola y creando otra), el recordatorio antiguo NO se reenvía y su enlace deja de ser válido. La cita nueva recibe su propio recordatorio, con su propio enlace, solo si su inicio cae dentro de la ventana de FR-002 en alguna ejecución del proceso (decisión de Sara, 27/11/2026).
- **FR-015**: Tras cancelar, la cita DEBE pasar a «cancelada» y su franja DEBE volver a figurar libre en la agenda, con las mismas garantías que una cancelación hecha desde recepción (sin solapes, sin cancelaciones dobles, sin cambios sobre citas ya iniciadas ni sobre citas en estado final).
- **FR-016**: La agenda de recepción DEBE permitir distinguir una cita cancelada por el propio paciente desde el recordatorio de una cancelada por la clínica.
- **FR-017**: Las pantallas y mensajes que ve el paciente al abrir el enlace DEBEN estar en español de España, sin códigos ni términos técnicos, con contraste y tamaños accesibles y objetivos táctiles de al menos 44 px, y funcionar en el móvil del paciente.
- **FR-018**: Un enlace inválido, manipulado, de otra cita o de una cita ya no activa DEBE responder sin revelar datos de otras citas o clínicas. El enlace deja de ser válido para cancelar a partir de la hora de inicio de la cita.
- **FR-019**: Las cancelaciones simultáneas de la misma cita (paciente y recepción, o dos veces el paciente) DEBEN resolverse aplicando solo la primera y mostrando a la otra parte el estado real de la cita con un aviso claro.

**Modo de correo simulado**

- **FR-020**: Mientras no haya un servidor de correo configurado, el sistema DEBE funcionar en modo simulado: cada recordatorio se guarda como un fichero de correo independiente en la carpeta de salida de correo (`datos/salida-correo/`), con un nombre que permita relacionarlo con su cita, y nunca se envía nada a una dirección real.
- **FR-021**: El modo de funcionamiento (simulado o real) DEBE decidirse por la configuración del entorno, sin cambiar el comportamiento observable salvo el destino físico del correo.

**Fuera de alcance (v1)**

- **FR-022**: Esta feature NO incluye SMS ni WhatsApp, ni pantallas de configuración de plantillas o plazos, ni estadísticas de asistencia, ni recordatorios al profesional o a la recepción; ninguna de estas cosas se construye «por si acaso».

### Entidades clave

- **Recordatorio**: aviso por email asociado a una cita concreta. Cita, paciente, dirección de destino, instante de generación y estado (generado / no generado), de modo que se pueda demostrar que no hay duplicados.
- **Enlace de cancelación**: acceso único y no adivinable de una cita concreta, que permite ver la cita y cancelarla con confirmación hasta que deja de ser válido.
- **Cita** (spec 001): sigue siendo el origen de los datos; esta feature solo la lee y, al cancelar desde el email, la lleva a «cancelada» como lo haría la recepción.
- **Paciente** (spec 001): aporta nombre y email opcional.
- **Clínica** (spec 001): aporta nombre y teléfono y delimita los datos de cada recordatorio.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: En el 100 % de las ejecuciones, cada cita reservada dentro de la ventana cuyo paciente tiene email recibe exactamente un recordatorio: ni uno menos, ni uno más, aunque el proceso se ejecute varias veces.
- **SC-002**: Un paciente puede cancelar su cita desde el recordatorio en menos de 30 segundos y en tres pasos como máximo (abrir el enlace, revisar la cita, confirmar), sin crear cuenta.
- **SC-003**: Tras una cancelación hecha por el paciente, el hueco aparece libre en la agenda de recepción de inmediato y puede reservarse de nuevo sin intervención técnica.
- **SC-004**: Con los datos de demostración, el número de recordatorios generados en una ejecución coincide con la cifra documentada para la posición relativa del día de ejecución (se documenta junto a las demás cifras citables).
- **SC-005**: Ningún recordatorio muestra una fecha u hora ambigua o equivocada, incluidos los días de cambio de horario: el 100 % de las horas visibles son horas peninsulares españolas en formato de 24 horas.
- **SC-006**: Cero recordatorios con datos de otra clínica o de otro paciente, y cero enlaces que cancelen una cita distinta de la que se muestra.
- **SC-007**: Disminuye la tasa de citas «no asistidas» del cliente piloto durante el primer mes respecto al mes anterior (objetivo de negocio que se medirá fuera del producto; el valor objetivo lo fija la clínica con Sara).

## Clarificaciones

### Sesión 27/11/2026 (respuestas de Sara)

- Q: Antelación exacta → A: Opción C — antelación fija de 24 h (FR-002).
- Q: Cancelación desde el email → A: Opción C — hasta 24 h antes; después, llamar a la clínica (FR-013).
- Q: Cita que se mueve → A: Opción C — la cita nueva recibe recordatorio solo si cae dentro de la ventana (FR-014).

## Suposiciones

- El proceso diario se ejecuta una vez al día a una hora fija en horario peninsular español; la hora exacta se decide en el plan junto con la respuesta de Sara.
- El email del paciente ya es un dato opcional de la ficha (spec 001, FR-019); esta feature no cambia la ficha ni obliga a introducirlo.
- Hoy no existe «mover una cita»: se cancela y se crea otra (spec 001, «Sin mover ni reactivar citas»). Si una feature futura añade el movimiento directo, el reenvío se rige por FR-014.
- Cancelar desde el email es más restrictivo que desde recepción: el paciente cancela hasta 24 h antes (FR-013), mientras que la recepción sigue pudiendo cancelar hasta el inicio (spec 001); esta feature no cambia esa regla.
- Con un proceso diario, «24 h de antelación» se aplica como ventana [24 h, 24 h + un día local) desde la ejecución (FR-002); la hora de ejecución (09:00, hora peninsular) se fija en el plan.
- El paciente no tiene acceso al producto más allá del enlace de su cita. Un portal del paciente más amplio (otra feature) queda fuera de esta spec; si coincide con ella en la cancelación, los propietarios de ambas specs lo resuelven en las specs antes que en el código.
- Esta feature solo lee citas, pacientes y clínicas y las cancela como lo haría recepción; no cambia el comportamiento definido en la spec 001. Si necesita datos de demostración nuevos (p. ej. pacientes con email), lo solicita al propietario de la spec 001.
- El modo simulado de correo es el único modo que se entrega y se prueba en esta feature; el envío real queda condicionado a configurar un servidor de correo y sus credenciales, sin pruebas contra servicios externos reales.
- Los correos viajan sin contenido clínico (solo servicio, profesional, día y hora) para minimizar la exposición de datos de salud.
