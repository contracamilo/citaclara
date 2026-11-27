# Guía de validación: Recordatorios de cita por email

**Plan**: [plan.md](plan.md) · **Contratos**: [api.yaml](contracts/api.yaml), [correo.md](contracts/correo.md), [ui.md](contracts/ui.md)

Recorrido de extremo a extremo para comprobar la feature una vez implementada. Los números exactos de recordatorios por día se documentan en `datos-demostracion.md` (se crea al implementar).

## Requisitos previos

- Docker en marcha; JDK 25 solo para las pruebas del backend.
- Entorno levantado con los datos de demostración:

```bash
docker compose up -d --build
docker compose run --rm semilla        # Clínica Eleva, clave eleva-demo-2026
```

## 1. Generar los recordatorios (US1)

```bash
docker compose run --rm recordatorios  # una ejecución del proceso diario y salir
ls datos/salida-correo/
```

> La semilla solo tiene citas en días laborables: si la ventana (de 24 h a 48 h desde ahora) cae en fin de semana o de noche, el comando responde «Recordatorios generados en esta ejecución: 0». Para forzar un caso, crea una cita propia dentro de la ventana:
>
> ```bash
> docker compose exec -T db psql -U citaclara -d citaclara -c "
>   INSERT INTO cita (clinica_id, profesional_id, servicio_id, paciente_id, inicio, fin, importe_centimos, estado)
>   SELECT c.id, p.id, s.id, pa.id, date_trunc('hour', now()) + interval '30 hours',
>          date_trunc('hour', now()) + interval '30 hours 45 minutes', 4000, 'RESERVADA'
>   FROM clinica c, profesional p, servicio s, paciente pa
>   WHERE c.identificador = 'eleva' AND p.nombre = 'María' AND s.nombre = 'Sesión fisioterapia'
>     AND pa.clinica_id = c.id ORDER BY pa.id LIMIT 1;"
> ```

**Esperado**: un `recordatorio-cita-{id}.eml` por cada cita reservada de la ventana [ahora + 24 h, siguiente ejecución + 24 h) con paciente con email. Abrir uno: asunto «Recordatorio de tu cita en Clínica Eleva: dd/MM/yyyy a las HH:mm», profesional, servicio, día y hora de 24 h, enlace `…/cancelar/…` y teléfono de la clínica; sin importe.

## 2. Sin duplicados (US1-AC2)

```bash
docker compose run --rm recordatorios
ls datos/salida-correo/ | wc -l        # no cambia
```

**Esperado**: la segunda ejecución no genera ficheros nuevos ni modifica los existentes.

## 3. Cancelar desde el correo (US2)

1. Copiar el enlace de un `.eml` y abrirlo en el navegador (o en el móvil, a 360 px). Debe verse la cita y los botones «Sí, cancelar mi cita» y «No, mantener mi cita»; la cita sigue reservada.
2. Pulsar «Sí, cancelar mi cita»: aparece «Tu cita está cancelada».
3. En el panel `http://localhost:8080/eleva`, la franja figura libre y la tarjeta dice «Cancelada por el paciente».
4. Recargar el enlace: sigue mostrando «Tu cita está cancelada».
5. Volver a reservar el mismo hueco desde recepción: se acepta sin solape.

## 4. Fuera de plazo y enlaces no válidos (US2-AC5, AC6)

- Un enlace de una cita que empieza en menos de 24 h muestra «Ya no puedes cancelar desde aquí» con el teléfono.
- `http://localhost:8080/cancelar/enlace-inventado` muestra «No hemos encontrado lo que buscas.» sin datos de ninguna clínica.

## 5. Cambio de cita (US3)

1. Con una cita ya recordada, cancelarla desde recepción y crear otra para el mismo paciente en la ventana.
2. Abrir el enlace antiguo: «Esta cita ya no está activa.»; la cita nueva no se ve afectada.
3. Ejecutar el proceso: la cita nueva recibe su propio recordatorio con enlace distinto; la antigua no se reenvía.

## 6. Pruebas automáticas

```bash
cd backend && ./mvnw verify                          # incluye ventana, duplicados, concurrencia, cambio de horario
cd frontend && npm ci && npm test                    # pantalla de cancelación
URL_BASE=http://localhost:8080 npm run e2e           # flujo completo a 360 px con axe
./scripts/trazabilidad.sh specs/003-recordatorios-cita   # debe salir vacío
```

## Limpieza

```bash
rm -f datos/salida-correo/*.eml
```
