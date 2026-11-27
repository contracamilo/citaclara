package es.citaclara.acceso;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Instant;

/**
 * Protege todo el panel (FR-001) y aplica la caducidad absoluta de 8 horas desde el acceso, con
 * independencia de la actividad (FR-002). Si la sesión ha caducado, la acción no se aplica.
 */
@Component
public class InterceptorSesion implements HandlerInterceptor {

    private final Clock reloj;

    public InterceptorSesion(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
        if ("DELETE".equals(peticion.getMethod()) && "/api/sesion".equals(peticion.getRequestURI())) {
            return true; // cerrar sesión siempre responde 204
        }
        HttpSession sesion = peticion.getSession(false);
        Long clinicaId = sesion == null ? null : (Long) sesion.getAttribute(SesionClinica.ATR_CLINICA);
        Instant accesoEn = sesion == null ? null : (Instant) sesion.getAttribute(SesionClinica.ATR_ACCESO);
        if (clinicaId == null || accesoEn == null) {
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, "SIN_SESION", Mensajes.SIN_SESION);
        }
        Instant caduca = SesionClinica.caducidad(accesoEn);
        if (!reloj.instant().isBefore(caduca)) {
            sesion.invalidate();
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, "SESION_CADUCADA", Mensajes.SESION_CADUCADA);
        }
        peticion.setAttribute(SesionClinica.CLINICA, clinicaId);
        peticion.setAttribute(SesionClinica.CADUCA_A, caduca);
        return true;
    }
}
