package es.citaclara.analitica;

import es.citaclara.analitica.AnaliticaRespuesta.Ingresos;
import es.citaclara.analitica.AnaliticaRespuesta.OcupacionProfesional;
import es.citaclara.analitica.AnaliticaRespuesta.ProfesionalAnalitica;
import es.citaclara.analitica.AnaliticaRespuesta.SemanaEvolucion;
import es.citaclara.analitica.AnaliticaRespuesta.ServicioIngresos;
import es.citaclara.analitica.AnaliticaRespuesta.UltimaSemana;
import es.citaclara.cita.EstadoCita;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Cálculo puro de la analítica (research D3): sin base de datos, sin reloj, sin efectos. Todas las
 * cifras salen de recuentos y minutos enteros; los porcentajes se redondean una sola vez en
 * {@link Proporcion}.
 *
 * <ul>
 *   <li>Tasa de no asistencia (FR-008): no asistidas ÷ (completadas + no asistidas + canceladas).</li>
 *   <li>Ocupación (FR-009): minutos de citas completadas ÷ minutos del horario de atención.</li>
 * </ul>
 */
public final class CalculadoraAnalitica {

    public record ProfesionalDato(long id, String nombre, String especialidadNombre) {
    }

    public record ServicioDato(long id, String nombre) {
    }

    /**
     * @param minutosHorarioSemana suma semanal de {@code cierre − apertura} del horario de atención
     *                             (igual para todos los profesionales y para todas las semanas)
     */
    public record DatosClinica(List<ProfesionalDato> profesionales, List<ServicioDato> servicios,
                               long minutosHorarioSemana) {
    }

    private CalculadoraAnalitica() {
    }

    private static boolean cuenta(CitaCerrada c) {
        return c.estado() == EstadoCita.COMPLETADA || c.estado() == EstadoCita.NO_ASISTIDA
                || c.estado() == EstadoCita.CANCELADA;
    }

    /** Acumulador de recuentos de un grupo de citas cerradas. */
    private static final class Cuenta {
        int completadas;
        int noAsistidas;
        int canceladas;
        long minutosCompletados;
        long ingresosCentimos;

        void sumar(CitaCerrada c) {
            switch (c.estado()) {
                case COMPLETADA -> {
                    completadas++;
                    minutosCompletados += c.minutos();
                    ingresosCentimos += c.importeCentimos();
                }
                case NO_ASISTIDA -> noAsistidas++;
                case CANCELADA -> canceladas++;
                case RESERVADA -> { /* no cerrada: no cuenta */ }
            }
        }

        long cerradas() {
            return (long) completadas + noAsistidas + canceladas;
        }

        Proporcion tasaNoAsistencia() {
            return Proporcion.de(noAsistidas, cerradas());
        }
    }

    /** Última semana completa por profesional (US1, FR-004, FR-005). */
    public static UltimaSemana ultimaSemana(PeriodoAnalitica periodo, List<CitaCerrada> citas, DatosClinica clinica) {
        int ultima = periodo.semanas().size() - 1;
        List<ProfesionalAnalitica> filas = new ArrayList<>();
        for (ProfesionalDato p : clinica.profesionales()) {
            Cuenta cuenta = new Cuenta();
            for (CitaCerrada c : citas) {
                if (c.profesionalId() == p.id() && cuenta(c) && periodo.indiceDe(c.inicio()).orElse(-1) == ultima) {
                    cuenta.sumar(c);
                }
            }
            filas.add(new ProfesionalAnalitica(p.id(), p.nombre(), p.especialidadNombre(),
                    cuenta.tasaNoAsistencia(), Proporcion.de(cuenta.minutosCompletados, clinica.minutosHorarioSemana())));
        }
        return new UltimaSemana(periodo.ultima(), List.copyOf(filas));
    }

    /** Ingresos y no obtenidos por servicio en las 8 semanas (US2, FR-006, FR-016). */
    public static Ingresos ingresos(PeriodoAnalitica periodo, List<CitaCerrada> citas, DatosClinica clinica) {
        List<ServicioIngresos> filas = new ArrayList<>();
        long total = 0;
        long totalNoObtenido = 0;
        for (ServicioDato s : clinica.servicios()) {
            int completadas = 0;
            int noAsistidas = 0;
            long importe = 0;
            long importeNoObtenido = 0;
            for (CitaCerrada c : citas) {
                if (c.servicioId() != s.id() || periodo.indiceDe(c.inicio()).isEmpty()) {
                    continue;
                }
                if (c.estado() == EstadoCita.COMPLETADA) {
                    completadas++;
                    importe += c.importeCentimos();
                } else if (c.estado() == EstadoCita.NO_ASISTIDA) {
                    noAsistidas++;
                    importeNoObtenido += c.importeCentimos();
                }
            }
            filas.add(new ServicioIngresos(s.id(), s.nombre(), completadas, importe, noAsistidas, importeNoObtenido));
            total += importe;
            totalNoObtenido += importeNoObtenido;
        }
        return new Ingresos(List.copyOf(filas), total, totalNoObtenido);
    }

    /** Las 8 semanas, de la más antigua a la más reciente, sin omitir ninguna (US3, FR-007). */
    public static List<SemanaEvolucion> evolucion(PeriodoAnalitica periodo, List<CitaCerrada> citas, DatosClinica clinica) {
        int n = periodo.semanas().size();
        List<Cuenta> clinicaPorSemana = new ArrayList<>(n);
        List<List<Cuenta>> profesionalPorSemana = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            clinicaPorSemana.add(new Cuenta());
            List<Cuenta> porProfesional = new ArrayList<>();
            clinica.profesionales().forEach(p -> porProfesional.add(new Cuenta()));
            profesionalPorSemana.add(porProfesional);
        }
        for (CitaCerrada c : citas) {
            if (!cuenta(c)) {
                continue;
            }
            OptionalInt semana = periodo.indiceDe(c.inicio());
            if (semana.isEmpty()) {
                continue;
            }
            clinicaPorSemana.get(semana.getAsInt()).sumar(c);
            for (int p = 0; p < clinica.profesionales().size(); p++) {
                if (clinica.profesionales().get(p).id() == c.profesionalId()) {
                    profesionalPorSemana.get(semana.getAsInt()).get(p).sumar(c);
                }
            }
        }
        List<SemanaEvolucion> evolucion = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Cuenta semana = clinicaPorSemana.get(i);
            List<OcupacionProfesional> ocupacion = new ArrayList<>();
            for (int p = 0; p < clinica.profesionales().size(); p++) {
                ocupacion.add(new OcupacionProfesional(clinica.profesionales().get(p).id(),
                        Proporcion.de(profesionalPorSemana.get(i).get(p).minutosCompletados, clinica.minutosHorarioSemana())));
            }
            evolucion.add(new SemanaEvolucion(periodo.semanas().get(i), semana.completadas, semana.noAsistidas,
                    semana.canceladas, semana.tasaNoAsistencia(), semana.ingresosCentimos, List.copyOf(ocupacion)));
        }
        return List.copyOf(evolucion);
    }
}
