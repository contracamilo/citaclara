package es.citaclara.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Reloj mutable para pruebas deterministas de RN2, caducidad de sesión y acciones de estado
 * (research D4: toda la lógica de «ahora» usa un {@link Clock} inyectado).
 */
public class RelojControlable extends Clock {

    private volatile Instant ahora;

    public RelojControlable(Instant inicial) {
        this.ahora = inicial;
    }

    public void fijar(ZonedDateTime momento) {
        this.ahora = momento.toInstant();
    }

    public void fijar(Instant momento) {
        this.ahora = momento;
    }

    public void avanzar(Duration duracion) {
        this.ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
