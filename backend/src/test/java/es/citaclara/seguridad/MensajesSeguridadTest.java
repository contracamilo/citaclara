package es.citaclara.seguridad;

import es.citaclara.comun.errores.Mensajes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-015: los mensajes visibles siguen en español de España, sin códigos ni jerga. */
class MensajesSeguridadTest {

    @Test
    @DisplayName("[005][FR-015] todo mensaje visible del API es una frase en español, sin códigos ni jerga técnica")
    void mensajesVisibles() throws IllegalAccessException {
        for (Field f : Mensajes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String texto = (String) f.get(null);
                assertThat(texto).as(f.getName()).endsWith(".").doesNotContainPattern("(?i)exception|sql|http|stack|\\b[45]\\d\\d\\b").doesNotContainPattern("[A-Z_]{5,}");
                assertThat(texto.split("\\s+").length).as(f.getName()).isGreaterThanOrEqualTo(4);
            }
        }
    }
}
