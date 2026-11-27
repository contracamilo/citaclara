package es.citaclara.portal.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el enlace después del commit y fuera de la petición (research R2, R3): la respuesta no espera
 * al SMTP (no delata si el email existe) y un fallo de envío no deshace la solicitud. Nunca registra
 * el email ni la URL (R13).
 */
@Component
public class EnvioEnlaces {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioEnlaces.class);

    private final CorreoPortal correo;

    public EnvioEnlaces(CorreoPortal correo) {
        this.correo = correo;
    }

    @Async(ConfiguracionCorreo.EJECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alSolicitarEnlace(EnlaceSolicitado evento) {
        try {
            correo.enviarEnlace(evento.email(), evento.nombreClinica(), evento.url());
        } catch (RuntimeException fallo) {
            LOG.warn("No se ha podido enviar un enlace de acceso al portal ({})", fallo.getClass().getSimpleName());
        }
    }
}
