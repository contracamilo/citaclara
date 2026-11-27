# Especificación de feature: Panel de analítica

**Rama de la feature**: `004-panel-analitica`

**Creada**: 2026-11-27

**Estado**: Implementada (aprobada el 2026-11-27; definiciones de Sara resueltas)

**Excluidos de la trazabilidad**: SC-003 (se mide con personas, no con tests)

**Entrada**: descripción del usuario: «Panel de analítica para la clínica. Sara lo necesita para las renovaciones: "enseñar a la clínica lo que CitaClara le ahorra". Alcance: una página del panel (misma clave que la agenda), con interfaz moderna y gráficos claros: ocupación semanal por profesional, tasa de no asistencia por profesional, ingresos por servicio (citas completadas; importes exactos, al céntimo), y evolución de las últimas 8 semanas. Solo lectura: esta feature no escribe NADA. Construye los ejemplos de la spec con los números reales de la semilla.»

## Aclaraciones

### Sesión 2026-11-27

- Q: ¿Qué periodo cubren las cifras por profesional de ocupación y de tasa de no asistencia, aparte de la evolución? → A: Solo la última semana completa; el detalle de 8 semanas va únicamente en la evolución.
- Q: ¿Debe la página mostrar alguna cifra en euros de lo que la clínica pierde por citas no asistidas? → A: Sí: el importe de las citas no asistidas, por servicio y total, como «Ingresos no obtenidos por no asistencias», sin llamarlo ahorro.

## Escenarios de usuario y pruebas *(obligatorio)*

Todos los números de los ejemplos salen de la semilla de Clínica Eleva (`specs/001-nucleo-agenda/datos-demostracion.md`, Principio V) y se citan por **posición relativa en días laborables**, nunca por fecha absoluta.

### Historia de usuario 1 - Ver cuánto rinde cada profesional (Prioridad: P1)

La persona responsable de la clínica abre la página de analítica con la misma clave del panel y ve, para cada profesional, su **ocupación** y su **tasa de no asistencia** de la **última semana completa**, con gráficos que se entienden de un vistazo y sin explicación previa. Es la pieza que Sara enseña en las renovaciones: cuánto de su tiempo útil llena CitaClara y cuántas citas se pierden por plantones.

**Por qué esta prioridad**: es el núcleo del valor que Sara quiere demostrar y lo primero que pregunta una clínica al renovar. Sola ya es una demo presentable.

**Prueba independiente**: con la semilla cargada, abrir la página y comprobar que muestra a María, Jorge y Lucía con sus cifras de ocupación y de no asistencia, idénticas a las de esta spec.

**Escenarios de aceptación**:

1. **Dado** la semilla cargada, **cuando** se abre la página de analítica con la clave de Clínica Eleva, **entonces** aparecen los tres profesionales, cada uno con su ocupación semanal y su tasa de no asistencia, en gráficos con etiquetas legibles y sin jerga técnica.
2. **Dado** la semilla cargada un lunes, **cuando** se consulta la tasa de no asistencia de cada profesional, **entonces** se muestran las cifras de la última semana completa (días laborables −5 a −1) de la tabla «Última semana de la semilla», con el número de citas en que se basa cada una (Jorge destaca por encima de María y Lucía).
3. **Dado** la semilla cargada un lunes, **cuando** se consulta la ocupación de cada profesional, **entonces** se muestran las cifras de la última semana completa de la tabla «Última semana de la semilla».
4. **Dado** un profesional sin ninguna cita cerrada en el periodo, **cuando** se muestra su tasa de no asistencia, **entonces** se indica «Sin datos» y no un 0 % que pueda confundirse con «nadie ha faltado».

---

### Historia de usuario 2 - Ver de dónde vienen los ingresos (Prioridad: P1)

La persona responsable ve los **ingresos por servicio**: cuánto ha facturado cada servicio con las citas completadas, con los importes exactos al céntimo, y el total, que coincide con la suma de las partes y con lo que muestran el resto de pantallas. Junto a ellos ve los **ingresos no obtenidos por no asistencias**: lo que habrían supuesto las citas a las que el paciente no acudió, por servicio y en total.

**Por qué esta prioridad**: el dinero es el argumento de renovación más directo y, por la constitución (Principio II), es donde un descuadre de un céntimo destruye la confianza.

**Prueba independiente**: con la semilla cargada, abrir la página y comprobar que los cuatro servicios muestran los importes de la tabla «Ingresos por servicio de la semilla» y que su suma es 34.870,00 €.

**Escenarios de aceptación**:

1. **Dado** la semilla cargada, **cuando** se consultan los ingresos por servicio de la historia, **entonces** se muestran los cuatro servicios con el número de citas completadas y su importe, y el total es 34.870,00 € (3487000 céntimos).
2. **Dado** cualquier periodo mostrado, **cuando** se suman los importes de todos los servicios, **entonces** el resultado es exactamente el total mostrado, sin diferencias de céntimos por redondeos.
3. **Dado** que solo cuentan las citas completadas, **cuando** hay citas reservadas, canceladas o no asistidas en el periodo, **entonces** no suman en los ingresos.
4. **Dado** un servicio sin ninguna cita completada en el periodo, **cuando** se muestran los ingresos, **entonces** aparece con 0,00 € y no desaparece de la lista.
5. **Dado** la semilla cargada, **cuando** se consultan los ingresos no obtenidos por no asistencias de la historia, **entonces** se muestran los importes de la tabla «Ingresos no obtenidos de la semilla», con un total de 4275,00 € (427500 céntimos), y las citas canceladas no suman.

---

### Historia de usuario 3 - Ver la evolución de las últimas 8 semanas (Prioridad: P2)

La persona responsable ve cómo han evolucionado semana a semana, durante las **últimas 8 semanas**, los ingresos, la ocupación y la tasa de no asistencia, para enseñar tendencia y no solo una foto fija.

**Por qué esta prioridad**: añade el relato de mejora que pide una renovación, pero las historias 1 y 2 ya aportan valor sin ella.

**Prueba independiente**: con la semilla cargada un lunes (reloj controlado), abrir la página y comprobar que la evolución muestra 8 semanas con las cifras de la tabla «Evolución de la semilla».

**Escenarios de aceptación**:

1. **Dado** la semilla cargada un lunes, **cuando** se abre la evolución, **entonces** se muestran 8 semanas consecutivas, de la más antigua a la más reciente, con sus ingresos, su ocupación y su tasa de no asistencia.
2. **Dado** una semana sin ninguna cita en el periodo, **cuando** se muestra la evolución, **entonces** la semana aparece igualmente con sus cifras a cero (o «Sin datos» donde un porcentaje no tenga sentido) y no se omite ni se salta.
3. **Dado** que las semanas se agrupan de lunes a domingo, **cuando** una cita cae en el límite entre dos semanas, **entonces** se cuenta en la semana de su fecha local, nunca en dos ni en ninguna, también en las semanas con cambio de horario de verano/invierno.

---

### Historia de usuario 4 - Entrar sin riesgo y sin tocar nada (Prioridad: P1)

Quien entra a la página usa exactamente la misma clave y sesión que la agenda, solo ve datos de su clínica, y nada de lo que hace en la página puede modificar citas, pacientes ni importes.

**Por qué esta prioridad**: protege los datos de la clínica y los de otras clínicas; sin ello no se puede enseñar la página a nadie.

**Prueba independiente**: sin sesión, la página no muestra datos; con la clave de una clínica, los datos de otra no aparecen; tras usar la página, la agenda sigue exactamente igual.

**Escenarios de aceptación**:

1. **Dado** que no hay sesión iniciada o ha caducado (8 horas), **cuando** se intenta abrir la analítica, **entonces** se lleva a la pantalla de acceso con el mensaje habitual y no se muestra ningún dato.
2. **Dado** dos clínicas con datos distintos, **cuando** se abre la analítica con la clave de una, **entonces** solo aparecen sus profesionales, servicios y citas.
3. **Dado** cualquier uso de la página (abrirla, recargarla, cambiar de vista), **cuando** se compara la agenda antes y después, **entonces** no ha cambiado ninguna cita, paciente ni importe.
4. **Dado** una clínica recién creada sin citas, **cuando** se abre la analítica, **entonces** se muestra un estado vacío claro que explica que aún no hay datos, sin errores ni gráficos rotos.

---

### Casos límite

- **Citas no cerradas**: las citas reservadas (futuras) no cuentan como completadas ni como no asistidas y no entran en ingresos ni en la tasa de no asistencia. Su tratamiento en la ocupación depende de la decisión pendiente sobre la ocupación.
- **Poca muestra**: con muy pocas citas cerradas, un único plantón puede dar un porcentaje engañoso (en la semilla, la última semana de María es 1 no asistida de 39 citas cerradas = 2,6 %); la página muestra también el número de citas en que se basa cada porcentaje, para que nadie lo lea como una tendencia.
- **Semana en curso**: está incompleta y cambia cada día, por lo que no forma parte de las 8 semanas mostradas.
- **Semanas parciales de la semilla**: la historia de la semilla son 40 días laborables, exactamente 8 semanas completas solo cuando se carga un lunes. Cargada otro día, la semana más antigua tiene menos días con datos y se muestra con lo que haya. Las cifras semanales de esta spec se verifican, por tanto, con la semilla cargada un lunes (reloj controlado).
- **Cambio de horario**: una semana con cambio de hora conserva la misma capacidad en horas de atención (el horario de la clínica es local) y no desplaza, duplica ni pierde citas entre semanas.
- **Importes**: todos los importes se muestran en euros con el formato español y deben cuadrar al céntimo entre servicios, semanas y total.
- **Pantalla pequeña**: los gráficos y sus etiquetas son legibles en el móvil y en el portátil de recepción.
- **Datos de otra clínica**: nunca se ven ni influyen en ninguna cifra.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Acceso y alcance**

- **FR-001**: La analítica DEBE ser una página del panel de recepción, accesible con la misma clave y la misma sesión que la agenda (caducidad de 8 horas incluida); sin sesión válida no se muestra ningún dato.
- **FR-002**: La analítica DEBE mostrar únicamente datos de la clínica de la sesión.
- **FR-003**: La feature es de **solo lectura**: no DEBE crear, modificar ni borrar ningún dato (citas, pacientes, servicios, profesionales ni datos de demostración), ni almacenar cifras calculadas.

**Cifras**

- **FR-004**: El sistema DEBE mostrar, para cada profesional, su **ocupación de la última semana completa** según la definición de FR-009; la ocupación de las 8 semanas se ofrece solo en la evolución (FR-007).
- **FR-005**: El sistema DEBE mostrar, para cada profesional, su **tasa de no asistencia de la última semana completa** según la definición de FR-008, junto con el número de citas en que se basa; no hay un resumen aparte de las 8 semanas por profesional.
- **FR-006**: El sistema DEBE mostrar los **ingresos por servicio**, contando únicamente citas completadas, con el importe de cada cita fijado al reservarla (precio del servicio al reservar), con importes exactos al céntimo, el número de citas por servicio y el total.
- **FR-007**: El sistema DEBE mostrar la **evolución de las últimas 8 semanas** naturales completas (de lunes a domingo, hora local de la clínica, sin incluir la semana en curso) con ingresos y tasa de no asistencia de toda la clínica y ocupación por profesional, semana a semana.
- **FR-008**: La **tasa de no asistencia** de un profesional en un periodo es: citas no asistidas ÷ todas las citas cerradas del periodo (completadas + no asistidas + canceladas). Las citas reservadas (aún no cerradas) no cuentan. Decidido por Sara; coincide con el 10 % global de la spec 001.
- **FR-009**: La **ocupación** de un profesional en un periodo es: minutos de citas completadas ÷ minutos del horario de atención de la clínica en ese periodo (en la semilla, 11 h × 5 días = 3.300 minutos por profesional y semana). Las no asistidas y canceladas no suman. Decidido por Sara.
- **FR-010**: Todos los importes mostrados (por servicio, por semana y totales) DEBEN cuadrar al céntimo entre sí y con el resto de pantallas; ningún redondeo intermedio puede alterar un total. Los porcentajes se calculan siempre desde los recuentos y minutos exactos y se redondean solo al mostrarse, a un decimal.
- **FR-011**: Un porcentaje sin base de cálculo (denominador cero) DEBE mostrarse como «Sin datos», nunca como 0 %.

**Presentación**

- **FR-012**: La página DEBE presentar cada cifra con un gráfico claro y su valor exacto visible (no solo en un aviso emergente), con etiquetas en español de España, sin jerga técnica ni códigos, y con un estado vacío comprensible cuando no hay datos.
- **FR-013**: La página DEBE cumplir los requisitos de accesibilidad y de uso en móvil y portátil de la constitución (Principio VII): contraste, tamaños táctiles y que la información no dependa solo del color.
- **FR-014**: Las semanas se etiquetan sin ambigüedad (fecha de inicio y fin en formato día/mes/año) y las horas, si se muestran, en hora peninsular española de 24 horas (Principio II).

**Datos de demostración**

- **FR-015**: Las cifras citadas en esta spec DEBEN poder reproducirse con la semilla de Clínica Eleva. Esta feature NO modifica la semilla; las cifras de analítica se documentan en la sección «Cifras de la semilla» de esta spec, derivada de los recuentos de `specs/001-nucleo-agenda/datos-demostracion.md`.

- **FR-016**: El sistema DEBE mostrar los **ingresos no obtenidos por no asistencias**: la suma de los importes (precio del servicio al reservar) de las citas no asistidas del periodo de 8 semanas, por servicio y en total, con la misma exactitud al céntimo que los ingresos. NO DEBE presentarse como «ahorro» ni como pérdida estimada: es un importe real de citas concretas. Las canceladas y las reservadas no suman.

### Cifras de la semilla (los ingresos por servicio y la evolución cubren los días laborables −40 a −1; las cifras por profesional, solo la última semana)

**Última semana de la semilla** (semana 8 = días laborables −5 a −1, cargada un lunes; 3.300 minutos de horario por profesional)

| Profesional | Completadas | No asistidas | Canceladas | Tasa de no asistencia | Min. completadas | Ocupación |
|---|---:|---:|---:|---:|---:|---:|
| María | 37 | 1 | 1 | 2,6 % (1 de 39) | 1.800 | 54,5 % |
| Jorge | 33 | 5 | 3 | 12,2 % (5 de 41) | 1.590 | 48,2 % |
| Lucía | 48 | 4 | 3 | 7,3 % (4 de 55) | 1.605 | 48,6 % |

**Ingresos por servicio de la semilla** (citas completadas)

| Servicio | Citas | Importe |
|---|---:|---:|
| Sesión fisioterapia | 426 | 17.040,00 € (1704000 céntimos) |
| Primera visita fisioterapia | 97 | 4850,00 € (485000 céntimos) |
| Consulta nutrición | 277 | 9695,00 € (969500 céntimos) |
| Primera visita nutrición | 73 | 3285,00 € (328500 céntimos) |
| **Total** | **873** | **34.870,00 € (3487000 céntimos)** |

El total coincide con el «Completada» de `datos-demostracion.md` (34.870,00 €), y por profesional con su «Importe completado» (María 11.250,00 €, Jorge 10.640,00 €, Lucía 12.980,00 €).

**Ingresos no obtenidos de la semilla** (citas no asistidas, días laborables −40 a −1)

| Servicio | Citas no asistidas | Importe |
|---|---:|---:|
| Sesión fisioterapia | 56 | 2240,00 € (224000 céntimos) |
| Primera visita fisioterapia | 11 | 550,00 € (55000 céntimos) |
| Consulta nutrición | 27 | 945,00 € (94500 céntimos) |
| Primera visita nutrición | 12 | 540,00 € (54000 céntimos) |
| **Total** | **106** | **4275,00 € (427500 céntimos)** |

El total coincide con el «No asistida» de `datos-demostracion.md` (4275,00 €).

**Evolución de la semilla** (cargada un lunes: semana 1 = días laborables −40 a −36 … semana 8 = −5 a −1)

| Semana | Completadas | No asistidas | Canceladas | Tasa de no asistencia | Ingresos |
|---|---:|---:|---:|---:|---:|
| 1 (−40 a −36) | 110 | 11 | 14 | 8,1 % | 4380,00 € (438000 céntimos) |
| 2 (−35 a −31) | 115 | 10 | 11 | 7,4 % | 4610,00 € (461000 céntimos) |
| 3 (−30 a −26) | 105 | 18 | 9 | 13,6 % | 4210,00 € (421000 céntimos) |
| 4 (−25 a −21) | 100 | 12 | 13 | 9,6 % | 3990,00 € (399000 céntimos) |
| 5 (−20 a −16) | 114 | 10 | 10 | 7,5 % | 4550,00 € (455000 céntimos) |
| 6 (−15 a −11) | 102 | 19 | 12 | 14,3 % | 4050,00 € (405000 céntimos) |
| 7 (−10 a −6) | 109 | 16 | 9 | 11,9 % | 4330,00 € (433000 céntimos) |
| 8 (−5 a −1) | 118 | 10 | 7 | 7,4 % | 4750,00 € (475000 céntimos) |
| **Total** | **873** | **106** | **85** | **10,0 %** | **34.870,00 € (3487000 céntimos)** |

**Ocupación semanal por profesional de la semilla** (3.300 minutos de horario por profesional y semana)

| Semana | María | Jorge | Lucía |
|---|---:|---:|---:|
| 1 | 46,8 % | 50,9 % | 41,8 % |
| 2 | 47,3 % | 52,7 % | 46,8 % |
| 3 | 50,9 % | 43,2 % | 40,5 % |
| 4 | 43,6 % | 42,7 % | 40,5 % |
| 5 | 51,8 % | 44,5 % | 47,7 % |
| 6 | 47,7 % | 35,5 % | 44,5 % |
| 7 | 46,4 % | 50,5 % | 40,9 % |
| 8 | 54,5 % | 48,2 % | 48,6 % |

### Entidades clave

- **Profesional**: persona que atiende; cada cifra de ocupación y de no asistencia se calcula por profesional.
- **Servicio**: tipo de atención con su precio; los ingresos se agrupan por servicio.
- **Cita**: reserva con inicio, duración, importe fijado al reservarla y estado (reservada, completada, cancelada, no asistida); la analítica solo la lee.
- **Semana de análisis**: periodo de lunes a domingo en hora local de la clínica que agrupa las cifras de la evolución.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: Con la semilla cargada, las cifras por profesional de la última semana, los ingresos por servicio y la evolución mostradas coinciden al 100 % con las de esta spec (según las definiciones decididas), reproduciéndose en cargas distintas.
- **SC-002**: La suma de los importes por servicio, la suma de las 8 semanas y el total coinciden exactamente (diferencia de 0 céntimos) y con el importe «Completada» de la semilla (34.870,00 €); lo mismo ocurre con los ingresos no obtenidos (4275,00 €).
- **SC-003**: Una persona sin formación identifica, en menos de 30 segundos y sin ayuda, qué profesional tiene la mayor tasa de no asistencia y qué servicio ingresa más.
- **SC-004**: La página se muestra completa, sin desbordes ni elementos ilegibles, en pantalla de móvil estrecha y en portátil, y supera las comprobaciones de accesibilidad automáticas del proyecto.
- **SC-005**: Tras usar la página con la semilla cargada, un recuento de todas las citas, importes y pacientes de la clínica es idéntico al de antes (0 cambios).
- **SC-006**: Con la semilla cargada, la página con todos sus gráficos está lista para consultar en menos de 3 segundos.

## Supuestos

- **Usuarios**: la analítica la consulta quien gestiona la clínica con la clave compartida del panel; no hay roles ni usuarios individuales (deuda ya registrada en la spec 001). Sara la usa en demos y renovaciones con Clínica Eleva.
- **Periodo**: los ingresos por servicio y la evolución cubren las últimas 8 semanas naturales completas; las cifras por profesional, solo la última semana completa; no hay selector de fechas ni de periodo (fuera de alcance).
- **Horario**: la ocupación se calcula sobre el horario de atención de la clínica, igual para todos sus profesionales (spec 001); los horarios individuales, festivos y ausencias no existen aún, por lo que un festivo cuenta como día laborable con capacidad (consistente con la semilla).
- **Importe de una cita**: es el precio del servicio al reservar (spec 001); un cambio posterior de precio no altera los ingresos históricos.
- **Reservadas**: las citas aún reservadas (futuras) no entran en ingresos ni en la tasa de no asistencia.
- **Semilla**: la semilla no cambia en esta feature; las cifras de este documento se derivan de ella y de los recuentos documentados en la spec 001. La evolución semanal solo es reproducible por posición con carga en lunes (ver Casos límite).
- **Fuera de alcance**: exportar a PDF/Excel/CSV, comparar clínicas o periodos arbitrarios, filtros, objetivos o alertas, pronóstico de ingresos, cualquier «ahorro» estimado o atribuido a CitaClara (los ingresos no obtenidos son un importe real de citas concretas, no una estimación; atribuir ahorro requeriría una regla de negocio propia que nadie ha definido), analítica del paciente o de recordatorios, y cualquier escritura de datos.
- **Dependencias**: lee los datos de la spec 001 (clínica, profesionales, servicios, citas) y reutiliza su acceso por clave. Sin dependencia de las specs en paralelo 002 y 003 (portal del paciente y recordatorios), que podrían cambiar los estados de las citas: si lo hacen, deberán solicitar a esta feature el ajuste de las definiciones.
- **Propiedad**: esta spec se registra en `specs/MAPA.md` con un único propietario, la feature 004-panel-analitica.
