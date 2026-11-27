# Contrato: cabeceras y límites de seguridad

**Spec**: [../spec.md](../spec.md) (FR-005, FR-006, FR-009) | Se comprueba con `frontend/tests/e2e/seguridad.spec.ts` y con pruebas de integración del backend.

Lo exigido a nginx (capa web) y al API. Valores de partida; la fase 2 los ajusta a lo que la interfaz necesite **sin relajarlos sin dejar constancia en el informe**.

## Cabeceras en toda respuesta HTML y estática

| Cabecera | Valor exigido | Motivo |
|---|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'` | Bloquea la ejecución de contenido inyectado (A02, A05) |
| `X-Content-Type-Options` | `nosniff` | Evita interpretaciones de tipo (A02) |
| `Referrer-Policy` | `no-referrer` | El identificador de clínica va en la ruta |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` | Reduce superficie; `payment` se reabrirá con la feature de cobros |
| `Cross-Origin-Opener-Policy` | `same-origin` | Aislamiento del contexto |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` **solo si** el despliegue termina TLS (`TLS_ACTIVO=true`) | Obliga a HTTPS (A04) |

Si el *build* de la interfaz exige `style-src 'unsafe-inline'`, se documenta como hallazgo de severidad baja con plan de eliminación y no se añade en silencio.

## Cabeceras en las respuestas del API (`/api/**`)

- `Cache-Control: no-store` (datos de pacientes, agenda y sesión no se guardan en cachés).
- `X-Content-Type-Options: nosniff`.
- Las cabeceras `Server` y `X-Powered-By` no revelan producto ni versión (`server_tokens off`).

## Cookie de sesión

- `HttpOnly`, `SameSite=Strict`, `Path=/`, y `Secure` cuando el perfil es `produccion` (el arranque falla si no lo es, D12).
- Nombre `CITACLARA_SESION` (sin cambios).

## Límites

| Límite | Valor inicial | Respuesta |
|---|---|---|
| Tamaño del cuerpo (`client_max_body_size`) | 64 KB | `413`, mensaje sin jerga |
| Longitud de `q` en búsqueda de pacientes | 80 caracteres | `400 DATOS_NO_VALIDOS` con texto para el usuario |
| Peticiones a `/api/` por origen | 20 por segundo con ráfaga de 40 | `429` |
| Peticiones a la ruta de acceso por origen | 60 por minuto con ráfaga de 20 (ajustado: con 5 por minuto la batería e2e, que inicia sesión decenas de veces, se bloquea; el freno estricto por fallos es la solicitud S-001) | `429` con tiempo de espera |
| Origen del cliente | nginx fija `X-Forwarded-For` con `$remote_addr` (sin concatenar el valor del cliente) | — |

Los mensajes `429` y `413` los muestra la interfaz en español de España y sin códigos (Principio VII): «Has probado demasiadas veces. Espera unos minutos e inténtalo de nuevo.»

## Qué NO cambia

- Todo POST solo en JSON (415 `TIPO_NO_ADMITIDO`) y sesión por cookie: convenciones [001] que se conservan.
- `/actuator/**` no se enruta desde nginx; el API solo expone `health` a la red interna.
