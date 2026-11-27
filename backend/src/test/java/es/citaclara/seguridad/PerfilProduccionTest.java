package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 005, FR-006: el perfil de producción no arranca con valores de desarrollo. */
class PerfilProduccionTest {

    @Test
    @DisplayName("[005][FR-006] con la contraseña de desarrollo, sin contraseña o con la cookie sin Secure el arranque falla con un mensaje claro")
    void valoresDeDesarrollo() {
        assertThatThrownBy(() -> new ValidacionProduccion("citaclara", true).validar())
                .hasMessageContaining("DB_CLAVE");
        assertThatThrownBy(() -> new ValidacionProduccion("", true).validar()).hasMessageContaining("DB_CLAVE");
        assertThatThrownBy(() -> new ValidacionProduccion("otra-clave-larga", false).validar())
                .hasMessageContaining("COOKIE_SEGURA");
        assertThatThrownBy(() -> new ValidacionProduccion("citaclara", false).validar())
                .hasMessageContaining("DB_CLAVE").hasMessageContaining("COOKIE_SEGURA");
    }

    @Test
    @DisplayName("[005][FR-006] con contraseña propia y cookie segura arranca")
    void valoresCorrectos() {
        assertThatCode(() -> new ValidacionProduccion("una-clave-propia-larga", true).validar()).doesNotThrowAnyException();
        assertThat(ValidacionProduccion.CLAVE_DESARROLLO).isEqualTo("citaclara");
    }
}
