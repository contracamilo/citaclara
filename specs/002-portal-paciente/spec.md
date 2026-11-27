# Especificación de feature: Portal del paciente

**Rama de la feature**: `002-portal-paciente`

**Creada**: 27/11/2026

**Estado**: Borrador (decisiones de producto cerradas por Sara el 27/11/2026)

**Propietario**: feature 002-portal-paciente (registrado en `specs/MAPA.md`)

**Entrada**: descripción del usuario: «Portal del paciente. Los pacientes de la clínica quieren ver sus citas y cancelarlas sin llamar por teléfono (Sonia, recepción de Eleva, dedica "la mitad de la mañana" a esto). Alcance: un paciente accede a una página personal moderna donde ve sus citas futuras y pasadas y puede cancelar una cita futura. Interfaz limpia y responsive, pensada para móvil. Preguntas que la spec debe dejar decididas (formúlalas cerradas, para Sara): cómo accede el paciente sin crear cuentas ni contraseñas (las clínicas pequeñas no van a gestionar altas), hasta cuándo puede cancelar, y qué pasa con el hueco liberado. Datos: cita casos reales de la semilla en los ejemplos. Fuera de alcance v1: reservar o mover citas online, pagos.»

## Clarifications

### Session 2026-11-27

- Q: ¿Cómo entra el paciente sin cuenta ni contraseña? A) código de un solo uso por SMS al teléfono de su ficha; B) enlace de un solo uso por email a la dirección de su ficha; C) enlace personal permanente que la recepción copia y envía. → A: Opción B — enlace de un solo uso enviado al email de la ficha. Los pacientes sin email en su ficha no pueden entrar en el portal y siguen llamando a la clínica.
- Q: ¿Hasta cuándo puede cancelar el paciente desde el portal? A) hasta la hora de inicio; B) hasta 24 horas antes del inicio; C) hasta las 20:00 del día laborable anterior. → A: Opción B — hasta 24 horas antes del inicio de la cita; después, solo por teléfono con la recepción.
- Q: ¿Qué pasa con el hueco liberado? A) queda libre sin más; B) queda libre y la agenda muestra que lo canceló el paciente; C) queda libre y la recepción recibe un aviso activo con una lista de cancelaciones del portal pendientes de revisar. → A: Opción C — el hueco queda libre de inmediato y la recepción ve un aviso con las cancelaciones del portal pendientes de revisar.

## Contexto

Hoy el paciente solo existe como ficha gestionada por la recepción (spec 001). Cada consulta del tipo «¿a qué hora tenía la cita?» o «no puedo ir el martes» llega por teléfono; en Clínica Eleva, Sonia dedica a ello «la mitad de la mañana». Esta feature da al paciente una página propia, pensada para el móvil, donde ve sus citas y cancela las futuras sin intervención de la recepción, sin cuentas ni contraseñas que nadie tenga que dar de alta; y da a la recepción un aviso de los huecos que se liberan así, para poder volver a ocuparlos.

## Ejemplos con los datos de demostración

Todos los ejemplos usan la semilla de Clínica Eleva documentada en [`specs/001-nucleo-agenda/datos-demostracion.md`](../001-nucleo-agenda/datos-demostracion.md) (posiciones en días laborables respecto al día de carga). Los emails son los ficticios de la semilla.

| Paciente | Email de la ficha | Cita citada | Qué ve en el portal |
|---|---|---|---|
| Teresa Méndez | teresa.mendez@ejemplo.es | Día laborable +1, María, 10:15–11:15, Primera visita fisioterapia, 50,00 €, reservada | Próxima cita; cancelable si faltan más de 24 h para las 10:15 del día laborable +1 |
| Laura Romero | laura.romero@ejemplo.es | Día laborable +2, Jorge, 09:45–10:30, Sesión fisioterapia, 40,00 €, reservada | Próxima cita; cancelable si faltan más de 24 h para las 09:45 del día laborable +2 |
| Rocío Garrido | rocio.garrido@ejemplo.es | Día laborable −3, María, 10:00–10:45, Sesión fisioterapia, 40,00 €, completada | Cita pasada «Realizada», sin acciones |
| Gonzalo Cruz | gonzalo.cruz@ejemplo.es | Día laborable −1, Lucía, 09:30–10:00, Consulta nutrición, 35,00 €, completada | Cita pasada «Realizada», sin acciones |

Los recuentos por paciente (cuántas citas futuras y pasadas tiene cada uno en la semilla) no están documentados todavía; el plan DEBE documentarlos para estos cuatro pacientes (FR-030) antes de citarlos en pruebas o demostraciones.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - Entrar en mi portal con un enlace a mi email (Prioridad: P1)

Un paciente de la clínica abre el portal desde su móvil, escribe el email que dio en la clínica y recibe un enlace de un solo uso; al abrirlo llega a su página personal. No crea cuenta ni contraseña, y nadie en la clínica tiene que darle de alta ni gestionar su acceso.

**Por qué esta prioridad**: sin una entrada segura no hay portal; es la base de las otras historias.

**Prueba independiente**: con la semilla cargada, pedir acceso para teresa.mendez@ejemplo.es, abrir el enlace recibido y llegar a una página que la saluda por su nombre; pedir acceso para un email que no es de ningún paciente y comprobar que la respuesta es idéntica y que no se envía nada.

**Escenarios de aceptación**:

1. **Dado** que Teresa Méndez es paciente de Clínica Eleva con email teresa.mendez@ejemplo.es, **cuando** lo escribe en el portal de la clínica y pide el enlace, **entonces** ve «Si tu email está registrado en Clínica Eleva, te hemos enviado un enlace para entrar. Revisa tu correo; el enlace caduca en 10 minutos.» y recibe en ese email un enlace de acceso.
2. **Dado** que Teresa abre el enlace recibido antes de 10 minutos, **cuando** se carga el portal, **entonces** entra en su página personal sin haber creado cuenta ni contraseña.
3. **Dado** un email que no corresponde a ninguna ficha de la clínica, **cuando** alguien pide el enlace, **entonces** ve exactamente el mismo mensaje que en el escenario 1 y no se envía ningún email.
4. **Dado** un enlace ya usado o emitido hace más de 10 minutos, **cuando** alguien lo abre, **entonces** no entra y ve «Este enlace ya no es válido. Pide uno nuevo.» con la opción de pedirlo.
5. **Dado** que la sesión del paciente ha caducado, **cuando** intenta cualquier acción, **entonces** vuelve a la entrada con un mensaje claro («Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.») y la acción no se aplica.
6. **Dado** que el paciente ha entrado, **cuando** pulsa «Salir», **entonces** su sesión termina en ese dispositivo y volver atrás no muestra sus datos.
7. **Dado** que se piden enlaces repetidamente para el mismo email, **cuando** se supera el límite, **entonces** el portal deja de enviarlos durante un tiempo y lo explica sin jerga («Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.»).

---

### Historia de usuario 2 - Ver mis citas futuras y pasadas (Prioridad: P1)

Una vez dentro, el paciente ve primero sus próximas citas (fecha, hora, profesional, servicio y precio) y, más abajo, su historial de citas pasadas con lo que ocurrió en cada una.

**Por qué esta prioridad**: responder «¿cuándo tengo cita?» es la llamada más frecuente; con solo esta historia (más la 1) la recepción ya se ahorra parte de las llamadas.

**Prueba independiente**: con la semilla cargada, entrar como Teresa Méndez y comprobar que su cita del día laborable +1 aparece la primera en «Próximas citas» con todos sus datos; entrar como Rocío Garrido y comprobar que su cita del día laborable −3 aparece en «Citas pasadas» como «Realizada».

**Escenarios de aceptación**:

1. **Dado** que Teresa Méndez tiene una cita reservada el día laborable +1 con María de 10:15 a 11:15, **cuando** entra en el portal, **entonces** la ve en «Próximas citas» con fecha (día de la semana y dd/mm/aaaa), horario «10:15–11:15», profesional «María», servicio «Primera visita fisioterapia» y precio «50,00 €».
2. **Dado** un paciente con varias citas futuras, **cuando** las consulta, **entonces** aparecen ordenadas de la más próxima a la más lejana.
3. **Dado** que Rocío Garrido tuvo una cita completada el día laborable −3 con María de 10:00 a 10:45, **cuando** consulta «Citas pasadas», **entonces** la ve marcada como «Realizada», sin ninguna acción disponible.
4. **Dado** un paciente con citas pasadas no asistidas o canceladas, **cuando** consulta «Citas pasadas», **entonces** cada una muestra su resultado en lenguaje del paciente («No acudiste», «Cancelada») y ninguna ofrece acciones.
5. **Dado** una cita futura cancelada (por el paciente o por la recepción), **cuando** el paciente consulta el portal, **entonces** la ve como «Cancelada» y ya no figura entre sus próximas citas activas.
6. **Dado** una cita reservada cuya hora de inicio ya ha llegado pero que la recepción aún no ha cerrado, **cuando** el paciente la consulta, **entonces** aparece en «Citas pasadas» como «Pendiente de cierre por la clínica» y no se puede cancelar.
7. **Dado** un paciente sin citas futuras, **cuando** entra, **entonces** ve un mensaje claro («No tienes citas próximas. Si quieres pedir una, llama a la clínica.») y su historial si lo tiene.
8. **Dado** cualquier paciente, **cuando** consulta el portal, **entonces** nunca ve citas, nombres ni datos de otros pacientes, ni huecos libres de la agenda.

---

### Historia de usuario 3 - Cancelar una cita futura yo mismo (Prioridad: P1)

El paciente que no puede acudir cancela desde el portal una cita futura, con una confirmación previa, siempre que falten más de 24 horas para su inicio. El hueco queda libre al instante y la recepción recibe el aviso.

**Por qué esta prioridad**: es la otra mitad de las llamadas que atiende Sonia y la que más tiempo le cuesta; además, un hueco avisado con 24 horas de margen puede volver a ocuparse.

**Prueba independiente**: con la semilla cargada y el reloj en el día laborable +1 a las 09:00 (más de 24 h antes de la cita), entrar como Laura Romero, cancelar su cita del día laborable +2 y comprobar que pasa a «Cancelada» en el portal y que su franja de 09:45 a 10:30 aparece libre en la agenda de Jorge.

**Escenarios de aceptación**:

1. **Dado** que Laura Romero tiene una cita reservada el día laborable +2 con Jorge de 09:45 a 10:30 y son las 09:00 del día laborable +1, **cuando** pulsa «Cancelar cita», **entonces** ve una confirmación con los datos de la cita («¿Cancelar tu cita del [día dd/mm/aaaa] a las 09:45 con Jorge?») y la cita solo se cancela si confirma.
2. **Dado** que Laura confirma la cancelación, **cuando** el sistema la registra, **entonces** la cita pasa a cancelada, el portal lo confirma («Tu cita se ha cancelado.») y la franja de 09:45 a 10:30 de Jorge queda libre en la agenda de recepción.
3. **Dado** que Laura abre la confirmación pero pulsa «Volver», **cuando** vuelve a su lista, **entonces** la cita sigue reservada.
4. **Dado** que son las 09:45 o más tarde del día laborable +1 (faltan 24 horas o menos para la cita de Laura), **cuando** Laura consulta la cita, **entonces** no se le ofrece cancelarla y ve «Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.».
5. **Dado** que Laura abre la confirmación a las 09:44 del día laborable +1 y confirma a las 09:45, **cuando** el sistema lo procesa, **entonces** la cancelación se rechaza con el mensaje del escenario 4 y la cita sigue reservada.
6. **Dado** que la recepción cambia el estado de la cita (o la cancela) mientras el paciente la está cancelando, **cuando** el sistema procesa ambas acciones, **entonces** solo se aplica la primera y el paciente ve el estado real con un aviso claro («Esta cita acaba de cambiar. Revisa su estado.»).
7. **Dado** que el paciente pulsa dos veces «Confirmar», **cuando** el sistema lo procesa, **entonces** la cita queda cancelada una sola vez y el paciente ve la confirmación sin errores.

---

### Historia de usuario 4 - Enterarme en recepción de los huecos que liberan los pacientes (Prioridad: P2)

Sonia, desde cualquier pantalla del panel de recepción, ve un aviso con el número de cancelaciones hechas desde el portal que aún no ha revisado. Al abrirlo ve la lista de esas cancelaciones (paciente, profesional, fecha y hora del hueco, servicio y cuándo se canceló) para poder ofrecer el hueco a otro paciente por teléfono, y marca cada una como revisada.

**Por qué esta prioridad**: sin el aviso, el hueco liberado pasa desapercibido hasta que alguien mira esa agenda; depende de que exista la cancelación desde el portal (historia 3).

**Prueba independiente**: con la semilla cargada, cancelar desde el portal la cita de Laura Romero del día laborable +2; entrar en el panel con la clave de Clínica Eleva y comprobar que el aviso muestra 1 pendiente, que la lista muestra la cancelación con sus datos y que, al marcarla como revisada, el aviso desaparece.

**Escenarios de aceptación**:

1. **Dado** que Laura Romero ha cancelado desde el portal su cita del día laborable +2 con Jorge de 09:45 a 10:30, **cuando** la recepción abre cualquier pantalla del panel, **entonces** ve un aviso «1 cancelación de pacientes por revisar».
2. **Dado** el aviso, **cuando** la recepción lo abre, **entonces** ve la cancelación con paciente «Laura Romero», profesional «Jorge», fecha y horario del hueco «09:45–10:30», servicio «Sesión fisioterapia» y el día y la hora en que se canceló, ordenadas de la más reciente a la más antigua.
3. **Dado** una cancelación de la lista, **cuando** la recepción pulsa «Marcar como revisada», **entonces** sale de la lista de pendientes y el contador baja en uno; al llegar a 0, el aviso desaparece.
4. **Dado** que la cancelación la hizo la recepción desde el panel, **cuando** se consulta el aviso, **entonces** no aparece en la lista (solo cuentan las cancelaciones hechas desde el portal).
5. **Dado** que dos recepcionistas marcan a la vez la misma cancelación como revisada, **cuando** el sistema lo procesa, **entonces** queda revisada una sola vez y ninguna de las dos ve un error.
6. **Dado** una cancelación pendiente cuyo hueco ya ha pasado, **cuando** se consulta la lista, **entonces** sigue en ella, indicada como «Hueco ya pasado», hasta que se marque como revisada.
7. **Dado** la clave de otra clínica, **cuando** se consulta el aviso, **entonces** nunca aparecen cancelaciones de Clínica Eleva.

---

### Casos límite

- **Inicio de la cita ya alcanzado**: con el plazo de 24 horas, una cita iniciada nunca se puede cancelar desde el portal; la recepción mantiene su propia regla (spec 001, FR-022: hasta el inicio).
- **Exactamente 24 horas antes**: el plazo es estricto; se puede cancelar mientras falten más de 24 horas. A las 09:45:00 del día laborable +1 ya no se puede cancelar la cita de las 09:45 del día laborable +2.
- **Plazo que cruza un cambio de hora**: las 24 horas son 24 horas reales; un cambio de horario verano/invierno no alarga ni acorta el plazo ni desplaza la hora mostrada de la cita.
- **Plazo que cruza un fin de semana**: las 24 horas son naturales, no laborables; una cita del lunes a las 10:00 se puede cancelar hasta antes del domingo a las 10:00.
- **Varias fichas con el mismo email** (p. ej. una madre que gestiona las citas de su hijo): el portal muestra las citas de todas las fichas de esa clínica con ese email, con el nombre del paciente en cada cita, y permite cancelar cualquiera de ellas en plazo.
- **Ficha sin email**: el paciente no puede entrar en el portal. Si escribe un email que no consta, recibe el mensaje neutro de siempre (que ya le dice que llame a la clínica si no le llega el enlace).
- **Email escrito con mayúsculas o espacios**: «Teresa.Mendez@ejemplo.es » identifica la misma ficha que «teresa.mendez@ejemplo.es».
- **Mismo email en dos clínicas**: cada clínica tiene su propio portal; el enlace de Clínica Eleva nunca da acceso a citas de otra clínica.
- **Paciente que cambia de email**: el acceso usa el email vigente en su ficha; la recepción lo actualiza fuera de esta feature. Las sesiones ya abiertas terminan de forma natural al caducar.
- **Enlace reenviado o abierto en otro dispositivo**: el enlace es de un solo uso y caduca a los 10 minutos; quien lo abre primero entra; un segundo intento ve «Este enlace ya no es válido».
- **Cita cancelada por la recepción**: en el portal aparece igual que una cancelada por el paciente («Cancelada») y no genera aviso en recepción.
- **Dos dispositivos del mismo paciente** cancelando la misma cita a la vez: se cancela una sola vez y genera un solo aviso.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Acceso del paciente**

- **FR-001**: El sistema DEBE ofrecer un portal por clínica al que el paciente accede sin crear cuenta ni contraseña y sin que la recepción tenga que darle de alta ni gestionar su acceso: el paciente escribe su email y recibe en él un enlace de acceso de un solo uso.
- **FR-002**: El sistema DEBE enviar el enlace solo si el email coincide con el de al menos una ficha de paciente de esa clínica, comparándolo sin distinguir mayúsculas y sin espacios al principio ni al final. El portal NO DEBE pedir ni guardar datos nuevos del paciente.
- **FR-003**: Ante un email que no corresponde a ninguna ficha, el portal DEBE responder exactamente igual (mismo mensaje y mismo paso siguiente) que ante uno que sí corresponde, sin revelar si existe, y NO DEBE enviar nada.
- **FR-004**: El sistema DEBE limitar las solicitudes de enlace a 5 por email y hora; al superarlo DEBE dejar de enviar enlaces a ese email durante el resto de esa hora y explicarlo sin jerga.
- **FR-005**: La sesión del paciente DEBE caducar 30 minutos después de entrar, con independencia de la actividad; el paciente DEBE poder salir explícitamente en cualquier momento.
- **FR-006**: El enlace de acceso DEBE caducar a los 10 minutos de emitirse y dejar de valer tras su primer uso; pedir un enlace nuevo no invalida una sesión ya abierta.
- **FR-007**: El email del enlace DEBE estar en español de España, indicar el nombre de la clínica, el plazo de caducidad y que, si el paciente no lo ha pedido, puede ignorarlo.

**Consulta de citas**

- **FR-008**: El portal DEBE mostrar al paciente sus citas en dos bloques: «Próximas citas» (reservadas cuyo inicio aún no ha llegado, de la más próxima a la más lejana) y «Citas pasadas» (el resto: completadas, no asistidas, canceladas y reservadas ya iniciadas, de la más reciente a la más antigua).
- **FR-009**: Cada cita DEBE mostrar fecha (día de la semana y dd/mm/aaaa), horario de inicio y fin en 24 h, nombre del profesional, servicio, precio en euros con formato español y estado en lenguaje del paciente: «Reservada», «Realizada», «No acudiste», «Cancelada» y «Pendiente de cierre por la clínica».
- **FR-010**: El portal DEBE mostrar todo el historial de citas del paciente en esa clínica, sin límite de antigüedad.
- **FR-011**: El portal NO DEBE mostrar nunca datos de otros pacientes, huecos libres, citas de otros pacientes ni información interna de la clínica.

**Cancelación por el paciente**

- **FR-012**: El paciente DEBE poder cancelar, previa confirmación, cualquier cita propia en estado reservada mientras falten más de 24 horas reales para su inicio.
- **FR-013**: Cuando faltan 24 horas o menos, el portal NO DEBE ofrecer la cancelación y DEBE indicar al paciente que llame a la clínica. La recepción conserva su propia regla de cancelación (spec 001, FR-022) también para esas citas.
- **FR-014**: La validación del plazo DEBE hacerse en el momento de registrar la cancelación, no en el de mostrar el botón.
- **FR-015**: La cancelación desde el portal DEBE producir la misma transición reservada → cancelada definida en la spec 001 (FR-021) y, por tanto, liberar el hueco de inmediato a efectos de solapes y de la agenda de recepción (spec 001, FR-024).
- **FR-016**: Si la cancelación del paciente compite con otro cambio de estado de la misma cita (de la recepción o de otro dispositivo), solo DEBE aplicarse el primero; el paciente DEBE ver el estado real con un aviso claro.
- **FR-017**: El sistema DEBE registrar, para cada cancelación hecha desde el portal, que la hizo el paciente desde el portal y el instante en que se hizo.
- **FR-018**: Ninguna acción del portal DEBE crear, mover ni reactivar citas.

**Aviso de cancelaciones en recepción**

- **FR-019**: El panel de recepción DEBE mostrar, en todas sus pantallas, un aviso con el número de cancelaciones hechas desde el portal pendientes de revisar; si no hay ninguna, el aviso no se muestra.
- **FR-020**: Desde el aviso, la recepción DEBE poder ver la lista de cancelaciones pendientes, de la más reciente a la más antigua, cada una con paciente, profesional, fecha y horario del hueco liberado, servicio, día y hora de la cancelación, y la indicación «Hueco ya pasado» cuando el inicio del hueco ya ha llegado.
- **FR-021**: La recepción DEBE poder marcar cada cancelación como revisada; una cancelación revisada sale de la lista de pendientes. Marcarla dos veces, o a la vez desde dos puestos, DEBE tener el mismo efecto que marcarla una vez.
- **FR-022**: Solo las cancelaciones hechas desde el portal DEBEN generar aviso; las hechas por la recepción no.
- **FR-023**: El aviso y la lista NO DEBEN cambiar la agenda del día: el hueco ya está libre (FR-015) y la lista es solo informativa; volver a ocupar el hueco se hace con el alta de cita normal (spec 001).

**Interfaz**

- **FR-024**: El portal DEBE estar diseñado primero para móvil: usable en un móvil de 360 px de ancho sin desplazamiento horizontal, con contraste, tamaños de texto y elementos táctiles accesibles; DEBE funcionar también en ordenador. El aviso y la lista de recepción cumplen los mismos requisitos.
- **FR-025**: Todo el texto del portal y del email DEBE estar en español de España, tratando al paciente de «tú», sin códigos de error, identificadores internos ni jerga técnica.
- **FR-026**: Fechas y horas DEBEN mostrarse en hora peninsular española, con formato día/mes/año y reloj de 24 horas; los importes con el formato único de la interfaz («50,00 €»).
- **FR-027**: El portal DEBE mostrar el nombre de la clínica en todas sus pantallas, para que el paciente sepa en qué clínica está.

**Aislamiento y privacidad**

- **FR-028**: Un paciente solo DEBE ver y cancelar las citas de las fichas de su clínica que tienen el email con el que entró, y solo en el portal de esa clínica; una cita ajena DEBE tratarse igual que una inexistente.
- **FR-029**: El acceso al portal NO DEBE dar acceso al panel de recepción ni viceversa: la clave de la clínica no abre el portal de ningún paciente y la sesión de un paciente no abre el panel ni el aviso de cancelaciones.

**Datos de demostración**

- **FR-030**: El plan DEBE documentar, a partir de la semilla actual y sin modificarla, las citas futuras y pasadas (recuentos e importes) de Teresa Méndez, Laura Romero, Rocío Garrido y Gonzalo Cruz, para que las pruebas y demostraciones del portal citen cifras reproducibles.
- **FR-031**: La demostración y las pruebas del portal con la semilla DEBEN poder hacerse sin enviar emails reales a las direcciones ficticias de los pacientes (`@ejemplo.es`), pudiendo consultar el enlace que se habría enviado.

### Entidades clave

- **Paciente** (definido en la spec 001): ficha con nombre, teléfono y email opcional. Esta feature lo convierte en usuario del portal a través de su email, sin añadirle cuenta ni contraseña.
- **Enlace de acceso**: credencial de un solo uso ligada a un email y a una clínica; caduca a los 10 minutos o al usarse.
- **Sesión del paciente**: periodo de 30 minutos durante el que el paciente ve y cancela, en un dispositivo, las citas de las fichas de su clínica con su email.
- **Cita** (definida en la spec 001): esta feature la lee y solo le aplica la transición reservada → cancelada.
- **Cancelación del portal**: registro de que una cita la canceló el paciente desde el portal, con el instante de la cancelación y si la recepción ya la ha revisado (y cuándo).

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: Un paciente sin instrucciones previas, con el enlace ya recibido, localiza la fecha y hora de su próxima cita en menos de 10 segundos desde que abre el enlace.
- **SC-002**: Un paciente cancela una cita futura dentro del plazo en menos de 30 segundos desde que está en su página personal.
- **SC-003**: El 100 % de los intentos de cancelar desde el portal una cita a la que le faltan 24 horas o menos se rechazan.
- **SC-004**: En las pruebas de aislamiento, el número de citas o datos de otro paciente o de otra clínica que un paciente llega a ver o cancelar es exactamente 0.
- **SC-005**: Ante 10 cancelaciones simultáneas de la misma cita (portal y recepción), la cita queda cancelada exactamente una vez, se genera como mucho un aviso y el resto recibe el estado real con un aviso claro, en el 100 % de las repeticiones.
- **SC-006**: Todas las pantallas del portal y el aviso de recepción se usan en un móvil de 360 px de ancho sin desplazamiento horizontal y cumplen los criterios de accesibilidad de contraste y tamaño táctil.
- **SC-007**: La respuesta del portal ante un email inexistente es indistinguible de la de uno existente (mismo mensaje y mismo paso siguiente) en el 100 % de los casos probados.
- **SC-008**: Una cancelación hecha desde el portal aparece en el aviso de recepción y su hueco como libre en la agenda la próxima vez que la recepción carga cualquier pantalla del panel, sin pasos manuales.
- **SC-009**: El 100 % de los enlaces usados o caducados se rechazan.

## Supuestos

- **Objetivo de negocio**: reducir las llamadas a recepción para consultar y cancelar citas; su medición real en Clínica Eleva (p. ej. llamadas por mañana) queda fuera de las pruebas automáticas de esta feature.
- **Pacientes**: solo pueden entrar los pacientes que ya tienen ficha con email en la clínica; el portal no crea fichas ni pide el email a quien no lo tiene. Conseguir el email de los pacientes que no lo dieron es tarea de la recepción, fuera de esta feature.
- **Envío de email**: el envío de los enlaces es una dependencia externa nueva que el plan debe justificar (constitución, Principio IV) vinculándola a FR-001.
- **Aviso en recepción**: se actualiza cuando la recepción carga o cambia de pantalla; no se exige aviso en tiempo real, sonido ni notificación fuera del panel. Las cancelaciones revisadas no se consultan después en esta feature.
- **Sin reservas ni cambios online**: reservar, mover o reactivar citas desde el portal queda fuera de la v1; para cambiar una cita el paciente cancela (si está en plazo) y llama a la clínica para pedir otra.
- **Sin pagos**: el portal muestra el precio de cada cita a título informativo; pagos, depósitos y penalizaciones por cancelación tardía quedan fuera.
- **Sin recordatorios**: el portal no envía recordatorios de citas; el único email que envía es el enlace de acceso.
- **Sin lista de espera**: reofrecer automáticamente el hueco liberado a otros pacientes queda fuera de la v1; la recepción lo ofrece por teléfono con ayuda del aviso.
- **Motivo de cancelación**: el portal no pide motivo de cancelación en la v1.
- **Teléfono de la clínica**: la clínica no tiene hoy un teléfono registrado; los mensajes dicen «llama a la clínica» sin mostrar el número. Mostrarlo exigiría ampliar los datos de la clínica (spec 001).
- **Duración de la sesión y límites** (FR-004–FR-006): valores por defecto razonables para datos de salud; se pueden ajustar en `/speckit-clarify` sin cambiar el alcance.
- **Dependencias con la spec 001** (propietario: feature 001-nucleo-agenda): esta feature reutiliza sin modificarlas las transiciones de estado (FR-021, FR-022, FR-024, FR-025), la ficha de paciente y la semilla, y no cambia la agenda del día. Sí añade un elemento nuevo (el aviso de FR-019) a todas las pantallas del panel de recepción; esta spec es la propietaria de ese aviso y de su lista, y su incorporación al panel se coordinará con el propietario de la spec 001 antes de implementarla.
- **Fuera de alcance** (specs propias): reservar o mover citas online, pagos, recordatorios, lista de espera, edición de la ficha por el paciente, cuentas o contraseñas de paciente, acceso por SMS.
- **Propiedad**: esta spec se registra en `specs/MAPA.md` con un único propietario, la feature 002-portal-paciente.
