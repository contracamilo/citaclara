package es.citaclara.clinica;

import java.util.Locale;
import java.util.Map;

/**
 * Única tabla de etiquetas visibles de las especialidades (FR-027, Principio VIII). Ningún texto
 * visible usa el valor almacenado («nutricion»).
 */
public final class Especialidades {

    private static final Map<String, String> ETIQUETAS = Map.of(
            "fisioterapia", "Fisioterapia",
            "nutricion", "Nutrición");

    private Especialidades() {
    }

    /** Etiqueta con mayúscula inicial: «Nutrición». */
    public static String nombre(String valor) {
        String etiqueta = ETIQUETAS.get(valor);
        if (etiqueta != null) {
            return etiqueta;
        }
        return valor.isEmpty() ? valor : valor.substring(0, 1).toUpperCase(Locale.ROOT) + valor.substring(1);
    }

    /** Etiqueta en minúscula, para usarla dentro de una frase: «nutrición». */
    public static String enFrase(String valor) {
        return nombre(valor).toLowerCase(Locale.forLanguageTag("es-ES"));
    }
}
