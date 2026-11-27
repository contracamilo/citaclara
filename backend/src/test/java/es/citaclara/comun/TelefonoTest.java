package es.citaclara.comun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TelefonoTest {

    @ParameterizedTest
    @ValueSource(strings = {"612 345 678", "612-345-678", "+34 612.345.678", "612345678", "+34612345678"})
    @DisplayName("[001][FR-019] normaliza los formatos habituales de un móvil español")
    void normalizaMovilEspanol(String entrada) {
        assertThat(Telefono.normalizar(entrada)).contains("+34612345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"712345678", "812345678", "912 34 56 78"})
    @DisplayName("[001][FR-019] acepta números españoles de 9 cifras que empiezan por 7, 8 o 9")
    void aceptaOtrosPrefijosEspanoles(String entrada) {
        assertThat(Telefono.normalizar(entrada)).hasValueSatisfying(t -> assertThat(t).matches("\\+34[789]\\d{8}"));
    }

    @Test
    @DisplayName("[001][FR-019] acepta números internacionales con + y de 8 a 15 cifras")
    void aceptaInternacionales() {
        assertThat(Telefono.normalizar("+33 1 23 45 67 89")).contains("+33123456789");
        assertThat(Telefono.normalizar("+12345678")).contains("+12345678");
        assertThat(Telefono.normalizar("+123456789012345")).contains("+123456789012345");
    }

    @ParameterizedTest
    @ValueSource(strings = {"512345678", "61234567", "6123456789", "61234567a", "", "   ", "+1234567",
            "+1234567890123456", "+34 512 345 678", "teléfono"})
    @DisplayName("[001][FR-019] rechaza teléfonos con formato no válido")
    void rechazaInvalidos(String entrada) {
        assertThat(Telefono.normalizar(entrada)).isEmpty();
    }

    @Test
    @DisplayName("[001][FR-019] un teléfono nulo no es válido")
    void nuloNoValido() {
        assertThat(Telefono.normalizar(null)).isEmpty();
    }
}
