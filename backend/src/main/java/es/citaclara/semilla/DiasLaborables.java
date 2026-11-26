package es.citaclara.semilla;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Posición relativa en días laborables respecto al día de carga (FR-029, FR-033, research D13):
 * «día laborable −1» es el último día de la historia y «+1» el primero del futuro. Días laborables =
 * lunes a viernes, sin descontar festivos. El día de carga nunca tiene posición.
 */
public final class DiasLaborables {

    public static final int HISTORIA = 40;
    public static final int FUTURO = 10;

    /** −40 … −1 y +1 … +10, en orden cronológico. */
    public static final List<Integer> POSICIONES = IntStream.rangeClosed(-HISTORIA, FUTURO)
            .filter(p -> p != 0).boxed().toList();

    private DiasLaborables() {
    }

    public static LocalDate fecha(LocalDate carga, int posicion) {
        if (posicion == 0) {
            throw new IllegalArgumentException("El día de carga no tiene posición laborable");
        }
        int paso = posicion > 0 ? 1 : -1;
        LocalDate fecha = carga;
        for (int restantes = Math.abs(posicion); restantes > 0; ) {
            fecha = fecha.plusDays(paso);
            if (esLaborable(fecha)) {
                restantes--;
            }
        }
        return fecha;
    }

    /** Posición de una fecha laborable respecto al día de carga (inversa de {@link #fecha}). */
    public static int posicion(LocalDate carga, LocalDate fecha) {
        if (!esLaborable(fecha) || fecha.equals(carga)) {
            throw new IllegalArgumentException("La fecha no es un día laborable distinto del de carga: " + fecha);
        }
        int paso = fecha.isAfter(carga) ? 1 : -1;
        int posicion = 0;
        for (LocalDate d = carga; !d.equals(fecha); ) {
            d = d.plusDays(paso);
            if (esLaborable(d)) {
                posicion += paso;
            }
        }
        return posicion;
    }

    public static boolean esLaborable(LocalDate fecha) {
        return fecha.getDayOfWeek() != DayOfWeek.SATURDAY && fecha.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
