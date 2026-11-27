# Contrato de interfaz: portal del paciente y aviso en recepción (002-portal-paciente)

**Plan**: [../plan.md](../plan.md) · **API**: [api.yaml](api.yaml) · **Base visual**: [ui.md de 001](../../001-nucleo-agenda/contracts/ui.md) y research D15 de 001

Este contrato define las pantallas, los elementos y los textos visibles que la implementación DEBE respetar y que comprueban las pruebas e2e. Todo texto está en español de España y trata al paciente de «tú» (FR-025). No aparecen códigos ni identificadores internos. Todas las pantallas se diseñan primero para móvil: una columna, sin desplazamiento horizontal a 360 px y con objetivos táctiles de al menos 44 px (FR-024).

## Direcciones

| Dirección | Contenido |
|---|---|
| `/{identificador}/mis-citas` | Entrada del portal si no hay sesión; «Mis citas» si la hay. |
| `/{identificador}/mis-citas#enlace={secreto}` | Consume el enlace y lleva a «Mis citas»; el fragmento se borra del historial nada más leerlo. |
| `/{identificador}` | Panel de recepción (001), con el aviso de §5. |

Todas las pantallas del portal muestran en la cabecera el nombre de la clínica («Clínica Eleva», FR-027).

## 1. Entrada del portal (US1)

- Título: «Tus citas en Clínica Eleva».
- Texto: «Escribe el email que diste en la clínica y te enviaremos un enlace para entrar. No necesitas contraseña.»
- Campo «Tu email» (`type="email"`, `autocomplete="email"`) y botón «Enviarme el enlace».
- Tras enviarlo, sea cual sea el email (US1-AC1, US1-AC3), siempre el mismo aviso: «Si tu email está registrado en Clínica Eleva, te hemos enviado un enlace para entrar. Revisa tu correo; el enlace caduca en 10 minutos. Si no te llega, llama a la clínica.», más el botón secundario «Pedir otro enlace».
- Email con formato no válido: «Escribe un email válido, por ejemplo nombre@correo.es.»
- Límite superado (US1-AC7): «Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.»
- Llegada por caducidad (US1-AC5): aviso «Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.»

## 2. Apertura del enlace (US1-AC2, US1-AC4)

- Mientras se consume: «Entrando en tus citas…».
- Enlace usado, caducado o de otra clínica: «Este enlace ya no es válido. Pide uno nuevo.», con el formulario de §1 debajo.

## 3. Mis citas (US2, US3)

**Cabecera**: nombre de la clínica, saludo «Hola, Teresa» (con varias fichas: «Hola», y el nombre del paciente en cada tarjeta) y botón «Salir» (US1-AC6).

**Bloque «Próximas citas»** (FR-008), de la más próxima a la más lejana:
- Tarjeta: fecha larga («martes, 01/12/2026»), franja «10:15–11:15», «con María», servicio «Primera visita fisioterapia», precio «50,00 €» y distintivo «Reservada».
- Si `cancelable`: botón «Cancelar cita».
- Si no es cancelable por plazo: texto «Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.» y ningún botón (US3-AC4).
- Sin próximas citas (US2-AC7): «No tienes citas próximas. Si quieres pedir una, llama a la clínica.»

**Bloque «Citas pasadas»**, de la más reciente a la más antigua: misma tarjeta, sin acciones, con estos distintivos (color, icono y texto; nunca solo color):

| `estadoPortal` | Texto visible |
|---|---|
| `RESERVADA` | Reservada |
| `PENDIENTE_CIERRE` | Pendiente de cierre por la clínica |
| `REALIZADA` | Realizada |
| `NO_ACUDISTE` | No acudiste |
| `CANCELADA` | Cancelada |

**Diálogo de cancelación** (US3-AC1–AC3), presentado como hoja inferior en móvil:
- «¿Cancelar tu cita del martes, 01/12/2026 a las 09:45 con Jorge?» [«Sí, cancelar»] [«Volver»].
- Mientras espera la respuesta, «Sí, cancelar» queda desactivado (US3-AC7).
- Éxito: aviso «Tu cita se ha cancelado.»; la tarjeta pasa a «Citas pasadas» como «Cancelada».
- `FUERA_DE_PLAZO` (US3-AC5): «Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.»
- `ESTADO_YA_CAMBIADO` (US3-AC6): «Esta cita acaba de cambiar. Revisa su estado.» y la tarjeta actualizada con `citaActual`.

## 4. Email del enlace (FR-007)

- Remitente: el configurado (`EMAIL_REMITENTE`), con el nombre «Clínica Eleva».
- Asunto: «Tu enlace para entrar en Clínica Eleva»
- Cuerpo (texto plano):

```text
Hola:

Has pedido entrar en tus citas de Clínica Eleva. Abre este enlace para verlas:

{URL_PUBLICA}/eleva/mis-citas#enlace={secreto}

El enlace caduca en 10 minutos y solo se puede usar una vez.

Si no has pedido este enlace, puedes ignorar este mensaje: nadie podrá entrar sin él.

Clínica Eleva
```

## 5. Aviso de cancelaciones en el panel de recepción (US4)

- En la cabecera fija de la agenda, junto a «Cerrar sesión», y solo si `pendientes > 0`: botón con icono de campana y el texto «1 cancelación de pacientes por revisar» (o «3 cancelaciones de pacientes por revisar»). En 360 px se muestra el icono con el número y el texto completo como nombre accesible.
- Se actualiza al cargar la agenda y al cambiar de día o de profesional (SC-008).
- Al pulsarlo, el diálogo «Cancelaciones de pacientes» lista las pendientes, de la más reciente a la más antigua. Cada elemento muestra:
  - «Laura Romero · Sesión fisioterapia»
  - «Jorge · miércoles, 02/12/2026 · 09:45–10:30»
  - «Cancelada el 01/12/2026 a las 09:00»
  - El distintivo «Hueco ya pasado» si `huecoPasado` (US4-AC6)
  - El botón «Marcar como revisada»
- Al marcar la última, el diálogo muestra «No hay cancelaciones pendientes.» y el aviso desaparece de la cabecera (US4-AC3).
