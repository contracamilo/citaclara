# Datos de demostración: Clínica Eleva

**Feature**: `001-nucleo-agenda` | **Spec**: [spec.md](spec.md) (FR-029–FR-033) | **Decisión**: [research.md D13](research.md)

Cifras exactas que produce la semilla determinista. Cualquier spec, ejemplo o demostración que cite números de los datos de demostración debe tomarlos de aquí (constitución, Principio V).

## Cómo cargarlos

```bash
docker compose up -d --build
docker compose run --rm semilla        # repetible: borra y recrea Clínica Eleva
```

Panel: `http://localhost:8080/eleva` · Clave: **`eleva-demo-2026`**

## Cómo leer las cifras

- El **día de referencia es el día de carga**, que no recibe citas.
- Las fechas se citan por **posición relativa en días laborables** (lunes a viernes, sin festivos): «día laborable −1» es el último día de la historia y «día laborable +1» el primero del futuro. Nunca por fecha absoluta ni por semana (FR-033).
- Dos cargas, hechas el mismo día o en días de la semana distintos, producen exactamente las mismas citas por posición; solo se desplazan las fechas absolutas.
- Importes con el formato del formateador único de la interfaz (CLDR `es-ES`: separador de miles a partir de 5 cifras) y, entre paréntesis, en céntimos.

## Catálogo

| Elemento | Valor |
|---|---|
| Clínica | Clínica Eleva (`eleva`), lunes a viernes de 09:00 a 20:00 |
| Profesionales | María (Fisioterapia), Jorge (Fisioterapia), Lucía (Nutrición) |
| Servicios | Sesión fisioterapia · 45 min · 40,00 €; Primera visita fisioterapia · 60 min · 50,00 €; Consulta nutrición · 30 min · 35,00 €; Primera visita nutrición · 45 min · 45,00 € |
| Pacientes | 40 fichas ficticias (teléfonos `+34 600 0…`, emails `@ejemplo.es`) |

## Recuentos

Los genera y verifica `DatosDemostracionDocumentadosTest` (`[001][FR-033]`): si la semilla cambia sin actualizar este documento, la suite falla.

<!-- recuentos:inicio -->
### Totales

| Recuento | Citas | Importe |
|---|---:|---:|
| Historia (días laborables −40 a −1) | 1064 | 42.535,00 € (4253500 céntimos) |
| Futuro (días laborables +1 a +10) | 180 | 7265,00 € (726500 céntimos) |
| Total | 1244 | 49.800,00 € (4980000 céntimos) |

### Historia por estado

| Estado | Citas | % de la historia | Importe |
|---|---:|---:|---:|
| Completada | 873 | 82,0 % | 34.870,00 € (3487000 céntimos) |
| No asistida | 106 | 10,0 % | 4275,00 € (427500 céntimos) |
| Cancelada | 85 | 8,0 % | 3390,00 € (339000 céntimos) |

### Por profesional

| Profesional | Historia | Completadas | No asistidas | Canceladas | Futuro (reservadas) | Importe completado |
|---|---:|---:|---:|---:|---:|---:|
| María | 321 | 269 | 30 | 22 | 56 | 11.250,00 € (1125000 céntimos) |
| Jorge | 320 | 254 | 37 | 29 | 59 | 10.640,00 € (1064000 céntimos) |
| Lucía | 423 | 350 | 39 | 34 | 65 | 12.980,00 € (1298000 céntimos) |

### Citas de ejemplo (primera cita del día de cada profesional)

| Día laborable | Profesional | Horario | Paciente | Servicio | Importe | Estado |
|---|---|---|---|---|---:|---|
| −3 | María | 10:00–10:45 | Rocío Garrido | Sesión fisioterapia | 40,00 € (4000 céntimos) | Completada |
| −1 | Lucía | 09:30–10:00 | Gonzalo Cruz | Consulta nutrición | 35,00 € (3500 céntimos) | Completada |
| +1 | María | 10:15–11:15 | Teresa Méndez | Primera visita fisioterapia | 50,00 € (5000 céntimos) | Reservada |
| +2 | Jorge | 09:45–10:30 | Laura Romero | Sesión fisioterapia | 40,00 € (4000 céntimos) | Reservada |
<!-- recuentos:fin -->
