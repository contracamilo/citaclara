package es.citaclara.portal.correo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envío SMTP del enlace en texto plano, con el texto exacto de contracts/ui.md §4 (FR-007). */
@Component
public class CorreoPortalSmtp implements CorreoPortal {

    private final JavaMailSender correo;
    private final String remitente;

    public CorreoPortalSmtp(JavaMailSender correo, @Value("${citaclara.email-remitente}") String remitente) {
        this.correo = correo;
        this.remitente = remitente;
    }

    @Override
    public void enviarEnlace(String email, String nombreClinica, String url) {
        correo.send(mensaje(remitente, email, nombreClinica, url));
    }

    static SimpleMailMessage mensaje(String remitente, String email, String nombreClinica, String url) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(nombreClinica.replace("\"", "") + " <" + remitente + ">");
        mensaje.setTo(email);
        mensaje.setSubject("Tu enlace para entrar en " + nombreClinica);
        mensaje.setText("""
                Hola:

                Has pedido entrar en tus citas de %1$s. Abre este enlace para verlas:

                %2$s

                El enlace caduca en 10 minutos y solo se puede usar una vez.

                Si no has pedido este enlace, puedes ignorar este mensaje: nadie podrá entrar sin él.

                %1$s
                """.formatted(nombreClinica, url));
        return mensaje;
    }
}
