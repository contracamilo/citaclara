package es.citaclara.comun;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalización y validación de teléfonos (FR-019, research D11). Se quitan espacios, guiones y
 * puntos. Válido: número español de 9 cifras que empieza por 6, 7, 8 o 9 (con +34 opcional), o
 * internacional «+» y de 8 a 15 cifras. Se guarda como +34XXXXXXXXX o +XXXXXXXX….
 */
public final class Telefono {

    public static final String MENSAJE_NO_VALIDO = "Escribe un teléfono válido, por ejemplo 612 345 678.";

    private static final Pattern ESPANOL = Pattern.compile("[6789]\\d{8}");
    private static final Pattern INTERNACIONAL = Pattern.compile("\\+\\d{8,15}");

    private Telefono() {
    }

    public static Optional<String> normalizar(String entrada) {
        if (entrada == null) {
            return Optional.empty();
        }
        String limpio = entrada.replaceAll("[\\s.\\-]", "");
        if (limpio.startsWith("+34")) {
            String nacional = limpio.substring(3);
            return ESPANOL.matcher(nacional).matches() ? Optional.of("+34" + nacional) : Optional.empty();
        }
        if (ESPANOL.matcher(limpio).matches()) {
            return Optional.of("+34" + limpio);
        }
        if (INTERNACIONAL.matcher(limpio).matches()) {
            return Optional.of(limpio);
        }
        return Optional.empty();
    }

    /** Cifras de una búsqueda por teléfono, para compararlas con el teléfono normalizado. */
    public static String soloCifras(String entrada) {
        return entrada == null ? "" : entrada.replaceAll("\\D", "");
    }
}
