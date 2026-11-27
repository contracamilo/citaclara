# Contrato del correo de recordatorio (003-recordatorios-cita)

**Plan**: [../plan.md](../plan.md) · **Decisiones**: [research.md D2, D9, D12](../research.md)

## Modos

| Modo | Cuándo | Efecto |
|---|---|---|
| Simulado (por defecto) | `CORREO_SMTP_HOST` ausente | Cada recordatorio se guarda como fichero `.eml` en el directorio de salida; no se envía nada a ninguna dirección real (FR-020). |
| Real | `CORREO_SMTP_HOST` presente | El mismo mensaje se envía por SMTP; el comportamiento observable es idéntico salvo el destino (FR-021). |

## Configuración

| Variable | Valor por defecto | Uso |
|---|---|---|
| `RECORDATORIOS_CRON` | `0 0 9 * * *` (09:00, Europe/Madrid) | Hora del proceso diario |
| `CORREO_DIRECTORIO` | `datos/salida-correo` (en Docker, `/app/datos/salida-correo`) | Carpeta de los `.eml` |
| `CORREO_REMITENTE` | `no-responder@citaclara.es` | Cabecera `From` |
| `CITACLARA_URL_PUBLICA` | `http://localhost:8080` | Prefijo del enlace de cancelación |
| `CORREO_SMTP_HOST` | ausente | Si existe, activa el modo real (SMTP) |
| `CORREO_SMTP_PUERTO`, `CORREO_SMTP_USUARIO`, `CORREO_SMTP_CLAVE` | 587, vacío, vacío | Datos del servidor SMTP; con usuario se activa autenticación y STARTTLS |

## Fichero de salida

- Nombre determinista: `recordatorio-cita-{citaId}.eml` (idempotente ante reintentos, D3). El identificador interno solo aparece en el nombre del fichero, nunca dentro del correo.
- Escritura a un fichero temporal en la misma carpeta y movimiento atómico: nunca queda un `.eml` a medias.
- Codificación UTF-8; asunto codificado según RFC 2047; `Message-ID` único.

## Mensaje

- **Para**: dirección del paciente (saneada: sin saltos de línea en el nombre visible).
- **Asunto**: `Recordatorio de tu cita en {clínica}: {dd/MM/yyyy} a las {HH:mm}`
- **Cuerpo** (`text/plain`, español de España, hora peninsular de 24 h):

```text
Hola, {nombre del paciente}:

Te recordamos tu cita en {clínica}.

  Profesional: {profesional}
  Servicio:    {servicio}
  Día:         {día de la semana} {dd/MM/yyyy}
  Hora:        {HH:mm}

Si no puedes venir, cancélala hasta 24 horas antes de la cita desde este enlace:
{CITACLARA_URL_PUBLICA}/cancelar/{enlace}

Así dejamos el hueco libre para otra persona. Si ya no puedes cancelar desde el enlace
o prefieres cambiar la cita, llama a la clínica: {teléfono}.

{clínica}
```

- No contiene importe (FR-011), ni contenido clínico, ni identificadores internos, ni datos de otra clínica o paciente (FR-008).
