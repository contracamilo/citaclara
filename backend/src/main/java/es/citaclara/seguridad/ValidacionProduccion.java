package es.citaclara.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Con el perfil {@code produccion} el arranque falla si quedan valores pensados para desarrollo
 * (spec 005, H-05 y H-06): contraseña de base de datos por defecto o cookie de sesión sin {@code Secure}.
 */
@Component
@Profile("produccion")
public class ValidacionProduccion {

    static final String CLAVE_DESARROLLO = "citaclara";

    private final String claveBaseDatos;
    private final boolean cookieSegura;

    public ValidacionProduccion(@Value("${spring.datasource.password:}") String claveBaseDatos,
                                @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSegura) {
        this.claveBaseDatos = claveBaseDatos;
        this.cookieSegura = cookieSegura;
    }

    @PostConstruct
    void validar() {
        List<String> fallos = new ArrayList<>();
        if (claveBaseDatos == null || claveBaseDatos.isBlank() || claveBaseDatos.equals(CLAVE_DESARROLLO)) {
            fallos.add("DB_CLAVE debe definirse con una contraseña propia, distinta de la de desarrollo");
        }
        if (!cookieSegura) {
            fallos.add("COOKIE_SEGURA debe ser true (la cookie de sesión debe llevar Secure)");
        }
        if (!fallos.isEmpty()) {
            throw new IllegalStateException("Configuración no apta para producción: " + String.join("; ", fallos));
        }
    }
}
