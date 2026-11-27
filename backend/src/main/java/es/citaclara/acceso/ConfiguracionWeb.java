package es.citaclara.acceso;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
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

    public ConfiguracionWeb(InterceptorSesion interceptorSesion) {
        this.interceptorSesion = interceptorSesion;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // Defensa CSRF junto con SameSite=Strict y mismo origen (research D6): todo POST es JSON.
        registro.addInterceptor(new SoloJson()).addPathPatterns("/api/**").order(0);
        registro.addInterceptor(interceptorSesion)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/clinicas/**")
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
