package es.citaclara.correo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeleccionCorreoTest {

    private final ConfiguracionCorreo configuracion = new ConfiguracionCorreo();

    @Test
    @DisplayName("[003][FR-021] sin servidor SMTP configurado el correo es simulado (ficheros .eml)")
    void sinSmtpEsSimulado() {
        assertThat(configuracion.enviadorCorreo("", 587, "", "", "no-responder@citaclara.es", "datos/salida-correo"))
                .isInstanceOf(EnviadorCorreoFichero.class);
    }

    @Test
    @DisplayName("[003][FR-021] con servidor SMTP configurado el correo se envía por SMTP")
    void conSmtpEsReal() {
        assertThat(configuracion.enviadorCorreo("smtp.ejemplo.es", 587, "u", "c", "no-responder@citaclara.es", "datos/salida-correo"))
                .isInstanceOf(EnviadorCorreoSmtp.class);
    }
}
