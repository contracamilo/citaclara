package es.citaclara.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

/** Construcción común del mensaje: UTF-8, asunto RFC 2047 y cabeceras saneadas (research D9). */
final class MensajeMime {

    private static final Session SESION = Session.getInstance(new Properties());

    private MensajeMime() {
    }

    static MimeMessage construir(String remitente, CorreoSaliente correo) {
        try {
            MimeMessage mensaje = new MimeMessage(SESION);
            mensaje.setFrom(new InternetAddress(remitente, true));
            // Dirección estricta (sin listas ni comentarios) y nombre visible sin saltos de línea.
            InternetAddress destino = new InternetAddress(correo.destinatario().trim(), true);
            if (destino.getAddress().contains(",")) {
                throw new IllegalArgumentException("Dirección de correo no válida");
            }
            destino.setPersonal(sanear(correo.nombreDestinatario()), "UTF-8");
            mensaje.setRecipient(Message.RecipientType.TO, destino);
            mensaje.setSubject(sanear(correo.asunto()), "UTF-8");
            mensaje.setText(correo.cuerpo(), "UTF-8");
            mensaje.setSentDate(Date.from(correo.fecha()));
            mensaje.saveChanges(); // fija Message-ID y las cabeceras MIME
            return mensaje;
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalArgumentException("No se puede construir el correo: " + e.getMessage(), e);
        }
    }

    static String sanear(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\r\\n]+", " ").trim();
    }
}
