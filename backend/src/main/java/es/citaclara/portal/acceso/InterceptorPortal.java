package es.citaclara.portal.acceso;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Instant;

/**
 * Protege {@code /api/portal/**} con la sesión del paciente (FR-005, FR-029, research R4): caducidad
 * absoluta de 30 minutos y {@code Cache-Control: no-store} en toda respuesta (US1-AC6).
 */
@Component
public class InterceptorPortal implements HandlerInterceptor {

    private final Clock reloj;

    public InterceptorPortal(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if ("DELETE".equals(peticion.getMethod()) && "/api/portal/sesion".equals(peticion.getRequestURI())) {
            return true; // salir siempre responde 204
        }
        HttpSession sesion = peticion.getSession(false);
        Long clinicaId = sesion == null ? null : (Long) sesion.getAttribute(SesionPortal.ATR_CLINICA);
        String email = sesion == null ? null : (String) sesion.getAttribute(SesionPortal.ATR_EMAIL);
        Instant accesoEn = sesion == null ? null : (Instant) sesion.getAttribute(SesionPortal.ATR_ACCESO);
        if (clinicaId == null || email == null || accesoEn == null) {
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, "SIN_SESION_PORTAL", Mensajes.SIN_SESION_PORTAL);
        }
        if (!reloj.instant().isBefore(SesionPortal.caducidad(accesoEn))) {
            sesion.invalidate();
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, "SESION_PORTAL_CADUCADA", Mensajes.SESION_PORTAL_CADUCADA);
        }
        peticion.setAttribute(SesionPortal.CLINICA, clinicaId);
        peticion.setAttribute(SesionPortal.EMAIL, email);
        return true;
    }
}
