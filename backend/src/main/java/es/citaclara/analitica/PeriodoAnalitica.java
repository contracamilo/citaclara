package es.citaclara.analitica;

import es.citaclara.comun.ZonaClinica;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Las 8 semanas naturales (lunes–domingo, hora de Madrid) completas anteriores a la semana en
 * curso (FR-007, research D2). Los límites son medianoches locales convertidas con
 * {@link ZonaClinica}, así que una semana con cambio de hora no desplaza, duplica ni pierde citas.
 */
public record PeriodoAnalitica(List<Semana> semanas) {

    public static final int NUMERO_SEMANAS = 8;

    public record Semana(LocalDate inicio, LocalDate fin) {

        public Instant desde() {
            return ZonaClinica.inicioDelDia(inicio);
        }

        /** Primer instante de la semana siguiente (excluido). */
        public Instant hasta() {
            return ZonaClinica.inicioDelDia(fin.plusDays(1));
        }
    }

    public PeriodoAnalitica {
        semanas = List.copyOf(semanas);
    }

    public static PeriodoAnalitica de(LocalDate hoy) {
        LocalDate lunesEnCurso = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Semana> semanas = new ArrayList<>(NUMERO_SEMANAS);
        for (int i = NUMERO_SEMANAS; i >= 1; i--) {
            LocalDate lunes = lunesEnCurso.minusWeeks(i);
            semanas.add(new Semana(lunes, lunes.plusDays(6)));
        }
        return new PeriodoAnalitica(semanas);
    }

    public Instant desde() {
        return semanas.getFirst().desde();
    }

    public Instant hasta() {
        return semanas.getLast().hasta();
    }

    public Semana ultima() {
        return semanas.getLast();
    }

    /** Índice (0 = la más antigua) de la semana de la fecha local del instante, si está en el periodo. */
    public OptionalInt indiceDe(Instant instante) {
        LocalDate fecha = ZonaClinica.fechaLocal(instante);
        for (int i = 0; i < semanas.size(); i++) {
            Semana s = semanas.get(i);
            if (!fecha.isBefore(s.inicio()) && !fecha.isAfter(s.fin())) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }
}
