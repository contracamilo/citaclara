# Especificación de feature: Núcleo de agenda de CitaClara

**Rama de la feature**: `001-nucleo-agenda`

**Creada**: 26/11/2026

**Estado**: Aprobada

**Propietario**: feature 001-nucleo-agenda (registrado en `specs/MAPA.md`)

**Entrada**: descripción del usuario: «Núcleo de agenda de CitaClara. Contexto: clínicas pequeñas (2-5 profesionales); la recepción gestiona la agenda; los pacientes, de momento, solo existen como fichas. Entidades: clínica (con clave de panel), profesionales, servicios, pacientes y citas. Estados reservada → completada | cancelada | no_asistida. RN1: imposible solapar citas reservadas o completadas del mismo profesional, también ante reservas simultáneas. RN2: no se crean citas en el pasado. Interfaz de recepción: agenda del día por profesional con huecos ocupados y libres, alta de cita y acciones de estado. Semilla determinista: Clínica Eleva, 3 profesionales, 4 servicios, ~40 pacientes, 8 semanas de historia y 2 semanas futuras. Fuera de alcance: acceso del paciente, recordatorios, analítica, pagos online.»

## Clarifications

### Session 2026-11-26

- Q: ¿Las fechas de los datos de demostración se anclan a una fecha de referencia fija o se calculan a partir del día en que se cargan? → A: Opción B — el día de carga es el día de referencia; la secuencia relativa (pacientes, servicios, estados, horas e importes) es idéntica en cada carga y solo se desplazan las fechas absolutas; las cifras se citan por posición relativa (p. ej. «semana −3»).
- Q: ¿Se puede cancelar una cita reservada cuya hora de inicio ya ha pasado, o en ese caso solo cabe marcarla como completada o no asistida? → A: Opción B — solo se cancela antes del inicio; desde el inicio solo se puede marcar completada o no asistida.
- Q: ¿La agenda del día muestra un único profesional cada vez, o a todos los profesionales de la clínica a la vez, en columnas? → A: Opción A — un único profesional cada vez, con selector para cambiar, en cualquier pantalla.
- Q: ¿Cuándo debe caducar el acceso al panel después de haber introducido la clave de la clínica? → A: Opción D — caduca 8 horas después del acceso, con independencia de la actividad.
- Q: ¿Qué debe pasar cuando alguien introduce mal la clave de la clínica varias veces seguidas? → A: Opción A — sin límite de intentos en esta feature (deuda consciente, junto a la clave compartida).
- Q: ¿La historia y el futuro de los datos de demostración se cuentan en semanas naturales o en días laborables? → A: En días laborables (lunes a viernes, sin descontar festivos): 40 días laborables hacia atrás y 10 hacia delante desde el día de carga, que no recibe citas; las cifras se citan como «día laborable −n / +n». Sustituye al ejemplo «semana −3» de la primera aclaración.
- Q: Con el reloj a las 12:00:00 o a las 12:00:30, ¿se puede reservar una cita que empiece a las 12:00? → A: No. Manda FR-014: se rechaza toda cita cuyo inicio no sea posterior al instante actual; la primera hora reservable es la siguiente en pasos de 5 minutos (12:05).

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - Consultar la agenda del día de un profesional (Prioridad: P1)

La recepcionista entra en el panel de la clínica con la clave de la clínica, elige un día y un profesional y ve de un vistazo qué franjas están ocupadas (con paciente, servicio, horario y estado de cada cita) y qué huecos quedan libres dentro del horario de atención.

**Por qué esta prioridad**: es la pantalla en la que la recepción vive todo el día; sin ella no hay agenda. Con la semilla cargada ya aporta valor por sí sola (consultar qué hay hoy, quién viene y cuándo).

**Prueba independiente**: con los datos de demostración cargados, acceder con la clave de Clínica Eleva, abrir la agenda de María para un día con citas y comprobar que se muestran todas sus citas del día con su estado y los huecos libres entre ellas.

**Escenarios de aceptación**:

1. **Dado** que la recepcionista no ha accedido, **cuando** abre el panel, **entonces** se le pide la clave de la clínica y no ve ningún dato de agenda hasta introducirla correctamente.
2. **Dado** que introduce una clave incorrecta, **cuando** intenta acceder, **entonces** ve un mensaje claro («La clave no es correcta. Revísala e inténtalo de nuevo.») y sigue sin acceso.
3. **Dado** que ha accedido, **cuando** abre la agenda, **entonces** ve por defecto el día de hoy y puede cambiar de profesional y de día (anterior, siguiente o una fecha concreta).
4. **Dado** un profesional con citas ese día, **cuando** se muestra su agenda, **entonces** cada cita aparece en su franja con hora de inicio y fin (24 h), paciente, servicio, importe en euros y estado, y los intervalos sin citas reservadas ni completadas dentro del horario de atención aparecen como huecos libres.
5. **Dado** una cita cancelada o no asistida, **cuando** se muestra la agenda, **entonces** la cita sigue visible y distinguible por su estado, pero su franja figura como libre.
6. **Dado** un día sin citas, **cuando** se muestra la agenda, **entonces** todo el horario de atención aparece como libre.
7. **Dado** que la recepcionista usa un móvil o un portátil, **cuando** consulta la agenda, **entonces** la vista es legible y utilizable sin desplazamiento horizontal.

---

### Historia de usuario 2 - Dar de alta una cita sin solapes (Prioridad: P1)

Con un paciente al teléfono o delante, la recepcionista elige profesional, servicio, paciente (existente o nuevo) y hora de inicio; el sistema calcula el fin según la duración del servicio y registra la cita como reservada solo si el hueco está libre y en el futuro.

**Por qué esta prioridad**: es la acción que da sentido al producto y donde vive el fallo capital (RN1). Junto con la historia 1 forma el MVP.

**Prueba independiente**: crear una cita en un hueco libre futuro y verla aparecer en la agenda; intentar crear otra que pise la primera y comprobar el rechazo; lanzar dos altas simultáneas del mismo hueco y comprobar que exactamente una queda registrada.

**Escenarios de aceptación**:

1. **Dado** un hueco libre futuro de Jorge de 10:00 a 11:00, **cuando** se reserva «Sesión fisioterapia» (45 min) a las 10:00 para un paciente, **entonces** se registra una cita reservada de 10:00 a 10:45 con importe 40,00 € y aparece en la agenda.
2. **Dado** que Jorge tiene una cita reservada de 10:00 a 10:45, **cuando** se intenta reservar otra cita suya que empiece a las 10:30, **entonces** se rechaza con un mensaje claro («Jorge ya tiene una cita de 10:00 a 10:45. Elige otro hueco.») y no se registra nada.
3. **Dado** que Jorge tiene una cita reservada de 10:00 a 10:45, **cuando** se reserva otra suya que empieza exactamente a las 10:45, **entonces** se registra (citas contiguas no se solapan).
4. **Dado** que Jorge tiene una cita cancelada de 10:00 a 10:45, **cuando** se reserva otra a las 10:00, **entonces** se registra (las citas canceladas y no asistidas no ocupan el hueco).
5. **Dado** que dos recepcionistas envían a la vez una reserva para el mismo hueco del mismo profesional, **cuando** el sistema las procesa, **entonces** exactamente una queda registrada y la otra recibe un rechazo claro que indica que el hueco acaba de ocuparse.
6. **Dado** que Jorge tiene una cita a las 10:00, **cuando** se reserva una cita de María a las 10:00, **entonces** se registra (el solape solo se controla por profesional).
7. **Dado** que son las 12:00 de hoy, **cuando** se intenta reservar una cita que empieza a las 11:30 de hoy o en un día anterior, **entonces** se rechaza indicando que no se pueden crear citas en el pasado.
8. **Dado** que el paciente no existe, **cuando** la recepcionista lo da de alta durante la reserva con nombre y teléfono, **entonces** se crea su ficha y la cita queda asociada a él.
9. **Dado** un servicio de nutrición, **cuando** se intenta reservar con un profesional de fisioterapia, **entonces** el sistema no lo permite (solo se ofrecen los servicios de la especialidad del profesional).
10. **Dado** una hora de inicio cuya cita terminaría fuera del horario de atención, **cuando** se intenta reservar, **entonces** se rechaza indicando el horario disponible.

---

### Historia de usuario 3 - Marcar el resultado de una cita (Prioridad: P2)

Desde la agenda del día, la recepcionista marca una cita reservada como completada, cancelada o no asistida.

**Por qué esta prioridad**: mantiene la agenda fiel a la realidad y libera huecos al cancelar; depende de que existan citas (historias 1 y 2 o la semilla).

**Prueba independiente**: sobre citas reservadas de la semilla, cancelar una futura y ver su hueco libre; marcar una ya iniciada como completada y otra como no asistida; comprobar que no se puede cambiar el estado de una cita ya cerrada.

**Escenarios de aceptación**:

1. **Dado** una cita reservada futura, **cuando** se cancela (con confirmación), **entonces** pasa a cancelada y su franja aparece como libre.
2. **Dado** una cita reservada cuya hora de inicio ya ha llegado, **cuando** se marca como completada, **entonces** pasa a completada y sigue ocupando su franja.
3. **Dado** una cita reservada cuya hora de inicio ya ha llegado, **cuando** se marca como no asistida, **entonces** pasa a no asistida.
4. **Dado** una cita reservada que aún no ha empezado, **cuando** se intenta marcar como completada o no asistida, **entonces** la acción no está disponible.
5. **Dado** una cita reservada cuya hora de inicio ya ha llegado, **cuando** la recepcionista consulta sus acciones, **entonces** no puede cancelarla y solo se le ofrece marcarla como completada o no asistida.
6. **Dado** una cita completada, cancelada o no asistida, **cuando** se consulta, **entonces** no ofrece ninguna acción de cambio de estado.
7. **Dado** que dos recepcionistas cambian a la vez el estado de la misma cita reservada, **cuando** el sistema lo procesa, **entonces** solo se aplica el primero y la otra persona ve el estado real con un aviso claro.

---

### Historia de usuario 4 - Datos de demostración deterministas (Prioridad: P2)

Cualquier persona del equipo puede cargar los datos de demostración de Clínica Eleva y obtener siempre la misma historia, para probar el producto y reproducir las cifras citadas en specs.

**Por qué esta prioridad**: lo exige la constitución (principio V) y es la base para demostrar y probar las historias anteriores con datos realistas.

**Prueba independiente**: cargar la semilla dos veces en entornos limpios y comprobar que el resultado es idéntico y cumple los recuentos y proporciones definidos.

**Escenarios de aceptación**:

1. **Dado** un entorno limpio, **cuando** se cargan los datos de demostración, **entonces** existe Clínica Eleva con su clave de demostración, 3 profesionales (María y Jorge, fisioterapia; Lucía, nutrición), 4 servicios (Sesión fisioterapia 45 min 40,00 €; Primera visita fisioterapia 60 min 50,00 €; Consulta nutrición 30 min 35,00 €; Primera visita nutrición 45 min 45,00 €) y 40 pacientes.
2. **Dado** la semilla cargada, **cuando** se revisan las citas de los 40 días laborables anteriores al día de carga, **entonces** todas están cerradas (completada, cancelada o no asistida), con alrededor de un 10 % de no asistidas y un 8 % de canceladas sobre el total de esas citas.
3. **Dado** la semilla cargada, **cuando** se revisan los 10 días laborables posteriores al día de carga, **entonces** hay citas reservadas para los tres profesionales y huecos libres.
4. **Dado** la semilla cargada, **cuando** se buscan solapes entre citas reservadas o completadas de un mismo profesional, **entonces** no existe ninguno.
5. **Dado** dos cargas con la misma semilla, **cuando** se comparan, **entonces** las fichas, los estados, los importes y la posición relativa de cada cita (día laborable respecto al día de carga y hora local) coinciden exactamente, aunque las cargas se hagan en días de la semana distintos; solo cambian las fechas absolutas si las cargas se hicieron en días distintos.

---

### Casos límite

- **Citas contiguas**: una cita que empieza justo cuando termina otra no es solape.
- **Solape parcial, contención y coincidencia exacta**: cualquier intersección de tiempo positiva entre dos citas reservadas o completadas del mismo profesional es solape y se rechaza.
- **Reservas simultáneas**: dos o más altas que compiten por el mismo hueco (total o parcialmente) → exactamente una prospera.
- **Hora de inicio igual a «ahora»**: se rechaza toda cita cuyo inicio no sea posterior al instante actual (FR-014). Por ejemplo, ni a las 12:00:00 ni a las 12:00:30 se puede reservar a las 12:00; la primera hora reservable es 12:05.
- **Cambio de horario verano/invierno**: una cita en un día con cambio de hora conserva su hora local y su duración real; ni se desplaza, ni se duplica, ni desaparece de la agenda.
- **Cita que cruzaría el fin del horario de atención o la medianoche**: se rechaza.
- **Cancelación tardía**: si el paciente avisa cuando su cita ya ha empezado, no se cancela; se marca como no asistida (o completada si finalmente se atiende).
- **Cita cancelada cuya hora ya ha pasado o cita no asistida**: no bloquea el hueco, pero sigue visible en la agenda con su estado.
- **Sesión caducada (8 horas desde el acceso)**: la recepcionista vuelve a la pantalla de acceso con un mensaje claro («Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.»); la acción en curso no se aplica y, si era un alta de cita, los datos introducidos no se dan por registrados.
- **Paciente duplicado**: al dar de alta un paciente con un teléfono que ya existe en la clínica, se avisa y se ofrece usar la ficha existente.
- **Día de carga de la semilla**: el propio día de carga no recibe citas de demostración (la historia termina el día laborable anterior y el futuro empieza el día laborable siguiente; si se carga en fin de semana, la historia termina el viernes previo y el futuro empieza el lunes siguiente), para que el resultado no dependa de la hora de carga ni choque con RN2.
- **Datos de otra clínica**: con la clave de una clínica nunca se ven ni modifican datos de otra.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Acceso y clínica**

- **FR-001**: El sistema DEBE proteger todo el panel de recepción con la clave de panel de la clínica; sin clave válida no se muestra ni se modifica ningún dato.
- **FR-002**: El acceso DEBE durar 8 horas desde que se introduce la clave, con independencia de la actividad; transcurrido ese plazo, la siguiente acción lleva a la pantalla de acceso. El sistema DEBE permitir además cerrar la sesión explícitamente en cualquier momento.
- **FR-003**: El sistema DEBE aislar los datos por clínica: profesionales, servicios, pacientes y citas pertenecen a una clínica y solo son visibles y modificables con su clave.
- **FR-004**: El acceso con una clave compartida por toda la recepción es una simplificación consciente de la v1 (deuda registrada): no hay usuarios individuales, roles, registro de quién hace cada acción ni límite de intentos fallidos de clave. El límite de intentos y la protección frente a la prueba masiva de claves quedan como deuda consciente, a resolver junto con la sustitución de la clave compartida.

**Agenda del día**

- **FR-005**: El sistema DEBE mostrar la agenda de un día para un único profesional cada vez, elegido con un selector que permite cambiar de profesional conservando el día, con hoy como día por defecto y navegación al día anterior, al siguiente y a una fecha concreta.
- **FR-006**: La agenda DEBE mostrar cada cita del día con hora de inicio y fin, paciente, servicio, importe y estado, y DEBE distinguir visualmente los cuatro estados.
- **FR-007**: La agenda DEBE mostrar como huecos libres todos los intervalos del horario de atención no ocupados por citas reservadas o completadas, con su hora de inicio y fin.
- **FR-008**: Desde un hueco libre DEBE poderse iniciar el alta de una cita con el profesional, el día y la hora de inicio ya propuestos.

**Alta de cita**

- **FR-009**: El sistema DEBE permitir crear una cita indicando profesional, servicio, paciente y fecha y hora de inicio; la hora de fin la calcula el sistema como inicio + duración del servicio y no es editable.
- **FR-010**: El sistema DEBE ofrecer para cada profesional solo los servicios de su especialidad.
- **FR-011**: Toda cita nueva DEBE nacer en estado «reservada».
- **FR-012 (RN1)**: El sistema NO DEBE registrar nunca una cita que se solape en el tiempo con otra cita reservada o completada del mismo profesional. Dos citas se solapan si el inicio de cada una es anterior al fin de la otra.
- **FR-013 (RN1, concurrencia)**: Cuando dos o más altas compiten por un mismo hueco (total o parcialmente) del mismo profesional al mismo tiempo, exactamente una DEBE quedar registrada y las demás DEBEN recibir un rechazo claro.
- **FR-014 (RN2)**: El sistema NO DEBE registrar una cita cuya hora de inicio no sea posterior al momento actual.
- **FR-015**: El sistema NO DEBE registrar una cita que empiece antes o termine después del horario de atención de la clínica ese día.
- **FR-016**: Al rechazar un alta, el sistema DEBE explicar el motivo en lenguaje de la clínica (hueco ocupado y por qué cita, hora pasada, fuera de horario) y conservar los datos introducidos para corregirlos.
- **FR-017**: El importe de la cita DEBE ser el precio del servicio en el momento de la reserva y mostrarse en euros con formato español (p. ej. «40,00 €»).

**Pacientes**

- **FR-018**: El sistema DEBE permitir buscar pacientes de la clínica por nombre o teléfono al dar de alta una cita.
- **FR-019**: El sistema DEBE permitir crear una ficha de paciente durante el alta de una cita, con nombre y teléfono obligatorios y email opcional; DEBE validar que el teléfono y, si se indica, el email tienen un formato válido.
- **FR-020**: Si el teléfono ya pertenece a otro paciente de la clínica, el sistema DEBE avisar y ofrecer usar la ficha existente.

**Estados de la cita**

- **FR-021**: Las únicas transiciones válidas son: reservada → completada, reservada → cancelada y reservada → no asistida. Completada, cancelada y no asistida son estados finales en esta feature.
- **FR-022**: Una cita reservada DEBE poder cancelarse, previa confirmación, solo mientras su hora de inicio no haya llegado; desde el inicio NO DEBE poder cancelarse y solo cabe marcarla como completada o no asistida.
- **FR-023**: Una cita reservada solo DEBE poder marcarse como completada o no asistida cuando su hora de inicio ya ha llegado. «No asistida» significa exclusivamente que el paciente no se presentó a una cita que seguía reservada.
- **FR-024**: Las citas canceladas y no asistidas NO DEBEN ocupar hueco a efectos de RN1; las completadas sí.
- **FR-025**: Si dos cambios de estado sobre la misma cita compiten, solo DEBE aplicarse el primero; el segundo DEBE ver el estado real y un aviso claro.

**Fechas, horas e interfaz**

- **FR-026**: Todas las fechas y horas DEBEN interpretarse y mostrarse en hora peninsular española (con sus cambios de horario), con formato día/mes/año y reloj de 24 horas.
- **FR-027**: La interfaz DEBE estar íntegramente en español de España, sin códigos de error, identificadores internos ni jerga técnica.
- **FR-028**: La interfaz DEBE ser utilizable en el portátil de recepción y en un móvil, con contraste y tamaños de texto y de elementos táctiles accesibles.

**Datos de demostración**

- **FR-029**: El sistema DEBE poder cargar unos datos de demostración deterministas: con la misma semilla produce siempre exactamente las mismas clínica, profesionales, servicios, pacientes y citas. El día de referencia de la semilla es el día de carga: las fechas de las citas se calculan relativas a él contando días laborables (días con horario de atención de la clínica: de lunes a viernes en la semilla), de modo que dos cargas el mismo día son idénticas y dos cargas en días distintos solo difieren en el desplazamiento de las fechas.
- **FR-030**: La semilla DEBE contener: Clínica Eleva con una clave de demostración documentada; 3 profesionales (María, fisioterapia; Jorge, fisioterapia; Lucía, nutrición); 4 servicios (Sesión fisioterapia, 45 min, 40,00 €; Primera visita fisioterapia, 60 min, 50,00 €; Consulta nutrición, 30 min, 35,00 €; Primera visita nutrición, 45 min, 45,00 €); 40 pacientes con nombre, teléfono y email ficticios.
- **FR-031**: La semilla DEBE contener una historia de 40 días laborables (≈ 8 semanas) que termina el día laborable anterior al día de carga, con todas las citas cerradas (las canceladas, como canceladas antes de su inicio), alrededor de un 10 % no asistidas y un 8 % canceladas (resto completadas), y un futuro de 10 días laborables (≈ 2 semanas) que empieza el día laborable siguiente al día de carga, con citas reservadas para los tres profesionales y huecos libres.
- **FR-032**: La semilla DEBE respetar RN1 y el horario de atención; es el único camino autorizado a registrar citas con inicio en el pasado (exención explícita de RN2 limitada a la historia de demostración).
- **FR-033**: Los recuentos exactos resultantes de la semilla (citas totales, por estado, por profesional e importes) DEBEN quedar documentados para que specs y ejemplos citen cifras reproducibles, expresadas por posición relativa al día de carga en días laborables (p. ej. «día laborable −3», «día laborable +2»; «día laborable −1» es el último día de la historia y «día laborable +1» el primero del futuro) y nunca por fecha absoluta ni por semana.

### Entidades clave

- **Clínica**: la consulta que usa el producto. Nombre, clave de panel y horario de atención. Contiene a todas las demás entidades.
- **Profesional**: persona que atiende citas en una clínica. Nombre y especialidad (fisioterapia, nutrición…).
- **Servicio**: prestación que se reserva. Nombre, especialidad a la que pertenece, duración en minutos y precio en euros.
- **Paciente**: ficha de una persona atendida en la clínica. Nombre, teléfono y email (opcional). En esta feature no tiene acceso al producto.
- **Cita**: reserva de un servicio para un paciente con un profesional. Inicio, fin (inicio + duración del servicio), importe (precio del servicio al reservar) y estado (reservada, completada, cancelada, no asistida).

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: En las pruebas de aceptación y de carga concurrente, el número de solapes registrados entre citas reservadas o completadas de un mismo profesional es exactamente 0.
- **SC-002**: Ante 10 altas simultáneas sobre el mismo hueco del mismo profesional, exactamente 1 queda registrada y las 9 restantes reciben un rechazo comprensible, en el 100 % de las repeticiones de la prueba.
- **SC-003**: El 100 % de los intentos de crear una cita con inicio pasado se rechazan.
- **SC-004**: Una recepcionista sin formación previa da de alta una cita para un paciente existente en menos de 1 minuto y para un paciente nuevo en menos de 2 minutos.
- **SC-005**: Una recepcionista sin formación previa identifica el primer hueco libre de un profesional en un día dado en menos de 10 segundos.
- **SC-006**: Al menos el 90 % de las recepcionistas completan sin ayuda las tareas de consultar agenda, dar de alta una cita y marcar su resultado en su primer intento.
- **SC-007**: La agenda del día se muestra completa en menos de 2 segundos desde que se elige profesional y día.
- **SC-008**: Dos cargas de la semilla en entornos limpios producen resultados idénticos en el 100 % de los datos comparados por posición relativa al día de carga, y las proporciones de no asistidas y canceladas en la historia quedan entre el 9 % y el 11 % y entre el 7 % y el 9 % respectivamente.
- **SC-009**: Todas las pantallas del flujo de recepción se usan en un móvil de 360 px de ancho sin desplazamiento horizontal.

## Supuestos

- **Horario de atención**: cada clínica tiene un horario de atención semanal; en la semilla, Clínica Eleva atiende de lunes a viernes de 09:00 a 20:00 sin pausa, y todos sus profesionales siguen ese horario. Los horarios individuales, festivos y ausencias de profesionales quedan fuera de esta feature; por eso, en la semilla, «día laborable» es cualquier día de lunes a viernes, sin descontar festivos.
- **Huecos libres**: un hueco libre es cualquier intervalo continuo del horario de atención sin citas reservadas o completadas; la agenda no impone una rejilla fija, pero la hora de inicio de una cita se elige en múltiplos de 5 minutos.
- **Catálogo**: profesionales, servicios y especialidades se cargan con la semilla; su alta, edición y baja desde la interfaz queda fuera de esta feature. La relación profesional–servicio se deduce de la especialidad.
- **Pacientes**: solo existen como fichas gestionadas por recepción (alta y búsqueda dentro del alta de cita). La edición y baja de fichas queda fuera de esta feature.
- **Vista de varios profesionales**: la vista simultánea de todos los profesionales (en columnas o combinada) queda fuera de esta feature; la agenda muestra un profesional cada vez.
- **Sin mover ni reactivar citas**: para cambiar una cita de hora se cancela y se crea otra; no existe reactivación de citas cerradas ni corrección de estados en esta feature.
- **Concurrencia**: «llegar a la vez» incluye peticiones desde varios dispositivos de la misma recepción o pestañas distintas.
- **Intentos fallidos de clave**: no se limitan en esta feature; cada intento fallido muestra el mismo mensaje claro y no revela información adicional.
- **Clave de panel**: una única clave por clínica, conocida por toda la recepción; su cambio y recuperación quedan fuera de esta feature.
- **Fuera de alcance** (specs propias): cualquier acceso del paciente, recordatorios, analítica, pagos online, gestión de usuarios individuales y roles.
- **Propiedad**: esta spec se registra en `specs/MAPA.md` con un único propietario, la feature 001-nucleo-agenda.
