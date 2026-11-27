# Contrato de interfaz: cancelación por el paciente (003-recordatorios-cita)

**Plan**: [../plan.md](../plan.md) · **API**: [api.yaml](api.yaml) · **Correo**: [correo.md](correo.md)

Pantalla pública pensada para el móvil del paciente (360 px mínimo). Español de España; sin códigos ni términos técnicos (FR-017). Contraste y objetivos táctiles ≥ 44 px; se comprueba con axe en e2e.

## Dirección

| Dirección | Contenido |
|---|---|
| `/cancelar/{enlace}` | Pantalla de cancelación; no requiere clave ni sesión. |

## Estados de la pantalla

Cabecera común: nombre de la clínica. Resumen de la cita (en todos los estados salvo error): servicio, profesional, día en formato `dd/MM/yyyy` con el día de la semana y hora de 24 h («lunes 30/11/2026 · 10:00»).

1. **Cargando**: indicador y texto «Estamos buscando tu cita…».
2. **Activa** (`situacion = ACTIVA`): título «¿Cancelamos tu cita?», resumen, texto «Al cancelar dejamos el hueco libre para otra persona.» y dos botones: **«Sí, cancelar mi cita»** (principal) y **«No, mantener mi cita»** (secundario, solo cierra el aviso y no hace ninguna llamada). Mientras se envía, el botón se desactiva y evita el doble envío.
3. **Cancelada ahora o antes por el paciente** (`YA_CERRADA` + `canceladaPorPaciente`): título «Tu cita está cancelada», resumen y «Gracias por avisarnos. Si quieres otra cita, llama a la clínica al {teléfono}.»
4. **Ya no activa** (`YA_CERRADA` sin `canceladaPorPaciente`, o 409): «Esta cita ya no está activa.» y «Si tienes dudas, llama a la clínica al {teléfono}.» Sin botón de cancelar.
5. **Fuera de plazo** (`FUERA_DE_PLAZO`, o 422): «Ya no puedes cancelar desde aquí.», «Faltan menos de 24 horas para tu cita.» y «Llama a la clínica al {teléfono}.» El teléfono es un enlace `tel:` con objetivo táctil de 44 px. Sin botón de cancelar.
6. **Enlace no válido** (404): «No hemos encontrado lo que buscas.» y «Revisa que has abierto el enlace completo del correo.» Sin ningún dato de clínica ni de cita.
7. **Error de red o del servidor**: «Algo ha fallado. Inténtalo de nuevo en unos segundos.» con botón «Reintentar».

## Reglas

- Abrir la pantalla solo consulta (`GET`); únicamente «Sí, cancelar mi cita» llama a `POST` (FR-012).
- Tras confirmar, el estado 3 sustituye a la pantalla sin recargar; un refresco muestra el mismo estado (US2-AC7).
- Sin desplazamiento horizontal a 360 px y sin texto desbordado; modo claro y oscuro con los tokens ya existentes del producto.

## Agenda de recepción (cambio mínimo, FR-016)

La tarjeta de una cita con `canceladaPor = PACIENTE` muestra, junto al distintivo «Cancelada», el texto «Cancelada por el paciente». Las canceladas por la clínica siguen mostrando solo «Cancelada».
