package es.citaclara.acceso;

import java.time.Duration;
import java.time.Instant;

/**
 * Sesión del panel (data-model «Sesión»): {@code clinicaId} + {@code accesoEn}. Válida mientras
 * {@code ahora < accesoEn + 8 h} (FR-002, research D6).
 */
public final class SesionClinica {

    public static final Duration DURACION = Duration.ofHours(8);

    /** Atributos de la HttpSession. */
    static final String ATR_CLINICA = "citaclara.clinicaId";
    static final String ATR_ACCESO = "citaclara.accesoEn";

    /** Atributo de petición con el id de la clínica ya validado; los controladores lo reciben con @RequestAttribute. */
    public static final String CLINICA = "clinicaId";
    /** Atributo de petición con el instante de caducidad de la sesión. */
    public static final String CADUCA_A = "sesionCaducaA";

    private SesionClinica() {
    }

    static Instant caducidad(Instant accesoEn) {
        return accesoEn.plus(DURACION);
    }
}
