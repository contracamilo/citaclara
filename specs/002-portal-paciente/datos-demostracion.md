# Datos de demostración del portal: citas por paciente

**Feature**: `002-portal-paciente` | **Spec**: [spec.md](spec.md) (FR-030) | **Decisión**: [research.md R11](research.md)

Cifras por paciente que produce la semilla determinista de Clínica Eleva, **sin modificarla**. Las pruebas y demostraciones del portal que citen citas de estos pacientes deben tomarlas de aquí (constitución, Principio V). El catálogo, los recuentos globales y cómo cargar la semilla están en [datos-demostracion.md de 001](../001-nucleo-agenda/datos-demostracion.md).

## Cómo leer las cifras

- Igual que en 001: el **día de referencia es el día de carga**, que no recibe citas, y las fechas se citan por **posición relativa en días laborables** («−1» es el último día de la historia y «+1» el primero del futuro).
- «Historia» son las citas de los días laborables −40 a −1; «Futuro», las de +1 a +10, todas reservadas al cargar.
- «Estado en el portal» es como lo ve el propio paciente en «Mis citas» justo después de cargar la semilla.
- Importes con el formato del formateador único de la interfaz y, entre paréntesis, en céntimos.

## Cifras

Las genera y verifica `DatosPortalDocumentadosTest` (`[002][FR-030]`): si la semilla cambia sin actualizar este documento, la suite falla.

<!-- portal:inicio -->
### Resumen por paciente

| Paciente | Email | Historia | Completadas | No asistidas | Canceladas | Importe completado | Futuro (reservadas) | Importe futuro |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Teresa Méndez | teresa.mendez@ejemplo.es | 29 | 19 | 4 | 6 | 760,00 € (76000 céntimos) | 3 | 125,00 € (12500 céntimos) |
| Laura Romero | laura.romero@ejemplo.es | 17 | 16 | 1 | 0 | 605,00 € (60500 céntimos) | 5 | 210,00 € (21000 céntimos) |
| Rocío Garrido | rocio.garrido@ejemplo.es | 26 | 23 | 1 | 2 | 940,00 € (94000 céntimos) | 8 | 355,00 € (35500 céntimos) |
| Gonzalo Cruz | gonzalo.cruz@ejemplo.es | 31 | 28 | 2 | 1 | 1120,00 € (112000 céntimos) | 2 | 70,00 € (7000 céntimos) |

### Primera cita futura y última cita pasada

| Paciente | Cita | Día laborable | Profesional | Horario | Servicio | Importe | Estado en el portal |
|---|---|---|---|---|---|---:|---|
| Teresa Méndez | Primera futura | +1 | María | 10:15–11:15 | Primera visita fisioterapia | 50,00 € (5000 céntimos) | Reservada |
| Teresa Méndez | Última pasada | −4 | Jorge | 12:00–12:45 | Sesión fisioterapia | 40,00 € (4000 céntimos) | Realizada |
| Laura Romero | Primera futura | +1 | Jorge | 11:45–12:30 | Sesión fisioterapia | 40,00 € (4000 céntimos) | Reservada |
| Laura Romero | Última pasada | −5 | Lucía | 19:15–19:45 | Consulta nutrición | 35,00 € (3500 céntimos) | Realizada |
| Rocío Garrido | Primera futura | +1 | Jorge | 13:00–14:00 | Primera visita fisioterapia | 50,00 € (5000 céntimos) | Reservada |
| Rocío Garrido | Última pasada | −1 | María | 14:15–15:00 | Sesión fisioterapia | 40,00 € (4000 céntimos) | Realizada |
| Gonzalo Cruz | Primera futura | +8 | Lucía | 09:30–10:00 | Consulta nutrición | 35,00 € (3500 céntimos) | Reservada |
| Gonzalo Cruz | Última pasada | −1 | Lucía | 09:30–10:00 | Consulta nutrición | 35,00 € (3500 céntimos) | Realizada |
<!-- portal:fin -->
