package es.citaclara.portal.acceso;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Email con el que el paciente pide su enlace (FR-002, research R5). Se compara normalizado
 * ({@code lower(btrim(email))}) y se valida con la misma regla que la ficha de 001 (D11: {@code @Email}).
 */
@Component
public class EmailPortal {

    private final Validator validador;

    public EmailPortal(Validator validador) {
        this.validador = validador;
    }

    public static String normalizar(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Devuelve el email normalizado o 400 {@code EMAIL_NO_VALIDO} (no depende de que exista, FR-003). */
    public String validar(String email) {
        if (email == null || email.isBlank()) {
            throw noValido();
        }
        String normalizado = normalizar(email);
        if (!validador.validateValue(Formato.class, "email", normalizado).isEmpty()) {
            throw noValido();
        }
        return normalizado;
    }

    private static ErrorNegocio noValido() {
        return new ErrorNegocio(HttpStatus.BAD_REQUEST, "EMAIL_NO_VALIDO", Mensajes.EMAIL_NO_VALIDO);
    }

    /** Misma regla de formato que {@code NuevoPaciente.email} (001, FR-019). */
    record Formato(@Email @Size(max = 254) String email) {
    }
}
