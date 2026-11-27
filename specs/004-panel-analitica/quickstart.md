# Guía de validación: Panel de analítica

**Plan**: [plan.md](plan.md) | **Contratos**: [contracts/api.yaml](contracts/api.yaml), [contracts/ui.md](contracts/ui.md) | **Modelo**: [data-model.md](data-model.md)

Cómo comprobar de extremo a extremo que la feature cumple la spec. Los comandos de entorno son los de `README.md` y `CLAUDE.md`.

## Requisitos previos

- Docker en marcha (Testcontainers y entorno de demostración), JDK 25 y Node 24.
- Rama `004-panel-analitica` con la 001 ya integrada.

## 1. Suite automática

```bash
cd backend && ./mvnw verify            # incluye AnaliticaSemillaTest, AnaliticaApiTest y CalculadoraAnaliticaTest
cd ../frontend && npm ci && npm test   # tests de componentes de la analítica
./scripts/trazabilidad.sh specs/004-panel-analitica   # debe salir vacía
```

**Esperado**: todo en verde. `AnaliticaSemillaTest` reproduce con la semilla (reloj fijado en lunes) las cifras de «Cifras de la semilla» de la spec, por ejemplo:

| Cifra | Valor esperado |
|---|---|
| Tasa de no asistencia, última semana | María 2,6 % (1 de 39) · Jorge 12,2 % (5 de 41) · Lucía 7,3 % (4 de 55) |
| Ocupación, última semana | María 54,5 % · Jorge 48,2 % · Lucía 48,6 % |
| Ingresos por servicio (8 semanas) | 17.040,00 € · 4850,00 € · 9695,00 € · 3285,00 € = **34.870,00 €** |
| Ingresos no obtenidos (8 semanas) | 2240,00 € · 550,00 € · 945,00 € · 540,00 € = **4275,00 €** |
| Tasa de no asistencia por semana | 8,1 · 7,4 · 13,6 · 9,6 · 7,5 · 14,3 · 11,9 · 7,4 % |

## 2. Prueba manual con el entorno de demostración

```bash
docker compose up -d --build          # PUERTO_WEB=8081 si el 8080 está ocupado
docker compose run --rm semilla       # carga Clínica Eleva (clave eleva-demo-2026)
```

1. Abre `http://localhost:8080/eleva`, entra con `eleva-demo-2026` y pulsa «Analítica» (o abre `/eleva/analitica`).
2. **US1**: aparecen María, Jorge y Lucía con «Ocupación» y «Tasa de no asistencia» de la última semana, con el valor y «x de y citas» a la vista. Jorge tiene la tasa más alta.
3. **US2**: los cuatro servicios con su importe y el «Total»; debajo, «Ingresos no obtenidos por no asistencias» con su total. Los totales cuadran con la suma de las filas.
4. **US3**: ocho semanas con fechas `dd/mm/aaaa`, ingresos, no asistencia y ocupación por profesional.
5. **US4**: «Cerrar sesión» y abrir `/eleva/analitica` lleva al acceso; tras entrar de nuevo se ve la analítica.
6. Reduce la ventana a 360 px: no hay desplazamiento horizontal y todo es legible.

**Las cifras exactas de la spec solo se reproducen si la semilla se carga un lunes.** Cargada otro día, la semana más antigua tiene menos días con datos y las cifras difieren (spec, Casos límite); los totales siguen cuadrando entre sí.

## 3. Comprobación de solo lectura (SC-005)

Con el entorno levantado, recuento antes y después de usar la analítica:

```bash
docker compose exec db psql -U citaclara -d citaclara -c \
  "select (select count(*) from cita) c, (select coalesce(sum(importe_centimos),0) from cita) i, (select count(*) from paciente) p;"
# usar la página analítica y recargarla varias veces
# repetir el comando: los tres valores deben ser idénticos
```

## 4. Extremo a extremo y accesibilidad

```bash
cd frontend && URL_BASE=http://localhost:8080 npm run e2e
```

**Esperado**: `analitica.spec.ts` en verde: 360 px sin desbordes, axe sin infracciones serias, objetivos táctiles ≥ 44 px, valores coherentes con el API y carga < 3 s (SC-006).
