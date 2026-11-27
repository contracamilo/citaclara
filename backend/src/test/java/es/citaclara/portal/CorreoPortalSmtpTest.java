package es.citaclara.portal;

import es.citaclara.portal.correo.CorreoPortalSmtp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorreoPortalSmtpTest {

    @Test
    @DisplayName("[002][FR-007] el email del enlace indica la clínica, la caducidad y que se puede ignorar")
    void textoDelCorreo() {
        JavaMailSender envio = mock(JavaMailSender.class);
        String url = "http://localhost:8080/eleva/mis-citas#enlace=abc";

        new CorreoPortalSmtp(envio, "citas@citaclara.local")
                .enviarEnlace("teresa.mendez@ejemplo.es", "Clínica Eleva", url);

        ArgumentCaptor<SimpleMailMessage> captura = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(envio).send(captura.capture());
        SimpleMailMessage correo = captura.getValue();
        assertThat(correo.getTo()).containsExactly("teresa.mendez@ejemplo.es");
        assertThat(correo.getFrom()).isEqualTo("Clínica Eleva <citas@citaclara.local>");
        assertThat(correo.getSubject()).isEqualTo("Tu enlace para entrar en Clínica Eleva");
        assertThat(correo.getText())
                .contains("Has pedido entrar en tus citas de Clínica Eleva")
                .contains(url)
                .contains("El enlace caduca en 10 minutos y solo se puede usar una vez.")
                .contains("Si no has pedido este enlace, puedes ignorar este mensaje");
    }
}
