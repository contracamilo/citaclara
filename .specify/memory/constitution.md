# Constitución de CitaClara

CitaClara es un gestor de citas para clínicas y consultas pequeñas (fisioterapia, nutrición,
podología). Es un producto greenfield que nace con desarrollo guiado por especificaciones (SDD)
y que opera con varias features y varios agentes trabajando en paralelo desde el primer mes.

Esta constitución recoge **principios de negocio y de calidad**. No contiene decisiones técnicas:
el stack, los formatos de datos, las estructuras y las herramientas se deciden en el plan de cada
feature y deben cumplir lo que aquí se establece.

## Core Principles

### I. Spec primero

- Todo comportamiento observable del producto DEBE nacer de una spec aprobada en `specs/`.
  Sin spec aprobada no hay implementación.
- Todo cambio de comportamiento DEBE empezar corrigiendo la spec existente o creando una nueva;
  el código nunca se adelanta a la spec.
- Si el código y la spec discrepan, la spec manda: o se corrige el código, o se corrige primero
  la spec por su propietario y después el código.
- La propiedad de cada spec vive en `specs/MAPA.md`. Cada spec tiene **exactamente un
  propietario**; solo él aprueba cambios en ella.

**Por qué**: con varias features y agentes en paralelo, la spec es el único contrato compartido.
Si el comportamiento puede surgir fuera de ella, nadie sabe qué es correcto.

### II. Los números no admiten creatividad

- Todo importe DEBE cuadrar al céntimo, siempre: en cada cita, en cada suma, en cada informe y
  entre pantallas. Ningún redondeo intermedio puede alterar un total; cuando una regla de reparto
  o redondeo sea necesaria, la spec la DEBE fijar explícitamente.
- Los importes se muestran en euros con el formato español (coma decimal, dos decimales,
  símbolo €).
- Toda fecha y hora DEBE mostrarse sin ambigüedad posible para una clínica española: hora
  peninsular española con sus cambios de horario, formato día/mes/año y reloj de 24 horas.
  Nunca se muestra una fecha que pueda leerse de dos maneras.
- Los cambios de horario (verano/invierno) y los límites de día NO PUEDEN desplazar, duplicar ni
  hacer desaparecer una cita.

**Por qué**: una clínica pierde la confianza en el producto con un solo céntimo descuadrado o una
cita a la hora equivocada.

### III. El solape es el fallo capital (NO NEGOCIABLE)

- Un profesional NO PUEDE tener dos citas que se solapen en el tiempo. Jamás.
- La garantía DEBE mantenerse también ante la concurrencia: si dos reservas del mismo hueco
  llegan en el mismo instante, exactamente una prospera y la otra recibe un rechazo claro.
- Ningún camino de escritura en la agenda (crear, mover, reactivar, importar, generar datos de
  demostración) queda exento.
- Toda feature que escriba en la agenda DEBE incluir pruebas que intenten provocar un solape,
  incluidas pruebas de reservas simultáneas sobre el mismo hueco.

**Por qué**: un solape es el error que más daña a la clínica delante del paciente; ningún otro
beneficio lo justifica.

### IV. Simplicidad y cero alcance fantasma

- Ante dos soluciones que cumplen la spec, se elige la más simple.
- No se construye ninguna funcionalidad, pantalla, opción ni comportamiento que no esté
  justificado en una spec.
- No se añade ninguna dependencia externa sin justificación explícita en el plan de una feature
  que la vincule a una necesidad de su spec.
- Lo que queda fuera de alcance en una spec se declara como tal y no se implementa «por si acaso».

**Por qué**: el alcance fantasma multiplica conflictos entre features paralelas y genera
comportamiento que nadie ha aprobado ni prueba.

### V. Demostrable con datos reproducibles

- El producto DEBE mantener datos de demostración deterministas: misma semilla, misma historia,
  siempre y en cualquier entorno.
- Las specs, sus ejemplos y la analítica DEBEN citar números que cualquiera pueda reproducir a
  partir de esos datos.
- Cambiar los datos de demostración es un cambio de comportamiento: sigue el Principio I y obliga
  a revisar las specs y ejemplos que citan cifras afectadas.

**Por qué**: un número que no se puede reproducir no se puede verificar, ni en una spec ni delante
de un cliente.

### VI. Los tests acompañan a la spec

- Cada regla de negocio y cada criterio de aceptación relevante de una spec DEBE tener al menos
  un test que la referencie de forma explícita (por spec y criterio).
- Un test que no referencia ninguna regla ni criterio debe justificar su existencia o desaparecer.
- La suite completa en verde es condición de merge. Sin excepciones ni tests desactivados para
  poder integrar.

**Por qué**: la trazabilidad spec ↔ test es lo que permite que varios agentes cambien el producto
en paralelo sin romper lo que otro ya entregó.

### VII. Interfaz clara y moderna

- El personal de recepción DEBE poder usar el producto sin formación y sin manual.
- En pantalla no aparece jerga técnica: ni códigos de error, ni identificadores internos, ni
  términos de sistema. Los mensajes dicen qué ha pasado y qué hacer, en el lenguaje de la clínica.
- Contraste y tamaños de texto y de elementos táctiles DEBEN ser accesibles.
- Todo flujo DEBE funcionar en el portátil de recepción y en el móvil de un paciente.

**Por qué**: el usuario real no es técnico y trabaja con un paciente delante o al teléfono.
(Esto es un requisito de calidad; la tecnología de la interfaz se decide en el plan.)

### VIII. Español de España en todo

- Todo texto visible para el usuario (pantallas, mensajes, correos, documentos y ejemplos) DEBE
  estar en español de España: vocabulario, ortografía y convenciones locales.
- Las specs, planes y demás artefactos del proyecto se redactan en español de España.

**Por qué**: el producto es para clínicas españolas y su equipo trabaja en español.

## Trabajo en paralelo y propiedad de las specs

- `specs/MAPA.md` es la fuente de verdad sobre qué specs existen y quién es el propietario único
  de cada una. Toda spec nueva se registra en el mapa al crearse.
- Una feature solo modifica las specs de las que es propietaria. Si necesita cambiar el
  comportamiento definido en otra spec, lo solicita a su propietario en lugar de editarla.
- Cuando dos features en paralelo tocan el mismo comportamiento, el conflicto se resuelve en las
  specs (con sus propietarios) antes de resolverse en el código.
- Las reglas transversales (importes, fechas, solapes, datos de demostración, idioma) no se
  redefinen en cada feature: se rigen por esta constitución.

## Flujo de trabajo y puertas de calidad

- Cada feature recorre: spec aprobada → plan → tareas → implementación con tests → merge.
- El plan de cada feature DEBE comprobar explícitamente el cumplimiento de los ocho principios y
  justificar por escrito cualquier complejidad añadida o dependencia nueva.
- Puertas de merge, todas obligatorias:
  1. La spec afectada está aprobada y actualizada con el comportamiento entregado.
  2. Cada regla y criterio de aceptación relevante tiene su test referenciado.
  3. Si la feature escribe en la agenda, incluye las pruebas de solape del Principio III.
  4. La suite completa está en verde.
  5. Las cifras citadas en specs y ejemplos se reproducen con los datos de demostración.
  6. No se introduce funcionalidad ni dependencia sin justificar en una spec.

## Governance

- Esta constitución prevalece sobre cualquier otra práctica, plantilla o preferencia del proyecto.
  Ante un conflicto entre un plan o una spec y la constitución, gana la constitución.
- Toda modificación de la constitución se hace por escrito, explicando el motivo y el impacto en
  specs y features en curso, y se aprueba antes de aplicarse.
- Versionado semántico de la constitución:
  - MAJOR: se elimina o redefine un principio de forma incompatible.
  - MINOR: se añade un principio o sección, o se amplía materialmente una regla.
  - PATCH: aclaraciones y correcciones de redacción sin cambio de significado.
- Toda revisión de spec, plan y merge DEBE verificar el cumplimiento de esta constitución. Las
  excepciones no se toleran en silencio: o se corrige el trabajo, o se enmienda la constitución.

**Version**: 1.0.0 | **Ratified**: 2026-11-26 | **Last Amended**: 2026-11-26
