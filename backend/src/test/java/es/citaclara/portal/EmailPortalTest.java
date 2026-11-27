package es.citaclara.portal;

import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.portal.acceso.EmailPortal;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailPortalTest {

    private final EmailPortal email = new EmailPortal(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    @DisplayName("[002][FR-002] normaliza el email sin distinguir mayúsculas ni espacios")
    void normaliza() {
        assertThat(email.validar("  Teresa.Mendez@Ejemplo.es ")).isEqualTo("teresa.mendez@ejemplo.es");
    }

    @Test
    @DisplayName("[002][FR-002] rechaza un email mal formado con EMAIL_NO_VALIDO")
    void rechazaMalFormado() {
        for (String malo : new String[] {"", "   ", "teresa", "teresa@@ejemplo.es"}) {
            assertThatThrownBy(() -> email.validar(malo))
                    .isInstanceOf(ErrorNegocio.class)
                    .extracting(e -> ((ErrorNegocio) e).codigo())
                    .isEqualTo("EMAIL_NO_VALIDO");
        }
    }
}
