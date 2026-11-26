# Contrato de interfaz: panel de recepción (001-nucleo-agenda)

**Plan**: [../plan.md](../plan.md) · **API**: [api.yaml](api.yaml) · **Decisiones visuales**: [research.md D15](../research.md)

Define pantallas, elementos y textos visibles que la implementación DEBE respetar y que las pruebas extremo a extremo comprueban. Todo texto en español de España (FR-027); sin códigos ni identificadores internos.

## Direcciones

| Dirección | Contenido |
|---|---|
| `/` | Mensaje: «Abre la dirección del panel de tu clínica para entrar.» |
| `/{identificador}` | Pantalla de acceso si no hay sesión; agenda si la hay. |
| `/{identificador}?profesional={id}&fecha=AAAA-MM-DD` | Agenda de ese profesional y día (enlace compartible; el botón atrás funciona). |

## 1. Pantalla de acceso (US1-AC1, US1-AC2)

- Título con el nombre de la clínica («Clínica Eleva»), campo «Clave de la clínica» (tipo contraseña, con botón para mostrarla) y botón «Entrar».
- Clave incorrecta → aviso bajo el campo: «La clave no es correcta. Revísala e inténtalo de nuevo.»
- Llegada por caducidad → aviso: «Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.»

## 2. Agenda del día (US1)

**Cabecera fija**: nombre de la clínica, botón «Cerrar sesión».

**Barra de control** (siempre visible al desplazarse):
- Selector de profesional: control segmentado con el nombre de cada profesional y su especialidad (FR-005). Cambiar de profesional conserva el día.
- Navegación de día: «Día anterior», fecha en formato largo («jueves, 26/11/2026»), «Día siguiente», botón «Hoy» y selector de fecha concreta. Por defecto, hoy (US1-AC3).

**Cuerpo**: línea de tiempo vertical, en orden cronológico, que intercala:
- **Tarjetas de cita** (FR-006): «10:00 – 10:45» · nombre del paciente · servicio · «40,00 €» · distintivo de estado (color + icono + texto: Reservada / Completada / Cancelada / No asistida). Las canceladas y no asistidas se ven atenuadas y no tapan el hueco (US1-AC5).
- **Huecos libres** (FR-007): bloque punteado «Libre · 10:45 – 12:00» con botón «Reservar aquí» si el hueco aún es reservable (FR-008).
- Día sin horario: «La clínica no atiende este día.»
- Día sin citas: un único hueco con todo el horario (US1-AC6).

**Acciones de estado** en cada tarjeta, solo las devueltas por el API (US3):
- «Cancelar cita» → confirmación: «¿Cancelar la cita de Ana Pérez del 26/11/2026 a las 10:00? El hueco quedará libre.» [«Sí, cancelar»] [«No, volver»] (FR-022).
- «Completada» y «No asistió» se aplican sin confirmación. No hay «deshacer»: son estados finales en esta feature (Supuestos de la spec).
- Conflicto → aviso con el estado real y la tarjeta actualizada (US3-AC7).

## 3. Alta de cita (US2)

Diálogo (hoja inferior en móvil) abierto desde «Reservar aquí» o desde el botón «Nueva cita»:

1. **Profesional** (prefijado) y **día** (prefijado).
2. **Servicio**: solo los de la especialidad del profesional, con duración y precio («Sesión fisioterapia · 45 min · 40,00 €») (FR-010).
3. **Hora de inicio**: selector en pasos de 5 min dentro del horario; muestra al lado «Termina a las 10:45» calculado (FR-009, no editable).
4. **Paciente**: buscador por nombre o teléfono (FR-018) o «Paciente nuevo» con Nombre*, Teléfono*, Email (FR-019).
   - Teléfono repetido → «Ya hay un paciente con ese teléfono: Ana Pérez. ¿Quieres usar su ficha?» [«Usar esta ficha»] (FR-020).
5. Botón «Reservar cita».

- Rechazos (FR-016): el mensaje del API aparece en la parte superior del diálogo y **todos los datos introducidos se conservan**.
- Éxito: el diálogo se cierra, la agenda se recarga y la nueva cita queda resaltada brevemente; aviso «Cita reservada para Ana Pérez a las 10:00.».
- Sesión caducada durante el alta: se vuelve a la pantalla de acceso con el aviso de caducidad; la cita no se ha registrado.

## Requisitos transversales comprobables

| Requisito | Cómo se comprueba |
|---|---|
| Sin desplazamiento horizontal a 360 px (SC-009, US1-AC7) | Playwright: `scrollWidth <= clientWidth` en todas las pantallas y diálogos. |
| Accesibilidad (FR-028) | `@axe-core/playwright` sin infracciones serias o críticas; objetivos táctiles ≥ 44 px; contraste AA en claro y oscuro. |
| Formatos (FR-026, FR-017) | Fechas `dd/mm/aaaa`, horas 24 h, importes «40,00 €». |
| Idioma (FR-027) | Ningún texto visible contiene códigos (`SOLAPE`, `409`…) ni identificadores. |
| Rendimiento (SC-007) | Agenda visible en < 2 s tras elegir profesional o día. |
