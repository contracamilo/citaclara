# Requisitos de seguridad para cobros con Stripe

**Spec**: [spec.md](spec.md) (FR-010, FR-011) | **Informe**: [informe-auditoria.md](informe-auditoria.md)

- **Fecha de redacción**: 27/11/2026
- **Contraste con la documentación de Stripe**: pendiente. Esta lista se redactó a partir de las guías públicas de Stripe sobre [seguridad](https://docs.stripe.com/security/guide), [avisos](https://docs.stripe.com/webhooks) y [claves](https://docs.stripe.com/keys), pero no se volvieron a consultar en línea durante la auditoría. Quien implemente los cobros debe confirmarla contra la documentación vigente antes de construir (tarea T026 de la 005, abierta).
- **Modelo asumido**: CitaClara **no** recibe, transmite ni guarda datos de tarjeta; la captura la delega por completo en Stripe (formulario alojado por Stripe o elementos de Stripe). Con ese modelo el comercio queda en el alcance más reducido de PCI DSS (cuestionario SAQ A). Si algún día se decidiera tocar datos de tarjeta, este documento deja de valer.
- **Orígenes de pago**: `recepcion` (la persona de recepción cobra en la clínica) y `paciente en línea` (la persona paciente paga desde un portal público, spec 002).

## Lista de comprobación

Veredicto de cada punto contra el sistema **actual** (no hay cobros): `se cumple`, `no se cumple` o `no aplica todavía`.

| Punto | Veredicto | Evidencia |
|---|---|---|
| La tarjeta nunca pasa por CitaClara (ni por el servidor, ni por los registros, ni por la base de datos) | se cumple | No existe ningún campo, columna ni endpoint de tarjeta; el esquema solo guarda importes en céntimos |
| Comunicación cifrada de extremo a extremo con Stripe y con el navegador | no se cumple | El entorno entregado sirve HTTP en el puerto 80 sin TLS (H-05 del informe) |
| Claves de API de Stripe restringidas, fuera del código y rotables | no aplica todavía | No hay claves de Stripe; `.gitleaks.toml` y el análisis de secretos en integración continua evitarán que se confirmen |
| Verificación de la firma de los avisos (*webhooks*) sobre el cuerpo sin modificar | no aplica todavía | No hay receptor de avisos |
| Tratamiento idempotente de avisos repetidos o desordenados | no aplica todavía | No hay receptor de avisos |
| Importe decidido por el servidor, nunca por el navegador | no aplica todavía | Los importes ya viajan como céntimos enteros y los fija el servidor al reservar (spec 001), pero no hay cobro |
| Conciliación al céntimo entre lo cobrado y lo registrado | no aplica todavía | No hay cobros que conciliar |
| Registros sin datos de pago ni datos personales | se cumple | `RegistroEventosTest` y `ManejadorErroresRegistroTest` (H-03 corregido) |
| Separación entre clínicas de los cobros | se cumple | El aislamiento por `clinica_id` está verificado por `AislamientoClinicaTest` y se extenderá a los pagos |
| Referencias de pago no adivinables, de un solo uso y con caducidad | no aplica todavía | No existen referencias de pago |
| Registro de eventos de seguridad de los cobros | no se cumple | No hay registro de eventos de seguridad (H-11) |

## Requisitos para quien construya los cobros

| Id | Requisito verificable | Origen | Verificación | Fuente |
|---|---|---|---|---|
| RP-001 | Ningún dato de tarjeta (número, caducidad, código) llega a CitaClara: ni en peticiones, ni en registros, ni en base de datos. La tarjeta se captura solo en el formulario de Stripe | ambos | Prueba que revisa el esquema y los registros tras un cobro de prueba y no encuentra datos de tarjeta | [Guía de seguridad](https://docs.stripe.com/security/guide) |
| RP-002 | El receptor de avisos verifica la firma de cada aviso sobre el cuerpo **sin modificar** y rechaza los que no la superan o llevan una marca de tiempo fuera de la tolerancia | ambos | Prueba con firma válida, firma alterada, cuerpo reescrito y aviso antiguo | [Avisos](https://docs.stripe.com/webhooks) |
| RP-003 | El tratamiento de avisos es idempotente: un aviso repetido o recibido fuera de orden no duplica ni invierte un cobro | ambos | Prueba que envía el mismo aviso dos veces y dos avisos en orden inverso y comprueba un único efecto | [Avisos](https://docs.stripe.com/webhooks) |
| RP-004 | El importe lo decide el servidor, en céntimos enteros, a partir del servicio reservado; el importe que envíe el navegador se ignora | ambos | Prueba que manda un importe distinto y comprueba que se cobra el del servicio | Constitución, principio II |
| RP-005 | Los importes se muestran siempre en euros con el formateador único `frontend/src/formato/euros.ts` | ambos | Prueba de interfaz sobre cada pantalla de cobro | Constitución, principio II |
| RP-006 | Las claves de Stripe son restringidas (permisos mínimos), viven fuera del código y del repositorio, se leen del entorno y se pueden rotar sin cambiar código | ambos | Análisis de secretos en integración continua en verde y arranque que falla sin la clave en producción | [Claves](https://docs.stripe.com/keys) |
| RP-007 | Cada cobro se concilia al céntimo con lo registrado: la suma de lo cobrado por Stripe coincide con la de las citas cobradas, y toda diferencia se señala | ambos | Prueba de conciliación con un cobro correcto, uno devuelto y uno parcial | Constitución, principio II |
| RP-008 | Toda referencia de pago expuesta a una persona paciente es no adivinable (aleatoria de al menos 128 bits), de un solo uso y con caducidad | paciente en línea | Prueba que reutiliza una referencia, usa una caducada y prueba valores consecutivos | [Guía de seguridad](https://docs.stripe.com/security/guide) |
| RP-009 | El portal público no revela datos de otras personas ni de otras clínicas: la misma respuesta ante una referencia inexistente, usada o ajena, con límite de peticiones | paciente en línea | Prueba entre clínicas y prueba de límite de peticiones | OWASP A01 y A07 |
| RP-010 | Cada cobro, devolución y aviso rechazado genera un evento de seguridad sin datos personales ni de pago y con la persona que lo originó (recepción) | recepcion | Prueba que provoca cada caso y revisa el registro | OWASP A09 |
| RP-011 | Solo las personas con permiso de cobro de la clínica pueden cobrar o devolver desde recepción; un recurso de otra clínica responde como inexistente | recepcion | Prueba entre clínicas y con una persona sin permiso | OWASP A01 |
| RP-012 | Ninguna ruta de cobro se publica sin TLS: el perfil de producción exige cookie segura y HSTS | ambos | `PerfilProduccionTest` ampliado a los cobros | [Guía de seguridad](https://docs.stripe.com/security/guide) |
