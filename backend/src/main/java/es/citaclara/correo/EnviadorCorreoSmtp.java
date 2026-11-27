package es.citaclara.correo;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

/** Modo real (FR-021): el mismo mensaje del modo simulado, enviado por SMTP. */
public class EnviadorCorreoSmtp implements EnviadorCorreo {

    private final JavaMailSender remitenteSmtp;
    private final String remitente;

    public EnviadorCorreoSmtp(JavaMailSender remitenteSmtp, String remitente) {
        this.remitenteSmtp = remitenteSmtp;
        this.remitente = remitente;
    }

    @Override
    public void enviar(CorreoSaliente correo) {
        MimeMessage mensaje = MensajeMime.construir(remitente, correo);
        try {
            remitenteSmtp.send(mensaje);
        } catch (MailException e) {
            throw new CorreoNoEnviadoException("No se ha podido enviar el correo por SMTP", e);
        }
    }
}
