package es.citaclara.acceso;

import es.citaclara.clinica.Clinica;
import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Acceso con la clave compartida de la clínica (FR-001). Sin límite de intentos y con la misma
 * respuesta para clave incorrecta o clínica inexistente (FR-004, deuda consciente).
 */
@Service
public class ServicioAcceso {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();
    /** Hash de relleno para que una clínica inexistente cueste lo mismo que una clave incorrecta. */
    private static final String HASH_RELLENO = BCRYPT.encode("clave-de-relleno");

    private final RepositorioClinica clinicas;
    private final Clock reloj;

    public ServicioAcceso(RepositorioClinica clinicas, Clock reloj) {
        this.clinicas = clinicas;
        this.reloj = reloj;
    }

    public static String hash(String clave) {
        return BCRYPT.encode(clave);
    }

    public static boolean coincide(String clave, String hash) {
        return BCRYPT.matches(clave, hash);
    }

    public void iniciarSesion(String identificador, String clave, HttpServletRequest peticion) {
        Optional<Clinica> clinica = clinicas.findByIdentificador(identificador);
        boolean correcta = BCRYPT.matches(clave, clinica.map(Clinica::getClaveHash).orElse(HASH_RELLENO));
        if (clinica.isEmpty() || !correcta) {
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, "CLAVE_INCORRECTA", Mensajes.CLAVE_INCORRECTA);
        }
        HttpSession anterior = peticion.getSession(false);
        if (anterior != null) {
            anterior.invalidate(); // evita la fijación de sesión
        }
        HttpSession sesion = peticion.getSession(true);
        sesion.setAttribute(SesionClinica.ATR_CLINICA, clinica.get().getId());
        sesion.setAttribute(SesionClinica.ATR_ACCESO, reloj.instant());
    }

    public void cerrarSesion(HttpServletRequest peticion) {
        HttpSession sesion = peticion.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }
}
