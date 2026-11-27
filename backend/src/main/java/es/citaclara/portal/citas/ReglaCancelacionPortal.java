package es.citaclara.portal.citas;

import java.time.Duration;
import java.time.Instant;

/**
 * Plazo de cancelación del portal (FR-012, research R6): se puede cancelar mientras falten MÁS de 24
 * horas reales para el inicio. Sobre instantes, así que los cambios de hora y los fines de semana no
 * alteran el plazo.
 */
public final class ReglaCancelacionPortal {

    public static final Duration PLAZO = Duration.ofHours(24);

    private ReglaCancelacionPortal() {
    }

    public static boolean dentroDePlazo(Instant inicio, Instant ahora) {
        return Duration.between(ahora, inicio).compareTo(PLAZO) > 0;
    }
}
