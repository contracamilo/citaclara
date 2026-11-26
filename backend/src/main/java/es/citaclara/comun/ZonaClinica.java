package es.citaclara.comun;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Hora peninsular española (FR-026, research D4). Es una constante del producto, no un dato de la
 * clínica. Se guarda en instantes (timestamptz) y el API intercambia fecha y hora locales.
 */
public final class ZonaClinica {

    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ZonaClinica() {
    }

    /**
     * Instante de una fecha y hora local de Madrid. Una hora inexistente (salto de marzo) o ambigua
     * (hora repetida de octubre) se rechaza como {@code HORA_NO_VALIDA}.
     */
    public static Instant aInstante(LocalDate fecha, LocalTime hora) {
        LocalDateTime local = LocalDateTime.of(fecha, hora);
        List<ZoneOffset> desfases = ZONA.getRules().getValidOffsets(local);
        if (desfases.size() != 1) {
            throw new ErrorNegocio(HttpStatus.BAD_REQUEST, "HORA_NO_VALIDA", Mensajes.HORA_INEXISTENTE);
        }
        return local.toInstant(desfases.getFirst());
    }

    /** Instante en que empieza un día local (la medianoche siempre existe en Madrid). */
    public static Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA).toInstant();
    }

    public static LocalDate fechaLocal(Instant instante) {
        return instante.atZone(ZONA).toLocalDate();
    }

    public static LocalTime horaLocal(Instant instante) {
        return instante.atZone(ZONA).toLocalTime();
    }

    public static String formatoHora(Instant instante) {
        return HORA.format(horaLocal(instante));
    }

    public static String formatoHora(LocalTime hora) {
        return HORA.format(hora);
    }

    /** «Hoy» lo decide el servidor (fecha de Madrid según su reloj), no el dispositivo. */
    public static LocalDate hoy(Clock reloj) {
        return fechaLocal(reloj.instant());
    }
}
