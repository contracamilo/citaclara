package es.citaclara.analitica;

import es.citaclara.cita.EstadoCita;

import java.time.Duration;
import java.time.Instant;

/** Proyección de lectura de una cita cerrada (data-model «CitaCerrada»). */
public record CitaCerrada(long profesionalId, long servicioId, Instant inicio, Instant fin,
                          int importeCentimos, EstadoCita estado) {

    /** Minutos reales de la cita (fin − inicio). */
    public long minutos() {
        return Duration.between(inicio, fin).toMinutes();
    }
}
