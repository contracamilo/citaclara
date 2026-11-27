package es.citaclara.acceso;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import es.citaclara.portal.acceso.InterceptorPortal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final InterceptorSesion interceptorSesion;
    private final InterceptorPortal interceptorPortal;

    public ConfiguracionWeb(InterceptorSesion interceptorSesion, InterceptorPortal interceptorPortal) {
        this.interceptorSesion = interceptorSesion;
        this.interceptorPortal = interceptorPortal;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // Defensa CSRF junto con SameSite=Strict y mismo origen (research D6): todo POST es JSON.
        registro.addInterceptor(new SoloJson()).addPathPatterns("/api/**").order(0);
        registro.addInterceptor(interceptorSesion)
                .addPathPatterns("/api/**")
                // /api/cancelacion/** es público: su autoridad es el enlace del recordatorio (003, research D6;
                // excepción acotada a FR-001 de la 001, petición 3 de specs/003-recordatorios-cita/plan.md).
                .excludePathPatterns("/api/clinicas/**", "/api/portal/**", "/api/cancelacion/**")
                .order(1);
        // Portal del paciente (002, FR-029, research R4): sesión propia; la del panel no lo abre ni al revés.
        registro.addInterceptor(interceptorPortal)
                .addPathPatterns("/api/portal/**")
                .excludePathPatterns("/api/portal/*/enlaces", "/api/portal/*/sesion")
                .order(1);
    }

    static final class SoloJson implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
            if ("POST".equals(peticion.getMethod())) {
                String tipo = peticion.getContentType();
                if (tipo == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(tipo))) {
                    throw new ErrorNegocio(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_ADMITIDO", Mensajes.TIPO_NO_ADMITIDO);
                }
            }
            return true;
        }
    }
}
