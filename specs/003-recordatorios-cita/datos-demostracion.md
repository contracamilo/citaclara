# Datos de demostración: recordatorios de Clínica Eleva

**Feature**: `003-recordatorios-cita` | **Spec**: [spec.md](spec.md) (SC-001, SC-004) | **Datos base**: [../001-nucleo-agenda/datos-demostracion.md](../001-nucleo-agenda/datos-demostracion.md)

Cifras exactas de recordatorios que produce el proceso diario sobre la semilla de la feature 001 (constitución, Principio V). Cualquier ejemplo o demostración que cite estos números debe tomarlos de aquí.

## Cómo reproducirlas

```bash
docker compose run --rm semilla         # carga Clínica Eleva (el día de carga es el día de referencia)
docker compose run --rm recordatorios   # una ejecución del proceso diario (ver quickstart.md)
```

## Cómo leer las cifras

- El **día de referencia es el día de carga**, que no recibe citas; las posiciones son **días laborables** relativos («día laborable +1» es el primer día con citas).
- Las cifras suponen el proceso diario ejecutado **a las 09:00 de cada día natural** (también fines de semana) a partir del propio día de carga. Así, las citas de un día laborable reciben su recordatorio en la ejecución de las 09:00 del día natural anterior, caiga en fin de semana o no.
- Los 40 pacientes de la semilla tienen email: todas las citas reservadas de la ventana generan recordatorio.

## Recuentos

Los genera y verifica `DatosDemostracionRecordatoriosTest` (`[003][SC-004]`): si la semilla o el proceso cambian sin actualizar este documento, la suite falla (regenerar con `-DactualizarDatosDemostracion=true`).

<!-- recuentos:inicio -->
### Recordatorios por día laborable de la cita

| Día laborable | Citas reservadas | Recordatorios generados |
|---|---:|---:|
| +1 | 19 | 19 |
| +2 | 19 | 19 |
| +3 | 19 | 19 |
| +4 | 17 | 17 |
| +5 | 19 | 19 |
| +6 | 17 | 17 |
| +7 | 17 | 17 |
| +8 | 18 | 18 |
| +9 | 17 | 17 |
| +10 | 18 | 18 |
| Total | 180 | 180 |

Recordatorios con menos de 24 h de antelación: 0.
<!-- recuentos:fin -->
