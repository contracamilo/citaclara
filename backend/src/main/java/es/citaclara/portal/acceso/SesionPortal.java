package es.citaclara.portal.acceso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Duration;
import java.time.Instant;

/**
 * Sesión del paciente en el portal (FR-005, research R4): clínica + email normalizado + instante de
 * entrada, con caducidad absoluta de 30 minutos. Atributos propios, distintos de los del panel
 * (FR-029); abrirla invalida cualquier sesión previa del navegador, también la del panel.
 */
public final class SesionPortal {

    public static final Duration DURACION = Duration.ofMinutes(30);

    static final String ATR_CLINICA = "citaclara.portal.clinicaId";
    static final String ATR_EMAIL = "citaclara.portal.email";
    static final String ATR_ACCESO = "citaclara.portal.accesoEn";

    /** Atributos de petición ya validados por {@link InterceptorPortal}. */
    public static final String CLINICA = "portalClinicaId";
    public static final String EMAIL = "portalEmail";

    private SesionPortal() {
    }

    static void abrir(HttpServletRequest peticion, Long clinicaId, String email, Instant ahora) {
        HttpSession anterior = peticion.getSession(false);
        if (anterior != null) {
            anterior.invalidate(); // fijación de sesión y exclusión mutua con el panel
        }
        HttpSession sesion = peticion.getSession(true);
        sesion.setAttribute(ATR_CLINICA, clinicaId);
        sesion.setAttribute(ATR_EMAIL, email);
        sesion.setAttribute(ATR_ACCESO, ahora);
    }

    static void cerrar(HttpServletRequest peticion) {
        HttpSession sesion = peticion.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }

    static Instant caducidad(Instant accesoEn) {
        return accesoEn.plus(DURACION);
    }
}
