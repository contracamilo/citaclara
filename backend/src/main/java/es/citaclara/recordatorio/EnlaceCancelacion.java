package es.citaclara.recordatorio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Enlace de cancelación: 256 bits aleatorios en Base64 URL (43 caracteres) y su huella SHA-256 en
 * hexadecimal (64). En base de datos solo existe la huella (research D5, FR-012).
 */
public final class EnlaceCancelacion {

    public static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private EnlaceCancelacion() {
    }

    public static String nuevo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String huella(String enlace) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(enlace.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 está garantizado en toda JVM
        }
    }
}
