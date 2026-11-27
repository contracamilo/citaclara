package es.citaclara.portal.acceso;

import es.citaclara.clinica.Clinica;
import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import es.citaclara.portal.correo.EnlaceSolicitado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Enlaces de acceso al portal (FR-001–FR-006, research R1 y R3). Nunca registra el email, el secreto
 * ni su hash (R13).
 */
@Service
public class ServicioEnlaces {

    static final Duration VALIDEZ = Duration.ofMinutes(10);
    static final Duration VENTANA_LIMITE = Duration.ofHours(1);
    static final int MAXIMO_POR_VENTANA = 5;
    static final Duration RETENCION = Duration.ofHours(24);

    private static final SecureRandom AZAR = new SecureRandom();

    private final RepositorioClinica clinicas;
    private final RepositorioSolicitudesEnlace solicitudes;
    private final RepositorioPacientesPortal pacientes;
    private final EmailPortal emails;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final String urlPublica;

    public ServicioEnlaces(RepositorioClinica clinicas, RepositorioSolicitudesEnlace solicitudes,
                           RepositorioPacientesPortal pacientes, EmailPortal emails,
                           ApplicationEventPublisher eventos, Clock reloj,
                           @Value("${citaclara.url-publica}") String urlPublica) {
        this.clinicas = clinicas;
        this.solicitudes = solicitudes;
        this.pacientes = pacientes;
        this.emails = emails;
        this.eventos = eventos;
        this.reloj = reloj;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    /**
     * Registra la solicitud y, solo si el email es de alguna ficha, publica el envío (que sale tras el
     * commit). La respuesta es la misma en ambos casos (FR-003); el límite cuenta también los emails
     * inexistentes (FR-004).
     */
    @Transactional
    public void pedir(String identificador, String email) {
        String normalizado = emails.validar(email);
        Clinica clinica = clinicas.bloquearPorIdentificador(identificador).orElseThrow(ErrorNegocio::noEncontrado);
        Instant ahora = reloj.instant();
        solicitudes.borrarAntiguas(clinica.getId(), normalizado, ahora.minus(RETENCION));
        if (solicitudes.contarDesde(clinica.getId(), normalizado, ahora.minus(VENTANA_LIMITE)) >= MAXIMO_POR_VENTANA) {
            throw new ErrorNegocio(HttpStatus.TOO_MANY_REQUESTS, "ENLACES_AGOTADOS", Mensajes.ENLACES_AGOTADOS);
        }
        String tokenHash = null;
        if (pacientes.existeConEmail(clinica.getId(), normalizado)) {
            String secreto = nuevoSecreto();
            tokenHash = hash(secreto);
            String url = urlPublica + "/" + clinica.getIdentificador() + "/mis-citas#enlace=" + secreto;
            eventos.publishEvent(new EnlaceSolicitado(normalizado, clinica.getNombre(), url));
        }
        solicitudes.insertar(clinica.getId(), normalizado, tokenHash, ahora, ahora.plus(VALIDEZ));
    }

    /** Consume el enlace y abre la sesión del portal; si no es válido, 401 {@code ENLACE_NO_VALIDO}. */
    @Transactional
    public void consumir(String identificador, String secreto, HttpServletRequest peticion) {
        Clinica clinica = clinicas.findByIdentificador(identificador).orElseThrow(this::enlaceNoValido);
        Instant ahora = reloj.instant();
        String email = solicitudes.consumir(clinica.getId(), hash(secreto), ahora).orElseThrow(this::enlaceNoValido);
        SesionPortal.abrir(peticion, clinica.getId(), email, ahora);
    }

    public void cerrar(HttpServletRequest peticion) {
        SesionPortal.cerrar(peticion);
    }

    private ErrorNegocio enlaceNoValido() {
        return new ErrorNegocio(HttpStatus.UNAUTHORIZED, "ENLACE_NO_VALIDO", Mensajes.ENLACE_NO_VALIDO);
    }

    private static String nuevoSecreto() {
        byte[] bytes = new byte[32]; // 256 bits
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String secreto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
