# Mapa de specs de CitaClara

Fuente de verdad sobre qué specs existen y quién es su propietario único (constitución, «Trabajo en paralelo y propiedad de las specs»).

| Spec | Ámbito | Propietario | Estado |
|------|--------|-------------|--------|
| [001-nucleo-agenda](001-nucleo-agenda/spec.md) | Clínica, profesionales, servicios, pacientes, citas, agenda del día de recepción, reglas de solape (RN1) y no-pasado (RN2), datos de demostración | Feature 001-nucleo-agenda | Implementada |
| [002-portal-paciente](002-portal-paciente/spec.md) | Portal del paciente: acceso por enlace de un solo uso al email de la ficha, consulta de citas futuras y pasadas, cancelación hasta 24 h antes y aviso en recepción de las cancelaciones del portal | Feature 002-portal-paciente | Aprobada |
| [004-panel-analitica](004-panel-analitica/spec.md) | Página de analítica de solo lectura: ocupación semanal y tasa de no asistencia por profesional, ingresos por servicio y evolución de 8 semanas | Feature 004-panel-analitica | Implementada |
| [005-security-audit](005-security-audit/spec.md) | Auditoría de seguridad (OWASP Top 10, preparación Stripe/PCI, datos de pacientes), informe y plan de mejoras | Feature 005-security-audit | Implementada con solicitudes pendientes |

## Solicitudes entre specs

La 005 no edita specs ajenas (constitución, «Trabajo en paralelo y propiedad de las specs»). Estas solicitudes las decide el propietario de la spec afectada; hasta entonces los hallazgos correspondientes constan como `derivado` en [el informe](005-security-audit/informe-auditoria.md).

| Id | Origen | Spec afectada | Propietario | Texto propuesto | Estado |
|----|--------|---------------|-------------|-----------------|--------|
| S-001 | 005 (H-01, H-02, H-15; FR-021 y FR-022 de la 005) | 001-nucleo-agenda | Feature 001-nucleo-agenda | Sustituir la clave compartida por clínica por usuarios individuales con revocación; limitar los intentos de acceso (por origen: 3 fallos seguidos → 5 min, duplicando hasta 60 min; por clínica: 15 fallos en 15 min → 15 min de bloqueo, se levanta solo y no afecta a sesiones abiertas); respuesta uniforme en `GET /api/clinicas/{identificador}` | Pendiente de aprobación |
| S-002 | 005 (H-04) | 001-nucleo-agenda (y nueva spec `006-derechos-datos`) | Feature 001-nucleo-agenda | Definir conservación de 5 años desde la última cita con anonimización posterior sin alterar importes, y las vías de acceso, rectificación y supresión de una ficha de paciente | Pendiente de aprobación |
| S-003 | 005 (requisitos de pagos) | 002 portal del paciente y futura de pagos | Propietarios de 002 y de la futura feature de pagos | Aplicar `RP-001…RP-012` de [requisitos-pagos.md](005-security-audit/requisitos-pagos.md) sin cambiar sus specs hasta que las aprueben | Informativa |
